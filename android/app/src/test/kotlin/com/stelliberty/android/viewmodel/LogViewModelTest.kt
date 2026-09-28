package com.stelliberty.android.viewmodel

import androidx.lifecycle.ViewModelStore
import com.stelliberty.android.domain.model.LogEvent
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.domain.repository.MihomoRepository
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelTest {
    private val store = ViewModelStore()
    private lateinit var viewModel: LogViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = LogViewModel()
        store.put("logs", viewModel)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runLogTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            store.clear()
        }
    }

    @Test
    fun subscribesWhenProxyStartsAfterPageOpens() = runLogTest {
        viewModel.startObserving()
        runCurrent()

        val source = LogSource()
        viewModel.setRepository(source.repository)
        runCurrent()
        assertEquals(1, source.activeSubscriptions)

        source.emit(LogEvent.Connected)
        source.emit(LogMessage(LogLevel.Info, "started after opening logs"))
        runCurrent()
        advanceTimeBy(120)
        runCurrent()

        assertTrue(viewModel.uiState.value.isConnected)
        assertEquals("started after opening logs", viewModel.logs.value.single().message.payload)
    }

    @Test
    fun subscribesOnlyOnceWhilePageIsVisible() = runLogTest {
        val source = LogSource()
        viewModel.setRepository(source.repository)
        runCurrent()
        assertEquals(0, source.subscriptionStarts)

        viewModel.startObserving()
        viewModel.startObserving()
        viewModel.setRepository(source.repository)
        runCurrent()
        assertEquals(1, source.subscriptionStarts)

        viewModel.stopObserving()
        runCurrent()
        assertEquals(0, source.activeSubscriptions)
    }

    @Test
    fun switchesToNewProxyWithoutLeavingPage() = runLogTest {
        val previous = LogSource()
        viewModel.setRepository(previous.repository)
        viewModel.startObserving()
        runCurrent()
        previous.emit(LogEvent.Connected)
        previous.emit(LogMessage(LogLevel.Info, "previous proxy"))
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        val previousId = viewModel.logs.value.single().id

        previous.emit(LogMessage(LogLevel.Info, "queued before switching"))
        val current = LogSource()
        viewModel.setRepository(current.repository)
        runCurrent()
        assertEquals(0, previous.activeSubscriptions)
        assertEquals(1, current.activeSubscriptions)
        assertFalse(viewModel.uiState.value.isConnected)
        assertTrue(viewModel.logs.value.isEmpty())

        previous.emit(LogEvent.Connected)
        previous.emit(LogMessage(LogLevel.Info, "stale proxy"))
        current.emit(LogEvent.Connected)
        current.emit(LogMessage(LogLevel.Info, "current proxy"))
        runCurrent()
        advanceTimeBy(120)
        runCurrent()

        assertTrue(viewModel.uiState.value.isConnected)
        assertEquals("current proxy", viewModel.logs.value.single().message.payload)
        assertTrue(viewModel.logs.value.single().id > previousId)
    }

    @Test
    fun resumesAfterBackgroundAndIgnoresBackgroundLogs() = runLogTest {
        val source = LogSource()
        viewModel.setRepository(source.repository)
        viewModel.startObserving()
        runCurrent()
        source.emit(LogEvent.Connected)
        source.emit(LogMessage(LogLevel.Info, "before background"))
        runCurrent()

        viewModel.stopObserving()
        runCurrent()
        assertEquals(0, source.activeSubscriptions)
        assertFalse(viewModel.uiState.value.isConnected)
        assertEquals("before background", viewModel.logs.value.single().message.payload)

        source.emit(LogMessage(LogLevel.Info, "while in background"))
        viewModel.startObserving()
        runCurrent()
        source.emit(LogEvent.Connected)
        source.emit(LogMessage(LogLevel.Info, "after foreground"))
        runCurrent()
        advanceTimeBy(120)
        runCurrent()

        assertEquals(2, source.subscriptionStarts)
        assertEquals(1, source.activeSubscriptions)
        assertEquals(
            listOf("before background", "after foreground"),
            viewModel.logs.value.map { it.message.payload },
        )
    }

    @Test
    fun resetsOnStopAndWaitsForVisibilityBeforeFollowingNewProxy() = runLogTest {
        val previous = LogSource()
        viewModel.setRepository(previous.repository)
        viewModel.startObserving()
        runCurrent()
        previous.emit(LogEvent.Connected)
        previous.emit(LogMessage(LogLevel.Info, "old session"))
        runCurrent()

        viewModel.setRepository(null)
        runCurrent()
        assertEquals(0, previous.activeSubscriptions)
        assertFalse(viewModel.uiState.value.isConnected)
        assertTrue(viewModel.logs.value.isEmpty())

        viewModel.stopObserving()
        val current = LogSource()
        viewModel.setRepository(current.repository)
        runCurrent()
        assertEquals(0, current.subscriptionStarts)

        viewModel.startObserving()
        runCurrent()
        assertEquals(1, current.activeSubscriptions)
    }

    @Test
    fun connectionStatusComesFromLogChannel() = runLogTest {
        val source = LogSource()
        viewModel.setRepository(source.repository)
        viewModel.startObserving()
        runCurrent()
        assertFalse(viewModel.uiState.value.isConnected)

        source.emit(LogEvent.Connected)
        runCurrent()
        assertTrue(viewModel.uiState.value.isConnected)

        source.emit(LogEvent.Disconnected)
        runCurrent()
        assertFalse(viewModel.uiState.value.isConnected)

        source.emit(LogEvent.Connected)
        runCurrent()
        assertTrue(viewModel.uiState.value.isConnected)
    }

    @Test
    fun batchesLogsCapsHistoryAndKeepsIdsIncreasingAfterClear() = runLogTest {
        val source = LogSource()
        viewModel.setRepository(source.repository)
        viewModel.startObserving()
        runCurrent()

        repeat(650) { source.emit(LogMessage(LogLevel.Info, "log $it")) }
        runCurrent()
        assertTrue(viewModel.logs.value.isEmpty())
        advanceTimeBy(120)
        runCurrent()
        assertEquals(500, viewModel.logs.value.size)
        assertEquals("log 150", viewModel.logs.value.first().message.payload)
        assertEquals("log 649", viewModel.logs.value.last().message.payload)
        val previousId = viewModel.logs.value.last().id

        source.emit(LogMessage(LogLevel.Info, "discard with clear"))
        runCurrent()
        viewModel.clearLogs()
        assertTrue(viewModel.logs.value.isEmpty())
        source.emit(LogMessage(LogLevel.Info, "after clear"))
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        assertEquals("after clear", viewModel.logs.value.single().message.payload)
        assertTrue(viewModel.logs.value.single().id > previousId)
    }

    @Test
    fun exportsUnpublishedLogsWithTimeLevelAndCompletePayload() = runLogTest {
        val source = LogSource()
        viewModel.setRepository(source.repository)
        viewModel.startObserving()
        runCurrent()
        val payload = "[TCP] Example --> example.com match Match using Example\n完整内容"
        source.emit(LogMessage(LogLevel.Warning, payload))
        runCurrent()
        assertTrue(viewModel.logs.value.isEmpty())

        val export = assertNotNull(viewModel.exportLogs())
        val timestamp = export.content.substringBefore(' ')
        Instant.parse(timestamp)
        assertEquals("$timestamp [WARNING] $payload\n", export.content)
        assertTrue(Regex("Stelliberty-logs-\\d{8}-\\d{6}\\.txt").matches(export.fileName))
    }

    @Test
    fun exportSnapshotSurvivesClearAndLaterMessages() = runLogTest {
        assertNull(viewModel.exportLogs())
        val source = LogSource()
        viewModel.setRepository(source.repository)
        viewModel.startObserving()
        runCurrent()
        source.emit(LogMessage(LogLevel.Info, "before export"))
        runCurrent()
        val export = assertNotNull(viewModel.exportLogs())

        viewModel.clearLogs()
        assertNull(viewModel.exportLogs())
        source.emit(LogMessage(LogLevel.Error, "after export"))
        runCurrent()

        assertTrue(export.content.endsWith(" [INFO] before export\n"))
        assertFalse(export.content.contains("after export"))
        assertTrue(assertNotNull(viewModel.exportLogs()).content.endsWith(" [ERROR] after export\n"))
    }

    @Test
    fun filtersHistoryWithoutDiscardingLogsAndResubscribesAtSelectedLevel() = runLogTest {
        val source = LogSource()
        viewModel.setMinimumLevel(LogLevel.Debug)
        viewModel.setRepository(source.repository)
        viewModel.startObserving()
        runCurrent()
        LogLevel.entries.forEach { source.emit(LogMessage(it, it.name)) }
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        val history = viewModel.logs.value
        assertEquals(LogLevel.entries, history.map { it.message.type })

        viewModel.setMinimumLevel(LogLevel.Warning)
        assertEquals(listOf(LogLevel.Warning, LogLevel.Error), viewModel.logs.value.map { it.message.type })
        runCurrent()
        assertEquals(listOf(LogLevel.Debug, LogLevel.Warning), source.subscribedLevels)
        assertEquals(1, source.activeSubscriptions)

        viewModel.setMinimumLevel(LogLevel.Warning)
        runCurrent()
        assertEquals(2, source.subscriptionStarts)

        viewModel.setMinimumLevel(LogLevel.Info)
        assertEquals(listOf(LogLevel.Info, LogLevel.Warning, LogLevel.Error), viewModel.logs.value.map { it.message.type })
        viewModel.setMinimumLevel(LogLevel.Debug)
        runCurrent()
        assertEquals(history, viewModel.logs.value)
        assertEquals(1, source.activeSubscriptions)
        assertEquals(LogLevel.Debug, source.subscribedLevels.last())
    }

    @Test
    fun keepsMinimumLevelAcrossConnectionEventsClearBackgroundAndProxyChanges() = runLogTest {
        val previous = LogSource()
        viewModel.setMinimumLevel(LogLevel.Warning)
        viewModel.setRepository(previous.repository)
        viewModel.startObserving()
        runCurrent()
        previous.emit(LogEvent.Connected)
        previous.emit(LogEvent.Disconnected)
        previous.emit(LogEvent.Connected)
        runCurrent()
        assertTrue(viewModel.uiState.value.isConnected)
        assertEquals(LogLevel.Warning, viewModel.uiState.value.minimumLevel)

        viewModel.clearLogs()
        viewModel.stopObserving()
        runCurrent()
        assertEquals(LogLevel.Warning, viewModel.uiState.value.minimumLevel)
        viewModel.setMinimumLevel(LogLevel.Error)
        val current = LogSource()
        viewModel.setRepository(null)
        viewModel.setRepository(current.repository)
        runCurrent()
        assertEquals(0, current.subscriptionStarts)
        assertFalse(viewModel.uiState.value.isConnected)

        viewModel.startObserving()
        runCurrent()
        assertEquals(listOf(LogLevel.Error), current.subscribedLevels)
        assertEquals(LogLevel.Error, viewModel.uiState.value.minimumLevel)
    }

    @Test
    fun exportsOnlyMatchingHistoryAndUnpublishedMatches() = runLogTest {
        val source = LogSource()
        viewModel.setRepository(source.repository)
        viewModel.startObserving()
        runCurrent()
        source.emit(LogMessage(LogLevel.Info, "hidden info"))
        runCurrent()
        viewModel.setMinimumLevel(LogLevel.Error)
        assertNull(viewModel.exportLogs())
        runCurrent()
        source.emit(LogMessage(LogLevel.Error, "pending error"))
        runCurrent()
        assertTrue(viewModel.logs.value.isEmpty())

        val export = assertNotNull(viewModel.exportLogs())
        assertTrue(export.content.endsWith(" [ERROR] pending error\n"))
        assertFalse(export.content.contains("hidden info"))

        viewModel.setMinimumLevel(LogLevel.Info)
        val complete = assertNotNull(viewModel.exportLogs())
        assertTrue(complete.content.contains(" [INFO] hidden info\n"))
        assertTrue(complete.content.contains(" [ERROR] pending error\n"))
    }

    private class LogSource {
        private val events = MutableSharedFlow<LogEvent>(extraBufferCapacity = 1024)
        val subscribedLevels = mutableListOf<LogLevel>()
        var subscriptionStarts = 0
            private set
        var activeSubscriptions = 0
            private set

        val repository = Proxy.newProxyInstance(
            MihomoRepository::class.java.classLoader,
            arrayOf(MihomoRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getConnectionState" -> MutableStateFlow(true)
                "logsFlow" -> flow {
                    subscribedLevels += args!![0] as LogLevel
                    subscriptionStarts++
                    activeSubscriptions++
                    try {
                        emitAll(events)
                    } finally {
                        activeSubscriptions--
                    }
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as MihomoRepository

        fun emit(event: LogEvent) {
            check(events.tryEmit(event))
        }

        fun emit(message: LogMessage) {
            emit(LogEvent.Message(message))
        }
    }
}
