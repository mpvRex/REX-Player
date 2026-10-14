package xyz.mpv.rex.utils.media

import xyz.mpv.rex.domain.network.NetworkFile
import xyz.mpv.rex.preferences.FolderSortType
import xyz.mpv.rex.preferences.SortOrder
import xyz.mpv.rex.utils.sort.SortUtils

/**
 * Pure comparator utility for remote [NetworkFile] items.
 * Enforces the folders-first invariant, natural alphanumeric name ordering,
 * date modified ordering, size ordering, and deterministic name tie-breaking.
 */
object NetworkFileComparator {

  /**
   * Builds a [Comparator] for [NetworkFile] according to the specified [sortType] and [sortOrder].
   *
   * @param sortType The attribute to sort by (Title, Date, Size, Duration, VideoCount).
   * @param sortOrder Ascending or Descending.
   * @param foldersFirst When true, directories are guaranteed to precede files regardless of sort direction.
   */
  fun create(
    sortType: FolderSortType = FolderSortType.Title,
    sortOrder: SortOrder = SortOrder.Ascending,
    foldersFirst: Boolean = true,
  ): Comparator<NetworkFile> {
    val naturalNameComparator = Comparator<NetworkFile> { a, b ->
      SortUtils.NaturalOrderComparator.DEFAULT.compare(a.name, b.name)
    }

    val contentComparator = when (sortType) {
      FolderSortType.Date -> compareBy<NetworkFile> { it.lastModified }.then(naturalNameComparator)
      FolderSortType.Size -> compareBy<NetworkFile> { it.size }.then(naturalNameComparator)
      else -> naturalNameComparator
    }

    val directionalComparator = if (sortOrder.isAscending) {
      contentComparator
    } else {
      contentComparator.reversed()
    }

    return if (foldersFirst) {
      compareBy<NetworkFile> { !it.isDirectory }.then(directionalComparator)
    } else {
      directionalComparator
    }
  }

  operator fun invoke(
    sortType: FolderSortType = FolderSortType.Title,
    sortOrder: SortOrder = SortOrder.Ascending,
    foldersFirst: Boolean = true,
  ): Comparator<NetworkFile> = create(sortType, sortOrder, foldersFirst)
}
