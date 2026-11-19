package com.metrolist.music.arc

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyExportTest {
    @Test
    fun readsPlaylistsWithLocalFilesAndSkipsEpisodes() {
        val text = """{"playlists":[{"name":"Road trip","lastModifiedDate":"2024-01-01","items":[
            {"track":{"trackName":"Panama","artistName":"Van Halen","albumName":"1984","trackUri":"spotify:track:05RgAMGypEvqhNs5hPCbMS"},"episode":null,"localTrack":null},
            {"track":null,"episode":{"episodeName":"Pod","showName":"Show","episodeUri":"spotify:episode:z"},"localTrack":null},
            {"track":null,"episode":null,"localTrack":{"uri":"spotify:local:Daft+Punk:Discovery:One+More+Time:320"}}
            ]},{"name":"Empty","items":[]}]}"""
        val lists = SpotifyExport.parse(text, "Liked")
        assertEquals(listOf("Road trip"), lists.map { it.title })
        assertEquals(
            listOf(SpotifyLinks.Track("Panama", listOf("Van Halen"), album = "1984"), SpotifyLinks.Track("One More Time", listOf("Daft Punk"), 320, "Discovery")),
            lists.single().tracks,
        )
    }

    @Test
    fun readsLikedSongsFromYourLibrary() {
        val text = """{"tracks":[{"artist":"Madonna","album":"True Blue","track":"La Isla Bonita","uri":"spotify:track:6r8k1vznHrzlEKYxL4dZEe"},{"artist":null,"track":null}],"albums":[]}"""
        val liked = SpotifyExport.parse(text, "Liked").single()
        assertEquals("Liked", liked.title)
        assertEquals(listOf(SpotifyLinks.Track("La Isla Bonita", listOf("Madonna"), album = "True Blue")), liked.tracks)
    }

    @Test
    fun readsTheZipInOrder() {
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                fun file(name: String, text: String) {
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
                file("Spotify Account Data/Playlist10.json", playlist("Ten"))
                file("Spotify Account Data/Userdata.json", "{}")
                file("Spotify Account Data/YourLibrary.json", """{"tracks":[{"artist":"A","album":"B","track":"Liked one"}]}""")
                file("Spotify Account Data/Playlist2.json", playlist("Two"))
            }
        }.toByteArray()
        assertEquals(listOf("Liked", "Two", "Ten"), SpotifyExport.read(bytes.inputStream(), "Liked").map { it.title })
        assertEquals(listOf("Two"), SpotifyExport.read(playlist("Two").byteInputStream(), "Liked").map { it.title })
    }

    private fun playlist(name: String) = """{"playlists":[{"name":"$name","items":[{"track":{"trackName":"S","artistName":"A","albumName":"B"}}]}]}"""

    @Test
    fun ignoresOtherFiles() {
        assertEquals(emptyList<SpotifyLinks.Found>(), SpotifyExport.parse("""{"tracks":[]}""", "Liked"))
        assertEquals(emptyList<SpotifyLinks.Found>(), SpotifyExport.parse("not json", "Liked"))
    }
}
