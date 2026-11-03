package xyz.mpv.rex.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import xyz.mpv.rex.database.entities.FolderAggregateEntity

@Dao
interface FolderAggregateDao {
  /** Direct children of one directory: one indexed lookup, no whole-library scan. */
  @Query("SELECT * FROM folder_aggregates WHERE parentPath = :parentPath")
  suspend fun getChildren(parentPath: String): List<FolderAggregateEntity>

  @Query("SELECT COUNT(*) FROM folder_aggregates")
  suspend fun count(): Int

  @Query("SELECT * FROM folder_aggregates ORDER BY path")
  suspend fun getAll(): List<FolderAggregateEntity>

  @Query("SELECT DISTINCT parentPath FROM folder_aggregates")
  suspend fun allParentPaths(): List<String>

  @Query("SELECT * FROM folder_aggregates WHERE path = :path")
  suspend fun getByPath(path: String): FolderAggregateEntity?

  @Upsert
  suspend fun upsertAll(rows: List<FolderAggregateEntity>)

  @Query("DELETE FROM folder_aggregates WHERE path IN (:paths)")
  suspend fun deleteByPaths(paths: List<String>)

  @Query("DELETE FROM folder_aggregates")
  suspend fun clear()
}
