package kr.dwas.dwas_EQ.standardfx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect


class AudioEffectSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId <= 0) return
        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> AudioEffectSessionRegistry.opened(sessionId)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> AudioEffectSessionRegistry.closed(sessionId)
        }
    }
}
