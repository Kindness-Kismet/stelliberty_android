package com.stelliberty.android.viewmodel

import androidx.lifecycle.ViewModelStore
import com.stelliberty.android.domain.model.LogEvent
import com.stelliberty.android.domain.model.LogMessage
import com.stelliberty.android.domain.repository.MihomoRepository
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        source.emit(LogMessage("info", "started after opening logs"))
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
        previous.emit(LogMessage("info", "previous proxy"))
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        val previousId = viewModel.logs.value.single().id

        previous.emit(LogMessage("info", "queued before switching"))
        val current = LogSource()
        viewModel.setRepository(current.repository)
        runCurrent()
        assertEquals(0, previous.activeSubscriptions)
        assertEquals(1, current.activeSubscriptions)
        assertFalse(viewModel.uiState.value.isConnected)
        assertTrue(viewModel.logs.value.isEmpty())

        previous.emit(LogEvent.Connected)
        previous.emit(LogMessage("info", "stale proxy"))
        current.emit(LogEvent.Connected)
        current.emit(LogMessage("info", "current proxy"))
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
        source.emit(LogMessage("info", "before background"))
        runCurrent()

        viewModel.stopObserving()
        runCurrent()
        assertEquals(0, source.activeSubscriptions)
        assertFalse(viewModel.uiState.value.isConnected)
        assertEquals("before background", viewModel.logs.value.single().message.payload)

        source.emit(LogMessage("info", "while in background"))
        viewModel.startObserving()
        runCurrent()
        source.emit(LogEvent.Connected)
        source.emit(LogMessage("info", "after foreground"))
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
        previous.emit(LogMessage("info", "old session"))
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

        repeat(650) { source.emit(LogMessage("info", "log $it")) }
        runCurrent()
        assertTrue(viewModel.logs.value.isEmpty())
        advanceTimeBy(120)
        runCurrent()
        assertEquals(500, viewModel.logs.value.size)
        assertEquals("log 150", viewModel.logs.value.first().message.payload)
        assertEquals("log 649", viewModel.logs.value.last().message.payload)
        val previousId = viewModel.logs.value.last().id

        source.emit(LogMessage("info", "discard with clear"))
        runCurrent()
        viewModel.clearLogs()
        assertTrue(viewModel.logs.value.isEmpty())
        source.emit(LogMessage("info", "after clear"))
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        assertEquals("after clear", viewModel.logs.value.single().message.payload)
        assertTrue(viewModel.logs.value.single().id > previousId)
    }

    private class LogSource {
        private val events = MutableSharedFlow<LogEvent>(extraBufferCapacity = 1024)
        var subscriptionStarts = 0
            private set
        var activeSubscriptions = 0
            private set

        val repository = Proxy.newProxyInstance(
            MihomoRepository::class.java.classLoader,
            arrayOf(MihomoRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getConnectionState" -> MutableStateFlow(true)
                "logsFlow" -> flow {
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
