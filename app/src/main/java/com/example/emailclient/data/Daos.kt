package com.example.emailclient.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Delete
    suspend fun delete(account: Account)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE accountId = :accountId AND folder = :folder ORDER BY dateEpochMillis DESC")
    fun observeFolder(accountId: Long, folder: String): Flow<List<EmailMessage>>

    @Query("SELECT * FROM messages WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): EmailMessage?

    @Query("SELECT uid FROM messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun getKnownUids(accountId: Long, folder: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<EmailMessage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: EmailMessage)

    @Query("UPDATE messages SET isRead = :isRead WHERE localId = :localId")
    suspend fun setRead(localId: Long, isRead: Boolean)

    @Query("UPDATE messages SET isStarred = :isStarred WHERE localId = :localId")
    suspend fun setStarred(localId: Long, isStarred: Boolean)

    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun clearAccount(accountId: Long)
}
