package kr.dwas.dwas_EQ.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    fillContentHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val contentModifier = if (fillContentHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        Column(
            modifier = contentModifier.padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, softWrap = true)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(0.42f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
        Text(value, modifier = Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, softWrap = true)
    }
}

@Composable
fun StatusPill(text: String, positive: Boolean = true) {
    Surface(
        color = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, softWrap = true)
    }
}

@Composable
fun HorizontalScrollIndicator(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val visible = scrollState.isScrollInProgress && scrollState.maxValue > 0
    val track = MaterialTheme.colorScheme.outlineVariant
    val thumb = MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth().height(8.dp),
        enter = fadeIn(animationSpec = tween(durationMillis = 230)),
        exit = fadeOut(animationSpec = tween(durationMillis = 230)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxScroll = scrollState.maxValue.toFloat()
            val viewport = size.width
            val content = viewport + maxScroll
            val thumbWidth = if (content <= viewport || maxScroll <= 0f) viewport else max(36.dp.toPx(), viewport * viewport / content)
            val travel = (viewport - thumbWidth).coerceAtLeast(0f)
            val fraction = if (maxScroll <= 0f) 0f else scrollState.value / maxScroll
            drawLine(track, Offset(0f, size.height / 2f), Offset(viewport, size.height / 2f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(thumb, Offset(travel * fraction, size.height / 2f), Offset(travel * fraction + thumbWidth, size.height / 2f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
fun VerticalScrollIndicator(listState: LazyListState, modifier: Modifier = Modifier) {
    val visible = listState.isScrollInProgress && (listState.canScrollBackward || listState.canScrollForward)
    val track = MaterialTheme.colorScheme.outlineVariant
    val thumb = MaterialTheme.colorScheme.primary
    val layout = listState.layoutInfo
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxHeight().width(8.dp),
        enter = fadeIn(animationSpec = tween(durationMillis = 230)),
        exit = fadeOut(animationSpec = tween(durationMillis = 230)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val total = layout.totalItemsCount
            val visibleItems = layout.visibleItemsInfo.size
            val trackHeight = size.height
            if (total <= 0 || visibleItems <= 0) return@Canvas
            val thumbHeight = max(36.dp.toPx(), trackHeight * (visibleItems.toFloat() / total.toFloat())).coerceAtMost(trackHeight)
            val maxIndex = (total - visibleItems).coerceAtLeast(1)
            val first = listState.firstVisibleItemIndex.coerceAtMost(maxIndex)
            val fraction = first.toFloat() / maxIndex.toFloat()
            val y = (trackHeight - thumbHeight) * fraction
            drawRoundRect(track, size = Size(4.dp.toPx(), trackHeight), topLeft = Offset((size.width - 4.dp.toPx()) / 2f, 0f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            drawRoundRect(thumb, size = Size(4.dp.toPx(), thumbHeight), topLeft = Offset((size.width - 4.dp.toPx()) / 2f, y), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}
