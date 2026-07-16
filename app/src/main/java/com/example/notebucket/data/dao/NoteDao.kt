package com.example.notebucket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.notebucket.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY timestamp DESC")
    fun observeByFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY timestamp DESC")
    suspend fun getByFolder(folderId: String): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :id")
    suspend fun move(id: String, folderId: String)

    @Query("UPDATE notes SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveBatch(ids: List<String>, folderId: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("SELECT folderId, COUNT(*) as cnt FROM notes GROUP BY folderId")
    fun observeCountsByFolder(): Flow<List<FolderCount>>

    @Query("SELECT COUNT(*) FROM notes WHERE folderId = :folderId")
    fun observeCountByFolder(folderId: String): Flow<Int>
}

data class FolderCount(val folderId: String, val cnt: Int)
