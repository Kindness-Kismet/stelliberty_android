package com.stelliberty.android.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.FieldHint
import com.stelliberty.android.ui.component.RestartRequiredHint
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.viewmodel.ClashFeaturesViewModel
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun PortControlScreen(
    viewModel: ClashFeaturesViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = rememberOverrideEditorState()

    fun portItem(
        key: String,
        @StringRes titleRes: Int,
        value: Int?,
        assign: ConfigurationOverride.(Int?) -> ConfigurationOverride,
    ) = CardItem(key) {
        val title = stringResource(titleRes)
        ArrowPreference(
            title = title,
            summary = portSummary(value),
            onClick = { editor.editPort(title, value) { v -> viewModel.update { it.assign(v) } } },
        )
    }

    SettingsSubPage(title = stringResource(R.string.port_control_title), onBack = onBack) {
        item { RestartRequiredHint() }

        item { SmallTitle(text = stringResource(R.string.network_proxy_ports)) }
        groupedCardItems(
            keyPrefix = "port_proxy",
            items = listOf(
                portItem("mixed", R.string.network_mixed_port, state.mixedPort) { copy(mixedPort = it) },
                portItem("socks", R.string.network_socks_port, state.socksPort) { copy(socksPort = it) },
                portItem("http", R.string.network_http_port, state.httpPort) { copy(httpPort = it) },
                portItem("redir", R.string.network_redir_port, state.redirPort) { copy(redirPort = it) },
                portItem("tproxy", R.string.network_tproxy_port, state.tproxyPort) { copy(tproxyPort = it) },
            ),
        )

        item { SmallTitle(text = stringResource(R.string.external_control_title)) }
        groupedCardItems(
            keyPrefix = "port_controller",
            items = listOf(
                CardItem("controller") {
                    val title = stringResource(R.string.external_control_ip)
                    ArrowPreference(
                        title = title,
                        summary = state.externalController ?: stringResource(R.string.common_not_modified),
                        onClick = {
                            editor.editText(title, state.externalController) { v ->
                                viewModel.update { it.copy(externalController = v) }
                            }
                        },
                    )
                    FieldHint(stringResource(R.string.external_control_controller_hint))
                },
                CardItem("secret") {
                    val title = stringResource(R.string.external_control_secret)
                    ArrowPreference(
                        title = title,
                        summary = state.secret ?: stringResource(R.string.common_not_modified),
                        onClick = { editor.editText(title, state.secret) { v -> viewModel.update { it.copy(secret = v) } } },
                    )
                    FieldHint(stringResource(R.string.external_control_secret_hint))
                },
            ),
        )
    }

    OverrideEditorDialogs(editor)
}
