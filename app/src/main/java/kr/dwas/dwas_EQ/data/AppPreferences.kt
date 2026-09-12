package kr.dwas.dwas_EQ.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kr.dwas.dwas_EQ.backend.BackendPreference
import kr.dwas.dwas_EQ.domain.EqCurve
import kr.dwas.dwas_EQ.domain.EqPreset
import kr.dwas.dwas_EQ.domain.PresetCodec
import kr.dwas.dwas_EQ.standardfx.StandardFxCodec
import kr.dwas.dwas_EQ.standardfx.StandardFxSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dwasEqDataStore by preferencesDataStore(name = "dwas_eq_preferences")

data class AppSettings(
    val backendPreference: BackendPreference = BackendPreference.AUTO,
    val rootOptIn: Boolean = false,
    val nonSpeakerOverride: Boolean = false,
    val uiCurve: EqCurve = EqCurve.flat(),
    val eqEnabled: Boolean = true,
    val bootPersistenceEnabled: Boolean = true,
    val persistentEqArmed: Boolean = false,
    val androidEqArmed: Boolean = false,
    val lastAppliedCurve: EqCurve = EqCurve.flat(),
    val standardFx: StandardFxSettings = StandardFxSettings(),
    val soundEffectsEnabled: Boolean = true,
    val userPresets: List<EqPreset> = emptyList(),
    val diagnosticsEnabled: Boolean = false,
    val controlAccessReset: Boolean = false,
    val hearingSafetyAcceptedVersion: Int = 0,
    val experimentalWarningAcknowledged: Boolean = false,
    val schemaVersion: Int = AppPreferences.SCHEMA_VERSION,
)

class AppPreferences(private val context: Context) {
    internal object Keys {
        val SCHEMA = intPreferencesKey("schema_version")
        val BACKEND = stringPreferencesKey("backend")
        val ROOT = booleanPreferencesKey("root_opt_in")
        val NON_SPEAKER = booleanPreferencesKey("non_speaker_override")
        val UI_CURVE = stringPreferencesKey("ui_gains_db")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val BOOT_PERSISTENCE = booleanPreferencesKey("boot_persistence_enabled")
        val LEGACY_AUTO_RESTORE = booleanPreferencesKey("auto_restore_on_boot")
        val PERSISTENT_ARMED = booleanPreferencesKey("persistent_eq_armed")
        val ANDROID_EQ_ARMED = booleanPreferencesKey("android_eq_armed")
        val LAST_APPLIED_CURVE = stringPreferencesKey("last_applied_curve_db")
        val LEGACY_LAST_APPLIED = stringPreferencesKey("last_applied_ui_gains_db")
        val STANDARD_FX = stringPreferencesKey("standard_fx_v1")
        val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val USER_PRESETS = stringPreferencesKey("user_presets_v1")
        val DIAGNOSTICS_ENABLED = booleanPreferencesKey("diagnostics_enabled")
        val CONTROL_ACCESS_RESET = booleanPreferencesKey("control_access_reset")
        val HEARING_SAFETY_ACCEPTED_VERSION = intPreferencesKey("hearing_safety_accepted_version")
        val EXPERIMENTAL_WARNING_ACKNOWLEDGED = booleanPreferencesKey("experimental_warning_acknowledged")
    }

    val settings: Flow<AppSettings> = context.dwasEqDataStore.data.map(::decode)

    suspend fun setBackend(value: BackendPreference) = editVersioned { it[Keys.BACKEND] = value.name }
    suspend fun setRootOptIn(value: Boolean) = editVersioned { it[Keys.ROOT] = value }
    suspend fun setNonSpeakerOverride(value: Boolean) = editVersioned { it[Keys.NON_SPEAKER] = value }
    suspend fun setEqEnabled(value: Boolean) = editVersioned { it[Keys.EQ_ENABLED] = value }
    suspend fun setSoundEffectsEnabled(value: Boolean) = editVersioned { it[Keys.SOUND_EFFECTS_ENABLED] = value }
    suspend fun setDiagnosticsEnabled(value: Boolean) = editVersioned { it[Keys.DIAGNOSTICS_ENABLED] = value }
    suspend fun setControlAccessReset(value: Boolean) = editVersioned { it[Keys.CONTROL_ACCESS_RESET] = value }
    suspend fun setHearingSafetyAcceptedVersion(value: Int) = editVersioned { it[Keys.HEARING_SAFETY_ACCEPTED_VERSION] = value }
    suspend fun setExperimentalWarningAcknowledged(value: Boolean) = editVersioned { it[Keys.EXPERIMENTAL_WARNING_ACKNOWLEDGED] = value }

