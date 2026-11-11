package com.therefore.logic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.RuleKind
import com.therefore.logic.ui.theme.FormulaStyle
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.LocalPlainText
import com.therefore.logic.ui.theme.Mono
import com.therefore.logic.ui.theme.inNotation

val CardShape = RoundedCornerShape(12.dp)

/** Text that may contain formula symbols; follows the notation setting. */
@Composable
fun LogicText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = Color.Unspecified) {
    Text(text.inNotation(LocalPlainText.current), modifier, style = style, color = color)
}

@Composable
fun FormulaText(text: String, modifier: Modifier = Modifier, style: TextStyle = FormulaStyle, color: Color = Color.Unspecified) {
    Text(text.inNotation(LocalPlainText.current), modifier, style = style, color = color)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.muted) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = LocalPalette.current.surface, border: Boolean = true, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color)
            .then(if (border) Modifier.border(1.dp, p.line, CardShape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) { content() }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        text.inNotation(LocalPlainText.current),
        modifier.clip(RoundedCornerShape(6.dp)).background(p.sunk).padding(horizontal = 7.dp, vertical = 1.dp),
        style = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        color = p.muted,
    )
}

@Composable
fun TipBox(title: String, body: String, warn: Boolean = false) {
    val p = LocalPalette.current
    Panel(color = if (warn) p.badSoft else p.accentSoft, border = false) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            LogicText(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun GradeBadge(grade: String?, modifier: Modifier = Modifier, size: Int = 36) {
    val p = LocalPalette.current
    val (fg, bg) = when (grade) {
        "A" -> p.good to p.goodSoft
        "B" -> p.accent to p.accentSoft
        "C", "D" -> p.hint to p.hintSoft
        null -> p.line to Color.Transparent
        else -> p.muted to p.sunk
    }
    Box(modifier.size(size.dp).clip(RoundedCornerShape((size / 3.6).dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(grade ?: "·", color = fg, fontWeight = FontWeight.Bold, fontSize = (size * 0.45).sp)
    }
}

@Composable
fun LevelDots(level: Int) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (k in 1..5) Box(Modifier.size(7.dp).clip(CircleShape).background(if (k <= level) p.accent else p.line))
    }
}

@Composable
fun RuleCard(rule: Rule, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Panel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Tag(rule.abbr)
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FormulaText(rule.from, style = FormulaStyle.copy(fontSize = 15.sp))
                Text(if (rule.kind == RuleKind.REPLACEMENT) "⟺" else "⟹", color = p.accent, fontWeight = FontWeight.Bold)
                FormulaText(rule.to, style = FormulaStyle.copy(fontSize = 15.sp))
            }
            Text(rule.plain, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
    }
}

/** One row of a proof: number, formula, justification, and an optional explanation. */
data class LedgerRow(val formula: String, val justification: String, val explanation: String? = null, val premise: Boolean = false, val depth: Int = 0)

/** The textbook proof layout. The conclusion is shown after the last premise, like "Premise ∴ ¬Q". */
@Composable
fun Ledger(rows: List<LedgerRow>, conclusion: String?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val lastPremise = rows.indexOfLast { it.premise }
    Column(modifier) {
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = p.line.copy(alpha = 0.7f), thickness = if (i == lastPremise + 1) 1.5.dp else 0.5.dp)
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("${i + 1}.", Modifier.width(30.dp), style = FormulaStyle, color = p.muted, textAlign = TextAlign.End)
                    // Indented lines sit inside an assumption.
                    Row(Modifier.padding(start = 8.dp)) {
                        repeat(r.depth) { Text("│ ", style = FormulaStyle, color = p.accent) }
                    }
                    FormulaText(r.formula, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(r.justification, style = FormulaStyle.copy(fontSize = 13.sp), color = p.muted)
                        if (i == lastPremise && conclusion != null)
                            FormulaText("∴ $conclusion", style = FormulaStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = p.accent)
                    }
                }
                if (r.explanation != null)
                    LogicText(r.explanation, Modifier.padding(start = 38.dp, top = 2.dp), style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
    }
}
