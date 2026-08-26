use std::ffi::{CStr, c_void};
use std::marker::PhantomData;
use std::num::NonZeroU64;
use std::ptr::NonNull;
use std::rc::Rc;

use ash::{Entry, Instance, vk};
#[cfg(feature = "ffmpeg")]
use ffmpeg_next::ffi;
use raw_window_handle::{
    RawDisplayHandle, RawWindowHandle, WaylandDisplayHandle, WaylandWindowHandle,
    XlibDisplayHandle, XlibWindowHandle,
};

use crate::{ColorMatrix, ColorRange, DecodedVideoFrame, Error, PixelFormat, Result, Subsystem};

const PRESENT_WAIT_NS: u64 = 1_000_000_000;
const ACQUIRE_WAIT_NS: u64 = 50_000_000;

pub enum NativeSurface<'a> {
    X11 {
        display: NonNull<c_void>,
        window: NonZeroU64,
        screen: i32,
        _owner: PhantomData<&'a mut c_void>,
    },
    Wayland {
        display: NonNull<c_void>,
        surface: NonNull<c_void>,
        _owner: PhantomData<&'a mut c_void>,
    },
}

impl<'a> NativeSurface<'a> {
    /// Borrows an Xlib display and window owned by the caller.
    ///
    /// # Safety
    ///
    /// `display` and `window` must identify a live X11 surface, Xlib threading must be initialized
    /// when other threads use the display, and both handles must outlive the returned value and
    /// every presenter borrowing it. The backend never closes or destroys either handle.
    pub unsafe fn borrow_x11(display: NonNull<c_void>, window: NonZeroU64, screen: i32) -> Self {
        Self::X11 {
            display,
            window,
            screen,
            _owner: PhantomData,
        }
    }

    /// Borrows a Wayland display and wl_surface owned by the caller.
    ///
    /// # Safety
    ///
    /// Both pointers must remain live, the surface must be used on its owning Wayland thread, and
    /// they must outlive the returned value and every presenter borrowing it. The backend never
    /// disconnects the display or destroys the wl_surface.
    pub unsafe fn borrow_wayland(display: NonNull<c_void>, surface: NonNull<c_void>) -> Self {
        Self::Wayland {
            display,
            surface,
            _owner: PhantomData,
        }
    }

    fn raw_display_handle(&self) -> RawDisplayHandle {
        match self {
            Self::X11 {
                display, screen, ..
            } => RawDisplayHandle::Xlib(XlibDisplayHandle::new(Some(*display), *screen)),
            Self::Wayland { display, .. } => {
                RawDisplayHandle::Wayland(WaylandDisplayHandle::new(*display))
            }
        }
    }

    fn raw_window_handle(&self) -> RawWindowHandle {
        match self {
            Self::X11 { window, .. } => {
                RawWindowHandle::Xlib(XlibWindowHandle::new(window.get() as libc::c_ulong))
            }
            Self::Wayland { surface, .. } => {
                RawWindowHandle::Wayland(WaylandWindowHandle::new(*surface))
            }
        }
    }
}

pub struct VulkanPresenter {
    _entry: Entry,
    instance: Instance,
    surface_loader: ash::khr::surface::Instance,
    surface: vk::SurfaceKHR,
    physical_device: vk::PhysicalDevice,
    device: ash::Device,
    queue: vk::Queue,
    queue_family: u32,
    swapchain_loader: ash::khr::swapchain::Device,
    swapchain: vk::SwapchainKHR,
    images: Vec<vk::Image>,
    initialized_images: Vec<bool>,
    surface_format: vk::SurfaceFormatKHR,
    extent: vk::Extent2D,
    command_pool: vk::CommandPool,
    command_buffer: vk::CommandBuffer,
    image_available: vk::Semaphore,
    render_finished: Vec<vk::Semaphore>,
    in_flight: vk::Fence,
    staging_buffer: vk::Buffer,
    staging_memory: vk::DeviceMemory,
    staging_capacity: vk::DeviceSize,
    converter: FrameConverter,
    needs_reconfigure: bool,
    _thread_affinity: PhantomData<Rc<()>>,
}

