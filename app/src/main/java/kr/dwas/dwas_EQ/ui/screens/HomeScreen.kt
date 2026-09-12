package kr.dwas.dwas_EQ.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dwas.dwas_EQ.EqUiState
import kr.dwas.dwas_EQ.EqViewModel
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeStatusPolicy
import kr.dwas.dwas_EQ.backend.BackendKind
import kr.dwas.dwas_EQ.ui.AppLocaleController
import kr.dwas.dwas_EQ.ui.components.SectionCard
import kr.dwas.dwas_EQ.ui.components.StatusPill

@Composable
fun HomeScreen(state: EqUiState, vm: EqViewModel, onReviewHearingSafety: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp),
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)),
                        RoundedCornerShape(28.dp),
                    )
                    .padding(26.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.home_hero_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_hero_subtitle), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            if (state.device?.dapDescriptorPresent == true) stringResource(R.string.status_dap_detected) else stringResource(R.string.status_dap_not_detected),
                            positive = state.device?.dapDescriptorPresent == true,
                        )
                        StatusPill(
                            if (state.route?.dolbyWriteSafeByDefault == true) stringResource(R.string.route_internal_speaker) else stringResource(R.string.route_external),
                            positive = state.route?.dolbyWriteSafeByDefault == true,
                        )
                    }
                }
            }
        }
        val directProbe = state.probes.firstOrNull { it.backend == BackendKind.DIRECT_DOLBY }
        if (WiredAdbBridgeStatusPolicy.shouldShowPermissionNotice(
                status = state.wiredAdbStatus,
                directAvailable = directProbe?.available == true,
                directHasControl = directProbe?.hasControl == true,
            )
        ) {
            item {
                SectionCard(
                    stringResource(R.string.home_adb_permission_title),
                    subtitle = stringResource(R.string.home_adb_permission_description),
                ) {}
            }
        }
        item {
            HearingSafetyHomeCard(onClick = onReviewHearingSafety)
        }
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val supportUrl = if (AppLocaleController.isKorean(context)) "http://pf.kakao.com/_HHVmG" else "https://github.com/dwas-KR/dwas-Equalizer/issues/1"
                if (maxWidth >= 900.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HomeLinkCard(
                            title = stringResource(R.string.home_developer_youtube_title),
                            description = stringResource(R.string.home_developer_youtube_description),
                            action = stringResource(R.string.home_developer_youtube_action),
                            onClick = { uriHandler.openUri("https://www.youtube.com/@dwas_KR?sub_confirmation=1") },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            equalizeHeight = true,
                        )
                        HomeLinkCard(
                            title = stringResource(R.string.home_test_support_title),
                            description = stringResource(R.string.home_test_support_description),
                            action = stringResource(R.string.home_test_support_action),
                            onClick = { uriHandler.openUri(supportUrl) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            equalizeHeight = true,
                        )
                        HomeLinkCard(
                            title = stringResource(R.string.home_donate_title),
                            description = stringResource(R.string.home_donate_description),
                            action = stringResource(R.string.home_donate_action),
                            onClick = { uriHandler.openUri("https://www.youtube.com/channel/UCe3U-W3fIYyIi4SZGK4zI8Q/join") },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            equalizeHeight = true,
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeLinkCard(
                            title = stringResource(R.string.home_developer_youtube_title),
                            description = stringResource(R.string.home_developer_youtube_description),
                            action = stringResource(R.string.home_developer_youtube_action),
                            onClick = { uriHandler.openUri("https://www.youtube.com/@dwas_KR?sub_confirmation=1") },
                            modifier = Modifier.fillMaxWidth(),
                            equalizeHeight = false,
                        )
                        HomeLinkCard(
                            title = stringResource(R.string.home_test_support_title),
                            description = stringResource(R.string.home_test_support_description),
                            action = stringResource(R.string.home_test_support_action),
                            onClick = { uriHandler.openUri(supportUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            equalizeHeight = false,
                        )
                        HomeLinkCard(
                            title = stringResource(R.string.home_donate_title),
                            description = stringResource(R.string.home_donate_description),
                            action = stringResource(R.string.home_donate_action),
                            onClick = { uriHandler.openUri("https://www.youtube.com/channel/UCe3U-W3fIYyIi4SZGK4zI8Q/join") },
                            modifier = Modifier.fillMaxWidth(),
                            equalizeHeight = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HearingSafetyHomeCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val titleStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value - 3f).sp)
        val buttonStyle = MaterialTheme.typography.labelSmall.copy(fontSize = (MaterialTheme.typography.labelSmall.fontSize.value + 2f).sp)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    stringResource(R.string.hearing_safety_home_title),
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    softWrap = true,
                )
                Text(
                    stringResource(R.string.hearing_safety_home_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true,
                )
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    stringResource(R.string.hearing_safety_home_action),
                    style = buttonStyle,
                    fontWeight = FontWeight.SemiBold,
                    softWrap = true,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun HomeLinkCard(
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    equalizeHeight: Boolean,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val titleStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value - 3f).sp)
        val buttonStyle = MaterialTheme.typography.labelSmall.copy(fontSize = (MaterialTheme.typography.labelSmall.fontSize.value + 2f).sp)
        val contentModifier = if (equalizeHeight) {
            Modifier.fillMaxHeight().padding(horizontal = 22.dp, vertical = 12.dp)
        } else {
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)
        }
        Column(
            modifier = contentModifier,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    title,
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    softWrap = true,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true,
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(15.dp))
            if (equalizeHeight) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    action,
                    style = buttonStyle,
                    fontWeight = FontWeight.SemiBold,
                    softWrap = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
