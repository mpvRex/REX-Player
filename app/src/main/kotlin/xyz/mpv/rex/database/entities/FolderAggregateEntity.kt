package xyz.mpv.rex.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted per-folder media aggregates for the tree browser.
 *
 * A row describes **only the media files directly inside [path]** (no children), which is
 * what keeps it diffable: reconcile touches the directories whose listing actually changed
 * and reuses every other row untouched. Recursive totals are folded on read, so they cost
 * nothing to recompute and stay consistent with whatever direct rows exist.
 *
 * Rows are discarded whenever the media-scanner generation they were captured under no
 * longer matches (see [xyz.mpv.rex.utils.storage.MediaTreeStore]); anything MediaStore or a
 * filesystem walk could no longer prove current is rebuilt instead of trusted.
 */
@Entity(
  tableName = "folder_aggregates",
  indices = [Index(value = ["parentPath"])],
)
data class FolderAggregateEntity(
  @PrimaryKey
  val path: String,
  /** Parent directory path, empty for a volume root. Indexed so a listing is one range scan. */
  val parentPath: String,
  val name: String,
  /** Latest modified time of the media directly in this folder, in seconds. */
  val lastModified: Long,
  val directVideoCount: Int = 0,
  val directAudioCount: Int = 0,
  val directNewCount: Int = 0,
  val directUnwatchedCount: Int = 0,
  val directVideoSize: Long = 0L,
  val directAudioSize: Long = 0L,
  val directVideoDuration: Long = 0L,
  val directAudioDuration: Long = 0L,
  /**
   * True when MediaStore had no rows for this directory and its statistics came from a
   * direct filesystem listing. Those counts are complete; the counterpart (a MediaStore
   * backed row) may be missing files the provider has not indexed yet.
   */
  val fromFileSystem: Boolean = false,
  /** Media-scanner generation this row was captured under; see [MediaTreeStore.invalidate]. */
  val indexedGeneration: Long = 0L,
  /**
   * True for directories materialised only to keep the tree walkable (no direct media and not
   * a volume root). They are transparent to the browser, and are the first rows evicted.
   */
  val isAnchor: Boolean = false,
)
