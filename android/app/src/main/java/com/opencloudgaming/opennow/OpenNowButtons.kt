package com.opencloudgaming.opennow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shared primary action button with an unmistakable controller/TV focus state.
 * Touch does not normally focus buttons, so the treatment stays out of the phone UI.
 */
@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    MaterialButton(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = if (focused) BorderStroke(4.dp, Color.White) else border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}
