package app.kanakku

import android.app.Application
import android.content.Context
import app.kanakku.ai.Advisor
import app.kanakku.ai.GeminiClient
import app.kanakku.data.KeystoreSecretBox
import app.kanakku.data.Repository
import app.kanakku.data.Settings
import app.kanakku.data.db.AppDatabase
import app.kanakku.data.sms.InboxSmsSource
import app.kanakku.data.sms.SyncWorker

/** The app's few long-lived objects, built once. Tests install their own with [Graph.install]. */
class Graph(
    val db: AppDatabase,
    val settings: Settings,
    val repository: Repository,
    val advisor: Advisor,
) {
    companion object {
        @Volatile private var instance: Graph? = null

        fun get(context: Context): Graph = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        fun install(graph: Graph) { instance = graph }

        private fun build(context: Context): Graph {
            val db = AppDatabase.open(context)
            val settings = Settings(context, KeystoreSecretBox())
            val repository = Repository(db, settings, InboxSmsSource(context))
            val advisor = Advisor(repository, settings, db.chat(), GeminiClient())
            return Graph(db, settings, repository, advisor)
        }
    }
}

class KanakkuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)
    }
}
