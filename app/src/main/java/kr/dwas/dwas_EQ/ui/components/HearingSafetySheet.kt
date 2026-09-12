package kr.dwas.dwas_EQ.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.dwas.dwas_EQ.R

data class HearingSafetySection(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)

object HearingSafetyContent {
    val sections = listOf(
        HearingSafetySection(R.string.hearing_safety_section_amplification_title, R.string.hearing_safety_section_amplification_body),
        HearingSafetySection(R.string.hearing_safety_section_exposure_title, R.string.hearing_safety_section_exposure_body),
        HearingSafetySection(R.string.hearing_safety_section_distortion_title, R.string.hearing_safety_section_distortion_body),
        HearingSafetySection(R.string.hearing_safety_section_cumulative_title, R.string.hearing_safety_section_cumulative_body),
        HearingSafetySection(R.string.hearing_safety_section_tinnitus_title, R.string.hearing_safety_section_tinnitus_body),
        HearingSafetySection(R.string.hearing_safety_section_symptoms_title, R.string.hearing_safety_section_symptoms_body),
        HearingSafetySection(R.string.hearing_safety_section_sudden_title, R.string.hearing_safety_section_sudden_body),
        HearingSafetySection(R.string.hearing_safety_section_existing_title, R.string.hearing_safety_section_existing_body),
        HearingSafetySection(R.string.hearing_safety_section_device_title, R.string.hearing_safety_section_device_body),
        HearingSafetySection(R.string.hearing_safety_section_safe_use_title, R.string.hearing_safety_section_safe_use_body),
        HearingSafetySection(R.string.hearing_safety_section_medical_title, R.string.hearing_safety_section_medical_body),
        HearingSafetySection(R.string.hearing_safety_section_ack_title, R.string.hearing_safety_section_ack_body),
    )
}

private data class HearingIntroPage(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val icon: ImageVector,
)

private val introPages = listOf(
    HearingIntroPage(R.string.hearing_safety_page_protect_title, R.string.hearing_safety_page_protect_body, Icons.Rounded.Hearing),
    HearingIntroPage(R.string.hearing_safety_page_safe_title, R.string.hearing_safety_page_safe_body, Icons.Rounded.VolumeDown),
    HearingIntroPage(R.string.hearing_safety_page_stop_title, R.string.hearing_safety_page_stop_body, Icons.Rounded.HealthAndSafety),
)

@Composable
fun HearingSafetySheet(
    requireAgreement: Boolean,
    onDecline: () -> Unit,
    onAgree: () -> Unit,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val finalListState = rememberLazyListState()
    var finalPageReadToEnd by remember { mutableStateOf(false) }
    val finalListAtEnd by remember {
        derivedStateOf {
            val layoutInfo = finalListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 &&
                !finalListState.canScrollForward &&
                layoutInfo.visibleItemsInfo.lastOrNull()?.index == totalItems - 1
        }
    }

    LaunchedEffect(pagerState.currentPage, finalListAtEnd) {
        if (pagerState.currentPage == 3 && finalListAtEnd) {
            finalPageReadToEnd = true
        }
    }

    Dialog(
        onDismissRequest = { if (!requireAgreement) onClose() },
        properties = DialogProperties(
            dismissOnBackPress = !requireAgreement,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f).widthIn(max = 760.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 18.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(if (requireAgreement) R.string.hearing_safety_dialog_title else R.string.hearing_safety_review_title),
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.hearing_safety_swipe_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    if (page < 3) {
                        HearingIntroPageContent(introPages[page])
                    } else {
                        HearingSafetyDetails(finalListState)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp).size(if (index == pagerState.currentPage) 9.dp else 7.dp)
                                .background(
                                    if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                        )
                    }
                }

                if (requireAgreement) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onDecline,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD33A3A), contentColor = Color.White),
                        ) {
                            Text(stringResource(R.string.hearing_safety_decline), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onAgree,
                            enabled = pagerState.currentPage == 3 && finalPageReadToEnd,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E9C59),
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(stringResource(R.string.hearing_safety_agree), fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp).height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HearingIntroPageContent(page: HearingIntroPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                modifier = Modifier.padding(22.dp).size(44.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HearingSafetyDetails(listState: LazyListState) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(stringResource(R.string.hearing_safety_detail_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            item {
                Text(
                    stringResource(R.string.hearing_safety_scroll_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(stringResource(R.string.hearing_safety_detail_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(
                items = HearingSafetyContent.sections,
                key = { _, section -> section.titleRes },
            ) { index, section ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}. ${stringResource(section.titleRes)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(section.bodyRes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
            }
        }
        VerticalScrollIndicator(listState, modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 18.dp))
    }
}
