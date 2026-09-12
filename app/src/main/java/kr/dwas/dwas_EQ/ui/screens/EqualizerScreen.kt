package kr.dwas.dwas_EQ.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kr.dwas.dwas_EQ.EqUiState
import kr.dwas.dwas_EQ.EqViewModel
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.builtInPresetNameRes
import kr.dwas.dwas_EQ.backend.ControlAccessResetGatePolicy
import kr.dwas.dwas_EQ.core.NineBandEqAdapter
import kr.dwas.dwas_EQ.domain.BuiltInEqPresets
import kr.dwas.dwas_EQ.domain.EqCurve
import kr.dwas.dwas_EQ.ui.components.HorizontalScrollIndicator
import kr.dwas.dwas_EQ.ui.components.SectionCard
import kr.dwas.dwas_EQ.ui.components.VerticalEqFader
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(state: EqUiState, vm: EqViewModel, modifier: Modifier = Modifier) {
    var showPresetManager by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.eq_master_title),
                subtitle = stringResource(R.string.eq_master_subtitle),
                trailing = {
                    Switch(
                        checked = state.settings.eqEnabled,
                        onCheckedChange = vm::setEqEnabled,
                        enabled = !state.busy && ControlAccessResetGatePolicy.isMasterToggleInteractive(state.settings.controlAccessReset),
                    )
                },
            ) {
                Text(
                    stringResource(
                        if (state.settings.controlAccessReset) R.string.eq_master_control_access_reset_hint
                        else if (state.settings.eqEnabled) R.string.eq_master_on_hint
                        else R.string.eq_master_off_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.eq_curve_title),
                subtitle = stringResource(R.string.eq_curve_subtitle),
            ) {
                FrequencyCurve(state.curve, enabled = state.settings.eqEnabled)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showPresetManager = true },
                        enabled = state.settings.eqEnabled && !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.presets_manage)) }
                    OutlinedButton(
                        onClick = vm::resetAudio,
                        enabled = state.settings.eqEnabled && !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.action_reset)) }
                    Button(onClick = vm::apply, enabled = state.settings.eqEnabled && !state.busy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_apply))
                    }
                }
            }
        }
        item { GraphicEqFaderDeck(state, vm) }
    }
    if (showPresetManager) PresetManagerDialog(state, vm, onDismiss = { showPresetManager = false })
}

@Composable
private fun GraphicEqFaderDeck(state: EqUiState, vm: EqViewModel) {
    val scrollState = rememberScrollState()
    SectionCard(
        title = stringResource(R.string.eq_band_controls),
        subtitle = stringResource(R.string.eq_fader_hint),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NineBandEqAdapter.LABELS.forEachIndexed { index, label ->
                VerticalEqFader(
                    label = label,
                    value = state.curve.gainsDb[index],
                    enabled = state.settings.eqEnabled && !state.busy,
                    onValueChange = { vm.setGain(index, it) },
                    onValueChangeFinished = vm::persistCurve,
                )
            }
        }
        HorizontalScrollIndicator(scrollState)
    }
}

@Composable
private fun FrequencyCurve(curve: EqCurve, enabled: Boolean) {
    val frequencies = NineBandEqAdapter.FREQUENCIES_HZ
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier.fillMaxWidth().height(240.dp).alpha(if (enabled) 1f else 0.42f).padding(horizontal = 8.dp, vertical = 14.dp)
    ) {
        val minLog = ln(frequencies.first().toFloat())
        val maxLog = ln(frequencies.last().toFloat())
        fun x(hz: Int): Float = ((ln(hz.toFloat()) - minLog) / (maxLog - minLog)) * size.width
        fun y(db: Float): Float = ((6f - db) / 12f) * size.height
        listOf(-6f, -3f, 0f, 3f, 6f).forEach { db ->
            drawLine(gridColor, Offset(0f, y(db)), Offset(size.width, y(db)), strokeWidth = if (db == 0f) 2f else 1f)
        }
        val path = Path()
        frequencies.forEachIndexed { index, hz ->
            val point = Offset(x(hz), y(curve.gainsDb[index]))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, lineColor, style = Stroke(width = 5f))
        frequencies.forEachIndexed { index, hz -> drawCircle(lineColor, 7f, Offset(x(hz), y(curve.gainsDb[index]))) }
    }
}

@Composable
private fun PresetManagerDialog(state: EqUiState, vm: EqViewModel, onDismiss: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }
    var renameIndex by remember { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.presets_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.presets_tab_basic)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.presets_tab_dwas)) },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.presets_tab_user)) },
                    )
                }
                key(selectedTab) {
                    when (selectedTab) {
                        0 -> PresetScrollableContent {
                            BuiltInEqPresets.basic.forEach { preset ->
                                PresetLoadCard(stringResource(builtInPresetNameRes(preset.id))) {
                                    vm.loadBuiltInPreset(preset.id)
                                    onDismiss()
                                }
                            }
                        }
                        1 -> PresetScrollableContent {
                            BuiltInEqPresets.recommended.forEach { preset ->
                                PresetLoadCard(stringResource(builtInPresetNameRes(preset.id))) {
                                    vm.loadBuiltInPreset(preset.id)
                                    onDismiss()
                                }
                            }
                        }
                        else -> PresetScrollableContent {
                            TextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = {
                                    Text(
                                        if (renameIndex >= 0) stringResource(R.string.presets_rename_label)
                                        else stringResource(R.string.presets_name_label)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (renameIndex >= 0) vm.renamePreset(renameIndex, newName) else vm.savePreset(newName)
                                        newName = ""
                                        renameIndex = -1
                                    },
                                    enabled = newName.isNotBlank(),
                                ) {
                                    Text(
                                        if (renameIndex >= 0) stringResource(R.string.action_rename)
                                        else stringResource(R.string.action_save_current)
                                    )
                                }
                                TextButton(onClick = { renameIndex = -1; newName = "" }) {
                                    Text(stringResource(R.string.action_clear))
                                }
                            }
                            HorizontalDivider()
                            state.settings.userPresets.forEachIndexed { index, preset ->
                                key(preset.name, preset.curve.gainsDb) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(preset.name, fontWeight = FontWeight.SemiBold)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                TextButton(onClick = { vm.loadPreset(index); onDismiss() }) {
                                                    Text(stringResource(R.string.action_load))
                                                }
                                                TextButton(onClick = { renameIndex = index; newName = preset.name }) {
                                                    Text(stringResource(R.string.action_rename))
                                                }
                                                TextButton(onClick = { vm.deletePreset(index) }) {
                                                    Text(stringResource(R.string.action_delete))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun PresetScrollableContent(content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .onSizeChanged { viewportHeightPx = it.height }
                .verticalScroll(scrollState)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (scrollState.maxValue > 0 && viewportHeightPx > 0) {
            val viewport = viewportHeightPx.toFloat()
            val contentHeight = viewport + scrollState.maxValue.toFloat()
            val minimumThumb = with(density) { 36.dp.toPx() }
            val thumbHeight = (viewport * viewport / contentHeight).coerceIn(minimumThumb, viewport)
            val travel = viewport - thumbHeight
            val fraction = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            val offsetY = (travel * fraction).roundToInt()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(4.dp)
                    .height(with(density) { viewportHeightPx.toDp() })
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, offsetY) }
                    .width(4.dp)
                    .height(with(density) { thumbHeight.toDp() })
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun PresetLoadCard(name: String, onLoad: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onLoad) { Text(stringResource(R.string.action_load)) }
        }
    }
}
