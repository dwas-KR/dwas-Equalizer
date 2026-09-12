package kr.dwas.dwas_EQ.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.dwas.dwas_EQ.EqUiState
import kr.dwas.dwas_EQ.EqViewModel
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeStatusPolicy
import kr.dwas.dwas_EQ.audio.RouteKind
import kr.dwas.dwas_EQ.backend.BackendKind
import kr.dwas.dwas_EQ.backend.BackendPreference
import kr.dwas.dwas_EQ.device.SocFamily
import kr.dwas.dwas_EQ.ui.AppLanguage
import kr.dwas.dwas_EQ.ui.AppLocaleController
import kr.dwas.dwas_EQ.ui.AppThemeController
import kr.dwas.dwas_EQ.ui.AppThemeMode
import kr.dwas.dwas_EQ.ui.components.KeyValue
import kr.dwas.dwas_EQ.ui.components.SectionCard

@Composable
fun SettingsScreen(
    state: EqUiState,
    vm: EqViewModel,
    modifier: Modifier = Modifier,
    onCheckForUpdates: () -> Unit = {},
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLocaleController.current(context)) }
    var theme by remember { mutableStateOf(AppThemeController.current(context)) }
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.card_update_check), subtitle = stringResource(R.string.update_check_description)) {
                Button(onClick = onCheckForUpdates) {
                    Text(stringResource(R.string.update_check_action), softWrap = true)
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.card_language), subtitle = stringResource(R.string.language_description)) {
                AppLanguage.entries.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = language == item,
                            onClick = {
                                language = item
                                vm.clearTransientStatus()
                                AppLocaleController.set(context, item)
                            },
                        )
                        Text(languageLabel(item), modifier = Modifier.weight(1f), softWrap = true)
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.card_theme), subtitle = stringResource(R.string.theme_description)) {
                AppThemeMode.entries.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = theme == item,
                            onClick = {
                                theme = item
                                AppThemeController.set(context, item)
                                (context as? Activity)?.recreate()
                            },
                        )
                        Text(themeLabel(item), modifier = Modifier.weight(1f), softWrap = true)
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.card_audio_state), subtitle = stringResource(R.string.audio_state_description)) {
                KeyValue(stringResource(R.string.label_route), routeLabel(state.route?.kind))
                KeyValue(stringResource(R.string.label_backend), backendLabel(state.activeBackend))
                KeyValue(stringResource(R.string.label_wired_adb), wiredAdbLabel(state.wiredAdbStatus))
                KeyValue(stringResource(R.string.nav_effects), stringResource(if (state.settings.eqEnabled && state.settings.standardFx.hasAnyEnabledEffect) R.string.fx_status_active_short else R.string.fx_status_off_short))
            }
        }
        item {
            SectionCard(stringResource(R.string.card_device), subtitle = state.device?.model ?: stringResource(R.string.value_unknown)) {
                val d = state.device
                KeyValue(stringResource(R.string.label_android), d?.let { "${it.androidRelease} · API ${it.sdk}" } ?: stringResource(R.string.value_unknown))
                KeyValue(stringResource(R.string.label_platform), d?.board ?: stringResource(R.string.value_unknown))
                KeyValue(stringResource(R.string.label_soc_family), when (d?.known?.socFamily) {
                    SocFamily.MEDIA_TEK -> "MediaTek"
                    SocFamily.QUALCOMM -> "Qualcomm"
                    SocFamily.UNKNOWN, null -> stringResource(R.string.value_unknown)
                })
                KeyValue(stringResource(R.string.label_dolby_package), if (d?.dolbyPackagePresent == true) stringResource(R.string.value_yes) else stringResource(R.string.value_no))
            }
        }
        item {
            SectionCard(stringResource(R.string.card_backend), subtitle = stringResource(R.string.backend_policy_description)) {
                BackendPreference.entries.forEach { pref ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.settings.backendPreference == pref, onClick = { vm.setBackend(pref) })
                        Text(backendPreferenceLabel(pref), modifier = Modifier.weight(1f), softWrap = true)
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.card_diagnostics_setting), subtitle = stringResource(R.string.diagnostics_setting_description)) {
                SettingToggle(stringResource(R.string.setting_diagnostics_enabled), state.settings.diagnosticsEnabled, vm::setDiagnosticsEnabled)
            }
        }
        item {
            SectionCard(stringResource(R.string.card_safety), subtitle = stringResource(R.string.safety_description)) {
                SettingToggle(stringResource(R.string.setting_root_opt_in), state.settings.rootOptIn, vm::setRootOptIn)
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), softWrap = true)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun backendPreferenceLabel(value: BackendPreference): String = when (value) {
    BackendPreference.AUTO -> stringResource(R.string.backend_auto)
    BackendPreference.WIRED_ADB -> stringResource(R.string.backend_wired_adb)
    BackendPreference.DIRECT -> stringResource(R.string.backend_direct)
    BackendPreference.ROOT -> stringResource(R.string.backend_root)
    BackendPreference.ANDROID_EQ -> stringResource(R.string.backend_android_eq)
}

@Composable
private fun languageLabel(value: AppLanguage): String = when (value) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.KOREAN -> stringResource(R.string.language_korean)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    AppLanguage.JAPANESE -> stringResource(R.string.language_japanese)
    AppLanguage.TRADITIONAL_CHINESE -> stringResource(R.string.language_traditional_chinese)
    AppLanguage.TAIWAN -> stringResource(R.string.language_taiwan)
    AppLanguage.RUSSIAN -> stringResource(R.string.language_russian)
    AppLanguage.VIETNAMESE -> stringResource(R.string.language_vietnamese)
}

@Composable
private fun themeLabel(value: AppThemeMode): String = when (value) {
    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
}

@Composable
private fun routeLabel(kind: RouteKind?): String = when (kind) {
    RouteKind.INTERNAL_SPEAKER -> stringResource(R.string.home_route_internal)
    RouteKind.EXTERNAL -> stringResource(R.string.home_route_external)
    RouteKind.UNKNOWN, null -> stringResource(R.string.home_route_unknown)
}

@Composable
private fun backendLabel(kind: BackendKind?): String = when (kind) {
    BackendKind.WIRED_ADB_DOLBY -> stringResource(R.string.backend_wired_adb_dolby)
    BackendKind.DIRECT_DOLBY -> stringResource(R.string.backend_direct_dolby)
    BackendKind.ROOT_DOLBY -> stringResource(R.string.backend_root_dolby)
    BackendKind.ANDROID_EQUALIZER -> stringResource(R.string.backend_android_equalizer)
    null -> stringResource(R.string.value_none)
}

@Composable
private fun wiredAdbLabel(status: String): String = if (WiredAdbBridgeStatusPolicy.isReady(status)) {
    stringResource(R.string.home_wired_adb_connected)
} else {
    stringResource(R.string.home_wired_adb_disconnected)
}
