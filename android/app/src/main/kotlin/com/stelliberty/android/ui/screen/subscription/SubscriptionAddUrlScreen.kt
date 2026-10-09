package com.stelliberty.android.ui.screen.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stelliberty.android.R
import com.stelliberty.android.domain.model.SubscriptionAutoUpdateMode
import com.stelliberty.android.domain.model.SubscriptionUpdateProxyMode
import com.stelliberty.android.ui.component.AdaptiveTopAppBar
import com.stelliberty.android.ui.component.CardItem
import com.stelliberty.android.ui.component.UpdateProxyModePreference
import com.stelliberty.android.ui.component.blur.BlurredBar
import com.stelliberty.android.ui.component.blur.rememberBlurBackdrop
import com.stelliberty.android.ui.component.groupedCardItems
import com.stelliberty.android.ui.icon.AppIcons
import com.stelliberty.android.ui.util.TestTags
import com.stelliberty.android.ui.util.horizontalCutoutPadding
import com.stelliberty.android.viewmodel.SubscriptionViewModel
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SubscriptionAddUrlScreen(
    viewModel: SubscriptionViewModel,
    initialUrl: String = "",
    initialName: String = "",
    initialIntervalMinutes: Long = 0,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    var inputName by remember { mutableStateOf(initialName) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var userAgent by remember { mutableStateOf("") }
    var ageSecretKey by remember { mutableStateOf("") }
    var updateProxyMode by remember { mutableStateOf(SubscriptionUpdateProxyMode.Core) }
    var autoDelayMinutes by remember { mutableStateOf("") }
    var autoUpdateMode by remember {
        mutableStateOf(
            if (initialIntervalMinutes > 0) SubscriptionAutoUpdateMode.Interval
            else SubscriptionAutoUpdateMode.Disabled
        )
    }
    var intervalMinutes by remember {
        mutableStateOf(if (initialIntervalMinutes > 0) initialIntervalMinutes.toString() else "")
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.subscription_config),
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
                .imePadding()
                .horizontalCutoutPadding()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
            ),
        ) {
            item(key = "hint") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 12.dp, bottom = 6.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Info,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = stringResource(R.string.subscription_url_hint),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            item(key = "name_title") {
                SmallTitle(text = stringResource(R.string.subscription_name))
            }
            item(key = "name_field") {
                TextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    modifier = Modifier
                        .testTag(TestTags.Subscription.NAME)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                    label = stringResource(R.string.subscription_name_auto_placeholder),
                    useLabelAsPlaceholder = true,
                )
            }
            item(key = "url_title") {
                SmallTitle(text = stringResource(R.string.subscription_url_label))
            }
            item(key = "url_field") {
                TextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier
                        .testTag(TestTags.Subscription.URL)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                    label = stringResource(R.string.subscription_url_placeholder),
                    useLabelAsPlaceholder = true,
                )
            }
            item(key = "user_agent_title") {
                SmallTitle(text = stringResource(R.string.subscription_user_agent))
            }
            item(key = "user_agent_field") {
                TextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    modifier = Modifier
                        .testTag(TestTags.Subscription.USER_AGENT)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                    label = stringResource(R.string.subscription_user_agent_placeholder),
                    useLabelAsPlaceholder = true,
                )
            }
            item(key = "age_secret_key_title") {
                SmallTitle(text = stringResource(R.string.subscription_age_secret_key))
            }
            item(key = "age_secret_key_field") {
                TextField(
                    value = ageSecretKey,
                    onValueChange = { ageSecretKey = it },
                    modifier = Modifier
                        .testTag(TestTags.Subscription.AGE_KEY)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                    label = stringResource(R.string.subscription_age_secret_key_placeholder),
                    useLabelAsPlaceholder = true,
                )
            }
            groupedCardItems(
                keyPrefix = "auto_update",
                items = listOf(
                    CardItem("subscriptionAutoUpdate") {
                        AutoUpdateModePreference(mode = autoUpdateMode, onModeChange = { autoUpdateMode = it })
                    },
                ),
                outerTopPadding = 6.dp,
                outerBottomPadding = 12.dp,
            )
            if (autoUpdateMode == SubscriptionAutoUpdateMode.Interval) {
                item(key = "interval_field") {
                    SmallTitle(text = stringResource(R.string.subscription_auto_update_interval_minutes))
                    TextField(
                        value = intervalMinutes,
                        onValueChange = { intervalMinutes = it.filter { c -> c.isDigit() } },
                        label = stringResource(R.string.subscription_auto_update_placeholder),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier
                            .testTag(TestTags.Subscription.INTERVAL)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    )
                }
            }
            groupedCardItems(
                keyPrefix = "update_proxy",
                items = listOf(
                    CardItem("subscriptionUpdateProxyMode") {
                        UpdateProxyModePreference(mode = updateProxyMode, onModeChange = { updateProxyMode = it })
                    },
                ),
                outerBottomPadding = 6.dp,
            )
            item(key = "auto_delay_field") {
                AutoDelayField(
                    value = autoDelayMinutes,
                    onValueChange = { autoDelayMinutes = it },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            item(key = "save") {
                TextButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        viewModel.addSubscription(
                            name = inputName.trim(),
                            url = inputUrl,
                            autoUpdateMode = autoUpdateMode,
                            intervalMinutes = if (autoUpdateMode == SubscriptionAutoUpdateMode.Interval) {
                                intervalMinutes.toIntOrNull() ?: 0
                            } else 0,
                            userAgent = userAgent.trim(),
                            ageSecretKey = ageSecretKey.trim(),
                            updateProxyMode = updateProxyMode,
                            autoTestDelayMinutes = autoDelayMinutes.toIntOrNull() ?: 0,
                            onComplete = onSaved,
                        )
                    },
                    enabled = inputUrl.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            if (uiState.error.isNotEmpty()) {
                item(key = "error") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(
                            text = uiState.error,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item(key = "bottom_spacer") {
                Spacer(
                    Modifier
                        .height(24.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }

    ImportProgressDialog(
        show = uiState.isLoading,
        step = uiState.importProgress?.let { importStepLabel(it) } ?: stringResource(R.string.common_processing),
        onCancel = { viewModel.cancelCurrentUpdate() },
    )
}
