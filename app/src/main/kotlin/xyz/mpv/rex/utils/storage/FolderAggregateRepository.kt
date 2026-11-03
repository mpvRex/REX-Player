package xyz.mpv.rex.utils.storage

import xyz.mpv.rex.database.dao.FolderAggregateDao
import xyz.mpv.rex.database.entities.FolderAggregateEntity

/**
 * Reads and writes the persisted per-directory aggregates that back the tree browser.
 *
 * The stored unit is one directory's **own** media (direct counts, no children), which is what
 * keeps an open cheap: reconciling a directory touches only that directory and the children
 * being listed. Recursive totals are folded on read by [LibrarySnapshot] from rows already in
 * memory, so they never require a provider round trip.
 */
internal interface FolderAggregateSource {
    suspend fun count(): Int
    suspend fun getChildren(parentPath: String): List<FolderAggregateEntity>
    suspend fun getByPath(path: String): FolderAggregateEntity?
    suspend fun loadAll(): List<FolderAggregateEntity>
    suspend fun parents(): List<String>
    suspend fun upsert(rows: Collection<FolderAggregateEntity>)
    suspend fun delete(paths: Collection<String>)
    suspend fun clear()
}

internal class FolderAggregateRepository(
    private val dao: FolderAggregateDao,
) : FolderAggregateSource {
    override suspend fun count(): Int = dao.count()

    override suspend fun getChildren(parentPath: String): List<FolderAggregateEntity> =
        dao.getChildren(folderKey(parentPath))

    override suspend fun getByPath(path: String): FolderAggregateEntity? = dao.getByPath(folderKey(path))

    override suspend fun loadAll(): List<FolderAggregateEntity> = dao.getAll()

    override suspend fun parents(): List<String> = dao.allParentPaths()

    override suspend fun upsert(rows: Collection<FolderAggregateEntity>) {
        if (rows.isEmpty()) return
        dao.upsertAll(rows.map { it.copy(path = folderKey(it.path)) })
    }

    override suspend fun delete(paths: Collection<String>) {
        if (paths.isEmpty()) return
        dao.deleteByPaths(paths.map(::folderKey))
    }

    override suspend fun clear() = dao.clear()
}
