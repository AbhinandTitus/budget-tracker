package app.kanakku.data.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.kanakku.core.sms.SmsParser

object SmsPermission {
    val required = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
}

/**
 * Woken by the system when a message arrives. It does no parsing and stores nothing: if the
 * message could be a money alert it schedules an inbox sync a few seconds later, otherwise it
 * returns at once. There is no running service and no polling.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val body = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.joinToString("") { it.messageBody.orEmpty() }
        } catch (_: Exception) {
            null
        } ?: return
        if (SmsParser.mightBeFinancial(body)) SyncWorker.enqueueSoon(context.applicationContext)
    }
}
