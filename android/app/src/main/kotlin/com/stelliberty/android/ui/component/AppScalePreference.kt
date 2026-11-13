package com.stelliberty.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.ui.theme.DefaultDensityScale
import com.stelliberty.android.ui.theme.DensityScaleSteps
import com.stelliberty.android.ui.theme.MaxDensityScale
import com.stelliberty.android.ui.theme.MinDensityScale
import com.stelliberty.android.ui.theme.normalizeDensityScale
import com.stelliberty.android.ui.util.TestTags
import java.util.Locale
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
fun AppScalePreference(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(scale) { mutableFloatStateOf(normalizeDensityScale(scale)) }
    var committed by remember(scale) { mutableFloatStateOf(normalizeDensityScale(scale)) }
    var hasPointerGesture by remember { mutableStateOf(false) }
    fun commit() {
        if (draft == committed) return
        committed = draft
        onScaleChange(draft)
    }
    // 无障碍单次调节没有完成回调，触摸拖动则只在松开时应用密度。
    val pointerObserver = Modifier.pointerInput(Unit) {
        try {
            awaitPointerEventScope {
                while (true) {
                    hasPointerGesture = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                }
            }
        } finally {
            hasPointerGesture = false
        }
    }
    SliderPreference(
        title = stringResource(R.string.settings_theme_density_scale),
        value = draft,
        valueText = densityScaleLabel(draft, stringResource(R.string.common_default)),
        valueRange = MinDensityScale..MaxDensityScale,
        steps = DensityScaleSteps,
        modifier = modifier.squircleClip(16.dp).then(pointerObserver).testTag(TestTags.Settings.APP_SCALE),
        onValueChange = {
            draft = normalizeDensityScale(it)
            if (!hasPointerGesture) commit()
        },
        onValueChangeFinished = ::commit,
    )
}

internal fun densityScaleLabel(scale: Float, defaultLabel: String): String =
    if (scale == DefaultDensityScale) defaultLabel
    else String.format(Locale.ROOT, "%.2f", scale).trimEnd('0').trimEnd('.') + "x"
