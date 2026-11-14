package com.stelliberty.android.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PopupMenuItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    role: Role = Role.Button,
    hasSubmenu: Boolean = false,
    index: Int = 0,
    optionCount: Int = 1,
) {
    Box(
        modifier.squircleClip(12.dp).clearAndSetSemantics {
            text = AnnotatedString(listOfNotNull(title, summary).joinToString("\n"))
            this.role = role
            if (role == Role.RadioButton) selected = isSelected
            if (role == Role.Checkbox) toggleableState = ToggleableState(isSelected)
            if (enabled) this.onClick { onClick(); true } else disabled()
        },
        propagateMinConstraints = true,
    ) {
        DropdownImpl(
            item = DropdownItem(
                text = title,
                summary = summary,
                icon = if (icon == null) null else { iconModifier ->
                    Box(iconModifier, contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (enabled) MiuixTheme.colorScheme.onSurface
                            else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                        )
                    }
                },
            ),
            optionSize = optionCount,
            isSelected = isSelected,
            index = index,
            enabled = enabled,
            hasSubmenu = hasSubmenu,
            onSelectedIndexChange = { onClick() },
        )
    }
}
