package com.ilukezippo.grabbit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, ripple(), onClick = onClick)
}
