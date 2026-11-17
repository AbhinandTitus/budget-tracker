package app.kanakku.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kanakku.core.model.AccountKind
import app.kanakku.core.model.CategorySource
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.core.model.Source
import app.kanakku.core.model.TxnType

/** One money movement. Amounts are paise; times are epoch milliseconds. */
@Entity(
    tableName = "transactions",
    indices = [
        Index("effectiveAt"),
        Index("merchantKey"),
        Index(value = ["smsHash", "occurredAt"], unique = true),
    ],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val direction: Direction,
    val type: TxnType,
    val categoryId: String,
    val merchantKey: String?,
    val merchantName: String,
    val accountKey: String,
    val accountLabel: String,
    val accountKind: AccountKind,
    val channel: Channel,
    /** When the money moved. */
    val occurredAt: Long,
    /** The moment used to place the transaction in a month; differs from [occurredAt] only for month-end salary. */
    val effectiveAt: Long,
    val note: String? = null,
    val source: Source,
    val smsSender: String? = null,
    val smsBody: String? = null,
    val smsHash: String? = null,
    val refNo: String? = null,
    val balanceMinor: Long? = null,
    val excluded: Boolean = false,
    val categorySource: CategorySource,
    val needsReview: Boolean = false,
    val createdAt: Long,
)

/** What the user (or the assistant) has taught about one merchant. */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: String?,
    val displayName: String?,
    val fromAi: Boolean,
    val updatedAt: Long,
)

/** A card statement or payment reminder. */
@Entity(tableName = "dues", indices = [Index(value = ["label", "dueDay"], unique = true)])
data class DueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val totalMinor: Long,
    val minimumMinor: Long?,
    /** Epoch day of the due date. */
    val dueDay: Long,
    val seenAt: Long,
)

@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "user" or "model". */
    val role: String,
    val text: String,
    val createdAt: Long,
    val failed: Boolean = false,
)
