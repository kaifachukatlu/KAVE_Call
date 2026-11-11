package com.kaif.mastercaller

import com.kaif.mastercaller.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun versionComparison() {
        assertTrue(UpdateManager.isNewer("1.2.0", "1.1.0"))
        assertTrue(UpdateManager.isNewer("v1.10.0", "1.9.9"))
        assertTrue(UpdateManager.isNewer("2", "1.9"))
        assertFalse(UpdateManager.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.1.9", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.2", "1.2.0"))
    }

    @Test
    fun parsesGithubLatestRelease() {
        val json = """
            {"tag_name":"v1.3.0","draft":false,"prerelease":false,"body":"Faster calls\nMore fixes",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10},
                       {"name":"KAVE-Call.apk","browser_download_url":"https://x/KAVE-Call.apk","size":1234}]}
        """.trimIndent()
        val u = UpdateManager.parseRelease(json)!!
        assertEquals("1.3.0", u.version)
        assertEquals("https://x/KAVE-Call.apk", u.apkUrl)
        assertEquals(1234L, u.sizeBytes)
        assertEquals("Faster calls", u.notes.lineSequence().first())
    }

    @Test
    fun ignoresReleasesWithoutApkOrDrafts() {
        assertNull(UpdateManager.parseRelease("""{"tag_name":"v2.0.0","assets":[]}"""))
        assertNull(UpdateManager.parseRelease("""{"tag_name":"v2.0.0","draft":true,"assets":[{"name":"a.apk"}]}"""))
    }
}
