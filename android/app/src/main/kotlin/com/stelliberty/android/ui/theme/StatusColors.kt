package com.stelliberty.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

object StatusColors {

    val neutral: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalAppDarkMode.current) Gray400Dark else Gray500Light

    val danger: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalAppDarkMode.current) Red300Dark else Red600Light

    val warning: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalAppDarkMode.current) Amber300Dark else Amber700Light

    val healthy: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalAppDarkMode.current) Green300Dark else Green600Light

    val info: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalAppDarkMode.current) Blue300Dark else Blue600Light

    @Composable
    @ReadOnlyComposable
    fun delay(value: Int?): Color = when {
        value == null -> neutral
        value < 0 -> danger
        value < 200 -> healthy
        value < 500 -> warning
        else -> danger
    }

    @Composable
    @ReadOnlyComposable
    fun runState(state: RunState): Color = when (state) {
        RunState.Running ->
            if (LocalAppMonetEnabled.current) MiuixTheme.colorScheme.primary else healthy

        RunState.Pending -> warning
        RunState.Stopped -> danger
    }

    @Composable
    @ReadOnlyComposable
    fun actionButton(action: ActionKind): ActionPalette {
        val isDark = LocalAppDarkMode.current
        return when (action) {
            ActionKind.Restart -> ActionPalette(if (isDark) Color(0xFF66BB6A) else Color(0xFF43A047))
            ActionKind.Stop -> ActionPalette(if (isDark) Color(0xFFEF5350) else Color(0xFFE53935))
            ActionKind.Reload -> ActionPalette(if (isDark) Color(0xFF42A5F5) else Color(0xFF1E88E5))
        }
    }

    val trafficUpload: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalAppDarkMode.current) Green300Dark else Green600Light

    val trafficDownload: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalAppDarkMode.current) Blue300Dark else Blue600Light

    @Composable
    @ReadOnlyComposable
    fun usage(progress: Float): Color = when {
        progress >= 0.9f -> danger
        progress >= 0.75f -> warning
        else -> MiuixTheme.colorScheme.primary
    }

    private val Green300Dark = Color(0xFF81C784)
    private val Green600Light = Color(0xFF4CAF50)
    private val Amber300Dark = Color(0xFFF9A825)
    private val Amber700Light = Color(0xFFFFB300)
    private val Red300Dark = Color(0xFFEF9A9A)
    private val Red600Light = Color(0xFFE53935)
    private val Gray400Dark = Color(0xFFBDBDBD)
    private val Gray500Light = Color(0xFF9E9E9E)
    private val Blue300Dark = Color(0xFF42A5F5)
    private val Blue600Light = Color(0xFF1E88E5)
}

enum class RunState { Running, Pending, Stopped }

enum class ActionKind { Restart, Stop, Reload }

data class ActionPalette(val content: Color)
