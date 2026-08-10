package com.opencloudgaming.opennow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
internal fun TouchOverlay(
    client: NativeStreamClient,
    touch: AndroidTouchSettings,
    onButtonTone: () -> Unit,
    layoutEditing: Boolean,
    onSaveAllOffsets: (Map<String, TouchOffset>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val opacity = touch.opacity
    val layoutScale = touch.scale
    val buttonScale = touch.buttonScale
    val stickScale = touch.stickScale

    val localOffsets = remember(touch.offsets) {
        androidx.compose.runtime.mutableStateMapOf<String, TouchOffset>().apply {
            putAll(touch.offsets)
        }
    }

    fun getLocalOffset(key: String): TouchOffset {
        val saved = localOffsets[key]
        if (saved != null) return saved
        val baseKey = key.substringBeforeLast("_")
        return when (baseKey) {
            "lt", "lb", "lstick", "dpad", "l3" -> TouchOffset(touch.leftOffsetXDp, touch.leftOffsetYDp)
            "rt", "rb", "rstick", "face", "r3" -> TouchOffset(touch.rightOffsetXDp, touch.rightOffsetYDp)
            else -> TouchOffset()
        }
    }

    val onLocalOffsetChange = { key: String, x: Float, y: Float ->
        localOffsets[key] = TouchOffset(x, y)
    }

    val currentLocalOffsets by rememberUpdatedState(localOffsets.toMap())
    val currentOnSaveAllOffsets by rememberUpdatedState(onSaveAllOffsets)
    DisposableEffect(layoutEditing) {
        onDispose {
            if (layoutEditing) {
                currentOnSaveAllOffsets(currentLocalOffsets)
            }
        }
    }

    DisposableEffect(client) {
        onDispose {
            NativeStreamInputRouter.clearTouchControllerPassthroughBounds()
        }
    }

    CompositionLocalProvider(LocalTouchControllerStyle provides touch.touchControllerStyle) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .padding(
                    start = touch.edgePaddingDp.dp,
                    top = 10.dp,
                    end = touch.edgePaddingDp.dp,
                    bottom = touch.bottomPaddingDp.dp,
                ),
        ) {
            if (touch.enabled) {
                val landscape = maxWidth > maxHeight
                val suffix = if (landscape) "_landscape" else "_portrait"
                val getOrientationLocalOffset = { key: String -> getLocalOffset(key + suffix) }
                val onOrientationLocalOffsetChange = { key: String, x: Float, y: Float ->
                    onLocalOffsetChange(key + suffix, x, y)
                }

                if (landscape) {
                    LandscapeTouchControls(
                        client = client,
                        opacity = opacity,
                        layoutScale = layoutScale,
                        buttonScale = buttonScale,
                        stickScale = stickScale,
                        joystickMode = touch.joystickMode,
                        joystickDeadZone = touch.joystickDeadZone,
                        viewportHeight = maxHeight,
                        layoutEditing = layoutEditing,
                        getLocalOffset = getOrientationLocalOffset,
                        onLocalOffsetChange = onOrientationLocalOffsetChange,
                        onButtonTone = onButtonTone,
                    )
                } else {
                    PortraitTouchControls(
                        client = client,
                        opacity = opacity,
                        layoutScale = layoutScale,
                        buttonScale = buttonScale,
                        stickScale = stickScale,
                        joystickMode = touch.joystickMode,
                        joystickDeadZone = touch.joystickDeadZone,
                        layoutEditing = layoutEditing,
                        getLocalOffset = getOrientationLocalOffset,
                        onLocalOffsetChange = onOrientationLocalOffsetChange,
                        onButtonTone = onButtonTone,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitTouchControls(
    client: NativeStreamClient,
    opacity: Float,
    layoutScale: Float,
    buttonScale: Float,
    stickScale: Float,
    joystickMode: TouchJoystickMode,
    joystickDeadZone: Float,
    layoutEditing: Boolean,
    getLocalOffset: (String) -> TouchOffset,
    onLocalOffsetChange: (String, Float, Float) -> Unit,
    onButtonTone: () -> Unit,
) {
    val leftStickDiameter = 116.dp * stickScale * layoutScale
    val rightStickDiameter = 104.dp * stickScale * layoutScale
    val buttonSize48 = 48.dp * buttonScale * layoutScale
    val buttonSize44 = 44.dp * buttonScale * layoutScale
    val faceWidth = buttonSize48 * 2.44f

    Box(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        val scale = buttonScale * layoutScale
        val triggerWidth = 64.dp * scale
        val bumperHeight = 32.dp * scale

        TouchControlGroup(
            id = "portrait-lt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lt").x.dp,
            offsetY = getLocalOffset("lt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lt", x, y) },
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            GamepadTriggerButton(
                label = "LT",
                left = true,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-lb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lb").x.dp,
            offsetY = getLocalOffset("lb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lb", x, y) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "LB",
                mask = 0x0100,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-lstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lstick").x.dp,
            offsetY = getLocalOffset("lstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lstick", x, y) },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            VirtualStick(
                label = "L",
                client = client,
                opacity = opacity,
                diameter = leftStickDiameter,
                mode = joystickMode,
                deadZone = joystickDeadZone,
                onChange = client::setVirtualLeftStick,
            )
        }

        TouchControlGroup(
            id = "portrait-l3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("l3").x.dp,
            offsetY = getLocalOffset("l3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("l3", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(
                start = (leftStickDiameter - buttonSize48) / 2,
                bottom = leftStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("LS", GamepadButtonMapping.LEFT_THUMB, client, opacity, buttonSize48, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-dpad",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("dpad").x.dp,
            offsetY = getLocalOffset("dpad").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("dpad", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = leftStickDiameter + 12.dp),
        ) {
            DpadCluster(client, opacity, buttonScale * layoutScale, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-rt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rt").x.dp,
            offsetY = getLocalOffset("rt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rt", x, y) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            GamepadTriggerButton(
                label = "RT",
                left = false,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-rb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rb").x.dp,
            offsetY = getLocalOffset("rb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rb", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "RB",
                mask = 0x0200,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "portrait-select",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("select").x.dp,
            offsetY = getLocalOffset("select").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("select", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = buttonSize48 + 8.dp, end = buttonSize44 + 8.dp),
        ) {
            GamepadButton("◀", 0x0020, client, opacity, buttonSize44, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-start",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("start").x.dp,
            offsetY = getLocalOffset("start").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("start", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = buttonSize48 + 8.dp),
        ) {
            GamepadButton("▶", 0x0010, client, opacity, buttonSize44, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-rstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rstick").x.dp,
            offsetY = getLocalOffset("rstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rstick", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = faceWidth + 12.dp),
        ) {
            VirtualStick(
                label = "R",
                client = client,
                opacity = opacity,
                diameter = rightStickDiameter,
                mode = joystickMode,
                deadZone = joystickDeadZone,
                onChange = client::setVirtualRightStick,
            )
        }

        TouchControlGroup(
            id = "portrait-r3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("r3").x.dp,
            offsetY = getLocalOffset("r3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("r3", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(
                end = faceWidth + 12.dp + (rightStickDiameter - buttonSize48) / 2,
                bottom = rightStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("RS", GamepadButtonMapping.RIGHT_THUMB, client, opacity, buttonSize48, onButtonTone)
        }

        TouchControlGroup(
            id = "portrait-face",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("face").x.dp,
            offsetY = getLocalOffset("face").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("face", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            FaceButtonCluster(client, opacity, buttonScale * layoutScale, onButtonTone)
        }
    }
}

@Composable
private fun BoxScope.LandscapeTouchControls(
    client: NativeStreamClient,
    opacity: Float,
    layoutScale: Float,
    buttonScale: Float,
    stickScale: Float,
    joystickMode: TouchJoystickMode,
    joystickDeadZone: Float,
    viewportHeight: Dp,
    layoutEditing: Boolean,
    getLocalOffset: (String) -> TouchOffset,
    onLocalOffsetChange: (String, Float, Float) -> Unit,
    onButtonTone: () -> Unit,
) {
    val controlScale = buttonScale * layoutScale
    val topControlClearance = landscapeTouchTopControlClearanceDp(viewportHeight.value, controlScale).dp
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        val triggerWidth = 76.dp * controlScale
        val bumperHeight = 36.dp * controlScale

        TouchControlGroup(
            id = "landscape-lt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lt").x.dp,
            offsetY = getLocalOffset("lt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lt", x, y) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = topControlClearance),
        ) {
            GamepadTriggerButton(
                label = "LT",
                left = true,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "landscape-lb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lb").x.dp,
            offsetY = getLocalOffset("lb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lb", x, y) },
            modifier = Modifier.align(Alignment.TopStart).padding(top = topControlClearance + bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "LB",
                mask = 0x0100,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        val selectSize = 42.dp * controlScale
        TouchControlGroup(
            id = "landscape-select",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("select").x.dp,
            offsetY = getLocalOffset("select").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("select", x, y) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(end = selectSize / 2 + 27.dp),
        ) {
            GamepadButton("◀", 0x0020, client, opacity, selectSize, onButtonTone)
        }

        TouchControlGroup(
            id = "landscape-start",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("start").x.dp,
            offsetY = getLocalOffset("start").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("start", x, y) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = selectSize / 2 + 27.dp),
        ) {
            GamepadButton("▶", 0x0010, client, opacity, selectSize, onButtonTone)
        }

        TouchControlGroup(
            id = "landscape-rb",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rb").x.dp,
            offsetY = getLocalOffset("rb").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rb", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = topControlClearance + bumperHeight + 6.dp),
        ) {
            GamepadBumperButton(
                label = "RB",
                mask = 0x0200,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                onPressTone = onButtonTone,
            )
        }

        TouchControlGroup(
            id = "landscape-rt",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rt").x.dp,
            offsetY = getLocalOffset("rt").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rt", x, y) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = topControlClearance),
        ) {
            GamepadTriggerButton(
                label = "RT",
                left = false,
                client = client,
                opacity = opacity,
                width = triggerWidth,
                height = bumperHeight,
                shape = RoundedCornerShape(50),
                onPressTone = onButtonTone,
            )
        }

        val dpadScale = controlScale * 0.88f
        val dpadButtonSize = 54.dp * dpadScale
        val dpadWidth = dpadButtonSize * 2.44f
        TouchControlGroup(
            id = "landscape-dpad",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("dpad").x.dp,
            offsetY = getLocalOffset("dpad").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("dpad", x, y) },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            DpadCluster(client, opacity, dpadScale, onButtonTone)
        }

        val leftStickDiameter = 112.dp * stickScale * layoutScale
        TouchControlGroup(
            id = "landscape-lstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("lstick").x.dp,
            offsetY = getLocalOffset("lstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("lstick", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = dpadWidth + 14.dp),
        ) {
            VirtualStick(
                label = "L",
                client = client,
                opacity = opacity,
                diameter = leftStickDiameter,
                mode = joystickMode,
                deadZone = joystickDeadZone,
                onChange = client::setVirtualLeftStick,
            )
        }

        val l3Size = 54.dp * controlScale
        TouchControlGroup(
            id = "landscape-l3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("l3").x.dp,
            offsetY = getLocalOffset("l3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("l3", x, y) },
            modifier = Modifier.align(Alignment.BottomStart).padding(
                start = dpadWidth + 14.dp + (leftStickDiameter - l3Size) / 2,
                bottom = leftStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("LS", GamepadButtonMapping.LEFT_THUMB, client, opacity, l3Size, onButtonTone)
        }

        val faceScale = controlScale * 0.9f
        val faceButtonSize = 54.dp * faceScale
        val faceWidth = faceButtonSize * 2.44f
        val rightStickDiameter = 112.dp * stickScale * layoutScale
        TouchControlGroup(
            id = "landscape-rstick",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("rstick").x.dp,
            offsetY = getLocalOffset("rstick").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("rstick", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = faceWidth + 14.dp),
        ) {
            VirtualStick(
                label = "R",
                client = client,
                opacity = opacity,
                diameter = rightStickDiameter,
                mode = joystickMode,
                deadZone = joystickDeadZone,
                onChange = client::setVirtualRightStick,
            )
        }

        val r3Size = 54.dp * controlScale
        TouchControlGroup(
            id = "landscape-r3",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("r3").x.dp,
            offsetY = getLocalOffset("r3").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("r3", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(
                end = faceWidth + 14.dp + (rightStickDiameter - r3Size) / 2,
                bottom = rightStickDiameter + 6.dp
            ),
        ) {
            GamepadButton("RS", GamepadButtonMapping.RIGHT_THUMB, client, opacity, r3Size, onButtonTone)
        }

        TouchControlGroup(
            id = "landscape-face",
            layoutEditing = layoutEditing,
            offsetX = getLocalOffset("face").x.dp,
            offsetY = getLocalOffset("face").y.dp,
            onOffsetChange = { x, y -> onLocalOffsetChange("face", x, y) },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            FaceButtonCluster(client, opacity, faceScale, onButtonTone)
        }
    }
}

internal fun landscapeTouchTopControlClearanceDp(viewportHeightDp: Float, controlScale: Float): Float {
    val viewportBand = (viewportHeightDp * 0.11f).coerceIn(34f, 58f)
    val scaledBand = viewportBand * controlScale.coerceIn(0.75f, 1.35f)
    return scaledBand.coerceIn(30f, 76f)
}

@Composable
private fun TouchControlGroup(
    id: String,
    layoutEditing: Boolean,
    offsetX: Dp,
    offsetY: Dp,
    onOffsetChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    Box(
        modifier
            .offset(x = offsetX, y = offsetY)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                NativeStreamInputRouter.setTouchControllerPassthroughBound(
                    id,
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (layoutEditing) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaXDp = with(density) { dragAmount.x.toDp().value }
                            val deltaYDp = with(density) { dragAmount.y.toDp().value }
                            currentOnOffsetChange(
                                (currentOffsetX.value + deltaXDp).coerceIn(-280f, 280f),
                                (currentOffsetY.value + deltaYDp).coerceIn(-280f, 280f),
                            )
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        "Drag",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
    DisposableEffect(id) {
        onDispose {
            NativeStreamInputRouter.clearTouchControllerPassthroughBound(id)
        }
    }
}

private fun clampStickOffset(offset: Offset, maxRadius: Float): Offset {
    val distance = sqrt(offset.x * offset.x + offset.y * offset.y)
    if (distance <= maxRadius || distance == 0f) return offset
    val scale = maxRadius / distance
    return Offset(offset.x * scale, offset.y * scale)
}

internal fun applyTouchJoystickDeadZone(value: Float, deadZone: Float): Float {
    val clampedValue = value.coerceIn(-1f, 1f)
    val clampedDeadZone = deadZone.coerceIn(0f, 0.95f)
    val magnitude = kotlin.math.abs(clampedValue)
    if (magnitude <= clampedDeadZone) return 0f
    val adjusted = (magnitude - clampedDeadZone) / (1f - clampedDeadZone)
    return if (clampedValue < 0f) -adjusted else adjusted
}

@Composable
private fun StickWithThumbButton(
    stickLabel: String,
    thumbLabel: String,
    thumbMask: Int,
    client: NativeStreamClient,
    opacity: Float,
    diameter: Dp,
    buttonScale: Float,
    mode: TouchJoystickMode = TouchJoystickMode.Fixed,
    deadZone: Float = 0f,
    onButtonTone: () -> Unit,
    onChange: (Float, Float) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GamepadPillButton(
            label = thumbLabel,
            mask = thumbMask,
            client = client,
            opacity = opacity,
            width = 56.dp * buttonScale,
            height = 34.dp * buttonScale,
            onPressTone = onButtonTone,
        )
        VirtualStick(
            label = stickLabel,
            client = client,
            opacity = opacity,
            diameter = diameter,
            mode = mode,
            deadZone = deadZone,
            onChange = onChange,
        )
    }
}

@Composable
private fun VirtualStick(
    label: String,
    client: NativeStreamClient,
    opacity: Float,
    diameter: androidx.compose.ui.unit.Dp,
    mode: TouchJoystickMode,
    deadZone: Float,
    onChange: (Float, Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var baseOffset by remember { mutableStateOf(Offset.Zero) }
    val style = LocalTouchControllerStyle.current

    DisposableEffect(client) {
        onDispose {
            currentOnChange(0f, 0f)
        }
    }

    Box(
        Modifier
            .size(diameter)
            .pointerInput(client, mode, deadZone) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val fixedCenter = Offset(size.width / 2f, size.height / 2f)
                    val gestureCenter = if (mode == TouchJoystickMode.Dynamic) down.position else fixedCenter
                    val maxRadius = min(size.width, size.height) * 0.34f
                    baseOffset = gestureCenter - fixedCenter

                    fun updateStick(position: Offset) {
                        val clamped = clampStickOffset(position - gestureCenter, maxRadius)
                        val rawX = (clamped.x / maxRadius).coerceIn(-1f, 1f)
                        val rawY = (clamped.y / maxRadius).coerceIn(-1f, 1f)
                        val magnitude = sqrt(rawX * rawX + rawY * rawY).coerceIn(0f, 1f)
                        val adjustedMagnitude = applyTouchJoystickDeadZone(magnitude, deadZone)
                        val adjustment = if (magnitude > 0f) adjustedMagnitude / magnitude else 0f
                        currentOnChange(rawX * adjustment, rawY * adjustment)
                        knobOffset = clamped
                    }

                    try {
                        updateStick(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            updateStick(change.position)
                            change.consume()
                        }
                    } finally {
                        currentOnChange(0f, 0f)
                        knobOffset = Offset.Zero
                        baseOffset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val knobBackground = if (style == TouchControllerStyle.V2) {
            Color.White.copy(alpha = opacity * 0.2f)
        } else {
            Color.LightGray.copy(alpha = opacity * 0.8f)
        }
        val knobBorderModifier = if (style == TouchControllerStyle.V2) {
            Modifier.border(1.dp, Color.White.copy(alpha = opacity * 0.5f), CircleShape)
        } else {
            Modifier
        }
        Box(
            Modifier
                .size(diameter)
                .graphicsLayer {
                    translationX = baseOffset.x
                    translationY = baseOffset.y
                }
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(1.dp, Color.White.copy(alpha = opacity * 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(diameter * 0.44f)
                    .graphicsLayer {
                        translationX = knobOffset.x
                        translationY = knobOffset.y
                    }
                    .clip(CircleShape)
                    .background(knobBackground)
                    .then(knobBorderModifier)
            )
        }
    }
}

@Composable
private fun FaceButtonCluster(client: NativeStreamClient, opacity: Float, scale: Float, onButtonTone: () -> Unit) {
    val buttonSize = 54.dp * scale
    val distance = buttonSize * 1.05f
    val boxSize = distance * 2 + buttonSize
    Box(Modifier.size(boxSize)) {
        Box(Modifier.align(Alignment.Center).offset(y = -distance)) {
            GamepadButton("Y", 0x8000, client, opacity, buttonSize, onButtonTone)
        }
        Box(Modifier.align(Alignment.Center).offset(y = distance)) {
            GamepadButton("A", 0x1000, client, opacity, buttonSize, onButtonTone)
        }
        Box(Modifier.align(Alignment.Center).offset(x = -distance)) {
            GamepadButton("X", 0x4000, client, opacity, buttonSize, onButtonTone)
        }
        Box(Modifier.align(Alignment.Center).offset(x = distance)) {
            GamepadButton("B", 0x2000, client, opacity, buttonSize, onButtonTone)
        }
    }
}

@Composable
private fun DpadArrowhead(
    label: String,
    pressed: Boolean,
    opacity: Float,
) {
    val arrowColor = if (pressed) {
        Color.White
    } else {
        Color.White.copy(alpha = opacity * 0.8f)
    }
    Text(
        text = label,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = arrowColor
    )
}

@Composable
private fun DpadCluster(client: NativeStreamClient, opacity: Float, scale: Float, onButtonTone: () -> Unit) {
    val currentOnButtonTone by rememberUpdatedState(onButtonTone)
    val buttonSize = 54.dp * scale
    val distance = buttonSize * 1.05f
    val boxSize = distance * 2 + buttonSize

    var upPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    val style = LocalTouchControllerStyle.current
    val crossColor = if (style == TouchControllerStyle.V2) Color.Transparent else Color.Black.copy(alpha = opacity * 0.6f)
    val crossBorderColor = if (style == TouchControllerStyle.V2) Color.White.copy(alpha = opacity * 0.5f) else Color.White.copy(alpha = opacity * 0.4f)
    val crossBorderWidth = 1.dp

    DisposableEffect(client) {
        onDispose {
            client.setVirtualButton(0x0001, false)
            client.setVirtualButton(0x0002, false)
            client.setVirtualButton(0x0004, false)
            client.setVirtualButton(0x0008, false)
        }
    }

    Box(
        Modifier
            .size(boxSize)
            .pointerInput(client) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

                    fun updateDirection(position: Offset) {
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f
                        val px = position.x
                        val py = position.y
                        val dx = px - cx
                        val dy = py - cy
                        val touchDist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        val deadzone = 12.dp.toPx()
                        var newUp = false
                        var newDown = false
                        var newLeft = false
                        var newRight = false
                        if (touchDist > deadzone) {
                            val absDx = Math.abs(dx)
                            val absDy = Math.abs(dy)
                            if (dy < 0 && absDy > absDx * 0.414f) newUp = true
                            if (dy > 0 && absDy > absDx * 0.414f) newDown = true
                            if (dx < 0 && absDx > absDy * 0.414f) newLeft = true
                            if (dx > 0 && absDx > absDy * 0.414f) newRight = true
                        }

                        val playTone = (!upPressed && newUp) || (!downPressed && newDown) ||
                                       (!leftPressed && newLeft) || (!rightPressed && newRight)
                        if (upPressed != newUp) { client.setVirtualButton(0x0001, newUp); upPressed = newUp }
                        if (downPressed != newDown) { client.setVirtualButton(0x0002, newDown); downPressed = newDown }
                        if (leftPressed != newLeft) { client.setVirtualButton(0x0004, newLeft); leftPressed = newLeft }
                        if (rightPressed != newRight) { client.setVirtualButton(0x0008, newRight); rightPressed = newRight }
                        if (playTone) currentOnButtonTone()
                    }

                    try {
                        updateDirection(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            updateDirection(change.position)
                            change.consume()
                        }
                    } finally {
                        if (upPressed) { client.setVirtualButton(0x0001, false); upPressed = false }
                        if (downPressed) { client.setVirtualButton(0x0002, false); downPressed = false }
                        if (leftPressed) { client.setVirtualButton(0x0004, false); leftPressed = false }
                        if (rightPressed) { client.setVirtualButton(0x0008, false); rightPressed = false }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val armSize = buttonSize.toPx()
            val cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())

            val crossPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = (w - armSize) / 2f,
                        top = 0f,
                        right = (w + armSize) / 2f,
                        bottom = h,
                        cornerRadius = cornerRadius
                    )
                )
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = (h - armSize) / 2f,
                        right = w,
                        bottom = (h + armSize) / 2f,
                        cornerRadius = cornerRadius
                    )
                )
            }

            if (style != TouchControllerStyle.V2) {
                drawPath(crossPath, crossColor)
            }

            val pressedColor = if (style == TouchControllerStyle.V2) {
                Color.White.copy(alpha = opacity * 0.15f)
            } else {
                Color.White.copy(alpha = opacity * 0.2f)
            }

            val pressedPath = Path()
            if (upPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = (w - armSize) / 2f,
                        top = 0f,
                        right = (w + armSize) / 2f,
                        bottom = h / 2f,
                        topLeftCornerRadius = cornerRadius,
                        topRightCornerRadius = cornerRadius
                    )
                )
            }
            if (downPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = (w - armSize) / 2f,
                        top = h / 2f,
                        right = (w + armSize) / 2f,
                        bottom = h,
                        bottomLeftCornerRadius = cornerRadius,
                        bottomRightCornerRadius = cornerRadius
                    )
                )
            }
            if (leftPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = (h - armSize) / 2f,
                        right = w / 2f,
                        bottom = (h + armSize) / 2f,
                        topLeftCornerRadius = cornerRadius,
                        bottomLeftCornerRadius = cornerRadius
                    )
                )
            }
            if (rightPressed) {
                pressedPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w / 2f,
                        top = (h - armSize) / 2f,
                        right = w,
                        bottom = (h + armSize) / 2f,
                        topRightCornerRadius = cornerRadius,
                        bottomRightCornerRadius = cornerRadius
                    )
                )
            }
            drawPath(pressedPath, pressedColor)

            drawPath(
                path = crossPath,
                color = crossBorderColor,
                style = Stroke(width = crossBorderWidth.toPx())
            )
        }

        Box(Modifier.align(Alignment.Center).offset(y = -distance)) {
            DpadArrowhead("▲", upPressed, opacity)
        }
        Box(Modifier.align(Alignment.Center).offset(y = distance)) {
            DpadArrowhead("▼", downPressed, opacity)
        }
        Box(Modifier.align(Alignment.Center).offset(x = -distance)) {
            DpadArrowhead("◀", leftPressed, opacity)
        }
        Box(Modifier.align(Alignment.Center).offset(x = distance)) {
            DpadArrowhead("▶", rightPressed, opacity)
        }
    }
}

private fun Modifier.virtualPressInput(
    client: NativeStreamClient,
    controlKey: Any,
    onPressedChange: State<(Boolean) -> Unit>,
): Modifier = pointerInput(client, controlKey) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPressedChange.value(true)
        try {
            down.consume()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                change.consume()
            }
        } finally {
            onPressedChange.value(false)
        }
    }
}