impl VulkanPresenter {
    pub fn new(target: &NativeSurface<'_>, width: u32, height: u32) -> Result<Self> {
        validate_presentation_extent(width, height)?;
        let entry = unsafe { Entry::load() }
            .map_err(|error| Error::unavailable(Subsystem::Vulkan, error.to_string()))?;
        let application_name = c"OpenNOW";
        let application = vk::ApplicationInfo::default()
            .application_name(application_name)
            .application_version(vk::make_api_version(0, 0, 1, 0))
            .engine_name(application_name)
            .engine_version(vk::make_api_version(0, 0, 1, 0))
            .api_version(vk::API_VERSION_1_1);
        let extensions = ash_window::enumerate_required_extensions(target.raw_display_handle())
            .map_err(|error| vk_error("enumerate WSI extensions", error))?;
        let instance_info = vk::InstanceCreateInfo::default()
            .application_info(&application)
            .enabled_extension_names(extensions);
        let instance = unsafe { entry.create_instance(&instance_info, None) }
            .map_err(|error| vk_error("create instance", error))?;
        let surface = match unsafe {
            ash_window::create_surface(
                &entry,
                &instance,
                target.raw_display_handle(),
                target.raw_window_handle(),
                None,
            )
        } {
            Ok(surface) => surface,
            Err(error) => {
                unsafe { instance.destroy_instance(None) };
                return Err(vk_error("create native surface", error));
            }
        };
        let surface_loader = ash::khr::surface::Instance::new(&entry, &instance);
        let selected = select_device(&instance, &surface_loader, surface);
        let (physical_device, queue_family) = match selected {
            Ok(selected) => selected,
            Err(error) => {
                unsafe {
                    surface_loader.destroy_surface(surface, None);
                    instance.destroy_instance(None);
                }
                return Err(error);
            }
        };
        let priority = [1.0_f32];
        let queue_info = [vk::DeviceQueueCreateInfo::default()
            .queue_family_index(queue_family)
            .queue_priorities(&priority)];
        let device_extensions = [ash::khr::swapchain::NAME.as_ptr()];
        let device_info = vk::DeviceCreateInfo::default()
            .queue_create_infos(&queue_info)
            .enabled_extension_names(&device_extensions);
        let device = match unsafe { instance.create_device(physical_device, &device_info, None) } {
            Ok(device) => device,
            Err(error) => {
                unsafe {
                    surface_loader.destroy_surface(surface, None);
                    instance.destroy_instance(None);
                }
                return Err(vk_error("create logical device", error));
            }
        };
        let queue = unsafe { device.get_device_queue(queue_family, 0) };
        let swapchain_loader = ash::khr::swapchain::Device::new(&instance, &device);
        let (command_pool, command_buffer, image_available, in_flight) =
            match create_command_resources(&device, queue_family) {
                Ok(resources) => resources,
                Err(error) => {
                    unsafe {
                        device.destroy_device(None);
                        surface_loader.destroy_surface(surface, None);
                        instance.destroy_instance(None);
                    }
                    return Err(error);
                }
            };

        let mut presenter = Self {
            _entry: entry,
            instance,
            surface_loader,
            surface,
            physical_device,
            device,
            queue,
            queue_family,
            swapchain_loader,
            swapchain: vk::SwapchainKHR::null(),
            images: Vec::new(),
            initialized_images: Vec::new(),
            surface_format: vk::SurfaceFormatKHR::default(),
            extent: vk::Extent2D { width, height },
            command_pool,
            command_buffer,
            image_available,
            render_finished: Vec::new(),
            in_flight,
            staging_buffer: vk::Buffer::null(),
            staging_memory: vk::DeviceMemory::null(),
            staging_capacity: 0,
            converter: FrameConverter::new(),
            needs_reconfigure: false,
            _thread_affinity: PhantomData,
        };
        if let Err(error) = presenter.create_swapchain(width, height) {
            drop(presenter);
            return Err(error);
        }
        Ok(presenter)
    }

    pub fn extent(&self) -> (u32, u32) {
        (self.extent.width, self.extent.height)
    }

    pub fn reconfigure(&mut self, width: u32, height: u32) -> Result<()> {
        validate_presentation_extent(width, height)?;
        self.needs_reconfigure = true;
        unsafe { self.device.device_wait_idle() }
            .map_err(|error| vk_error("wait before swapchain rebuild", error))?;
        self.destroy_swapchain();
        self.create_swapchain(width, height)?;
        self.recreate_command_resources()?;
        self.needs_reconfigure = false;
        Ok(())
    }

