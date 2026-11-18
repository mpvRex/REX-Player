package xyz.mpv.rex.utils.storage

import java.net.URI
import java.net.URLDecoder
import java.io.File

/** Translate Android's external-storage document IDs to paths used by media scanning. */
internal fun selectedFolderPath(value: String): String {
    if (value.isBlank()) return ""
    if (!value.startsWith("content://")) return folderKey(File(value).normalize().path)
    return runCatching {
        val uri = URI(value)
        val raw = uri.rawPath
        val id = URLDecoder.decode(
            raw.substringAfterLast(if ("/document/" in raw) "/document/" else "/tree/"),
            "UTF-8",
        )
        if (uri.authority == "com.android.externalstorage.documents" && ':' in id) {
            val volume = id.substringBefore(':')
            val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
            folderKey("$root/${id.substringAfter(':')}")
        } else {
            // Tree and document URIs for the same provider must compare by document ID.
            "content://${uri.authority}/document/$id".trimEnd('/')
        }
    }.getOrDefault(value.trimEnd('/'))
}

internal fun comparableFolderPath(value: String): String =
    selectedFolderPath(value).let {
        when {
            it == "/sdcard" || it.startsWith("/sdcard/") -> "/storage/emulated/0" + it.removePrefix("/sdcard")
            else -> it
        }
    }

