package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stelliberty.android.R
import com.stelliberty.android.data.bridge.AgeKeyPair
import com.stelliberty.android.data.bridge.StellibertyCoreBridge
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.platform.setPlainText
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AgeKeyScreen(
    onBack: () -> Unit = {},
) {
    // 关闭时保留密钥对，弹窗退场动画期间内容不消失。
    var showKeyPair by remember { mutableStateOf(false) }
    var keyPair by remember { mutableStateOf<AgeKeyPair?>(null) }
    val failedMsg = stringResource(R.string.age_key_generate_failed)
    val generate: (Boolean) -> Unit = { hybrid ->
        val pair = StellibertyCoreBridge.generateAgeKeyPair(hybrid)
        if (pair != null) {
            keyPair = pair
            showKeyPair = true
        } else {
            showToast(failedMsg)
        }
    }

    SettingsSubPage(title = stringResource(R.string.age_key_title), onBack = onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        groupedCardItems(
            keyPrefix = "age_key",
            items = listOf(
                CardItem("generate") {
                    ArrowPreference(
                        title = stringResource(R.string.age_key_generate),
                        summary = stringResource(R.string.age_key_generate_summary),
                        onClick = { generate(false) },
                    )
                },
                CardItem("generateHybrid") {
                    ArrowPreference(
                        title = stringResource(R.string.age_key_generate_hybrid),
                        summary = stringResource(R.string.age_key_generate_hybrid_summary),
                        onClick = { generate(true) },
                    )
                },
            ),
        )
    }

    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val copiedMsg = stringResource(R.string.common_copied)
    val pair = keyPair
    WindowDialog(
        show = showKeyPair && pair != null,
        title = stringResource(R.string.age_key_pair_title),
        onDismissRequest = { showKeyPair = false },
    ) {
        if (pair != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    KeyText(stringResource(R.string.age_key_secret), pair.secretKey)
                    KeyText(stringResource(R.string.age_key_public), pair.publicKey)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.age_key_copy_secret),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboardScope.launch { clipboard.setPlainText(pair.secretKey) }
                            showToast(copiedMsg)
                        },
                    )
                    TextButton(
                        text = stringResource(R.string.age_key_copy_public),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboardScope.launch { clipboard.setPlainText(pair.publicKey) }
                            showToast(copiedMsg)
                        },
                    )
                }
                TextButton(
                    text = stringResource(R.string.common_close),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    onClick = { showKeyPair = false },
                )
            }
        }
    }
}

@Composable
private fun KeyText(label: String, key: String) {
    SmallTitle(
        text = label,
        insideMargin = PaddingValues(vertical = 8.dp),
    )
    Text(
        text = key,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