    /// Presents one frame, returning `false` when the compositor temporarily
    /// has no swapchain image available. A WSI timeout is normal while a
    /// Wayland/X11 surface is being hidden or rearranged and must not tear
    /// down the media session.
    pub fn present(&mut self, frame: &DecodedVideoFrame) -> Result<bool> {
        frame.validate()?;
        if self.needs_reconfigure {
            return Err(Error::backend(
                Subsystem::Vulkan,
                "presentation synchronization is invalid; call VulkanPresenter::reconfigure",
            ));
        }
        unsafe {
            self.device
                .wait_for_fences(&[self.in_flight], true, PRESENT_WAIT_NS)
        }
        .map_err(|error| vk_error("wait for presentation", error))?;
        self.converter
            .convert(frame, self.extent, self.surface_format.format)?;
        let rgba_len = self.converter.output().len();
        self.ensure_staging(rgba_len as vk::DeviceSize)?;
        let mapped = unsafe {
            self.device.map_memory(
                self.staging_memory,
                0,
                rgba_len as vk::DeviceSize,
                vk::MemoryMapFlags::empty(),
            )
        }
        .map_err(|error| vk_error("map staging memory", error))?;
        let rgba = self.converter.output();
        unsafe {
            std::ptr::copy_nonoverlapping(rgba.as_ptr(), mapped.cast(), rgba.len());
            self.device.unmap_memory(self.staging_memory);
        }

        let (image_index, acquire_suboptimal) = match unsafe {
            self.swapchain_loader.acquire_next_image(
                self.swapchain,
                ACQUIRE_WAIT_NS,
                self.image_available,
                vk::Fence::null(),
            )
        } {
            Ok(result) => result,
            Err(vk::Result::ERROR_OUT_OF_DATE_KHR) => {
                self.needs_reconfigure = true;
                return Err(Error::backend(
                    Subsystem::Vulkan,
                    "surface is out of date; call VulkanPresenter::reconfigure",
                ));
            }
            Err(vk::Result::TIMEOUT) | Err(vk::Result::NOT_READY) => return Ok(false),
            Err(error) => return Err(vk_error("acquire swapchain image", error)),
        };
        let image_index = image_index as usize;
        if image_index >= self.images.len()
            || image_index >= self.initialized_images.len()
            || image_index >= self.render_finished.len()
        {
            self.needs_reconfigure = true;
            return Err(Error::backend(
                Subsystem::Vulkan,
                "Vulkan returned an invalid swapchain image index",
            ));
        }
        unsafe {
            if let Err(error) = self
                .device
                .reset_command_buffer(self.command_buffer, vk::CommandBufferResetFlags::empty())
            {
                self.needs_reconfigure = true;
                return Err(vk_error("reset presentation command buffer", error));
            }
            let begin = vk::CommandBufferBeginInfo::default()
                .flags(vk::CommandBufferUsageFlags::ONE_TIME_SUBMIT);
            if let Err(error) = self
                .device
                .begin_command_buffer(self.command_buffer, &begin)
            {
                self.needs_reconfigure = true;
                return Err(vk_error("begin presentation command buffer", error));
            }
            let old_layout = if self.initialized_images[image_index] {
                vk::ImageLayout::PRESENT_SRC_KHR
            } else {
                vk::ImageLayout::UNDEFINED
            };
            let to_transfer = vk::ImageMemoryBarrier::default()
                .old_layout(old_layout)
                .new_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
                .src_access_mask(vk::AccessFlags::empty())
                .dst_access_mask(vk::AccessFlags::TRANSFER_WRITE)
                .image(self.images[image_index])
                .subresource_range(color_range());
            self.device.cmd_pipeline_barrier(
                self.command_buffer,
                vk::PipelineStageFlags::TOP_OF_PIPE,
                vk::PipelineStageFlags::TRANSFER,
                vk::DependencyFlags::empty(),
                &[],
                &[],
                &[to_transfer],
            );
            let copy = vk::BufferImageCopy::default()
                .image_subresource(
                    vk::ImageSubresourceLayers::default()
                        .aspect_mask(vk::ImageAspectFlags::COLOR)
                        .layer_count(1),
                )
                .image_extent(vk::Extent3D {
                    width: self.extent.width,
                    height: self.extent.height,
                    depth: 1,
                });
            self.device.cmd_copy_buffer_to_image(
                self.command_buffer,
                self.staging_buffer,
                self.images[image_index],
                vk::ImageLayout::TRANSFER_DST_OPTIMAL,
                &[copy],
            );
            let to_present = vk::ImageMemoryBarrier::default()
                .old_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
                .new_layout(vk::ImageLayout::PRESENT_SRC_KHR)
                .src_access_mask(vk::AccessFlags::TRANSFER_WRITE)
                .dst_access_mask(vk::AccessFlags::empty())
                .image(self.images[image_index])
                .subresource_range(color_range());
            self.device.cmd_pipeline_barrier(
                self.command_buffer,
                vk::PipelineStageFlags::TRANSFER,
                vk::PipelineStageFlags::BOTTOM_OF_PIPE,
                vk::DependencyFlags::empty(),
                &[],
                &[],
                &[to_present],
            );
            if let Err(error) = self.device.end_command_buffer(self.command_buffer) {
                self.needs_reconfigure = true;
                return Err(vk_error("end presentation command buffer", error));
            }
            let wait_semaphores = [self.image_available];
            let wait_stages = [vk::PipelineStageFlags::TRANSFER];
            let command_buffers = [self.command_buffer];
            let signal_semaphores = [self.render_finished[image_index]];
            let submit = [vk::SubmitInfo::default()
                .wait_semaphores(&wait_semaphores)
                .wait_dst_stage_mask(&wait_stages)
                .command_buffers(&command_buffers)
                .signal_semaphores(&signal_semaphores)];
            if let Err(error) = self.device.reset_fences(&[self.in_flight]) {
                self.needs_reconfigure = true;
                return Err(vk_error("reset presentation fence", error));
            }
            if let Err(error) = self
                .device
                .queue_submit(self.queue, &submit, self.in_flight)
            {
                self.needs_reconfigure = true;
                return Err(vk_error("submit presentation command buffer", error));
            }
            self.initialized_images[image_index] = true;
            let swapchains = [self.swapchain];
            let image_indices = [image_index as u32];
            let present = vk::PresentInfoKHR::default()
                .wait_semaphores(&signal_semaphores)
                .swapchains(&swapchains)
                .image_indices(&image_indices);
            match self.swapchain_loader.queue_present(self.queue, &present) {
                Ok(false) => {}
                Ok(true) | Err(vk::Result::ERROR_OUT_OF_DATE_KHR) => {
                    self.needs_reconfigure = true;
                    return Err(Error::backend(
                        Subsystem::Vulkan,
                        "surface changed; call VulkanPresenter::reconfigure",
                    ));
                }
                Err(error) => {
                    self.needs_reconfigure = true;
                    return Err(vk_error("present swapchain image", error));
                }
            }
        }
        if acquire_suboptimal {
            self.needs_reconfigure = true;
            return Err(Error::backend(
                Subsystem::Vulkan,
                "surface is suboptimal; call VulkanPresenter::reconfigure",
            ));
        }
        Ok(true)
    }

    fn create_swapchain(&mut self, requested_width: u32, requested_height: u32) -> Result<()> {
        let capabilities = unsafe {
            self.surface_loader
                .get_physical_device_surface_capabilities(self.physical_device, self.surface)
        }
        .map_err(|error| vk_error("query surface capabilities", error))?;
        if !capabilities
            .supported_usage_flags
            .contains(vk::ImageUsageFlags::TRANSFER_DST)
        {
            return Err(Error::unavailable(
                Subsystem::Vulkan,
                "surface swapchain images do not support transfer-destination usage",
            ));
        }
        let formats = unsafe {
            self.surface_loader
                .get_physical_device_surface_formats(self.physical_device, self.surface)
        }
        .map_err(|error| vk_error("query surface formats", error))?;
        let present_modes = unsafe {
            self.surface_loader
                .get_physical_device_surface_present_modes(self.physical_device, self.surface)
        }
        .map_err(|error| vk_error("query surface present modes", error))?;
        let present_mode = choose_present_mode(&present_modes);
        self.surface_format = choose_surface_format(&formats)?;
        self.extent = choose_extent(capabilities, requested_width, requested_height);
        validate_presentation_extent(self.extent.width, self.extent.height)?;
        let mut image_count = capabilities.min_image_count.saturating_add(1);
        if capabilities.max_image_count > 0 {
            image_count = image_count.min(capabilities.max_image_count);
        }
        let create = vk::SwapchainCreateInfoKHR::default()
            .surface(self.surface)
            .min_image_count(image_count)
            .image_format(self.surface_format.format)
            .image_color_space(self.surface_format.color_space)
            .image_extent(self.extent)
            .image_array_layers(1)
            .image_usage(vk::ImageUsageFlags::TRANSFER_DST)
            .image_sharing_mode(vk::SharingMode::EXCLUSIVE)
            .pre_transform(capabilities.current_transform)
            .composite_alpha(choose_composite_alpha(
                capabilities.supported_composite_alpha,
            ))
            .present_mode(present_mode)
            .clipped(true);
        self.swapchain = unsafe { self.swapchain_loader.create_swapchain(&create, None) }
            .map_err(|error| vk_error("create swapchain", error))?;
        self.images = unsafe { self.swapchain_loader.get_swapchain_images(self.swapchain) }
            .map_err(|error| vk_error("get swapchain images", error))?;
        eprintln!(
            "Vulkan presenter configured: mode={present_mode:?} images={} extent={}x{}",
            self.images.len(),
            self.extent.width,
            self.extent.height
        );
        let semaphore_info = vk::SemaphoreCreateInfo::default();
        let mut render_finished = Vec::with_capacity(self.images.len());
        for _ in &self.images {
            match unsafe { self.device.create_semaphore(&semaphore_info, None) } {
                Ok(semaphore) => render_finished.push(semaphore),
                Err(error) => {
                    for semaphore in render_finished {
                        unsafe { self.device.destroy_semaphore(semaphore, None) };
                    }
                    return Err(vk_error("create per-image render semaphore", error));
                }
            }
        }
        self.render_finished = render_finished;
        self.initialized_images = vec![false; self.images.len()];
        Ok(())
    }

