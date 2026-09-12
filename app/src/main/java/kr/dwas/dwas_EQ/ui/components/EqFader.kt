package kr.dwas.dwas_EQ.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.dwas.dwas_EQ.domain.EqCurve


@Composable
fun VerticalEqFader(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = EqCurve.MIN_DB..EqCurve.MAX_DB
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    val zero = MaterialTheme.colorScheme.outline
    val thumbCenter = MaterialTheme.colorScheme.surface
    val valueColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val contentAlpha = if (enabled) 1f else 0.42f

    fun quantizedFromY(y: Float, heightPx: Float): Float {
        if (heightPx <= 0f) return value
        val normalized = (1f - (y / heightPx)).coerceIn(0f, 1f)
        return EqCurve.quantizeDb(range.start + normalized * (range.endInclusive - range.start))
    }

    Column(
        modifier = modifier.width(68.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = String.format("%+.1f", value),
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(
            modifier = Modifier
                .width(52.dp)
                .height(220.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range, 23)
                    setProgress { requested ->
                        if (!enabled) return@setProgress false
                        onValueChange(EqCurve.quantizeDb(requested))
                        onValueChangeFinished()
                        true
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { start -> onValueChange(quantizedFromY(start.y, size.height.toFloat())) },
                        onDragEnd = onValueChangeFinished,
                        onDragCancel = onValueChangeFinished,
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            onValueChange(quantizedFromY(change.position.y, size.height.toFloat()))
                        },
                    )
                },
        ) {
            val trackWidth = 6.dp.toPx()
            val thumbRadius = 10.dp.toPx()
            val centerX = size.width / 2f
            val normalized = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val thumbY = (1f - normalized) * size.height
            val zeroNormalized = ((0f - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val zeroY = (1f - zeroNormalized) * size.height

            drawRoundRect(
                color = inactive.copy(alpha = contentAlpha),
                topLeft = Offset(centerX - trackWidth / 2f, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
            )
            drawRoundRect(
                color = primary.copy(alpha = contentAlpha),
                topLeft = Offset(centerX - trackWidth / 2f, thumbY),
                size = Size(trackWidth, (size.height - thumbY).coerceAtLeast(0f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f),
            )
            drawLine(
                color = zero.copy(alpha = 0.75f * contentAlpha),
                start = Offset(centerX - 14.dp.toPx(), zeroY),
                end = Offset(centerX + 14.dp.toPx(), zeroY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = primary.copy(alpha = contentAlpha),
                radius = thumbRadius,
                center = Offset(centerX, thumbY.coerceIn(thumbRadius, size.height - thumbRadius)),
            )
            drawCircle(
                color = thumbCenter.copy(alpha = contentAlpha),
                radius = 3.dp.toPx(),
                center = Offset(centerX, thumbY.coerceIn(thumbRadius, size.height - thumbRadius)),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            fontWeight = FontWeight.Medium,
        )
    }
}
