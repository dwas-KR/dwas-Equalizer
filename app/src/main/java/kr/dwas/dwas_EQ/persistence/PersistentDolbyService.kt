package kr.dwas.dwas_EQ.persistence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kr.dwas.dwas_EQ.MainActivity
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.audio.AudioMutationCoordinator
import kr.dwas.dwas_EQ.audio.AudioRouteMonitor
import kr.dwas.dwas_EQ.audio.RouteState
import kr.dwas.dwas_EQ.backend.BackendTransactionRuntime
import kr.dwas.dwas_EQ.core.NineBandEqAdapter
import kr.dwas.dwas_EQ.backend.AndroidEqualizerRuntime
import kr.dwas.dwas_EQ.data.AppPreferences
import kr.dwas.dwas_EQ.data.AppSettings
import kr.dwas.dwas_EQ.domain.EqCurve
import kr.dwas.dwas_EQ.standardfx.AudioSessionDiscovery
import kr.dwas.dwas_EQ.standardfx.HeadroomCalculator
import kr.dwas.dwas_EQ.standardfx.StandardFxRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock





class PersistentDolbyService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val validationMutex = Mutex()

    private lateinit var preferences: AppPreferences
    private lateinit var routeMonitor: AudioRouteMonitor
    private lateinit var audioManager: AudioManager

    @Volatile private var settings = AppSettings()
    @Volatile private var settingsLoaded = false
    @Volatile private var route: RouteState? = null
    @Volatile private var standardFxMessage: String = ""
    @Volatile private var androidEqMessage: String = ""
    @Volatile private var lastAndroidEqArmed: Boolean = false
    @Volatile private var lastAndroidEqCurve: EqCurve? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val mediaStarted = configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
            if (mediaStarted) serviceScope.launch {
                delay(250L)
                AudioMutationCoordinator.serialized {
                    routeMonitor.refresh()
                    route = routeMonitor.state.value
                    AudioSessionDiscovery.invalidateCache()
                    if (settingsLoaded && settings.eqEnabled && settings.androidEqArmed) {
                        val target = NineBandEqAdapter.uiDbToDap(settings.lastAppliedCurve.toFloatArray())
                        androidEqMessage = AndroidEqualizerRuntime.reapplyArmed(applicationContext, target).message
                        lastAndroidEqArmed = true
                        lastAndroidEqCurve = settings.lastAppliedCurve
                    }
                    if (settingsLoaded && settings.eqEnabled && settings.standardFx.requiresRuntimeHold) {
                        val eqTransactionActive = settings.persistentEqArmed || settings.androidEqArmed ||
                            PersistentDolbyRuntime.activeTransaction() != null || BackendTransactionRuntime.get() != null
                        val headroomCurve = HeadroomCalculator.appliedCurve(eqTransactionActive, settings.lastAppliedCurve)
                        val fxResult = StandardFxRuntime.apply(
                            applicationContext,
                            settings.standardFx,
                            headroomCurve,
                            forceSessionRefresh = true,
                        )
                        val eqFailure = verifyEqAfterStandardFx(settings)
                        if (eqFailure != null) StandardFxRuntime.release(applicationContext)
                        standardFxMessage = eqFailure ?: fxResult.message
                    }
                }
                validateOnly(PersistentTrigger.PLAYBACK_CHANGE)
            }
        }
    }

    private fun verifyEqAfterStandardFx(currentSettings: AppSettings): String? {
        val target = NineBandEqAdapter.uiDbToDap(currentSettings.lastAppliedCurve.toFloatArray())
        if (currentSettings.androidEqArmed) {
            val verification = AndroidEqualizerRuntime.verifyApplied(applicationContext, target)
            return verification.message.takeIf { !verification.ok }
        }
        if (currentSettings.persistentEqArmed && PersistentDolbyRuntime.activeTransaction() != null) {
            val gains = PersistentDolbyRuntime.readHeld()
            val enabled = PersistentDolbyRuntime.readHeldEnabled()
            if (gains?.contentEquals(target) != true || enabled != true) {
                return "Persistent Direct Dolby post-FX GEQ readback differs from the applied target."
            }
        }
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification(getString(R.string.notification_dolby_ready))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        preferences = AppPreferences(applicationContext)
        routeMonitor = AudioRouteMonitor(applicationContext)
        route = routeMonitor.state.value
        audioManager = getSystemService(AudioManager::class.java)
        audioManager.registerAudioPlaybackCallback(playbackCallback, Handler(Looper.getMainLooper()))

        serviceScope.launch {
            preferences.settings.collectLatest { value ->
                settings = value
                settingsLoaded = true

                val androidEqArmed = value.eqEnabled && value.androidEqArmed
                val androidEqCurve = if (androidEqArmed) value.lastAppliedCurve else null
                AudioMutationCoordinator.serialized {
                    if (androidEqArmed && (!lastAndroidEqArmed || lastAndroidEqCurve != androidEqCurve)) {
                        val target = NineBandEqAdapter.uiDbToDap(value.lastAppliedCurve.toFloatArray())
                        androidEqMessage = AndroidEqualizerRuntime.reapplyArmed(applicationContext, target).message
                    } else if (!androidEqArmed) {
                        androidEqMessage = ""
                    }
                    lastAndroidEqArmed = androidEqArmed
                    lastAndroidEqCurve = androidEqCurve

                    if (value.eqEnabled && value.standardFx.requiresRuntimeHold) {
                        val eqTransactionActive = value.persistentEqArmed || value.androidEqArmed ||
                            PersistentDolbyRuntime.activeTransaction() != null || BackendTransactionRuntime.get() != null
                        val headroomCurve = HeadroomCalculator.appliedCurve(eqTransactionActive, value.lastAppliedCurve)
                        val fxResult = StandardFxRuntime.apply(applicationContext, value.standardFx, headroomCurve)
                        val eqFailure = verifyEqAfterStandardFx(value)
                        if (eqFailure != null) StandardFxRuntime.release(applicationContext)
                        standardFxMessage = eqFailure ?: fxResult.message
                    } else {
                        StandardFxRuntime.release(applicationContext)
                        standardFxMessage = ""
                    }

                    if (!value.persistentEqArmed) PersistentDolbyRuntime.releaseSession()
                }

                val dolbyPersistenceActive = value.persistentEqArmed

                if (!PersistentRestorePolicy.shouldKeepServiceAlive(
                        dolbyPersistenceActive = dolbyPersistenceActive,
                        standardFxEnabled = value.eqEnabled && value.standardFx.requiresRuntimeHold,
                        androidEqEnabled = value.eqEnabled && value.androidEqArmed,
                    )
                ) {
                    stopSelf()
                } else {
                    validateOnly(PersistentTrigger.SERVICE_START)
                }
            }
        }

        serviceScope.launch {
            routeMonitor.state.collectLatest { value ->
                route = value
                if (settingsLoaded) validateOnly(PersistentTrigger.ROUTE_CHANGE)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_PERSISTENCE) {
            serviceScope.launch {
                preferences.disarmPersistentEq()
                preferences.disarmAndroidEqualizer()
                AudioMutationCoordinator.serialized {
                    PersistentDolbyRuntime.releaseSession()
                    AndroidEqualizerRuntime.restoreAndRelease()
                    StandardFxRuntime.release(applicationContext)
                }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        serviceScope.launch { validateOnly(PersistentTrigger.SERVICE_START) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun validateOnly(trigger: PersistentTrigger) = validationMutex.withLock {
        AudioMutationCoordinator.serialized {
            check(!PersistentRestorePolicy.mayWrite(trigger)) {
                "Foreground service validation must never write Dolby outside the explicit Apply action."
            }
            if (!settingsLoaded) {
                updateNotification(getString(R.string.notification_dolby_ready))
                return@serialized
            }

            val currentSettings = settings
            val dolbyPersistenceActive = currentSettings.persistentEqArmed
            if (!dolbyPersistenceActive) {
                PersistentDolbyRuntime.releaseSession()
                updateCombinedNotification(getString(R.string.notification_dolby_ready))
                return@serialized
            }

            val currentRoute = route ?: routeMonitor.state.value
            val routeAllowed = currentRoute.dolbyWriteSafeByDefault || currentSettings.nonSpeakerOverride
            if (!routeAllowed) {
                PersistentDolbyRuntime.releaseSession()
                updateCombinedNotification(getString(R.string.notification_dolby_route_released, currentRoute.description))
                return@serialized
            }

            val validation = PersistentDolbyRuntime.validateHeld()
            updateCombinedNotification(
                if (validation.holding && validation.hasControl) getString(R.string.notification_dolby_active)
                else getString(R.string.notification_dolby_ready)
            )
        }
    }

    private fun updateCombinedNotification(dolbyMessage: String) {
        val parts = listOf(dolbyMessage, androidEqMessage, standardFxMessage).filter { it.isNotBlank() }
        updateNotification(parts.joinToString(" · "))
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_dolby_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_dolby_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(message: String): Notification {
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PersistentDolbyService::class.java).setAction(ACTION_STOP_PERSISTENCE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.notification_dolby_title))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.notification_stop), stopPendingIntent).build())
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        if (::audioManager.isInitialized) runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
        if (::routeMonitor.isInitialized) routeMonitor.close()
        PersistentDolbyRuntime.releaseSession()
        StandardFxRuntime.release(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "dwas_eq_persistent_audio"
        private const val NOTIFICATION_ID = 301
        private const val ACTION_STOP_PERSISTENCE = "kr.dwas.dwas_EQ.action.STOP_PERSISTENT_AUDIO"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PersistentDolbyService::class.java))
        }

        fun stop(context: Context) {
            PersistentDolbyRuntime.releaseSession()
            StandardFxRuntime.release(context.applicationContext)
            context.stopService(Intent(context, PersistentDolbyService::class.java))
        }
    }
}
