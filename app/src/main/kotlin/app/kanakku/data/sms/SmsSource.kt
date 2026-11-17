package app.kanakku.data.sms

import android.content.Context
import android.provider.Telephony

data class RawSms(val sender: String, val body: String, val date: Long)

/** Where messages come from. An interface so ingestion can be tested without a phone. */
fun interface SmsSource {
    /** Messages received at or after [since], oldest first. */
    fun read(since: Long): Sequence<RawSms>
}

/** Reads the device inbox. Requires READ_SMS; yields nothing when it has not been granted. */
class InboxSmsSource(private val context: Context) : SmsSource {
    override fun read(since: Long): Sequence<RawSms> = sequence {
        val cursor = try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )
        } catch (_: SecurityException) {
            null
        } ?: return@sequence
        cursor.use { c ->
            val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val a = c.getString(address) ?: continue
                val b = c.getString(body) ?: continue
                yield(RawSms(a, b, c.getLong(date)))
            }
        }
    }
}
