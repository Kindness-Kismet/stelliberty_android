package com.stelliberty.android.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobWorkItem
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.stelliberty.android.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class ProfileUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val task: ProfileUpdateTask by inject()
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val previous = running
        running = scope.launch {
            previous?.join()
            try {
                withContext(Dispatchers.IO) {
                    while (true) {
                        val work = params.dequeueWork() ?: break
                        task.run(work.intent)
                        params.completeWork(work)
                    }
                }
                jobFinished(params, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error(TAG, "Deferred profile update failed", e)
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        // 未完成的 work item 由系统重新投递，切回界面不参与恢复。
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ProfileUpdateJobService"
        private const val JOB_ID = 1

        fun enqueue(context: Context, intent: Intent) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ProfileUpdateJobService::class.java))
                // 系统联网探测失败时，本地订阅或 mixed-port 仍可能可达。
                .setRequiredNetwork(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build())
                .setBackoffCriteria(ProfileUpdateScheduler.MIN_INTERVAL_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            val result = context.getSystemService(JobScheduler::class.java).enqueue(job, JobWorkItem(intent))
            check(result == JobScheduler.RESULT_SUCCESS) { "Unable to enqueue automatic profile update" }
            AppLogger.info(TAG, "Queued automatic profile update: ${intent.action}")
        }
    }
}