    fn recreate_command_resources(&mut self) -> Result<()> {
        unsafe {
            if self.in_flight != vk::Fence::null() {
                self.device.destroy_fence(self.in_flight, None);
            }
            if self.image_available != vk::Semaphore::null() {
                self.device.destroy_semaphore(self.image_available, None);
            }
            if self.command_pool != vk::CommandPool::null() {
                self.device.destroy_command_pool(self.command_pool, None);
            }
        }
        self.command_pool = vk::CommandPool::null();
        self.command_buffer = vk::CommandBuffer::null();
        self.image_available = vk::Semaphore::null();
        self.in_flight = vk::Fence::null();
        let (command_pool, command_buffer, image_available, in_flight) =
            create_command_resources(&self.device, self.queue_family)?;
        self.command_pool = command_pool;
        self.command_buffer = command_buffer;
        self.image_available = image_available;
        self.in_flight = in_flight;
        Ok(())
    }

    fn destroy_swapchain(&mut self) {
        for semaphore in self.render_finished.drain(..) {
            unsafe { self.device.destroy_semaphore(semaphore, None) };
        }
        if self.swapchain != vk::SwapchainKHR::null() {
            unsafe {
                self.swapchain_loader
                    .destroy_swapchain(self.swapchain, None)
            };
            self.swapchain = vk::SwapchainKHR::null();
            self.images.clear();
            self.initialized_images.clear();
        }
    }

    fn ensure_staging(&mut self, required: vk::DeviceSize) -> Result<()> {
        if required <= self.staging_capacity {
            return Ok(());
        }
        unsafe { self.device.device_wait_idle() }
            .map_err(|error| vk_error("wait before staging resize", error))?;
        if self.staging_buffer != vk::Buffer::null() {
            unsafe {
                self.device.destroy_buffer(self.staging_buffer, None);
                self.device.free_memory(self.staging_memory, None);
            }
            self.staging_buffer = vk::Buffer::null();
            self.staging_memory = vk::DeviceMemory::null();
            self.staging_capacity = 0;
        }
        let buffer_info = vk::BufferCreateInfo::default()
            .size(required)
            .usage(vk::BufferUsageFlags::TRANSFER_SRC)
            .sharing_mode(vk::SharingMode::EXCLUSIVE);
        let staging_buffer = unsafe { self.device.create_buffer(&buffer_info, None) }
            .map_err(|error| vk_error("create staging buffer", error))?;
        let requirements = unsafe { self.device.get_buffer_memory_requirements(staging_buffer) };
        let memory_type = match find_memory_type(
            &self.instance,
            self.physical_device,
            requirements.memory_type_bits,
            vk::MemoryPropertyFlags::HOST_VISIBLE | vk::MemoryPropertyFlags::HOST_COHERENT,
        ) {
            Ok(memory_type) => memory_type,
            Err(error) => {
                unsafe { self.device.destroy_buffer(staging_buffer, None) };
                return Err(error);
            }
        };
        let allocation = vk::MemoryAllocateInfo::default()
            .allocation_size(requirements.size)
            .memory_type_index(memory_type);
        let staging_memory = match unsafe { self.device.allocate_memory(&allocation, None) } {
            Ok(memory) => memory,
            Err(error) => {
                unsafe { self.device.destroy_buffer(staging_buffer, None) };
                return Err(vk_error("allocate staging memory", error));
            }
        };
        if let Err(error) = unsafe {
            self.device
                .bind_buffer_memory(staging_buffer, staging_memory, 0)
        } {
            unsafe {
                self.device.destroy_buffer(staging_buffer, None);
                self.device.free_memory(staging_memory, None);
            }
            return Err(vk_error("bind staging memory", error));
        }
        self.staging_buffer = staging_buffer;
        self.staging_memory = staging_memory;
        self.staging_capacity = requirements.size;
        Ok(())
    }
}

fn create_command_resources(
    device: &ash::Device,
    queue_family: u32,
) -> Result<(vk::CommandPool, vk::CommandBuffer, vk::Semaphore, vk::Fence)> {
    let command_pool_info = vk::CommandPoolCreateInfo::default()
        .queue_family_index(queue_family)
        .flags(vk::CommandPoolCreateFlags::RESET_COMMAND_BUFFER);
    let command_pool = unsafe { device.create_command_pool(&command_pool_info, None) }
        .map_err(|error| vk_error("create command pool", error))?;
    let command_info = vk::CommandBufferAllocateInfo::default()
        .command_pool(command_pool)
        .level(vk::CommandBufferLevel::PRIMARY)
        .command_buffer_count(1);
    let command_buffer = match unsafe { device.allocate_command_buffers(&command_info) } {
        Ok(buffers) => buffers[0],
        Err(error) => {
            unsafe { device.destroy_command_pool(command_pool, None) };
            return Err(vk_error("allocate command buffer", error));
        }
    };
    let semaphore_info = vk::SemaphoreCreateInfo::default();
    let image_available = match unsafe { device.create_semaphore(&semaphore_info, None) } {
        Ok(semaphore) => semaphore,
        Err(error) => {
            unsafe { device.destroy_command_pool(command_pool, None) };
            return Err(vk_error("create acquire semaphore", error));
        }
    };
    let fence_info = vk::FenceCreateInfo::default().flags(vk::FenceCreateFlags::SIGNALED);
    let in_flight = match unsafe { device.create_fence(&fence_info, None) } {
        Ok(fence) => fence,
        Err(error) => {
            unsafe {
                device.destroy_semaphore(image_available, None);
                device.destroy_command_pool(command_pool, None);
            }
            return Err(vk_error("create presentation fence", error));
        }
    };
    Ok((command_pool, command_buffer, image_available, in_flight))
}

