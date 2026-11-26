@file:OptIn(ExperimentalLayoutApi::class)

package com.therefore.logic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.therefore.logic.games.ClauseStatus
import com.therefore.logic.games.Reason
import com.therefore.logic.games.SolverState
import com.therefore.logic.ui.theme.FormulaStyle
import com.therefore.logic.ui.theme.LocalPalette
import com.therefore.logic.ui.theme.LocalPlainText
import com.therefore.logic.ui.theme.Mono
import com.therefore.logic.ui.theme.inNotation

/**
 * The DPLL board: letter chips you can guess, the clause list with each clause's state,
 * and Propagate / Backtrack. Used by the SAT lessons and the Solver game.
 */
@Composable
fun SolverBoard(
    s: SolverState,
    propagateAll: Boolean,
    onGuess: (Int, Boolean) -> Unit,
    onForce: (Int) -> Unit,
    onPropagate: () -> Unit,
    onBacktrack: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val plain = LocalPlainText.current
    val conflict = s.conflict
    val locked = s.finished || conflict != null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Letters: tap an unset one to guess it.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.cnf.letters.forEachIndexed { v, letter ->
                val entry = s.trail.lastOrNull { it.lit.v == v }
                var open by remember { mutableStateOf(false) }
                val set = entry != null
                val value = entry?.lit?.positive
                Box {
                    Column(
                        Modifier.width(56.dp).clip(CardShape)
                            .background(when (value) { true -> p.goodSoft; false -> p.badSoft; null -> p.surface })
                            .border(1.5.dp, if (entry?.reason == Reason.GUESS) p.accent else p.line, CardShape)
                            .clickable(enabled = !set && !locked) { open = true }
                            .semantics {
                                contentDescription = "Letter $letter, " + when (value) { true -> "true"; false -> "false"; null -> "unset" } +
                                    (entry?.let { ", " + it.reason.name.lowercase() } ?: "")
                            }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$letter", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            when (value) { true -> "T"; false -> "F"; null -> "–" },
                            fontWeight = FontWeight.Bold,
                            color = when (value) { true -> p.good; false -> p.bad; null -> p.muted },
                        )
                        Text(
                            when (entry?.reason) { Reason.GUESS -> "guess"; Reason.FORCED -> "forced"; Reason.FLIPPED -> "flipped"; null -> "" },
                            style = MaterialTheme.typography.labelSmall, color = p.muted, fontSize = 10.sp,
                        )
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        for (b in listOf(true, false)) DropdownMenuItem(
                            text = { Text("Guess $letter is ${if (b) "true" else "false"}") },
                            onClick = { open = false; onGuess(v, b) },
                        )
                    }
                }
            }
        }
        Text(
            "Guesses: ${s.guesses}" + (if (s.backtracks > 0) " · backtracks: ${s.backtracks}" else "") +
                " · tap an unset letter to guess it. A blue border marks a guess.",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )

        // The clauses.
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.cnf.clauses.forEachIndexed { i, clause ->
                    val st = s.status(i)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("C${i + 1}", Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, color = p.muted)
                        Text(
                            buildAnnotatedString {
                                clause.forEachIndexed { j, l ->
                                    if (j > 0) withStyle(SpanStyle(color = p.muted)) { append(" ∨ ".inNotation(plain)) }
                                    val v = s.value(l)
                                    withStyle(
                                        when (v) {
                                            true -> SpanStyle(color = p.good, fontWeight = FontWeight.Bold)
                                            false -> SpanStyle(color = p.muted, textDecoration = TextDecoration.LineThrough)
                                            null -> SpanStyle(color = p.ink)
                                        },
                                    ) { append(s.cnf.show(l).inNotation(plain)) }
                                }
                            },
                            Modifier.weight(1f), style = FormulaStyle.copy(fontSize = 17.sp),
                        )
                        when (st) {
                            ClauseStatus.SATISFIED -> Text("✓", color = p.good, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            ClauseStatus.CONFLICT -> Badge("conflict", p.bad, p.badSoft)
                            ClauseStatus.UNIT -> {
                                val l = s.forced(i)!!
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp)).background(p.accentSoft)
                                        .clickable(enabled = !locked) { onForce(i) }
                                        .semantics { contentDescription = "Force clause ${i + 1}: ${s.cnf.show(l)}" }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) { Text("forced: ${s.cnf.show(l).inNotation(plain)}", color = p.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                            }
                            ClauseStatus.OPEN -> {}
                        }
                    }
                }
            }
        }

        when {
            s.unsat -> Panel(color = p.goodSoft, border = false) {
                Text("No guesses left to undo: every branch ended in a conflict. These clauses are unsatisfiable.", color = p.ink)
            }
            s.solved -> Panel(color = p.goodSoft, border = false) { Text("Every clause is satisfied.", color = p.ink) }
            conflict != null -> Panel(color = p.badSoft, border = false) {
                Text(
                    "C${conflict + 1} has every literal false: a conflict. Some guess was wrong. Press Backtrack to undo the latest guess and try its other value.",
                    color = p.ink,
                )
            }
            s.units.isNotEmpty() -> Text(
                if (propagateAll) "Some letters are forced. Tap a \"forced\" tag, or press Propagate to set them all."
                else "Some letters are forced. Tap a \"forced\" tag to set one.",
                style = MaterialTheme.typography.bodySmall, color = p.muted,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (propagateAll) Button(onClick = onPropagate, enabled = !locked && s.units.isNotEmpty()) { Text("Propagate") }
            OutlinedButton(onClick = onBacktrack, enabled = conflict != null && !s.finished) { Text("Backtrack") }
            TextButton(onClick = onReset) { Text("Start over") }
        }

        // The search so far, in order.
        if (s.trail.isNotEmpty()) {
            Text(
                "Steps: " + s.trail.joinToString("  →  ") { a ->
                    s.cnf.show(a.lit).inNotation(plain) + when (a.reason) {
                        Reason.GUESS -> " (guess)"
                        Reason.FORCED -> " (C${a.clause!! + 1})"
                        Reason.FLIPPED -> " (flipped)"
                    }
                },
                style = MaterialTheme.typography.bodySmall, color = p.muted,
            )
        }
    }
}

@Composable
private fun Badge(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
