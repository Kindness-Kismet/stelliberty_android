package com.stelliberty.android.ui.screen.subscription

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stelliberty.android.R
import com.stelliberty.android.ui.util.TestTags
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField

// 自动测试延迟间隔，单位分钟；空值与 0 表示关闭，与 PC 一致。
@Composable
internal fun AutoDelayField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SmallTitle(text = stringResource(R.string.subscription_auto_delay_interval))
        TextField(
            value = value,
            onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(MAX_DIGITS)) },
            label = stringResource(R.string.subscription_auto_delay_placeholder),
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .testTag(TestTags.Subscription.AUTO_DELAY),
        )
    }
}

// 最多 6 位（约两年）：不限位数时超出 Int 的输入会被 toIntOrNull 当成关闭。
private const val MAX_DIGITS = 6