impl Drop for VulkanPresenter {
    fn drop(&mut self) {
        unsafe {
            let _ = self.device.device_wait_idle();
        }
        self.destroy_swapchain();
        unsafe {
            if self.staging_buffer != vk::Buffer::null() {
                self.device.destroy_buffer(self.staging_buffer, None);
                self.device.free_memory(self.staging_memory, None);
            }
            if self.in_flight != vk::Fence::null() {
                self.device.destroy_fence(self.in_flight, None);
            }
            if self.image_available != vk::Semaphore::null() {
                self.device.destroy_semaphore(self.image_available, None);
            }
            if self.command_pool != vk::CommandPool::null() {
                self.device.destroy_command_pool(self.command_pool, None);
            }
            self.device.destroy_device(None);
            self.surface_loader.destroy_surface(self.surface, None);
            self.instance.destroy_instance(None);
        }
    }
}

pub(crate) fn probe_vulkan() -> std::result::Result<(Vec<&'static str>, String), String> {
    let entry = unsafe { Entry::load() }.map_err(|error| error.to_string())?;
    let extensions = unsafe { entry.enumerate_instance_extension_properties(None) }
        .map_err(|error| error.to_string())?;
    let has = |name: &CStr| {
        extensions
            .iter()
            .any(|extension| (unsafe { CStr::from_ptr(extension.extension_name.as_ptr()) }) == name)
    };
    if !has(ash::khr::surface::NAME) {
        return Err("Vulkan loader lacks VK_KHR_surface".to_owned());
    }
    let mut window_systems = Vec::new();
    let mut extension_names = vec![ash::khr::surface::NAME.as_ptr()];
    if has(ash::khr::xlib_surface::NAME) {
        window_systems.push("x11");
        extension_names.push(ash::khr::xlib_surface::NAME.as_ptr());
    }
    if has(ash::khr::wayland_surface::NAME) {
        window_systems.push("wayland");
        extension_names.push(ash::khr::wayland_surface::NAME.as_ptr());
    }
    if window_systems.is_empty() {
        return Err("Vulkan loader has neither Xlib nor Wayland WSI".to_owned());
    }
    let create = vk::InstanceCreateInfo::default().enabled_extension_names(&extension_names);
    let instance =
        unsafe { entry.create_instance(&create, None) }.map_err(|error| error.to_string())?;
    let devices = match unsafe { instance.enumerate_physical_devices() } {
        Ok(devices) => devices,
        Err(error) => {
            unsafe { instance.destroy_instance(None) };
            return Err(error.to_string());
        }
    };
    let usable_devices = devices
        .into_iter()
        .filter(|device| {
            supports_swapchain(&instance, *device).unwrap_or(false)
                && unsafe { instance.get_physical_device_queue_family_properties(*device) }
                    .iter()
                    .any(|queue| queue.queue_flags.contains(vk::QueueFlags::GRAPHICS))
        })
        .count();
    unsafe { instance.destroy_instance(None) };
    if usable_devices == 0 {
        Err("Vulkan found no graphics device with VK_KHR_swapchain".to_owned())
    } else {
        let detail = format!(
            "{} Vulkan physical device(s), {} WSI",
            usable_devices,
            window_systems.join(" and ")
        );
        Ok((window_systems, detail))
    }
}

fn select_device(
    instance: &Instance,
    surface_loader: &ash::khr::surface::Instance,
    surface: vk::SurfaceKHR,
) -> Result<(vk::PhysicalDevice, u32)> {
    let devices = unsafe { instance.enumerate_physical_devices() }
        .map_err(|error| vk_error("enumerate physical devices", error))?;
    for device in devices {
        if !supports_swapchain(instance, device)
            .map_err(|error| vk_error("enumerate device extensions", error))?
        {
            continue;
        }
        let queues = unsafe { instance.get_physical_device_queue_family_properties(device) };
        for (index, properties) in queues.iter().enumerate() {
            if !properties.queue_flags.contains(vk::QueueFlags::GRAPHICS) {
                continue;
            }
            let present = unsafe {
                surface_loader.get_physical_device_surface_support(device, index as u32, surface)
            }
            .map_err(|error| vk_error("query queue presentation support", error))?;
            if present {
                return Ok((device, index as u32));
            }
        }
    }
    Err(Error::unavailable(
        Subsystem::Vulkan,
        "no graphics queue can present to the supplied native surface",
    ))
}

fn supports_swapchain(
    instance: &Instance,
    device: vk::PhysicalDevice,
) -> std::result::Result<bool, vk::Result> {
    let extensions = unsafe { instance.enumerate_device_extension_properties(device) }?;
    Ok(extensions.iter().any(|extension| {
        (unsafe { CStr::from_ptr(extension.extension_name.as_ptr()) }) == ash::khr::swapchain::NAME
    }))
}

fn choose_surface_format(formats: &[vk::SurfaceFormatKHR]) -> Result<vk::SurfaceFormatKHR> {
    for desired in [
        vk::Format::B8G8R8A8_UNORM,
        vk::Format::B8G8R8A8_SRGB,
        vk::Format::R8G8B8A8_UNORM,
        vk::Format::R8G8B8A8_SRGB,
    ] {
        if let Some(format) = formats.iter().find(|format| format.format == desired) {
            return Ok(*format);
        }
    }
    Err(Error::unavailable(
        Subsystem::Vulkan,
        "surface has no 8-bit BGRA or RGBA transfer-destination format",
    ))
}

