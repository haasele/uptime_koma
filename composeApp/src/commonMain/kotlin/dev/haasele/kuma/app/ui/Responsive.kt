package dev.haasele.koma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class MetricSpec(
    val label: String,
    val value: String,
    val accent: Color? = null,
)

/**
 * Wraps metric tiles into 2 or 4 columns depending on available width so half-width
 * WQHD / laptop windows do not clip a rigid four-across row.
 */
@Composable
fun ResponsiveMetricRow(
    metrics: List<MetricSpec>,
    modifier: Modifier = Modifier,
    narrowBreakpoint: Dp = 560.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth < narrowBreakpoint) 2 else minOf(4, metrics.size.coerceAtLeast(1))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            metrics.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    row.forEach { metric ->
                        MetricTile(
                            label = metric.label,
                            value = metric.value,
                            accent = metric.accent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
