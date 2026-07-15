package com.example.notebucket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.notebucket.data.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("UPDATE folders SET name = :name, isUserRenamed = 1 WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE folders SET centroid = :centroid, noteCount = :count WHERE id = :id")
    suspend fun updateCentroid(id: String, centroid: ByteArray, count: Int)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM folders")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int
}
