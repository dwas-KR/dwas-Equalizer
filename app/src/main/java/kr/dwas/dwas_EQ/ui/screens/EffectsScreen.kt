package kr.dwas.dwas_EQ.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.dwas.dwas_EQ.EqUiState
import kr.dwas.dwas_EQ.EqViewModel
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.standardfx.HeadroomCalculator
import kr.dwas.dwas_EQ.ui.components.SectionCard

@Composable
fun EffectsScreen(state: EqUiState, vm: EqViewModel, modifier: Modifier = Modifier) {
    val fx = state.settings.standardFx
    val masterEnabled = state.settings.eqEnabled
    val headroomCurve = HeadroomCalculator.appliedCurve(state.eqTransactionActive, state.settings.lastAppliedCurve)
    val effectiveAttenuation = HeadroomCalculator.attenuationDb(fx, headroomCurve)

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
    ) {
        item {
            BoxWithConstraints {
                if (maxWidth >= 760.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            AttenuatorCard(state, vm, effectiveAttenuation, masterEnabled)
                            ChannelBalanceCard(state, vm, masterEnabled)
                            BassBoostCard(state, vm, masterEnabled)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LimiterCard(state, vm, masterEnabled)
                            VirtualizerCard(state, vm, masterEnabled)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AttenuatorCard(state, vm, effectiveAttenuation, masterEnabled)
                        LimiterCard(state, vm, masterEnabled)
                        ChannelBalanceCard(state, vm, masterEnabled)
                        BassBoostCard(state, vm, masterEnabled)
                        VirtualizerCard(state, vm, masterEnabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttenuatorCard(state: EqUiState, vm: EqViewModel, effective: Float, masterEnabled: Boolean) {
    val fx = state.settings.standardFx
    val available = masterEnabled && state.standardFxCapabilities.dynamicsProcessing
    val sectionEnabled = masterEnabled && state.standardFxCapabilities.dynamicsProcessing && fx.attenuatorEnabled
    SectionCard(
        stringResource(R.string.fx_attenuator),
        subtitle = stringResource(R.string.fx_attenuator_subtitle),
        trailing = { FxSwitch(available, state.standardFxCapabilities.dynamicsProcessing && fx.attenuatorEnabled) { on -> vm.setStandardFx { it.copy(attenuatorEnabled = on) } } },
    ) {
        ToggleRow(stringResource(R.string.fx_automatic_attenuation), fx.automaticAttenuation, sectionEnabled) { on ->
            vm.setStandardFx { it.copy(automaticAttenuation = on) }
        }
        Text(stringResource(R.string.fx_effective_attenuation, effective), style = MaterialTheme.typography.labelLarge)
        ValueSlider(
            label = stringResource(R.string.fx_manual_attenuation), value = fx.manualAttenuationDb,
            text = String.format("%.1f dB", fx.manualAttenuationDb), range = -12f..0f, steps = 119,
            enabled = sectionEnabled && !fx.automaticAttenuation,
            onChange = { v -> vm.stageStandardFx { it.copy(manualAttenuationDb = v) } },
            onChangeFinished = vm::commitStandardFx,
        )
    }
}

@Composable
private fun ChannelBalanceCard(state: EqUiState, vm: EqViewModel, masterEnabled: Boolean) {
    val fx = state.settings.standardFx
    val available = masterEnabled && state.standardFxCapabilities.dynamicsProcessing
    val sectionEnabled = masterEnabled && state.standardFxCapabilities.dynamicsProcessing && fx.channelBalanceEnabled
    SectionCard(
        stringResource(R.string.fx_channel_balance),
        subtitle = stringResource(R.string.fx_channel_balance_subtitle),
        trailing = { FxSwitch(available, state.standardFxCapabilities.dynamicsProcessing && fx.channelBalanceEnabled) { on -> vm.setStandardFx { it.copy(channelBalanceEnabled = on) } } },
    ) {
        ValueSlider(
            stringResource(R.string.fx_left_speaker), fx.leftBalanceDb, String.format("%.1f dB", fx.leftBalanceDb), -10f..0f, 99, sectionEnabled,
            onChange = { v -> vm.stageStandardFx { it.copy(leftBalanceDb = v) } },
            onChangeFinished = vm::commitStandardFx,
        )
        ValueSlider(
            stringResource(R.string.fx_right_speaker), fx.rightBalanceDb, String.format("%.1f dB", fx.rightBalanceDb), -10f..0f, 99, sectionEnabled,
            onChange = { v -> vm.stageStandardFx { it.copy(rightBalanceDb = v) } },
            onChangeFinished = vm::commitStandardFx,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = vm::resetChannelBalance, enabled = sectionEnabled) {
                Text(stringResource(R.string.action_center_channels))
            }
        }
    }
}

@Composable
private fun LimiterCard(state: EqUiState, vm: EqViewModel, masterEnabled: Boolean) {
    val limiter = state.settings.standardFx.limiter
    val available = masterEnabled && state.standardFxCapabilities.dynamicsProcessing
    val sectionEnabled = masterEnabled && state.standardFxCapabilities.dynamicsProcessing && limiter.enabled
    SectionCard(
        stringResource(R.string.fx_limiter),
        subtitle = stringResource(R.string.fx_limiter_subtitle),
        trailing = { FxSwitch(available, state.standardFxCapabilities.dynamicsProcessing && limiter.enabled) { on -> vm.setStandardFx { it.copy(limiter = it.limiter.copy(enabled = on)) } } },
    ) {
        ValueSlider(stringResource(R.string.fx_attack), limiter.attackMs, "${limiter.attackMs.toInt()} ms", 1f..200f, 198, sectionEnabled, { v -> vm.stageStandardFx { it.copy(limiter = it.limiter.copy(attackMs = v)) } }, vm::commitStandardFx)
        ValueSlider(stringResource(R.string.fx_release), limiter.releaseMs, "${limiter.releaseMs.toInt()} ms", 10f..1000f, 98, sectionEnabled, { v -> vm.stageStandardFx { it.copy(limiter = it.limiter.copy(releaseMs = v)) } }, vm::commitStandardFx)
        ValueSlider(stringResource(R.string.fx_ratio), limiter.ratio, String.format("%.1f:1", limiter.ratio), 1f..20f, 75, sectionEnabled, { v -> vm.stageStandardFx { it.copy(limiter = it.limiter.copy(ratio = v)) } }, vm::commitStandardFx)
        ValueSlider(stringResource(R.string.fx_threshold), limiter.thresholdDb, String.format("%.1f dB", limiter.thresholdDb), -24f..0f, 95, sectionEnabled, { v -> vm.stageStandardFx { it.copy(limiter = it.limiter.copy(thresholdDb = v)) } }, vm::commitStandardFx)
        ValueSlider(stringResource(R.string.fx_post_gain), limiter.postGainDb, String.format("%+.1f dB", limiter.postGainDb), -12f..12f, 95, sectionEnabled, { v -> vm.stageStandardFx { it.copy(limiter = it.limiter.copy(postGainDb = v)) } }, vm::commitStandardFx)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = vm::resetLimiter, enabled = sectionEnabled) { Text(stringResource(R.string.action_reset)) }
        }
    }
}

@Composable
private fun BassBoostCard(state: EqUiState, vm: EqViewModel, masterEnabled: Boolean) {
    val fx = state.settings.standardFx
    val available = masterEnabled && state.standardFxCapabilities.bassBoost
    val sectionEnabled = available && fx.bassBoostEnabled
    SectionCard(
        stringResource(R.string.fx_bass_boost), subtitle = stringResource(R.string.fx_bass_boost_subtitle),
        trailing = { FxSwitch(available, fx.bassBoostEnabled) { on -> vm.setStandardFx { it.withBassBoostEnabled(on) } } },
    ) {
        ValueSlider(stringResource(R.string.fx_strength), fx.bassBoostStrength.toFloat(), "${fx.bassBoostStrength} / 1000", 0f..1000f, 99, sectionEnabled, { v -> vm.stageStandardFx { it.copy(bassBoostStrength = v.toInt()) } }, vm::commitStandardFx)
    }
}

@Composable
private fun VirtualizerCard(state: EqUiState, vm: EqViewModel, masterEnabled: Boolean) {
    val fx = state.settings.standardFx
    val available = masterEnabled && state.standardFxCapabilities.virtualizer
    val sectionEnabled = available && fx.virtualizerEnabled
    SectionCard(
        stringResource(R.string.fx_virtualizer), subtitle = stringResource(R.string.fx_virtualizer_subtitle),
        trailing = { FxSwitch(available, fx.virtualizerEnabled) { on -> vm.setStandardFx { it.withVirtualizerEnabled(on) } } },
    ) {
        ValueSlider(stringResource(R.string.fx_strength), fx.virtualizerStrength.toFloat(), "${fx.virtualizerStrength} / 1000", 0f..1000f, 99, sectionEnabled, { v -> vm.stageStandardFx { it.copy(virtualizerStrength = v.toInt()) } }, vm::commitStandardFx)
    }
}

@Composable
private fun FxSwitch(enabled: Boolean, checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), softWrap = true)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ValueSlider(label: String, value: Float, text: String, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, onChange: (Float) -> Unit, onChangeFinished: () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, softWrap = true)
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, onValueChangeFinished = onChangeFinished, valueRange = range, steps = steps, enabled = enabled)
    }
}
