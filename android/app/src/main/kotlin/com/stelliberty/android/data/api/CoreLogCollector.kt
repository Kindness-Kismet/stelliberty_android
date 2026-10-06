package com.stelliberty.android.data.api

import com.stelliberty.android.domain.model.LogEvent
import com.stelliberty.android.domain.model.LogLevel
import com.stelliberty.android.domain.model.LogSource
import com.stelliberty.android.domain.repository.MihomoRepository
import com.stelliberty.android.util.AppLogger
import com.stelliberty.android.util.DiagnosticLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CoreLogCollector(
    private val scope: CoroutineScope,
    private val logs: DiagnosticLogStore = AppLogger.logs,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var repository: MihomoRepository? = null
    private var job: Job? = null

    @Synchronized
    fun setRepository(repo: MihomoRepository?) {
        if (repository === repo) return
        job?.cancel()
        repository = repo
        logs.setCoreConnected(false)
        job = repo?.let {
            scope.launch(dispatcher) {
                repo.logsFlow(LogLevel.Debug).collect { event ->
                    synchronized(this@CoreLogCollector) {
                        if (repository !== repo) return@collect
                        when (event) {
                            LogEvent.Connected -> logs.setCoreConnected(true)
                            LogEvent.Disconnected -> logs.setCoreConnected(false)
                            is LogEvent.Message -> logs.append(LogSource.Core, event.message.type, "mihomo", event.message.payload)
                        }
                    }
                }
            }
        }
    }
}
