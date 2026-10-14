package xyz.mpv.rex.utils.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.mpv.rex.domain.network.NetworkConnection
import xyz.mpv.rex.domain.network.NetworkProtocol
import xyz.mpv.rex.ui.browser.networkstreaming.proxy.NetworkStreamingProxy

class SmbPathParseTest {
  @Test
  fun `parses the raw unencoded path MiXplorer sends`() {
    val raw = "smb://192.168.0.2/useless/Videos/EXT/Sample.Movie.2026.Director." +
      "(Dont Stop Go Deeper ...).Part.1.1080p.mp4"

    val parsed = FolderPlaylistOps.parseSmbPath(raw)

    assertEquals(
      FolderPlaylistOps.SmbPath(
        host = "192.168.0.2",
        share = "useless",
        filePath = "Videos/EXT/Sample.Movie.2026.Director.(Dont Stop Go Deeper ...).Part.1.1080p.mp4",
      ),
      parsed,
    )
  }

  @Test
  fun `strips credentials and port from the authority`() {
    val parsed = FolderPlaylistOps.parseSmbPath("smb://user:pass@nas.local:4450/media/a/b.mkv")

    assertEquals("nas.local", parsed?.host)
    assertEquals("media", parsed?.share)
    assertEquals("a/b.mkv", parsed?.filePath)
    assertEquals("user", parsed?.username)
    assertEquals("pass", parsed?.password)
    assertEquals(4450, parsed?.port)
  }

  @Test
  fun `parses authority with username only and no password`() {
    val parsed = FolderPlaylistOps.parseSmbPath("smb://admin@nas.local/media/video.mp4")

    assertEquals("nas.local", parsed?.host)
    assertEquals("media", parsed?.share)
    assertEquals("video.mp4", parsed?.filePath)
    assertEquals("admin", parsed?.username)
    assertNull(parsed?.password)
    assertNull(parsed?.port)
  }

  @Test
  fun `parses authority with port only and no user`() {
    val parsed = FolderPlaylistOps.parseSmbPath("smb://nas.local:1445/media/video.mp4")

    assertEquals("nas.local", parsed?.host)
    assertEquals("media", parsed?.share)
    assertEquals("video.mp4", parsed?.filePath)
    assertNull(parsed?.username)
    assertNull(parsed?.password)
    assertEquals(1445, parsed?.port)
  }

  @Test
  fun `parses bracketed IPv6 host with port and credentials`() {
    val parsed = FolderPlaylistOps.parseSmbPath("smb://user:pass@[fe80::1]:445/share/movie.mkv")

    assertEquals("fe80::1", parsed?.host)
    assertEquals("share", parsed?.share)
    assertEquals("movie.mkv", parsed?.filePath)
    assertEquals("user", parsed?.username)
    assertEquals("pass", parsed?.password)
    assertEquals(445, parsed?.port)
  }

  @Test
  fun `keeps a file sitting directly in the share root`() {
    val parsed = FolderPlaylistOps.parseSmbPath("smb://host/share/movie.mp4")

    assertEquals("share", parsed?.share)
    assertEquals("movie.mp4", parsed?.filePath)
  }

  @Test
  fun `rejects anything that is not a full smb file path`() {
    assertNull(FolderPlaylistOps.parseSmbPath("http://127.0.0.1:34858/924984514"))
    assertNull(FolderPlaylistOps.parseSmbPath("/storage/emulated/0/Movies/a.mp4"))
    assertNull(FolderPlaylistOps.parseSmbPath("smb://host"))
    assertNull(FolderPlaylistOps.parseSmbPath("smb://host/share"))
    assertNull(FolderPlaylistOps.parseSmbPath("smb:///share/a.mp4"))
  }

  @Test
  fun `extracts streamId from local proxy URL`() {
    assertEquals(
      "123_456789",
      NetworkStreamingProxy.getInstance().extractStreamId("http://127.0.0.1:8080/123_456789"),
    )
    assertEquals(
      "smb_123456",
      NetworkStreamingProxy.getInstance().extractStreamId("http://localhost:8080/smb_123456"),
    )
    assertNull(
      NetworkStreamingProxy.getInstance().extractStreamId("http://192.168.1.10:8080/123_456"),
    )
    assertNull(
      NetworkStreamingProxy.getInstance().extractStreamId(null),
    )
    assertNull(
      NetworkStreamingProxy.getInstance().extractStreamId("content://media/external/video/1"),
    )
  }

  @Test
  fun `unregisterStream removes stream and executes asynchronously without error`() {
    val proxy = NetworkStreamingProxy.getInstance()
    val connection = NetworkConnection(
      id = 99L,
      name = "TestSMB",
      protocol = NetworkProtocol.SMB,
      host = "127.0.0.1",
      port = 445,
      path = "Share",
    )
    val streamId = "test_stream_123"
    val url = proxy.registerStream(streamId, connection, "/test.mp4")
    assertEquals(streamId, proxy.extractStreamId(url))

    proxy.unregisterStream(streamId)
    proxy.unregisterStream(streamId)
    proxy.unregisterStream("unknown_stream_id")
  }
}