fn choose_extent(
    capabilities: vk::SurfaceCapabilitiesKHR,
    width: u32,
    height: u32,
) -> vk::Extent2D {
    if capabilities.current_extent.width != u32::MAX {
        return capabilities.current_extent;
    }
    vk::Extent2D {
        width: width.clamp(
            capabilities.min_image_extent.width,
            capabilities.max_image_extent.width,
        ),
        height: height.clamp(
            capabilities.min_image_extent.height,
            capabilities.max_image_extent.height,
        ),
    }
}

fn choose_composite_alpha(supported: vk::CompositeAlphaFlagsKHR) -> vk::CompositeAlphaFlagsKHR {
    [
        vk::CompositeAlphaFlagsKHR::OPAQUE,
        vk::CompositeAlphaFlagsKHR::PRE_MULTIPLIED,
        vk::CompositeAlphaFlagsKHR::POST_MULTIPLIED,
        vk::CompositeAlphaFlagsKHR::INHERIT,
    ]
    .into_iter()
    .find(|mode| supported.contains(*mode))
    .unwrap_or(vk::CompositeAlphaFlagsKHR::OPAQUE)
}

/// FIFO blocks `acquire_next_image` on the compositor's presentation cadence
/// and can fall into half-refresh pacing when conversion misses a vblank.
/// Mailbox keeps only the newest complete frame without tearing; immediate is
/// the low-latency fallback on WSI implementations that lack mailbox support.
fn choose_present_mode(supported: &[vk::PresentModeKHR]) -> vk::PresentModeKHR {
    [vk::PresentModeKHR::MAILBOX, vk::PresentModeKHR::IMMEDIATE]
        .into_iter()
        .find(|mode| supported.contains(mode))
        .unwrap_or(vk::PresentModeKHR::FIFO)
}

fn find_memory_type(
    instance: &Instance,
    physical_device: vk::PhysicalDevice,
    type_bits: u32,
    required: vk::MemoryPropertyFlags,
) -> Result<u32> {
    let properties = unsafe { instance.get_physical_device_memory_properties(physical_device) };
    (0..properties.memory_type_count)
        .find(|index| {
            type_bits & (1 << index) != 0
                && properties.memory_types[*index as usize]
                    .property_flags
                    .contains(required)
        })
        .ok_or_else(|| {
            Error::unavailable(
                Subsystem::Vulkan,
                "no coherent host-visible memory for the presentation staging buffer",
            )
        })
}

fn color_range() -> vk::ImageSubresourceRange {
    vk::ImageSubresourceRange::default()
        .aspect_mask(vk::ImageAspectFlags::COLOR)
        .level_count(1)
        .layer_count(1)
}

struct FrameConverter {
    #[cfg(feature = "ffmpeg")]
    context: *mut ffi::SwsContext,
    output: Vec<u8>,
}

impl FrameConverter {
    fn new() -> Self {
        Self {
            #[cfg(feature = "ffmpeg")]
            context: std::ptr::null_mut(),
            output: Vec::new(),
        }
    }

    fn output(&self) -> &[u8] {
        &self.output
    }

    #[cfg(feature = "ffmpeg")]
    fn convert(
        &mut self,
        frame: &DecodedVideoFrame,
        extent: vk::Extent2D,
        surface_format: vk::Format,
    ) -> Result<()> {
        frame.validate()?;
        let output_len = (extent.width as usize)
            .checked_mul(extent.height as usize)
            .and_then(|pixels| pixels.checked_mul(4))
            .ok_or_else(|| Error::InvalidFormat("presentation buffer size overflow".to_owned()))?;
        self.output
            .try_reserve(output_len.saturating_sub(self.output.len()))
            .map_err(|error| {
                Error::backend(
                    Subsystem::Vulkan,
                    format!("allocate presentation buffer: {error}"),
                )
            })?;
        self.output.resize(output_len, 0);

        let (offset_x, offset_y, target_width, target_height) = aspect_fit_extent(
            frame.format.width,
            frame.format.height,
            extent.width,
            extent.height,
        );
        if offset_x != 0
            || offset_y != 0
            || target_width != extent.width
            || target_height != extent.height
        {
            self.output.fill(0);
            for alpha in self.output[3..].iter_mut().step_by(4) {
                *alpha = 255;
            }
        }

        let source_format = match frame.format.pixel_format {
            PixelFormat::Nv12 => ffi::AVPixelFormat::AV_PIX_FMT_NV12,
            PixelFormat::I420 => ffi::AVPixelFormat::AV_PIX_FMT_YUV420P,
            PixelFormat::Bgra8 => ffi::AVPixelFormat::AV_PIX_FMT_BGRA,
            PixelFormat::Rgba8 => ffi::AVPixelFormat::AV_PIX_FMT_RGBA,
        };
        let destination_format = if matches!(
            surface_format,
            vk::Format::B8G8R8A8_UNORM | vk::Format::B8G8R8A8_SRGB
        ) {
            ffi::AVPixelFormat::AV_PIX_FMT_BGRA
        } else {
            ffi::AVPixelFormat::AV_PIX_FMT_RGBA
        };
        let context = unsafe {
            ffi::sws_getCachedContext(
                self.context,
                frame.format.width as i32,
                frame.format.height as i32,
                source_format,
                target_width as i32,
                target_height as i32,
                destination_format,
                ffi::SwsFlags::SWS_FAST_BILINEAR as i32,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null(),
            )
        };
        if context.is_null() {
            // sws_getCachedContext may free the previous context while trying
            // to replace it, so never retain the old pointer on failure.
            self.context = std::ptr::null_mut();
            return Err(Error::backend(
                Subsystem::Vulkan,
                "FFmpeg could not create the accelerated color converter",
            ));
        }
        self.context = context;

        if matches!(
            frame.format.pixel_format,
            PixelFormat::Nv12 | PixelFormat::I420
        ) {
            let colorspace = match frame.format.color_matrix {
                ColorMatrix::Bt601 => ffi::SWS_CS_ITU601,
                ColorMatrix::Bt709 => ffi::SWS_CS_ITU709,
                ColorMatrix::Bt2020 => ffi::SWS_CS_BT2020,
            };
            let coefficients = unsafe { ffi::sws_getCoefficients(colorspace) };
            let source_range = i32::from(frame.format.color_range == ColorRange::Full);
            let color_result = unsafe {
                ffi::sws_setColorspaceDetails(
                    self.context,
                    coefficients,
                    source_range,
                    coefficients,
                    1,
                    0,
                    1 << 16,
                    1 << 16,
                )
            };
            if color_result < 0 {
                return Err(Error::backend(
                    Subsystem::Vulkan,
                    format!("FFmpeg color conversion setup failed: {color_result}"),
                ));
            }
        }

        let mut source_data = [std::ptr::null(); 4];
        let mut source_stride = [0_i32; 4];
        for (index, plane) in frame.planes.iter().enumerate() {
            source_data[index] = plane.data.as_ptr();
            source_stride[index] = i32::try_from(plane.stride).map_err(|_| {
                Error::InvalidFormat("video plane stride exceeds FFmpeg limits".to_owned())
            })?;
        }
        let destination_offset = ((offset_y * extent.width + offset_x) * 4) as usize;
        let destination_data = unsafe { self.output.as_mut_ptr().add(destination_offset) };
        let destination = [
            destination_data,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            std::ptr::null_mut(),
        ];
        let destination_stride = [
            i32::try_from(extent.width as usize * 4).map_err(|_| {
                Error::InvalidFormat("presentation stride exceeds FFmpeg limits".to_owned())
            })?,
            0,
            0,
            0,
        ];
        let rows = unsafe {
            ffi::sws_scale(
                self.context,
                source_data.as_ptr(),
                source_stride.as_ptr(),
                0,
                frame.format.height as i32,
                destination.as_ptr(),
                destination_stride.as_ptr(),
            )
        };
        if rows != target_height as i32 {
            return Err(Error::backend(
                Subsystem::Vulkan,
                format!("FFmpeg converted {rows} rows, expected {target_height}"),
            ));
        }
        Ok(())
    }

