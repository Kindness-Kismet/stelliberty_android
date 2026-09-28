package com.stelliberty.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stelliberty.android.ui.util.rememberIsWideScreen
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
fun rememberSearchScreenStatus(label: String): MutableState<SearchStatus> {
    val status = remember { mutableStateOf(SearchStatus(label = label)) }
    LaunchedEffect(label) {
        if (status.value.label != label) {
            status.value = status.value.copy(label = label)
        }
    }
    return status
}

@Composable
fun SearchResultStatusEffect(status: MutableState<SearchStatus>, isResultEmpty: Boolean) {
    val resultStatus = when {
        status.value.searchText.isEmpty() -> SearchStatus.ResultStatus.DEFAULT
        isResultEmpty -> SearchStatus.ResultStatus.EMPTY
        else -> SearchStatus.ResultStatus.SHOW
    }
    LaunchedEffect(resultStatus) {
        if (status.value.resultStatus != resultStatus) {
            status.value = status.value.copy(resultStatus = resultStatus)
        }
    }
}

@Composable
fun rememberSearchBarTopPadding(scrollBehavior: ScrollBehavior): () -> Dp {
    val isWideScreen = rememberIsWideScreen()
    return remember(isWideScreen, scrollBehavior) {
        if (isWideScreen) {
            { 0.dp }
        } else {
            { SearchBarTopPadding * (1f - scrollBehavior.state.collapsedFraction) }
        }
    }
}

private val SearchBarTopPadding = 12.dp
