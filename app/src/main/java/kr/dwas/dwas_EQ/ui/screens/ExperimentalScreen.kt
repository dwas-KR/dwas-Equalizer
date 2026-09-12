package kr.dwas.dwas_EQ.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.dwas.dwas_EQ.EqUiState
import kr.dwas.dwas_EQ.EqViewModel
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.standardfx.ReverbPreset
import kr.dwas.dwas_EQ.ui.components.SectionCard

@Composable
fun ExperimentalScreen(state: EqUiState, vm: EqViewModel, modifier: Modifier = Modifier) {
    val fx = state.settings.standardFx
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.experimental_title), subtitle = stringResource(R.string.experimental_subtitle)) {
                Text(stringResource(R.string.experimental_note))
            }
        }
        item {
            SectionCard(
                stringResource(R.string.card_route_override),
                subtitle = stringResource(R.string.route_override_note),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.setting_non_speaker_override), modifier = Modifier.weight(1f), softWrap = true)
                    Switch(checked = state.settings.nonSpeakerOverride, onCheckedChange = vm::setNonSpeakerOverride)
                }
            }
        }
        item {
            SectionCard(
                stringResource(R.string.fx_reverb),
                subtitle = stringResource(R.string.fx_reverb_subtitle),
                trailing = {
                    Switch(
                        checked = fx.reverbEnabled,
                        onCheckedChange = { on -> vm.setStandardFx { it.withReverbEnabled(on) } },
                        enabled = state.settings.eqEnabled && state.standardFxCapabilities.presetReverb,
                    )
                },
            ) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReverbPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = fx.reverbPreset == preset,
                            onClick = { vm.setStandardFx { it.copy(reverbPreset = preset) } },
                            label = { Text(reverbLabel(preset)) },
                            enabled = state.settings.eqEnabled && fx.reverbEnabled && state.standardFxCapabilities.presetReverb,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun reverbLabel(preset: ReverbPreset): String = when (preset) {
    ReverbPreset.NONE -> stringResource(R.string.reverb_none)
    ReverbPreset.SMALL_ROOM -> stringResource(R.string.reverb_small_room)
    ReverbPreset.MEDIUM_ROOM -> stringResource(R.string.reverb_medium_room)
    ReverbPreset.LARGE_ROOM -> stringResource(R.string.reverb_large_room)
    ReverbPreset.MEDIUM_HALL -> stringResource(R.string.reverb_medium_hall)
    ReverbPreset.LARGE_HALL -> stringResource(R.string.reverb_large_hall)
    ReverbPreset.PLATE -> stringResource(R.string.reverb_plate)
}
