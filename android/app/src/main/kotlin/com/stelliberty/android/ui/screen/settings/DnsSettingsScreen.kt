package com.stelliberty.android.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.RestartRequiredHint
import com.stelliberty.android.ui.component.TriStatePreference
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.viewmodel.ClashFeaturesViewModel
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun DnsSettingsScreen(
    viewModel: ClashFeaturesViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = rememberOverrideEditorState()
    val dns = state.dns
    // 显式关闭 DNS 时其余项不生效。
    val subEnabled = dns?.enable != false

    fun listItem(key: String, @StringRes titleRes: Int, value: List<String>?, assign: (List<String>?) -> Unit) =
        CardItem(key) {
            val title = stringResource(titleRes)
            ArrowPreference(
                title = title,
                summary = listSummary(value),
                onClick = { editor.editList(title, value, assign) },
                enabled = subEnabled,
            )
        }

    SettingsSubPage(title = stringResource(R.string.dns_settings_title), onBack = onBack) {
        item { RestartRequiredHint() }
        item { Spacer(Modifier.height(6.dp)) }

        groupedCardItems(
            keyPrefix = "dns",
            items = listOf(
                CardItem("enable") {
                    TriStatePreference(
                        title = stringResource(R.string.network_dns_enable),
                        value = dns?.enable,
                        onValueChange = { v -> viewModel.updateDns { it.copy(enable = v) } },
                    )
                },
                CardItem("listen") {
                    val dialogTitle = stringResource(R.string.network_dns_listen_title)
                    ArrowPreference(
                        title = stringResource(R.string.network_dns_listen),
                        summary = dns?.listen ?: stringResource(R.string.common_not_modified),
                        onClick = { editor.editText(dialogTitle, dns?.listen) { v -> viewModel.updateDns { it.copy(listen = v) } } },
                        enabled = subEnabled,
                    )
                },
                CardItem("ipv6") {
                    TriStatePreference(
                        title = stringResource(R.string.network_dns_ipv6),
                        value = dns?.ipv6,
                        onValueChange = { v -> viewModel.updateDns { it.copy(ipv6 = v) } },
                        enabled = subEnabled,
                    )
                },
                CardItem("preferH3") {
                    TriStatePreference(
                        title = stringResource(R.string.network_dns_prefer_h3),
                        value = dns?.preferH3,
                        onValueChange = { v -> viewModel.updateDns { it.copy(preferH3 = v) } },
                        enabled = subEnabled,
                    )
                },
                CardItem("useHosts") {
                    TriStatePreference(
                        title = stringResource(R.string.network_dns_use_hosts),
                        value = dns?.useHosts,
                        onValueChange = { v -> viewModel.updateDns { it.copy(useHosts = v) } },
                        enabled = subEnabled,
                    )
                },
                CardItem("enhancedMode") {
                    OverrideChoicePreference(
                        title = stringResource(R.string.network_dns_enhanced_mode),
                        choices = DNS_ENHANCED_MODE_CHOICES,
                        value = dns?.enhancedMode,
                        onValueChange = { v -> viewModel.updateDns { it.copy(enhancedMode = v) } },
                        enabled = subEnabled,
                    )
                },
                listItem("nameserver", R.string.network_dns_nameserver, dns?.nameserver) { v ->
                    viewModel.updateDns { it.copy(nameserver = v) }
                },
                listItem("fallback", R.string.network_dns_fallback, dns?.fallback) { v ->
                    viewModel.updateDns { it.copy(fallback = v) }
                },
                listItem("defaultNameserver", R.string.network_dns_default_nameserver, dns?.defaultNameserver) { v ->
                    viewModel.updateDns { it.copy(defaultNameserver = v) }
                },
                listItem("fakeipFilter", R.string.network_dns_fakeip_filter, dns?.fakeIpFilter) { v ->
                    viewModel.updateDns { it.copy(fakeIpFilter = v) }
                },
            ),
        )
    }

    OverrideEditorDialogs(editor)
}