    #[cfg(not(feature = "ffmpeg"))]
    fn convert(
        &mut self,
        frame: &DecodedVideoFrame,
        extent: vk::Extent2D,
        surface_format: vk::Format,
    ) -> Result<()> {
        self.output = convert_frame(frame, extent, surface_format)?;
        Ok(())
    }
}

#[cfg(feature = "ffmpeg")]
impl Drop for FrameConverter {
    fn drop(&mut self) {
        if !self.context.is_null() {
            unsafe { ffi::sws_freeContext(self.context) };
            self.context = std::ptr::null_mut();
        }
    }
}

#[cfg(not(feature = "ffmpeg"))]
fn convert_frame(
    frame: &DecodedVideoFrame,
    extent: vk::Extent2D,
    surface_format: vk::Format,
) -> Result<Vec<u8>> {
    let bgra = matches!(
        surface_format,
        vk::Format::B8G8R8A8_UNORM | vk::Format::B8G8R8A8_SRGB
    );
    let output_len = (extent.width as usize)
        .checked_mul(extent.height as usize)
        .and_then(|pixels| pixels.checked_mul(4))
        .ok_or_else(|| Error::InvalidFormat("presentation buffer size overflow".to_owned()))?;
    let mut output = Vec::new();
    output.try_reserve_exact(output_len).map_err(|error| {
        Error::backend(
            Subsystem::Vulkan,
            format!("allocate presentation buffer: {error}"),
        )
    })?;
    output.resize(output_len, 0);
    for pixel in output.chunks_exact_mut(4) {
        pixel[3] = 255;
    }
    let (offset_x, offset_y, target_width, target_height) = aspect_fit_extent(
        frame.format.width,
        frame.format.height,
        extent.width,
        extent.height,
    );
    for relative_y in 0..target_height as usize {
        let source_y = relative_y * frame.format.height as usize / target_height as usize;
        let target_y = offset_y as usize + relative_y;
        for relative_x in 0..target_width as usize {
            let source_x = relative_x * frame.format.width as usize / target_width as usize;
            let target_x = offset_x as usize + relative_x;
            let (r, g, b) = sample_rgb(frame, source_x, source_y)?;
            let offset = (target_y * extent.width as usize + target_x) * 4;
            if bgra {
                output[offset..offset + 4].copy_from_slice(&[b, g, r, 255]);
            } else {
                output[offset..offset + 4].copy_from_slice(&[r, g, b, 255]);
            }
        }
    }
    Ok(output)
}

fn aspect_fit_extent(
    source_width: u32,
    source_height: u32,
    target_width: u32,
    target_height: u32,
) -> (u32, u32, u32, u32) {
    let scale = (target_width as f64 / source_width as f64)
        .min(target_height as f64 / source_height as f64);
    let width = ((source_width as f64 * scale).round().max(1.0) as u32).min(target_width);
    let height = ((source_height as f64 * scale).round().max(1.0) as u32).min(target_height);
    (
        (target_width - width) / 2,
        (target_height - height) / 2,
        width,
        height,
    )
}

#[cfg(not(feature = "ffmpeg"))]
fn sample_rgb(frame: &DecodedVideoFrame, x: usize, y: usize) -> Result<(u8, u8, u8)> {
    if matches!(
        frame.format.pixel_format,
        PixelFormat::Bgra8 | PixelFormat::Rgba8
    ) {
        let plane = &frame.planes[0];
        let offset = y * plane.stride + x * 4;
        let pixel = &plane.data[offset..offset + 4];
        return Ok(if frame.format.pixel_format == PixelFormat::Bgra8 {
            (pixel[2], pixel[1], pixel[0])
        } else {
            (pixel[0], pixel[1], pixel[2])
        });
    }
    let y_sample = frame.planes[0].data[y * frame.planes[0].stride + x];
    let (u_sample, v_sample) = match frame.format.pixel_format {
        PixelFormat::Nv12 => {
            let offset = (y / 2) * frame.planes[1].stride + (x / 2) * 2;
            (
                frame.planes[1].data[offset],
                frame.planes[1].data[offset + 1],
            )
        }
        PixelFormat::I420 => (
            frame.planes[1].data[(y / 2) * frame.planes[1].stride + x / 2],
            frame.planes[2].data[(y / 2) * frame.planes[2].stride + x / 2],
        ),
        _ => {
            return Err(Error::InvalidFormat(
                "unsupported presentation format".to_owned(),
            ));
        }
    };
    Ok(yuv_to_rgb(
        y_sample,
        u_sample,
        v_sample,
        frame.format.color_matrix,
        frame.format.color_range,
    ))
}