@Composable
private fun GamepadTriggerButton(
    label: String,
    left: Boolean,
    client: NativeStreamClient,
    opacity: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
    onPressTone: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPressedChange = rememberUpdatedState<(Boolean) -> Unit> { down ->
        if (down != pressed) {
            client.setVirtualTrigger(left, down)
            pressed = down
            if (down) onPressTone()
        }
    }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, shape)
            .virtualPressInput(client, left, currentOnPressedChange),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = opacity * 0.9f))
    }
    DisposableEffect(client, left) {
        onDispose {
            client.setVirtualTrigger(left, false)
        }
    }
}

@Composable
private fun GamepadBumperButton(
    label: String,
    mask: Int,
    client: NativeStreamClient,
    opacity: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onPressTone: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPressedChange = rememberUpdatedState<(Boolean) -> Unit> { down ->
        if (down != pressed) {
            client.setVirtualButton(mask, down)
            pressed = down
            if (down) onPressTone()
        }
    }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, shape)
            .virtualPressInput(client, mask, currentOnPressedChange),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = opacity * 0.9f))
    }
    DisposableEffect(client, mask) {
        onDispose {
            client.setVirtualButton(mask, false)
        }
    }
}

@Composable
private fun GamepadButton(
    label: String,
    mask: Int,
    client: NativeStreamClient,
    opacity: Float,
    size: androidx.compose.ui.unit.Dp,
    onPressTone: () -> Unit = {},
) {
    val currentOnPressTone by rememberUpdatedState(onPressTone)
    var pressed by remember { mutableStateOf(false) }
    val currentOnPressedChange = rememberUpdatedState<(Boolean) -> Unit> { down ->
        if (down != pressed) {
            client.setVirtualButton(mask, down)
            pressed = down
            if (down) currentOnPressTone()
        }
    }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, CircleShape)
            .virtualPressInput(client, mask, currentOnPressedChange),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = opacity * 0.9f),
        )
    }
    DisposableEffect(client, mask) {
        onDispose {
            client.setVirtualButton(mask, false)
        }
    }
}

