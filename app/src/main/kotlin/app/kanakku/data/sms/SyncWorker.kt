package app.kanakku.data.sms

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kanakku.Graph
import java.util.concurrent.TimeUnit

/**
 * Reads new messages from the inbox. The inbox is the single source of truth: whether this runs
 * seconds after a message arrives, twice a day, or only when the app is opened, the result is
 * the same and nothing is counted twice.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = Graph.get(applicationContext)
        if (!SmsPermission.granted(applicationContext) || !graph.settings.current().onboarded) return Result.success()
        return try {
            val result = graph.repository.scan()
            if (result.added > 0) CategoriseWorker.enqueue(applicationContext)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val SOON = "sync-soon"
        private const val PERIODIC = "sync-periodic"

        /** Shortly after a bank alert arrives; bursts of alerts collapse into one run. */
        fun enqueueSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(4, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }

        /** A twice-daily safety net, skipped when the battery is low. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS, 3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Asks Gemini to place merchants the built-in dictionaries could not. Runs only with a key, on a network. */
class CategoriseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = Graph.get(applicationContext)
        return try {
            graph.advisor.categoriseUnknownMerchants()
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CategoriseWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("categorise", ExistingWorkPolicy.KEEP, request)
        }
    }
}
