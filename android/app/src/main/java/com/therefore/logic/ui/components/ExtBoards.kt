package com.therefore.logic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.ext.Kripke
import com.therefore.logic.ext.TreeState
import com.therefore.logic.ui.theme.FormulaStyle
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.Mono
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ======================= Truth trees =======================

/**
 * A truth tree drawn top-down: a column of lines, splitting side by side at each branch. Ticked lines are broken down.
 * Under each branch: ✗ when closed, "open" when finished open, or a Close button.
 */
@Composable
fun TreeBoard(s: TreeState, onSelect: (Int) -> Unit, onClose: (Int) -> Unit, modifier: Modifier = Modifier) {
    val leaves = s.leaves.size
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxOf(maxWidth, (leaves * 112).dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(width)) { Subtree(s, 0, 0, onSelect, onClose) }
        }
    }
}

@Composable
private fun Subtree(s: TreeState, start: Int, depth: Int, onSelect: (Int) -> Unit, onClose: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        var id = start
        while (true) {
            TreeLine(s, id, depth, onSelect)
            val kids = s.children(id)
            if (kids.size == 1) { id = kids[0].id; continue }
            if (kids.size == 2) {
                // The split: a small fork, then the two branches side by side.
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val mid = size.width / 2
                    drawLine(p.line, Offset(mid, 0f), Offset(size.width * 0.25f, size.height), 3f)
                    drawLine(p.line, Offset(mid, 0f), Offset(size.width * 0.75f, size.height), 3f)
                }
                Row(Modifier.fillMaxWidth()) {
                    for (k in kids) Box(Modifier.weight(1f)) { Subtree(s, k.id, depth + 1, onSelect, onClose) }
                }
            } else LeafEnd(s, id, onClose)
            break
        }
    }
}

@Composable
private fun TreeLine(s: TreeState, id: Int, depth: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val n = s.node(id)
    val ticked = id in s.decomposed
    val selected = s.selected == id
    val size = when (depth) { 0 -> 17.sp; 1 -> 15.sp; else -> 13.sp }
    Column(
        Modifier.padding(horizontal = 2.dp, vertical = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) p.accentSoft else Color.Transparent)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) p.accent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onSelect(id) }
            .semantics { contentDescription = "Tree line ${id + 1}: ${n.f.show()}${if (ticked) ", broken down" else ""}" }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${id + 1}. ", fontFamily = Mono, fontSize = 11.sp, color = p.muted)
            FormulaText(n.f.show(), style = FormulaStyle.copy(fontSize = size), color = if (ticked) p.muted else p.ink)
            if (ticked) Text(" ✓", color = p.good, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        n.from?.let { Text("from ${it + 1}", fontSize = 10.sp, color = p.muted) }
    }
}

@Composable
private fun LeafEnd(s: TreeState, leaf: Int, onClose: (Int) -> Unit) {
    val p = LocalPalette.current
    when {
        !s.open(leaf) -> Text("✗", color = p.bad, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.semantics { contentDescription = "Closed branch" })
        s.complete(leaf) -> Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.goodSoft).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("open", color = p.good, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        else -> Box(
            Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, p.line, RoundedCornerShape(8.dp))
                .clickable { onClose(leaf) }
                .semantics { contentDescription = "Close the branch ending at line ${leaf + 1}" }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("Close ✗", color = p.muted, fontSize = 13.sp) }
    }
}

// ======================= Possible worlds =======================

/** World positions for 1–3 worlds: a row of two, or a triangle of three. */
private fun worldPositions(n: Int, w: Float, h: Float): List<Offset> = when (n) {
    1 -> listOf(Offset(w / 2, h / 2))
    2 -> listOf(Offset(w * 0.25f, h / 2), Offset(w * 0.75f, h / 2))
    else -> listOf(Offset(w * 0.5f, h * 0.22f), Offset(w * 0.2f, h * 0.78f), Offset(w * 0.8f, h * 0.78f))
}

/** A diagram of the worlds, their arrows, and the letters true in each. */
@Composable
fun WorldsDiagram(m: Kripke, worldName: (Int) -> String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = p.ink)
    val small = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = p.good, fontWeight = FontWeight.SemiBold)
    Canvas(
        modifier.fillMaxWidth().height(if (m.n == 3) 230.dp else 150.dp).semantics {
            contentDescription = (0 until m.n).joinToString("; ") { w ->
                worldName(w) + " sees " + ((0 until m.n).filter { m.sees(w, it) }.joinToString { worldName(it) }.ifEmpty { "nothing" }) +
                    ", true: " + m.truth.filterValues { w in it }.keys.joinToString().ifEmpty { "nothing" }
            }
        },
    ) {
        val r = 30.dp.toPx()
        val pos = worldPositions(m.n, size.width, size.height)
        for ((a, b) in m.arrows) {
            if (a == b) selfLoop(pos[a], r, p.accent) else arrow(pos[a], pos[b], r, p.accent, offset = if (m.sees(b, a)) 7.dp.toPx() else 0f)
        }
        for (w in 0 until m.n) {
            drawCircle(p.surface, r, pos[w])
            drawCircle(p.accent, r, pos[w], style = Stroke(2.dp.toPx()))
            val t = measurer.measure(worldName(w), label)
            drawText(t, topLeft = pos[w] - Offset(t.size.width / 2f, t.size.height / 2f))
            val trues = m.truth.filterValues { w in it }.keys.sorted().joinToString(" ").ifEmpty { "–" }
            val tt = measurer.measure(trues, small)
            drawText(tt, topLeft = pos[w] + Offset(-tt.size.width / 2f, r + 2.dp.toPx()))
        }
    }
}