    suspend fun setUiCurve(curve: EqCurve) = editVersioned { it[Keys.UI_CURVE] = encodeCurve(curve) }

    suspend fun armLastApplied(curve: EqCurve) = editVersioned { prefs ->
        prefs[Keys.LAST_APPLIED_CURVE] = encodeCurve(curve)
        prefs[Keys.PERSISTENT_ARMED] = true
    }

    suspend fun recordLastAppliedCurve(curve: EqCurve) = editVersioned { prefs ->
        prefs[Keys.LAST_APPLIED_CURVE] = encodeCurve(curve)
    }

    suspend fun armAndroidEqualizer(curve: EqCurve) = editVersioned { prefs ->
        prefs[Keys.LAST_APPLIED_CURVE] = encodeCurve(curve)
        prefs[Keys.ANDROID_EQ_ARMED] = true
    }

    suspend fun disarmAndroidEqualizer() = editVersioned { it[Keys.ANDROID_EQ_ARMED] = false }

    suspend fun disarmPersistentEq() = editVersioned { it[Keys.PERSISTENT_ARMED] = false }

    suspend fun setStandardFx(settings: StandardFxSettings) = editVersioned {
        it[Keys.STANDARD_FX] = StandardFxCodec.encode(settings)
    }

    suspend fun setUserPresets(presets: List<EqPreset>) = editVersioned {
        it[Keys.USER_PRESETS] = PresetCodec.encode(presets)
    }

    private suspend inline fun editVersioned(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dwasEqDataStore.edit { prefs ->
            prefs[Keys.SCHEMA] = SCHEMA_VERSION
            block(prefs)
        }
    }

    private fun decode(p: Preferences): AppSettings {
        val curve = parseCurve(p[Keys.UI_CURVE])
        val lastApplied = parseCurve(p[Keys.LAST_APPLIED_CURVE] ?: p[Keys.LEGACY_LAST_APPLIED])
        val eqEnabled = p[Keys.EQ_ENABLED] ?: true
        return AppSettings(
            backendPreference = runCatching { BackendPreference.valueOf(p[Keys.BACKEND] ?: BackendPreference.AUTO.name) }
                .getOrDefault(BackendPreference.AUTO),
            rootOptIn = p[Keys.ROOT] ?: false,
            nonSpeakerOverride = p[Keys.NON_SPEAKER] ?: false,
            uiCurve = curve,
            eqEnabled = eqEnabled,
            bootPersistenceEnabled = true,
            persistentEqArmed = p[Keys.PERSISTENT_ARMED] ?: false,
            androidEqArmed = p[Keys.ANDROID_EQ_ARMED] ?: false,
            lastAppliedCurve = lastApplied,
            standardFx = StandardFxCodec.decode(p[Keys.STANDARD_FX]),
            soundEffectsEnabled = eqEnabled,
            userPresets = PresetCodec.decode(p[Keys.USER_PRESETS]),
            diagnosticsEnabled = p[Keys.DIAGNOSTICS_ENABLED] ?: false,
            controlAccessReset = p[Keys.CONTROL_ACCESS_RESET] ?: false,
            hearingSafetyAcceptedVersion = p[Keys.HEARING_SAFETY_ACCEPTED_VERSION] ?: 0,
            experimentalWarningAcknowledged = p[Keys.EXPERIMENTAL_WARNING_ACKNOWLEDGED] ?: false,
            schemaVersion = p[Keys.SCHEMA] ?: LEGACY_SCHEMA_VERSION,
        )
    }

    private fun encodeCurve(curve: EqCurve): String = curve.gainsDb.joinToString(",") { it.toString() }

    private fun parseCurve(raw: String?): EqCurve = raw?.split(',')
        ?.mapNotNull { it.toFloatOrNull() }
        ?.takeIf { it.size == EqCurve.BAND_COUNT || it.size == 10 }
        ?.let(EqCurve::fromStoredValues)
        ?: EqCurve.flat()

    companion object {
        const val SCHEMA_VERSION = 9
        private const val LEGACY_SCHEMA_VERSION = 3
    }
}
