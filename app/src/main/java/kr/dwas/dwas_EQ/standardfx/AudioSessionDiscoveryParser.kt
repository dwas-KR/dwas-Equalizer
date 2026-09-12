package kr.dwas.dwas_EQ.standardfx

object AudioSessionDiscoveryParser {
    private val activeMediaSession = Regex(
        """AudioPlaybackConfiguration\b.*\bstate:started\b.*\busage=USAGE_MEDIA\b.*\bsessionId:(\d+)\b"""
    )
    private val newPlayer = Regex(
        """new player piid:(\d+)\b.*\busage=(USAGE_[A-Z_]+)\b.*\bsession:(\d+)\b"""
    )
    private val playerAttributes = Regex(
        """player piid:(\d+)\b new AudioAttributes:.*\busage=(USAGE_[A-Z_]+)\b"""
    )
    private val playerStarted = Regex("""player piid:(\d+)\b event:started\b""")
    private val playerInactive = Regex("""player piid:(\d+)\b event:(?:paused|stopped)\b""")
    private val playerReleased = Regex("""releasing player piid:(\d+)\b""")

    private data class PlayerState(
        val sessionId: Int,
        val media: Boolean,
        val started: Boolean,
    )

    fun parse(text: String): LinkedHashSet<Int> {
        val sessions = linkedSetOf<Int>()
        val players = linkedMapOf<Int, PlayerState>()
        var inPlaybackEvents = false

        text.lineSequence().forEach { line ->
            val active = activeMediaSession.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (active != null && active > 0) sessions += active

            if (line.contains("Events log: playback activity as reported through PlayerBase")) {
                inPlaybackEvents = true
                return@forEach
            }
            if (!inPlaybackEvents) return@forEach

            newPlayer.find(line)?.let { match ->
                val piid = match.groupValues[1].toIntOrNull() ?: return@let
                val sessionId = match.groupValues[3].toIntOrNull() ?: 0
                players[piid] = PlayerState(
                    sessionId = sessionId,
                    media = match.groupValues[2] == "USAGE_MEDIA",
                    started = false,
                )
                return@forEach
            }

            playerAttributes.find(line)?.let { match ->
                val piid = match.groupValues[1].toIntOrNull() ?: return@let
                val state = players[piid] ?: return@let
                players[piid] = state.copy(media = match.groupValues[2] == "USAGE_MEDIA")
                return@forEach
            }

            playerStarted.find(line)?.let { match ->
                val piid = match.groupValues[1].toIntOrNull() ?: return@let
                val state = players[piid] ?: return@let
                players[piid] = state.copy(started = true)
                return@forEach
            }

            playerInactive.find(line)?.let { match ->
                val piid = match.groupValues[1].toIntOrNull() ?: return@let
                val state = players[piid] ?: return@let
                players[piid] = state.copy(started = false)
                return@forEach
            }

            playerReleased.find(line)?.let { match ->
                match.groupValues[1].toIntOrNull()?.let(players::remove)
            }
        }

        players.values.forEach { state ->
            if (state.started && state.media && state.sessionId > 0) sessions += state.sessionId
        }
        return sessions
    }
}
