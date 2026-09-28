package com.stelliberty.android.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.ConfigurationOverride
import com.stelliberty.android.domain.model.DnsOverride
import com.stelliberty.android.platform.showToast
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.ListEditDialog
import com.stelliberty.android.ui.component.RestartRequiredHint
import com.stelliberty.android.ui.component.TriStatePreference
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.NetworkSettingsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun NetworkSettingsScreen(
    viewModel: NetworkSettingsViewModel,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val resetDoneMsg = stringResource(R.string.dialog_reset_done)

    fun updateTop(transform: (ConfigurationOverride) -> ConfigurationOverride) {
        viewModel.update(transform)
    }

    fun updateDns(transform: (DnsOverride) -> DnsOverride) {
        viewModel.updateDns(transform)
    }

    var showPortDialog by remember { mutableStateOf(false) }
    var editingPortTitle by remember { mutableStateOf("") }
    var editingPortSetter by remember { mutableStateOf<(Int?) -> Unit>({}) }
    val portTextState = rememberTextFieldState()

    var showStringDialog by remember { mutableStateOf(false) }
    var editingStringTitle by remember { mutableStateOf("") }
    var editingStringSetter by remember { mutableStateOf<(String?) -> Unit>({}) }
    val stringTextState = rememberTextFieldState()

    var showListDialog by remember { mutableStateOf(false) }
    var editingListTitle by remember { mutableStateOf("") }
    var editingListSetter by remember { mutableStateOf<(List<String>?) -> Unit>({}) }
    val listTextState = rememberTextFieldState()

    fun openPortDialog(title: String, value: Int?, setter: (Int?) -> Unit) {
        editingPortTitle = title
        editingPortSetter = setter
        portTextState.edit { replace(0, length, value?.toString() ?: "") }
        showPortDialog = true
    }

    fun openStringDialog(title: String, value: String?, setter: (String?) -> Unit) {
        editingStringTitle = title
        editingStringSetter = setter
        stringTextState.edit { replace(0, length, value ?: "") }
        showStringDialog = true
    }

    fun openListDialog(title: String, value: List<String>?, setter: (List<String>?) -> Unit) {
        editingListTitle = title
        editingListSetter = setter
        listTextState.edit { replace(0, length, value?.joinToString("\n") ?: "") }
        showListDialog = true
    }

    val dns = uiState.dns

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.network_settings_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                                },
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
            ),
        ) {
            item { RestartRequiredHint() }

            item { SmallTitle(text = stringResource(R.string.network_proxy_ports)) }
            groupedCardItems(
                keyPrefix = "network_ports",
                items = listOf(
                    CardItem("http") {
                        val httpPortTitle = stringResource(R.string.network_http_port)
                        ArrowPreference(
                            title = httpPortTitle,
                            summary = portSummary(uiState.httpPort),
                            onClick = { openPortDialog(httpPortTitle, uiState.httpPort) { v -> updateTop { it.copy(httpPort = v) } } },
                        )
                    },
                    CardItem("socks") {
                        val socksPortTitle = stringResource(R.string.network_socks_port)
                        ArrowPreference(
                            title = socksPortTitle,
                            summary = portSummary(uiState.socksPort),
                            onClick = { openPortDialog(socksPortTitle, uiState.socksPort) { v -> updateTop { it.copy(socksPort = v) } } },
                        )
                    },
                    CardItem("redir") {
                        val redirPortTitle = stringResource(R.string.network_redir_port)
                        ArrowPreference(
                            title = redirPortTitle,
                            summary = portSummary(uiState.redirPort),
                            onClick = { openPortDialog(redirPortTitle, uiState.redirPort) { v -> updateTop { it.copy(redirPort = v) } } },
                        )
                    },
                    CardItem("tproxy") {
                        val tproxyPortTitle = stringResource(R.string.network_tproxy_port)
                        ArrowPreference(
                            title = tproxyPortTitle,
                            summary = portSummary(uiState.tproxyPort),
                            onClick = {
                                openPortDialog(
                                    tproxyPortTitle,
                                    uiState.tproxyPort
                                ) { v -> updateTop { it.copy(tproxyPort = v) } }
                            },
                        )
                    },
                    CardItem("mixed") {
                        val mixedPortTitle = stringResource(R.string.network_mixed_port)
                        ArrowPreference(
                            title = mixedPortTitle,
                            summary = portSummary(uiState.mixedPort),
                            onClick = { openPortDialog(mixedPortTitle, uiState.mixedPort) { v -> updateTop { it.copy(mixedPort = v) } } },
                        )
                    },
                ),
            )

            item { SmallTitle(text = stringResource(R.string.network_options)) }
            groupedCardItems(
                keyPrefix = "network_options",
                items = listOf(
                    CardItem("allowLan") {
                        TriStatePreference(
                            title = stringResource(R.string.network_allow_lan),
                            value = uiState.allowLan,
                            onValueChange = { v -> updateTop { it.copy(allowLan = v) } },
                        )
                    },
                    CardItem("ipv6") {
                        TriStatePreference(
                            title = stringResource(R.string.network_ipv6),
                            value = uiState.ipv6,
                            onValueChange = { v -> updateTop { it.copy(ipv6 = v) } },
                        )
                    },
                    CardItem("bindAddress") {
                        val bindAddrTitle = stringResource(R.string.network_bind_address)
                        ArrowPreference(
                            title = bindAddrTitle,
                            summary = uiState.bindAddress ?: stringResource(R.string.common_not_modified),
                            onClick = {
                                openStringDialog(
                                    bindAddrTitle,
                                    uiState.bindAddress
                                ) { v -> updateTop { it.copy(bindAddress = v) } }
                            },
                        )
                    },
                    CardItem("logLevel") {
                        LogLevelPreference(
                            value = uiState.logLevel,
                            onValueChange = { v -> updateTop { it.copy(logLevel = v) } },
                        )
                    },
                ),
            )

            val dnsSubEnabled = dns?.enable != false
            item { SmallTitle(text = stringResource(R.string.network_dns)) }
            groupedCardItems(
                keyPrefix = "network_dns",
                items = listOf(
                    CardItem("enable") {
                        TriStatePreference(
                            title = stringResource(R.string.network_dns_enable),
                            value = dns?.enable,
                            onValueChange = { v -> updateDns { it.copy(enable = v) } },
                        )
                    },
                    CardItem("listen") {
                        val dnsListenTitle = stringResource(R.string.network_dns_listen_title)
                        ArrowPreference(
                            title = stringResource(R.string.network_dns_listen),
                            summary = dns?.listen ?: stringResource(R.string.common_not_modified),
                            onClick = { openStringDialog(dnsListenTitle, dns?.listen) { v -> updateDns { it.copy(listen = v) } } },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("ipv6") {
                        TriStatePreference(
                            title = stringResource(R.string.network_dns_ipv6),
                            value = dns?.ipv6,
                            onValueChange = { v -> updateDns { it.copy(ipv6 = v) } },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("preferH3") {
                        TriStatePreference(
                            title = stringResource(R.string.network_dns_prefer_h3),
                            value = dns?.preferH3,
                            onValueChange = { v -> updateDns { it.copy(preferH3 = v) } },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("useHosts") {
                        TriStatePreference(
                            title = stringResource(R.string.network_dns_use_hosts),
                            value = dns?.useHosts,
                            onValueChange = { v -> updateDns { it.copy(useHosts = v) } },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("enhancedMode") {
                        DnsEnhancedModePreference(
                            value = dns?.enhancedMode,
                            onValueChange = { v -> updateDns { it.copy(enhancedMode = v) } },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("nameserver") {
                        val nameserverTitle = stringResource(R.string.network_dns_nameserver)
                        ArrowPreference(
                            title = nameserverTitle,
                            summary = listSummary(dns?.nameserver),
                            onClick = { openListDialog(nameserverTitle, dns?.nameserver) { v -> updateDns { it.copy(nameserver = v) } } },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("fallback") {
                        val fallbackTitle = stringResource(R.string.network_dns_fallback)
                        ArrowPreference(
                            title = fallbackTitle,
                            summary = listSummary(dns?.fallback),
                            onClick = { openListDialog(fallbackTitle, dns?.fallback) { v -> updateDns { it.copy(fallback = v) } } },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("defaultNameserver") {
                        val defaultNsTitle = stringResource(R.string.network_dns_default_nameserver)
                        ArrowPreference(
                            title = defaultNsTitle,
                            summary = listSummary(dns?.defaultNameserver),
                            onClick = {
                                openListDialog(
                                    defaultNsTitle,
                                    dns?.defaultNameserver
                                ) { v -> updateDns { it.copy(defaultNameserver = v) } }
                            },
                            enabled = dnsSubEnabled,
                        )
                    },
                    CardItem("fakeipFilter") {
                        val fakeipFilterTitle = stringResource(R.string.network_dns_fakeip_filter)
                        ArrowPreference(
                            title = fakeipFilterTitle,
                            summary = listSummary(dns?.fakeIpFilter),
                            onClick = {
                                openListDialog(
                                    fakeipFilterTitle,
                                    dns?.fakeIpFilter
                                ) { v -> updateDns { it.copy(fakeIpFilter = v) } }
                            },
                            enabled = dnsSubEnabled,
                        )
                    },
                ),
            )

            item {
                Spacer(
                    Modifier
                        .height(24.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }

    PortEditDialog(
        show = showPortDialog,
        title = editingPortTitle,
        textState = portTextState,
        onDismiss = { showPortDialog = false },
        onConfirm = { port -> editingPortSetter(port) },
        onReset = {
            editingPortSetter(null)
            showToast(resetDoneMsg)
        },
    )

    StringEditDialog(
        show = showStringDialog,
        title = editingStringTitle,
        textState = stringTextState,
        onDismiss = { showStringDialog = false },
        onConfirm = { value -> editingStringSetter(value) },
        onReset = {
            editingStringSetter(null)
            showToast(resetDoneMsg)
        },
    )

    ListEditDialog(
        show = showListDialog,
        title = editingListTitle,
        textState = listTextState,
        onDismiss = { showListDialog = false },
        onConfirm = { list -> editingListSetter(list) },
        onReset = {
            editingListSetter(null)
            showToast(resetDoneMsg)
        },
    )
}

@Composable
private fun LogLevelPreference(
    value: String?,
    onValueChange: (String?) -> Unit,
) {
    val notModifiedStr = stringResource(R.string.common_not_modified)
    val items = listOf(notModifiedStr, "Info", "Warning", "Error", "Debug", "Silent")
    val values = listOf(null, "info", "warning", "error", "debug", "silent")
    val selectedIndex = values.indexOf(value).coerceAtLeast(0)

    OverlayDropdownPreference(
        title = stringResource(R.string.network_log_level),
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index -> onValueChange(values[index]) },
    )
}

@Composable
private fun DnsEnhancedModePreference(
    value: String?,
    onValueChange: (String?) -> Unit,
    enabled: Boolean = true,
) {
    val notModifiedStr2 = stringResource(R.string.common_not_modified)
    val items = listOf(notModifiedStr2, "Normal", "FakeIP", "Redir-Host")
    val values = listOf(null, "normal", "fake-ip", "redir-host")
    val selectedIndex = values.indexOf(value).coerceAtLeast(0)

    OverlayDropdownPreference(
        title = stringResource(R.string.network_dns_enhanced_mode),
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index -> onValueChange(values[index]) },
        enabled = enabled,
    )
}

@Composable
private fun PortEditDialog(
    show: Boolean,
    title: String,
    textState: TextFieldState,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    onReset: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = stringResource(R.string.network_port_zero_hint),
        onDismissRequest = onDismiss,
    ) {
        TextField(
            state = textState,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.network_port_label),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.common_not_modified),
                modifier = Modifier.weight(1f),
                onClick = {
                    onReset()
                    onDismiss()
                },
            )
            TextButton(
                text = stringResource(R.string.common_cancel),
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            )
            TextButton(
                text = stringResource(R.string.common_confirm),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    val port = textState.text.toString().toIntOrNull()
                    if (port != null && port in 0..65535) {
                        onConfirm(port)
                    }
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun StringEditDialog(
    show: Boolean,
    title: String,
    textState: TextFieldState,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    onReset: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        TextField(
            state = textState,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.network_input_value),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.common_not_modified),
                modifier = Modifier.weight(1f),
                onClick = {
                    onReset()
                    onDismiss()
                },
            )
            TextButton(
                text = stringResource(R.string.common_cancel),
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            )
            TextButton(
                text = stringResource(R.string.common_confirm),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    val value = textState.text.toString().trim()
                    onConfirm(value.ifEmpty { null })
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun portSummary(port: Int?): String = if (port == null) stringResource(R.string.common_not_modified) else "$port"

@Composable
private fun listSummary(list: List<String>?): String {
    if (list == null) return stringResource(R.string.common_not_modified)
    if (list.isEmpty()) return stringResource(R.string.common_cleared)
    return pluralStringResource(R.plurals.common_items_count, list.size, list.size)
}
