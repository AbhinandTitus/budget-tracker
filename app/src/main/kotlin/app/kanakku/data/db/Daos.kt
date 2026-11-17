package app.kanakku.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.kanakku.core.model.CategorySource
import app.kanakku.core.model.Direction
import app.kanakku.core.model.TxnType
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: TxnEntity): Long

    @Update
    suspend fun update(txn: TxnEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TxnEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TxnEntity?>

    @Query("SELECT * FROM transactions WHERE effectiveAt >= :from AND effectiveAt < :to ORDER BY occurredAt DESC, id DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TxnEntity>>

    @Query("SELECT * FROM transactions WHERE effectiveAt >= :from AND effectiveAt < :to ORDER BY occurredAt DESC, id DESC")
    suspend fun between(from: Long, to: Long): List<TxnEntity>

    @Query(
        "SELECT * FROM transactions WHERE merchantName LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' " +
            "OR accountLabel LIKE '%' || :q || '%' ORDER BY occurredAt DESC LIMIT :limit",
    )
    fun search(q: String, limit: Int = 200): Flow<List<TxnEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(effectiveAt) FROM transactions")
    suspend fun earliest(): Long?

    @Query("SELECT COUNT(*) FROM transactions WHERE refNo = :ref AND amountMinor = :amount AND direction = :direction AND ABS(occurredAt - :at) < 259200000")
    suspend fun countSameReference(ref: String, amount: Long, direction: Direction, at: Long): Int

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE amountMinor = :amount AND direction = :direction AND accountKey = :account " +
            "AND ABS(occurredAt - :at) < 120000 AND (refNo IS NULL OR :ref IS NULL)",
    )
    suspend fun countNearTwin(amount: Long, direction: Direction, account: String, at: Long, ref: String?): Int

    @Query(
        "UPDATE transactions SET categoryId = :categoryId, type = :type, categorySource = :source, needsReview = 0 " +
            "WHERE merchantKey = :key AND direction = :direction AND categorySource != 'USER' AND type != 'TRANSFER'",
    )
    suspend fun recategorise(key: String, direction: Direction, categoryId: String, type: TxnType, source: CategorySource): Int

    @Query("UPDATE transactions SET merchantName = :name WHERE merchantKey = :key")
    suspend fun rename(key: String, name: String): Int

    @Query(
        "SELECT merchantKey AS merchantKey, MAX(merchantName) AS merchantName, COUNT(*) AS uses FROM transactions " +
            "WHERE needsReview = 1 AND merchantKey IS NOT NULL GROUP BY merchantKey ORDER BY uses DESC LIMIT :limit",
    )
    suspend fun unknownMerchants(limit: Int): List<UnknownMerchant>

    @Query("SELECT * FROM transactions WHERE balanceMinor IS NOT NULL ORDER BY occurredAt DESC LIMIT 400")
    suspend fun recentWithBalance(): List<TxnEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    suspend fun all(): List<TxnEntity>
}

data class UnknownMerchant(val merchantKey: String, val merchantName: String, val uses: Int)

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity)

    @Query("SELECT * FROM rules")
    suspend fun all(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE merchantKey = :key")
    suspend fun byKey(key: String): RuleEntity?

    @Query("DELETE FROM rules")
    suspend fun deleteAll()
}

@Dao
interface DueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(due: DueEntity)

    @Query("SELECT * FROM dues WHERE dueDay >= :fromDay ORDER BY dueDay ASC")
    fun observeFrom(fromDay: Long): Flow<List<DueEntity>>

    @Query("DELETE FROM dues")
    suspend fun deleteAll()
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatEntity): Long

    @Query("SELECT * FROM chat ORDER BY id ASC")
    fun observe(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat ORDER BY id DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<ChatEntity>

    @Query("DELETE FROM chat WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM chat")
    suspend fun clear()
}
