package kr.dwas.dwas_EQ.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.dwas.dwas_EQ.EqUiState
import kr.dwas.dwas_EQ.EqViewModel
import kr.dwas.dwas_EQ.audio.RouteDetectionSource
import kr.dwas.dwas_EQ.audio.RouteKind
import kr.dwas.dwas_EQ.backend.BackendKind
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.ui.components.KeyValue
import kr.dwas.dwas_EQ.ui.components.SectionCard
import kr.dwas.dwas_EQ.ui.components.StatusPill

@Composable
fun DiagnosticsScreen(state: EqUiState, vm: EqViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.diagnostics_status_title)) {
                Text(
                    state.status.ifBlank { stringResource(R.string.status_probe_pending) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            SectionCard(stringResource(R.string.card_route), subtitle = diagnosticsRouteLabel(state.route?.kind)) {
                KeyValue(stringResource(R.string.label_route_source), diagnosticsRouteSourceLabel(state.route?.detectionSource))
                KeyValue(stringResource(R.string.label_route_safe), if (state.route?.dolbyWriteSafeByDefault == true) stringResource(R.string.value_yes) else stringResource(R.string.value_no))
            }
        }
        item {
            SectionCard(stringResource(R.string.diagnostics_backend_title), subtitle = stringResource(R.string.diagnostics_backend_subtitle)) {
                state.probes.forEach { probe ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(diagnosticsBackendLabel(probe.backend), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, softWrap = true)
                        StatusPill(if (probe.available && probe.hasControl) stringResource(R.string.status_control) else stringResource(R.string.status_unavailable), positive = probe.available && probe.hasControl)
                    }
                    Text(probe.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.fx_capabilities_title)) {
                val c = state.standardFxCapabilities
                KeyValue("DynamicsProcessing", c.dynamicsProcessing.toString())
                KeyValue("BassBoost", c.bassBoost.toString())
                KeyValue("Virtualizer", c.virtualizer.toString())
                KeyValue("PresetReverb", c.presetReverb.toString())
                c.details.forEach { (key, value) -> KeyValue(key, value) }
            }
        }
        if (state.technicalDetail.isNotBlank()) {
            item {
                SectionCard(
                    stringResource(R.string.diagnostics_technical_title),
                    subtitle = stringResource(R.string.diagnostics_technical_subtitle),
                ) {
                    Text(
                        state.technicalDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::refreshCapabilities, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_refresh)) }
                Button(onClick = vm::runSafeProbe, enabled = state.safeProbeAvailable && !state.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_safe_probe)) }
            }
        }
        item {
            SectionCard(
                stringResource(R.string.action_reset_all_control_access),
                subtitle = stringResource(R.string.diagnostics_reset_control_access_description),
            ) {
                OutlinedButton(
                    onClick = vm::resetAllControlAccess,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_reset_all_control_access))
                }
            }
        }
    }
}
@Composable
private fun diagnosticsRouteLabel(kind: RouteKind?): String = when (kind) {
    RouteKind.INTERNAL_SPEAKER -> stringResource(R.string.home_route_internal)
    RouteKind.EXTERNAL -> stringResource(R.string.home_route_external)
    RouteKind.UNKNOWN, null -> stringResource(R.string.home_route_unknown)
}

@Composable
private fun diagnosticsRouteSourceLabel(source: RouteDetectionSource?): String = when (source) {
    RouteDetectionSource.ACTIVE_MEDIA -> stringResource(R.string.route_source_active_media)
    RouteDetectionSource.PRESENCE_FALLBACK -> stringResource(R.string.route_source_presence_fallback)
    null -> stringResource(R.string.value_unknown)
}

@Composable
private fun diagnosticsBackendLabel(kind: BackendKind): String = when (kind) {
    BackendKind.WIRED_ADB_DOLBY -> stringResource(R.string.backend_wired_adb_dolby)
    BackendKind.DIRECT_DOLBY -> stringResource(R.string.backend_direct_dolby)
    BackendKind.ROOT_DOLBY -> stringResource(R.string.backend_root_dolby)
    BackendKind.ANDROID_EQUALIZER -> stringResource(R.string.backend_android_equalizer)
}
