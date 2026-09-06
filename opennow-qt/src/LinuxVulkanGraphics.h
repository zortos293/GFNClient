#pragma once

#include <QByteArrayList>
#include <QtGlobal>
#include <QtGui/qtguiglobal.h>

#if defined(Q_OS_LINUX) && QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
#include <QVulkanInstance>
#include <QVulkanFunctions>
#include <atomic>

namespace LinuxVulkanGraphics {
inline std::atomic<bool> extensionRequestInstalled{false};
inline QByteArrayList deviceExtensions()
{
    return {"VK_KHR_external_memory", "VK_KHR_external_memory_fd",
            "VK_EXT_external_memory_dma_buf", "VK_EXT_image_drm_format_modifier",
            "VK_KHR_image_format_list", "VK_KHR_bind_memory2",
            "VK_KHR_get_memory_requirements2", "VK_KHR_sampler_ycbcr_conversion",
            "VK_KHR_maintenance1"};
}

inline void requestDeviceExtensions()
{
    QVulkanInstance probe;
    if (probe.supportedApiVersion() < QVersionNumber(1, 1)) return;
    auto requested = qgetenv("QT_VULKAN_DEVICE_EXTENSIONS").split(';');
    for (const auto &extension : deviceExtensions()) {
        if (!requested.contains(extension)) requested.append(extension);
    }
    qputenv("QT_VULKAN_DEVICE_EXTENSIONS", requested.join(';'));
    extensionRequestInstalled.store(true, std::memory_order_release);
}

inline bool hasDmabufImportContract(const QVersionNumber &instanceVersion,
                                   uint32_t physicalDeviceVersion,
                                   const QByteArrayList &requested,
                                   const QByteArrayList &available)
{
    if (instanceVersion < QVersionNumber(1, 1) || physicalDeviceVersion < VK_API_VERSION_1_1)
        return false;
    auto required = QByteArrayList{"VK_KHR_external_memory_fd", "VK_EXT_external_memory_dma_buf",
                                  "VK_EXT_image_drm_format_modifier"};
    if (instanceVersion < QVersionNumber(1, 2) || physicalDeviceVersion < VK_API_VERSION_1_2)
        required.append("VK_KHR_image_format_list");
    for (const auto &extension : required) {
        if (!requested.contains(extension) || !available.contains(extension)) return false;
    }
    return true;
}

inline bool dmabufImportEnabled(QVulkanInstance *instance, VkPhysicalDevice physicalDevice)
{
    if (!extensionRequestInstalled.load(std::memory_order_acquire)
            || !instance || instance->apiVersion() < QVersionNumber(1, 1)) return false;
    auto *functions = instance->functions();
    VkPhysicalDeviceProperties properties{};
    functions->vkGetPhysicalDeviceProperties(physicalDevice, &properties);
    if (properties.apiVersion < VK_API_VERSION_1_1) return false;
    uint32_t count = 0;
    if (functions->vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &count, nullptr)
            != VK_SUCCESS) return false;
    QList<VkExtensionProperties> available(count);
    if (functions->vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &count,
                                                       available.data()) != VK_SUCCESS) return false;
    available.resize(count);
    QByteArrayList supported;
    for (const auto &property : available) supported.append(property.extensionName);
    return hasDmabufImportContract(instance->apiVersion(), properties.apiVersion,
                                  qgetenv("QT_VULKAN_DEVICE_EXTENSIONS").split(';'), supported);
}
}
#endif
