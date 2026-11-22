package com.therefore.logic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.therefore.logic.ui.theme.LocalPalette
import kotlin.math.ceil
import kotlin.math.max

/*
 * Small single-series charts. One series means one color (the accent) and no legend: the card title names it.
 * Thin marks with 2px gaps and rounded data ends, recessive gridlines, and tap-to-inspect in place of hover.
 * Each chart also carries a spoken summary for screen readers.
 */

/** A "nice" top for an axis, so gridlines land on round numbers. */
private fun niceMax(v: Float): Float = when {
    v <= 1f -> 1f
    v <= 5f -> ceil(v)
    v <= 10f -> (ceil(v / 2) * 2)
    else -> (ceil(v / 5) * 5)
}

/** Columns over time or categories. Tap a column to read its value in the caption line. */
@Composable
fun ColumnChart(
    values: List<Float>,
    labels: List<String>,
    describe: (Int) -> String,
    summary: String,
    modifier: Modifier = Modifier,
    /** Which labels to print under the axis (others stay blank to avoid collisions). */
    showLabel: (Int) -> Boolean = { true },
    /** For long series: a few labels spread evenly under the axis instead of one per bar. */
    axisLabels: List<String>? = null,
) {
    val p = LocalPalette.current
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val top = niceMax(values.maxOrNull() ?: 0f)
    Column(modifier.semantics { contentDescription = summary }) {
        Text(
            selected?.let(describe) ?: "Tap a bar to see its value",
            style = MaterialTheme.typography.bodySmall, color = if (selected != null) p.ink else p.muted,
        )
        Box(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(132.dp).pointerInput(values) {
                    detectTapGestures { o ->
                        val slot = size.width / values.size
                        val i = (o.x / slot).toInt().coerceIn(0, values.lastIndex)
                        selected = if (selected == i) null else i
                    }
                },
            ) {
                val gap = 2.dp.toPx()
                val slot = size.width / values.size
                // Recessive gridlines at the top and middle.
                for (f in listOf(0.5f, 1f)) {
                    val y = size.height * (1 - f)
                    drawLine(p.line, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
                drawLine(p.line, Offset(0f, size.height), Offset(size.width, size.height), 1.5f)
                values.forEachIndexed { i, v ->
                    if (v <= 0f) return@forEachIndexed
                    val h = size.height * (v / top)
                    val x = i * slot + gap / 2
                    val w = (slot - gap).coerceAtLeast(1f)
                    val color = if (selected == null || selected == i) p.accent else p.accent.copy(alpha = 0.35f)
                    val r = minOf(4.dp.toPx(), w / 2)
                    // Rounded top, square base anchored to the baseline.
                    drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(r, r))
                    drawRect(color, Offset(x, size.height - minOf(h, r)), Size(w, minOf(h, r)))
                }
            }
            Text("${top.toInt()}", Modifier.align(Alignment.TopEnd), style = MaterialTheme.typography.labelSmall, color = p.muted)
        }
        if (axisLabels != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            axisLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = p.muted) }
        } else Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (showLabel(i)) l else "", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Visible,
                    style = MaterialTheme.typography.labelSmall, color = p.muted,
                )
            }
        }
    }
}

/** A trend line with markers; gaps where there's no data. Tap near a point to read it. */
@Composable
fun TrendLine(
    values: List<Float?>,
    labels: List<String>,
    describe: (Int) -> String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val top = niceMax(max(1f, values.filterNotNull().maxOrNull() ?: 1f))
    Column(modifier.semantics { contentDescription = summary }) {
        Text(
            selected?.let(describe) ?: "Tap a point to see its value",
            style = MaterialTheme.typography.bodySmall, color = if (selected != null) p.ink else p.muted,
        )
        Box(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(132.dp).pointerInput(values) {
                    detectTapGestures { o ->
                        val step = size.width / (values.size - 1).coerceAtLeast(1)
                        val i = ((o.x + step / 2) / step).toInt().coerceIn(0, values.lastIndex)
                        selected = if (values[i] == null || selected == i) null else i
                    }
                },
            ) {
                val pad = 6.dp.toPx()
                val w = size.width - 2 * pad
                val h = size.height - 2 * pad
                val step = w / (values.size - 1).coerceAtLeast(1)
                for (f in listOf(0.5f, 1f)) {
                    val y = pad + h * (1 - f)
                    drawLine(p.line, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
                drawLine(p.line, Offset(0f, pad + h), Offset(size.width, pad + h), 1.5f)
                fun pt(i: Int, v: Float) = Offset(pad + i * step, pad + h * (1 - v / top))
                val path = Path()
                var drawing = false
                values.forEachIndexed { i, v ->
                    if (v == null) { drawing = false; return@forEachIndexed }
                    val o = pt(i, v)
                    if (!drawing) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    drawing = true
                }
                drawPath(path, p.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val o = pt(i, v)
                    // 8dp marker with a surface ring.
                    drawCircle(p.surface, 6.dp.toPx(), o)
                    drawCircle(if (selected == i) p.ink else p.accent, 4.dp.toPx(), o)
                }
            }
            Text(String.format("%.1f", top), Modifier.align(Alignment.TopEnd), style = MaterialTheme.typography.labelSmall, color = p.muted)
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = p.muted) }
        }
    }
}

/** Ranked horizontal bars with the value printed at the end of each. */
@Composable
fun RankedBars(items: List<Pair<String, Int>>, summary: String, modifier: Modifier = Modifier, empty: String = "Nothing yet.") {
    val p = LocalPalette.current
    if (items.isEmpty()) { Text(empty, modifier, style = MaterialTheme.typography.bodyMedium, color = p.muted); return }
    val top = items.maxOf { it.second }.coerceAtLeast(1)
    Column(modifier.semantics { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (v > 0) Box(
                        Modifier.fillMaxWidth(v.toFloat() / top * 0.85f).height(14.dp)
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(p.accent),
                    )
                    Text("  $v", style = MaterialTheme.typography.labelSmall, color = p.muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
