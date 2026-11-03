package xyz.mpv.rex.utils.storage

import xyz.mpv.rex.database.entities.FolderAggregateEntity

/**
 * In-memory [FolderAggregateSource] so scanner behaviour can be exercised without Room.
 */
internal class FakeFolderAggregateSource : FolderAggregateSource {
  private val rows = LinkedHashMap<String, FolderAggregateEntity>()

  override suspend fun count(): Int = rows.size

  override suspend fun getChildren(parentPath: String): List<FolderAggregateEntity> =
    rows.values.filter { it.parentPath == folderKey(parentPath) }

  override suspend fun getByPath(path: String): FolderAggregateEntity? = rows[folderKey(path)]

  override suspend fun loadAll(): List<FolderAggregateEntity> = rows.values.toList()

  override suspend fun parents(): List<String> = rows.values.map { it.parentPath }.distinct()

  override suspend fun upsert(rows: Collection<FolderAggregateEntity>) {
    for (row in rows) this.rows[folderKey(row.path)] = row.copy(path = folderKey(row.path))
  }

  override suspend fun delete(paths: Collection<String>) {
    for (path in paths) rows.remove(folderKey(path))
  }

  override suspend fun clear() = rows.clear()
}
