package com.example.notebucket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.notebucket.data.entity.DraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE id = 1")
    fun observe(): Flow<DraftEntity?>

    @Query("SELECT * FROM drafts WHERE id = 1")
    suspend fun get(): DraftEntity?

    @Query("DELETE FROM drafts WHERE id = 1")
    suspend fun delete()
}