#[cfg(any(not(feature = "ffmpeg"), test))]
fn yuv_to_rgb(y: u8, u: u8, v: u8, matrix: ColorMatrix, range: ColorRange) -> (u8, u8, u8) {
    let (y, u, v) = match range {
        ColorRange::Limited => (
            ((y as f32 - 16.0) * (255.0 / 219.0)).max(0.0),
            (u as f32 - 128.0) * (255.0 / 224.0),
            (v as f32 - 128.0) * (255.0 / 224.0),
        ),
        ColorRange::Full => (y as f32, u as f32 - 128.0, v as f32 - 128.0),
    };
    let (r, g, b) = match matrix {
        ColorMatrix::Bt601 => (
            y + 1.402 * v,
            y - 0.344_136 * u - 0.714_136 * v,
            y + 1.772 * u,
        ),
        ColorMatrix::Bt709 => (
            y + 1.5748 * v,
            y - 0.187_324 * u - 0.468_124 * v,
            y + 1.8556 * u,
        ),
        ColorMatrix::Bt2020 => (
            y + 1.4746 * v,
            y - 0.164_553 * u - 0.571_353 * v,
            y + 1.8814 * u,
        ),
    };
    (clamp_u8(r), clamp_u8(g), clamp_u8(b))
}

#[cfg(any(not(feature = "ffmpeg"), test))]
fn clamp_u8(value: f32) -> u8 {
    value.round().clamp(0.0, 255.0) as u8
}

fn validate_presentation_extent(width: u32, height: u32) -> Result<()> {
    if width == 0 || height == 0 {
        return Err(Error::InvalidFormat(
            "presentation extent must be non-zero".to_owned(),
        ));
    }
    if width > 16_384 || height > 16_384 {
        return Err(Error::InvalidFormat(
            "presentation extent exceeds the backend limit".to_owned(),
        ));
    }
    Ok(())
}

fn vk_error(operation: &str, error: vk::Result) -> Error {
    if error == vk::Result::ERROR_DEVICE_LOST {
        return Error::DeviceLost {
            subsystem: Subsystem::Vulkan,
            reason: format!("{operation}: {error:?}"),
        };
    }
    Error::backend(Subsystem::Vulkan, format!("{operation}: {error:?}"))
}

#[cfg(test)]
mod tests {
    #[cfg(feature = "ffmpeg")]
    use std::sync::Arc;
    #[cfg(feature = "ffmpeg")]
    use std::time::Instant;

    use super::*;

    #[test]
    fn limited_bt709_black_and_white_are_mapped_correctly() {
        assert_eq!(
            yuv_to_rgb(16, 128, 128, ColorMatrix::Bt709, ColorRange::Limited),
            (0, 0, 0)
        );
        assert_eq!(
            yuv_to_rgb(235, 128, 128, ColorMatrix::Bt709, ColorRange::Limited),
            (255, 255, 255)
        );
    }

    #[test]
    fn presentation_preserves_source_aspect_ratio() {
        assert_eq!(aspect_fit_extent(1920, 1080, 1024, 768), (0, 96, 1024, 576));
        assert_eq!(
            aspect_fit_extent(1024, 768, 1920, 1080),
            (240, 0, 1440, 1080)
        );
    }

    #[test]
    fn low_latency_present_mode_avoids_fifo_when_supported() {
        assert_eq!(
            choose_present_mode(&[vk::PresentModeKHR::FIFO, vk::PresentModeKHR::MAILBOX]),
            vk::PresentModeKHR::MAILBOX
        );
        assert_eq!(
            choose_present_mode(&[vk::PresentModeKHR::FIFO, vk::PresentModeKHR::IMMEDIATE]),
            vk::PresentModeKHR::IMMEDIATE
        );
        assert_eq!(
            choose_present_mode(&[vk::PresentModeKHR::FIFO]),
            vk::PresentModeKHR::FIFO
        );
    }

    #[cfg(feature = "ffmpeg")]
    #[test]
    fn ffmpeg_converter_processes_1440p_frames() {
        use crate::{ChromaLocation, FramePlane, StreamFormat};

        let width = 2560;
        let height = 1440;
        let frame = DecodedVideoFrame {
            format: StreamFormat {
                width,
                height,
                pixel_format: PixelFormat::Nv12,
                color_range: ColorRange::Limited,
                color_matrix: ColorMatrix::Bt709,
                chroma_location: ChromaLocation::Left,
            },
            planes: vec![
                FramePlane {
                    data: Arc::from(vec![16_u8; width as usize * height as usize]),
                    stride: width as usize,
                    rows: height as usize,
                },
                FramePlane {
                    data: Arc::from(vec![128_u8; width as usize * height as usize / 2]),
                    stride: width as usize,
                    rows: height as usize / 2,
                },
            ],
            timestamp_us: 0,
        };
        let mut converter = FrameConverter::new();
        let started = Instant::now();
        for _ in 0..30 {
            converter
                .convert(
                    &frame,
                    vk::Extent2D { width, height },
                    vk::Format::B8G8R8A8_UNORM,
                )
                .expect("convert NV12");
        }
        eprintln!(
            "1440p NV12 conversion average: {:.2} ms",
            started.elapsed().as_secs_f64() * 1000.0 / 30.0
        );
        assert_eq!(
            converter.output().len(),
            width as usize * height as usize * 4
        );
    }
}