@Composable
private fun GamepadPillButton(
    label: String,
    mask: Int,
    client: NativeStreamClient,
    opacity: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onPressTone: () -> Unit = {},
) {
    val currentOnPressTone by rememberUpdatedState(onPressTone)
    var pressed by remember { mutableStateOf(false) }
    val currentOnPressedChange = rememberUpdatedState<(Boolean) -> Unit> { down ->
        if (down != pressed) {
            client.setVirtualButton(mask, down)
            pressed = down
            if (down) currentOnPressTone()
        }
    }
    val style = LocalTouchControllerStyle.current
    val buttonColor = if (style == TouchControllerStyle.V2) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = opacity * 0.6f)
    }
    val pressedColor = if (style == TouchControllerStyle.V2) {
        Color.White.copy(alpha = opacity * 0.15f)
    } else {
        Color.White.copy(alpha = opacity * 0.2f)
    }
    val borderColor = if (style == TouchControllerStyle.V2) {
        if (pressed) Color.White.copy(alpha = opacity * 0.9f) else Color.White.copy(alpha = opacity * 0.5f)
    } else {
        Color.White.copy(alpha = opacity * 0.4f)
    }
    val borderWidth = if (style == TouchControllerStyle.V2 && pressed) 2.dp else 1.dp
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(if (pressed) pressedColor else buttonColor)
            .border(borderWidth, borderColor, RoundedCornerShape(999.dp))
            .virtualPressInput(client, mask, currentOnPressedChange),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = opacity * 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    DisposableEffect(client, mask) {
        onDispose {
            client.setVirtualButton(mask, false)
        }
    }
}
