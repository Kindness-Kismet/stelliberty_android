package com.stelliberty.android.viewmodel

import androidx.lifecycle.ViewModelStore
import com.stelliberty.android.data.store.ProxySelectionStore
import com.stelliberty.android.domain.model.GroupsResponse
import com.stelliberty.android.domain.model.MihomoConfig
import com.stelliberty.android.domain.model.ProvidersResponse
import com.stelliberty.android.domain.model.ProxiesResponse
import com.stelliberty.android.domain.model.ProxyNode
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.platform.ProfileFileManager
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyViewModelTest {
    private val mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private val selections = ProxySelectionStore(
        Proxy.newProxyInstance(
            ProfileFileManager::class.java.classLoader,
            arrayOf(ProfileFileManager::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "readMihomoFile", "writeMihomoFile" -> null
                else -> error("Unexpected file operation: ${method.name}")
            }
        } as ProfileFileManager,
        persistenceScope,
    )
    private var activeUuid = "profile-a"
    private lateinit var viewModel: ProxyViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        viewModel = ProxyViewModel(selections, { activeUuid })
        store.put("proxies", viewModel)
    }

    @AfterTest
    fun tearDown() {
        persistenceScope.cancel()
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    private fun runProxyTest(block: suspend () -> Unit) = runBlocking<Unit> {
        withContext(mainDispatcher) {
            try {
                withTimeout(5_000) { block() }
            } finally {
                store.clear()
            }
        }
    }

    private suspend fun attach(source: ProxySource) {
        viewModel.setRepository(source)
        viewModel.uiState.first { it.groups.isNotEmpty() }
    }

    @Test
    fun lastClickWinsEvenWhenEarlierSelectionIsSlow() = runProxyTest {
        val source = ProxySource()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        source.onSelect = { _, node ->
            if (node == "B") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            Result.success(Unit)
        }
        attach(source)

        viewModel.selectProxy("Example", "B")
        firstStarted.await()
        viewModel.selectProxy("Example", "C")
        yield()
        releaseFirst.complete(Unit)
        source.awaitSelections(2)

        assertEquals("C", source.groups.first().now)
        assertEquals("C", viewModel.uiState.value.groups.first().now)
        assertEquals("C", selections.selections(activeUuid)["Example"])
    }

    @Test
    fun restoringOtherGroupsCannotOverwriteManualSelection() = runProxyTest {
        selections.select(activeUuid, "Example", "B")
        selections.select(activeUuid, "Other", "B")
        val source = ProxySource(listOf(group("Example"), group("Other")))
        val restoreStarted = CompletableDeferred<Unit>()
        val releaseRestore = CompletableDeferred<Unit>()
        source.onSelect = { group, _ ->
            if (group == "Other") {
                restoreStarted.complete(Unit)
                releaseRestore.await()
            }
            Result.success(Unit)
        }
        attach(source)
        restoreStarted.await()

        viewModel.selectProxy("Example", "C")
        yield()
        releaseRestore.complete(Unit)
        source.awaitSelections(3)

        assertEquals("C", source.groups.first().now)
        assertEquals("C", viewModel.uiState.value.groups.first().now)
        assertEquals("C", selections.selections(activeUuid)["Example"])
    }

    @Test
    fun failedSelectionKeepsCurrentNodeAndReportsReason() = runProxyTest {
        val source = ProxySource()
        source.onSelect = { _, _ -> Result.failure(IllegalStateException("Selection rejected")) }
        attach(source)
        viewModel.selectProxy("Example", "B")
        source.awaitSelections(1)

        assertEquals("A", viewModel.uiState.value.groups.first().now)
        assertTrue(selections.selections(activeUuid).isEmpty())
        assertEquals("Selection rejected", viewModel.uiState.value.error)
    }

    @Test
    fun delayedResponseCannotWriteSelectionToAnotherProfile() = runProxyTest {
        val source = ProxySource()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        source.onSelect = { _, _ ->
            started.complete(Unit)
            release.await()
            Result.success(Unit)
        }
        attach(source)
        viewModel.selectProxy("Example", "B")
        started.await()
        activeUuid = "profile-b"
        release.complete(Unit)
        source.awaitSelections(1)

        assertTrue(selections.selections("profile-b").isEmpty())
    }

    @Test
    fun refreshFinishesBeforeApplyingQueuedSelection() = runProxyTest {
        val source = ProxySource()
        attach(source)
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        source.onGetGroups = {
            val snapshot = source.groups
            refreshStarted.complete(Unit)
            releaseRefresh.await()
            Result.success(GroupsResponse(snapshot))
        }
        viewModel.loadProxies()
        refreshStarted.await()
        viewModel.selectProxy("Example", "C")
        yield()
        assertTrue(source.selectionRequests.isEmpty())

        releaseRefresh.complete(Unit)
        source.awaitSelections(1)
        assertEquals("C", source.groups.single().now)
        assertEquals("C", viewModel.uiState.value.groups.single().now)
    }

    @Test
    fun interruptedRestorationRetriesUnfinishedGroups() = runProxyTest {
        selections.select(activeUuid, "Example", "B")
        selections.select(activeUuid, "Other", "B")
        val source = ProxySource(listOf(group("Example"), group("Other")))
        val restoreStarted = CompletableDeferred<Unit>()
        var attempts = 0
        source.onSelect = { group, _ ->
            if (group == "Other" && ++attempts == 1) {
                restoreStarted.complete(Unit)
                awaitCancellation()
            }
            Result.success(Unit)
        }
        attach(source)
        restoreStarted.await()
        viewModel.loadProxies()
        viewModel.uiState.first { state -> state.groups.all { it.now == "B" } }

        assertEquals(2, attempts)
        assertEquals(listOf("B", "B"), source.groups.map { it.now })
    }

    @Test
    fun repositorySwitchCancelsSelectionsAndQueuedRequests() = runProxyTest {
        val previous = ProxySource()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        previous.onSelect = { _, _ ->
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        attach(previous)
        viewModel.selectProxy("Example", "B")
        started.await()
        viewModel.selectProxy("Example", "C")
        activeUuid = "profile-b"
        val current = ProxySource()
        attach(current)
        cancelled.await()

        assertEquals(listOf("Example" to "B"), previous.selectionRequests)
        assertEquals("A", viewModel.uiState.value.groups.single().now)
        assertTrue(selections.selections(activeUuid).isEmpty())
    }

    @Test
    fun selectingAfterUnfixWinsInCoreUiAndStorage() = runProxyTest {
        val source = ProxySource(listOf(group("Example").copy(type = "URLTest", now = "B", fixed = "B")))
        selections.select(activeUuid, "Example", "B")
        val unfixStarted = CompletableDeferred<Unit>()
        val releaseUnfix = CompletableDeferred<Unit>()
        source.onUnfix = {
            unfixStarted.complete(Unit)
            releaseUnfix.await()
            Result.success(Unit)
        }
        attach(source)
        viewModel.unfixProxy("Example")
        unfixStarted.await()
        viewModel.selectProxy("Example", "C")
        releaseUnfix.complete(Unit)
        source.awaitSelections(1)

        assertEquals("C", source.groups.single().fixed)
        assertEquals("C", viewModel.uiState.value.groups.single().fixed)
        assertEquals("C", selections.selections(activeUuid)["Example"])
    }

    private class ProxySource(initialGroups: List<ProxyNode> = listOf(group("Example"))) :
        MihomoRepository by unsupportedRepository() {
        var groups = initialGroups
        val selectionRequests = mutableListOf<Pair<String, String>>()
        var onSelect: suspend (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) }
        var onUnfix: suspend () -> Result<Unit> = { Result.success(Unit) }
        var onGetGroups: suspend () -> Result<GroupsResponse> = { Result.success(GroupsResponse(groups)) }
        private val completed = Channel<Unit>(Channel.UNLIMITED)

        override suspend fun getGroups() = onGetGroups()
        override suspend fun getProxies() = Result.success(ProxiesResponse())
        override suspend fun getProviders() = Result.success(ProvidersResponse())
        override suspend fun getConfig() = Result.success(MihomoConfig(mode = "rule"))

        override suspend fun selectProxy(group: String, name: String): Result<Unit> {
            selectionRequests += group to name
            val result = onSelect(group, name)
            if (result.isSuccess) groups = groups.map {
                if (it.name == group) it.copy(now = name, fixed = if (it.type == "Selector") "" else name) else it
            }
            completed.send(Unit)
            return result
        }

        override suspend fun unfixProxy(group: String): Result<Unit> {
            val result = onUnfix()
            if (result.isSuccess) groups = groups.map { if (it.name == group) it.copy(now = "A", fixed = "") else it }
            return result
        }

        suspend fun awaitSelections(count: Int) {
            repeat(count) { completed.receive() }
            yield()
        }
    }

    companion object {
        private fun group(name: String) = ProxyNode(name = name, type = "Selector", now = "A", all = listOf("A", "B", "C"))

        private fun unsupportedRepository() = Proxy.newProxyInstance(
            MihomoRepository::class.java.classLoader,
            arrayOf(MihomoRepository::class.java),
        ) { _, method, _ -> error("Unexpected repository call: ${method.name}") } as MihomoRepository
    }
}
