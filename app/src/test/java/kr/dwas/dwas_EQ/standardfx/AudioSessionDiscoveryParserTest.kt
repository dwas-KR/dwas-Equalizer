package kr.dwas.dwas_EQ.standardfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSessionDiscoveryParserTest {
    @Test
    fun activeCurrentMediaSessionIsPreserved() {
        val dump = """
            AudioPlaybackConfiguration piid:1 deviceIds:[2] type:android.media.AudioTrack u/pid:10262/5484 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null sessionId:49 mutedState:none
        """.trimIndent()
        assertEquals(linkedSetOf(49), AudioSessionDiscoveryParser.parse(dump))
    }

    @Test
    fun eventHistoryRecoversActiveMediaSession() {
        val dump = """
            AudioPlaybackConfiguration piid:95 deviceId:0 type:android.media.AudioTrack u/pid:10262/5484 state:idle attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null sessionId:49 mutedState:none
            Events log: playback activity as reported through PlayerBase
            new player piid:95 uid/pid:10262/5484 type:android.media.AudioTrack attr:AudioAttributes: usage=USAGE_UNKNOWN content=CONTENT_TYPE_UNKNOWN flags=0x0 tags= bundle=null session:49
            player piid:95 new AudioAttributes:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null
            player piid:95 event:started
        """.trimIndent()
        assertEquals(linkedSetOf(49), AudioSessionDiscoveryParser.parse(dump))
    }

    @Test
    fun releasedEventHistorySessionIsExcluded() {
        val dump = """
            Events log: playback activity as reported through PlayerBase
            new player piid:95 uid/pid:10262/5484 type:android.media.AudioTrack attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null session:49
            player piid:95 event:started
            player piid:95 event:stopped
            releasing player piid:95
        """.trimIndent()
        assertTrue(AudioSessionDiscoveryParser.parse(dump).isEmpty())
    }
}
