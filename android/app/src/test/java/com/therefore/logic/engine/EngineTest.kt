package com.therefore.logic.engine

import com.therefore.logic.content.Curriculum
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun p(s: String): Formula = when (val r = Parser.parse(s)) {
        is ParseResult.Ok -> r.formula
        is ParseResult.Error -> throw AssertionError("$s: ${r.message}")
    }

    @Test fun parserAcceptsBothNotations() {
        assertEquals(p("(P ∧ Q) → R"), p("(P & Q) -> R"))
        assertEquals(p("¬R ∨ S"), p("~R v S"))
        assertTrue(Parser.parse("P ∨ Q ∨ R") is ParseResult.Error)
        assertTrue(Parser.parse("(P ∧ Q") is ParseResult.Error)
        assertEquals("(P ∧ Q) → R", p("(P&Q)->R").show())
    }

    @Test fun solvesTheExamplePuzzle() {
        val lines = mutableListOf("(P & Q) -> R", "P", "~R v S", "~S").map(::p).toMutableList()
        val steps = listOf(
            Triple("S v ~R", Rule.Com, listOf(3)),
            Triple("~R", Rule.DS, listOf(5, 4)),
            Triple("P -> (Q -> R)", Rule.Exp, listOf(1)),
            Triple("Q -> R", Rule.MP, listOf(7, 2)),
            Triple("~Q", Rule.MT, listOf(8, 6)),
        )
        for ((f, rule, cites) in steps) {
            val r = Checker.check(rule, cites.map { lines[it - 1] }, p(f))
            assertTrue("$f by $rule: ${r.message}", r.ok)
            lines += p(f)
        }
    }

    private fun rejects(rule: Rule, cited: List<String>, f: String, trap: Trap? = null) {
        val r = Checker.check(rule, cited.map(::p), p(f))
        assertFalse("$rule should reject $f", r.ok)
        if (trap != null) assertEquals(trap, r.trap)
    }

    @Test fun catchesMistakesWithTheRightLesson() {
        rejects(Rule.MP, listOf("P -> Q", "Q"), "P", Trap.AFFIRM)
        rejects(Rule.MT, listOf("P -> Q", "~P"), "~Q", Trap.DENY)
        rejects(Rule.DS, listOf("~R v S", "~S"), "~R", Trap.DS_RIGHT)
        rejects(Rule.Simp, listOf("P & Q"), "Q", Trap.SIMP_RIGHT)
        rejects(Rule.Add, listOf("P"), "Q v P", Trap.ADD_RIGHT)
        rejects(Rule.MT, listOf("P -> ~Q", "Q"), "~P", Trap.DN)
        rejects(Rule.Com, listOf("P -> Q"), "Q -> P")
        val alt = Checker.check(Rule.MP, listOf(p("P -> Q"), p("~Q")), p("~P"))
        assertEquals(Rule.MT, alt.alt)
    }

    @Test fun replacementRulesWorkInsideLinesAndBothWays() {
        assertTrue(Checker.check(Rule.DN, listOf(p("P -> Q")), p("~~P -> Q")).ok)
        assertTrue(Checker.check(Rule.DeM, listOf(p("~(P v Q)")), p("~P & ~Q")).ok)
        assertTrue(Checker.check(Rule.Trans, listOf(p("~Q -> ~P")), p("P -> Q")).ok)
        assertTrue(Checker.check(Rule.Impl, listOf(p("~P v Q")), p("P -> Q")).ok)
        assertTrue(Checker.check(Rule.Dist, listOf(p("(P & Q) v (P & R)")), p("P & (Q v R)")).ok)
        assertTrue(Checker.check(Rule.CD, listOf(p("(P -> Q) & (R -> S)"), p("P v R")), p("Q v S")).ok)
    }

    @Test fun formulasPrintReadablyInsideSentences() {
        assertEquals("if P → Q", "if ${p("P -> Q")}")
        assertEquals("¬(P ∧ Q)", "${p("~(P & Q)")}")
    }

    @Test fun lessonExamplesAreValidProofs() {
        for (c in Curriculum.courses) {
            val ex = c.lesson!!.example
            val ls = ArrayList<Formula>()
            ex.lines.forEachIndexed { i, (f, j) ->
                val formula = p(f)
                if (j != "Premise") {
                    val parts = j.split(",").map { it.trim() }
                    val rule = Rule.valueOf(parts.last())
                    val r = Checker.check(rule, parts.dropLast(1).map { ls[it.toInt() - 1] }, formula)
                    assertTrue("${c.id} line ${i + 1}: ${r.message}", r.ok)
                }
                ls += formula
            }
            assertEquals(p(ex.conclusion), ls.last())
        }
    }

    @Test fun generatorMakesValidPuzzlesForEveryCourseAndLevel() {
        var count = 0
        val t0 = System.currentTimeMillis()
        for (c in Curriculum.courses + Curriculum.daily) for (level in 1..5) for (seed in 1..20) {
            val puzzle = Generator.generate(c.spec, level, seed * 7919L + level)
            count++
            assertTrue(satisfiable(puzzle.premises))
            val used = puzzle.solution.map { it.rule }.toSet()
            assertTrue("${c.id} used a rule outside the course", c.spec.rules.containsAll(used))
            if (c.spec.focus.isNotEmpty()) assertTrue("${c.id} missing focus rule", used.any { it in c.spec.focus })
            val lines = Proofs.numbered(puzzle)
            val fs = ArrayList<Formula>()
            for (l in lines) {
                if (l.rule != null) assertTrue("${c.id} model step", Checker.check(l.rule, l.cites.map { fs[it - 1] }, l.formula).ok)
                fs += l.formula
            }
            assertEquals(puzzle.conclusion, fs.last())
            // Hints lead all the way to the conclusion
            val have = puzzle.premises.toMutableList()
            var guard = 0
            while (guard++ < 30) have += (Proofs.hint(puzzle, have) ?: break).step.formula
            assertTrue(puzzle.conclusion in have)
        }
        println("$count puzzles in ${System.currentTimeMillis() - t0} ms")
    }

    @Test fun dailyPuzzleIsTheSameForTheSameSeed() {
        val a = Generator.generate(Curriculum.daily.spec, 3, 12345L)
        val b = Generator.generate(Curriculum.daily.spec, 3, 12345L)
        assertEquals(a, b)
    }

    @Test fun puzzlesSurviveSaveAndLoad() {
        val puzzle = Generator.generate(Curriculum.courses.last().spec, 4, 99L)
        val json = Json.encodeToString(puzzle)
        assertEquals(puzzle, Json.decodeFromString<Puzzle>(json))
        assertNotNull(Proofs.hint(puzzle, puzzle.premises))
    }
}
