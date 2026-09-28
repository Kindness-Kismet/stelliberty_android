package com.stelliberty.android.ui.screen.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.DnsAnswer
import com.stelliberty.android.domain.model.DnsQueryTypes
import com.stelliberty.android.domain.model.dnsTypeName
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.theme.StatusColors
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.DnsQueryViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DnsQueryScreen(
    viewModel: DnsQueryViewModel,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val textFieldState = rememberTextFieldState()
    var showCacheDialog by remember { mutableStateOf(false) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.dns_title),
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
                    actions = {
                        IconButton(onClick = { showCacheDialog = true }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = stringResource(R.string.dns_clear_cache),
                                tint = MiuixTheme.colorScheme.onSurface,
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
            item(key = "input") {
                Column(
                    modifier = Modifier.padding(all = 12.dp)
                ) {
                    TextField(
                        state = textFieldState,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.dns_domain),
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = {
                            viewModel.setQueryName(textFieldState.text.toString())
                            viewModel.queryDns()
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    val types = DnsQueryTypes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        types.take(3).forEach { type ->
                            TextButton(
                                text = type,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setQueryType(type) },
                                colors = if (uiState.queryType == type) {
                                    ButtonDefaults.textButtonColorsPrimary()
                                } else {
                                    ButtonDefaults.textButtonColors()
                                },
                                minHeight = 36.dp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        types.drop(3).forEach { type ->
                            TextButton(
                                text = type,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setQueryType(type) },
                                colors = if (uiState.queryType == type) {
                                    ButtonDefaults.textButtonColorsPrimary()
                                } else {
                                    ButtonDefaults.textButtonColors()
                                },
                                minHeight = 36.dp,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(
                        text = if (uiState.isQuerying) stringResource(R.string.dns_querying) else stringResource(R.string.dns_query),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = textFieldState.text.isNotBlank() && !uiState.isQuerying,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            viewModel.setQueryName(textFieldState.text.toString())
                            viewModel.queryDns()
                        },
                    )
                }
            }

            if (uiState.error.isNotEmpty()) {
                item(key = "error") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp, bottom = 6.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dns_query_failed, uiState.error),
                            color = StatusColors.danger,
                        )
                    }
                }
            }

            if (uiState.status != null) {
                item(key = "result_title") {
                    SmallTitle(text = stringResource(R.string.dns_results))
                }

                if (uiState.answers.isEmpty()) {
                    item(key = "no_result") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.dns_no_record),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                } else {
                    groupedCardItems(
                        keyPrefix = "dns_result",
                        outerBottomPadding = 12.dp,
                        items = uiState.answers.mapIndexed { index, answer ->
                            CardItem("$index:${answer.data}") {
                                DnsAnswerItem(answer)
                            }
                        },
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    WindowDialog(
        show = showCacheDialog,
        title = stringResource(R.string.dns_clear_cache),
        onDismissRequest = { showCacheDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = stringResource(R.string.dns_clear_dns),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.flushDnsCache()
                    showCacheDialog = false
                },
            )
            TextButton(
                text = stringResource(R.string.dns_clear_fakeip),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.flushFakeIp()
                    showCacheDialog = false
                },
            )
        }
    }
}

@Composable
private fun DnsAnswerItem(answer: DnsAnswer) {
    BasicComponent(
        title = answer.data,
        summary = "TTL: ${answer.TTL}s",
        startAction = {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .squircleBackground(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f), 3.dp)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dnsTypeName(answer.type),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
    )
}
