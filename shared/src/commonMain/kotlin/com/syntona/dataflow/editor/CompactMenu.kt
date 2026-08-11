package com.syntona.dataflow.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of a menu row, about 30% less than Material's 48.dp default.
 *
 * Material sizes menu rows for touch. Syntona's menus are long - the module tree, the examples
 * list - and are driven with a mouse, so the default leaves them taller than the screen without
 * making them any easier to hit.
 */
val CompactMenuItemHeight: Dp = 34.dp

private val CompactMenuItemPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)

/**
 * A [DropdownMenuItem] with the row height reduced.
 *
 * Material enforces a 48.dp minimum touch target on anything clickable, so shrinking the row also
 * means opting out of that for this item; [LocalMinimumInteractiveComponentSize] is how that is
 * done, the same way the faders in the module editor do it.
 */
@Composable
fun CompactDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    height: Dp = CompactMenuItemHeight
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        DropdownMenuItem(
            text = text,
            onClick = onClick,
            modifier = modifier.height(height),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            contentPadding = CompactMenuItemPadding
        )
    }
}

/**
 * Height for a menu row carrying a second line, such as a device's manufacturer.
 *
 * The row height is fixed rather than intrinsic, so a two-line item needs to say so or its
 * second line is clipped.
 */
val CompactMenuItemHeightTwoLine: Dp = 44.dp