private fun DrawScope.arrow(from: Offset, to: Offset, r: Float, color: Color, offset: Float) {
    val dx = to.x - from.x; val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    val ux = dx / len; val uy = dy / len
    // Shift sideways when there's an arrow back the other way, so the two don't overlap.
    val sx = -uy * offset; val sy = ux * offset
    val a = Offset(from.x + ux * r + sx, from.y + uy * r + sy)
    val b = Offset(to.x - ux * (r + 2) + sx, to.y - uy * (r + 2) + sy)
    drawLine(color, a, b, 2.5f * density)
    head(b, atan2(uy, ux), color)
}

private fun DrawScope.head(tip: Offset, angle: Float, color: Color) {
    val l = 10.dp.toPx()
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - l * cos(angle - 0.45f), tip.y - l * sin(angle - 0.45f))
        lineTo(tip.x - l * cos(angle + 0.45f), tip.y - l * sin(angle + 0.45f))
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.selfLoop(c: Offset, r: Float, color: Color) {
    val lr = r * 0.55f
    val center = Offset(c.x + r * 0.75f, c.y - r * 0.75f)
    drawCircle(color, lr, center, style = Stroke(2.5f * density))
    head(Offset(c.x + r * 0.95f, c.y - r * 0.3f), 1.9f, color)
}

/** Letter switches for each world, and (optionally) the arrow grid. */
@Composable
fun WorldControls(
    m: Kripke,
    worldName: (Int) -> String,
    arrowsEditable: Boolean,
    arrowWord: String,
    onLetter: (Char, Int) -> Unit,
    onArrow: (Int, Int) -> Unit,
) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (w in 0 until m.n) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(worldName(w), Modifier.width(40.dp), fontFamily = Mono, fontWeight = FontWeight.Bold)
            for (l in m.truth.keys.sorted()) Toggle("$l", m.has(l, w), "$l at ${worldName(w)}") { onLetter(l, w) }
        }
        if (arrowsEditable) {
            Text("Arrows: tap to draw or erase one ($arrowWord)", style = MaterialTheme.typography.bodySmall, color = p.muted)
            for (i in 0 until m.n) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(worldName(i) + " →", Modifier.width(52.dp), fontFamily = Mono, fontSize = 13.sp)
                for (j in 0 until m.n) Toggle(worldName(j), m.sees(i, j), "Arrow ${worldName(i)} to ${worldName(j)}") { onArrow(i, j) }
            }
        }
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, desc: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(width = 48.dp, height = 38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (on) p.accent else p.surface).border(1.5.dp, if (on) p.accent else p.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$desc, ${if (on) "on" else "off"}" },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, color = if (on) p.onAccent else p.ink) }
}

/** Formulas with their truth value at each world (or moment); the first column is the one the goal is about. */
@Composable
fun TruthGrid(formulas: List<String>, columns: List<String>, value: (String, Int) -> Boolean) {
    val p = LocalPalette.current
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Box(Modifier.weight(1f))
                columns.forEachIndexed { i, c ->
                    Text(c, Modifier.width(44.dp), textAlign = TextAlign.Center, fontFamily = Mono, fontSize = 12.sp,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal, color = if (i == 0) p.accent else p.muted)
                }
            }
            for (f in formulas) Row(verticalAlignment = Alignment.CenterVertically) {
                FormulaText(f, Modifier.weight(1f), style = FormulaStyle.copy(fontSize = 17.sp))
                columns.indices.forEach { i ->
                    val v = value(f, i)
                    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(if (v) p.goodSoft else p.badSoft)
                                .border(if (i == 0) 1.5.dp else 0.dp, if (v) p.good else p.bad, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) { Text(if (v) "T" else "F", color = if (v) p.good else p.bad, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

// ======================= Timelines =======================

/** Moments 0, 1, 2 … left to right; tap a cell to switch a letter at that moment. */
@Composable
fun TimelineControls(trace: List<Set<Char>>, letters: List<Char>, onToggle: (Char, Int) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Box(Modifier.width(32.dp))
            trace.indices.forEach { t ->
                Text(if (t == 0) "now" else "$t", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp,
                    color = if (t == 0) p.accent else p.muted, fontWeight = if (t == 0) FontWeight.Bold else FontWeight.Normal)
            }
        }
        for (l in letters) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$l", Modifier.width(32.dp), fontFamily = Mono, fontWeight = FontWeight.Bold)
            trace.forEachIndexed { t, set ->
                val on = l in set
                Box(Modifier.weight(1f).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (on) p.accent else p.surface).border(1.5.dp, if (on) p.accent else p.line, RoundedCornerShape(8.dp))
                            .clickable { onToggle(l, t) }
                            .semantics { contentDescription = "$l at moment $t, ${if (on) "on" else "off"}" },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (on) "T" else "", color = p.onAccent, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Text("Time runs left to right and stops at the last moment.", style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
}
