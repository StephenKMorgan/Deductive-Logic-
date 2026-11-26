package com.therefore.logic.engine

import com.therefore.logic.content.Curriculum
import com.therefore.logic.content.LessonStep
import com.therefore.logic.content.f
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Lean translation, the put-in-order steps, and the later courses. */
class LeanAndMoreTest {
    private fun lines(premises: List<String>, vararg steps: Triple<String, Rule, List<Int>>) =
        premises.map { ProofLine(f(it), null) } + steps.map { (fs, r, c) -> ProofLine(f(fs), r, c) }

    @Test fun modusPonensInLean() {
        val ls = lines(listOf("P -> Q", "P"), Triple("Q", Rule.MP, listOf(2, 1)))
        assertEquals(
            "import Mathlib.Tactic\n\nexample (P Q : Prop) (h1 : P → Q) (h2 : P) : Q := by\n  have h3 : Q := h1 h2\n  exact h3\n",
            Lean.proof(listOf(f("P -> Q"), f("P")), ls, f("Q")),
        )
    }

    @Test fun conditionalAndIndirectProofsInLean() {
        val cp = lines(
            listOf("P -> Q", "Q -> R"),
            Triple("P", Rule.ACP, emptyList()), Triple("Q", Rule.MP, listOf(1, 3)), Triple("R", Rule.MP, listOf(2, 4)), Triple("P -> R", Rule.CP, listOf(3, 5)),
        )
        val code = Lean.proof(cp.take(2).map { it.formula }, cp, f("P -> R"))
        assertTrue(code, code.contains("  have h6 : P → R := by\n    intro h3\n    have h4 : Q := h1 h3\n    have h5 : R := h2 h4\n    exact h5\n  exact h6\n"))

        val neg = lines(
            listOf("P -> Q", "~Q"),
            Triple("P", Rule.AIP, emptyList()), Triple("Q", Rule.MP, listOf(1, 3)), Triple("Q & ~Q", Rule.Conj, listOf(4, 2)), Triple("~P", Rule.IP, listOf(3, 5)),
        )
        val c2 = Lean.proof(neg.take(2).map { it.formula }, neg, f("~P"))
        assertTrue(c2, c2.contains("    intro h3\n") && c2.contains("have h5 : Q ∧ ¬Q := ⟨h4, h2⟩") && c2.contains("exact h5.2 h5.1"))

        val classical = lines(
            listOf("~P -> Q", "~Q"),
            Triple("~P", Rule.AIP, emptyList()), Triple("Q", Rule.MP, listOf(1, 3)), Triple("Q & ~Q", Rule.Conj, listOf(4, 2)), Triple("P", Rule.IP, listOf(3, 5)),
        )
        assertTrue(Lean.proof(classical.take(2).map { it.formula }, classical, f("P")).contains("    by_contra h3\n"))
    }

    @Test fun everyPropositionalModelProofTranslates() {
        for (c in Curriculum.courses + Curriculum.daily) for (level in 1..5) for (seed in 1..8) {
            val puzzle = Generator.generate(c.spec, level, seed * 17L + level)
            val ls = Proofs.numbered(puzzle).map { it.line }
            assertTrue(Lean.supports(puzzle.premises, ls))
            val code = Lean.proof(puzzle.premises, ls, puzzle.conclusion)
            assertTrue(code.endsWith("\n") && code.contains("example ("))
            // Inference-only courses need no tauto at all.
            if (c.spec.rules.all { it.kind == RuleKind.INFERENCE }) assertFalse("${c.id}: $code", code.contains("tauto"))
            // Every have line names a hypothesis that exists.
            Regex("h(\\d+)").findAll(code).forEach { assertTrue(it.value, it.groupValues[1].toInt() <= ls.size) }
        }
        // Predicate logic is left out.
        val pred = Generator.generate(Curriculum.predicateCourses[0].spec, 1, 3)
        assertFalse(Lean.supports(pred.premises, Proofs.numbered(pred).map { it.line }))
    }

    @Test fun orderStepsAreWellFormed() {
        val steps = Curriculum.allCourses.flatMap { it.lessons }.flatMap { it.steps }.filterIsInstance<LessonStep.OrderStep>()
        assertTrue(steps.size >= 7)
        for (s in steps) {
            assertEquals(s.title, s.pieces.size, s.pieces.toSet().size)
            assertTrue(s.title, s.extra.none { it in s.pieces })
        }
    }

    @Test fun laterCoursesAndPracticeSets() {
        for (id in listOf("meta", "induction", "lean", "everyday")) {
            val c = Curriculum.byId(id)
            assertFalse(c.practisesProofs)
            for (seed in 1L..10L) {
                val (_, set) = Curriculum.lesson(Curriculum.practiceSetId(id, seed))
                assertTrue(set.steps.isNotEmpty())
                set.steps.filterIsInstance<LessonStep.Quiz>().forEach { assertTrue(it.answer in it.options.indices) }
            }
        }
        // Descriptions practice uses its own patterns.
        val desc = Curriculum.byId("pred-desc")
        val used = (1..20).flatMap { Generator.generate(desc.spec, 3, it.toLong()).solution.map { s -> s.rule } }.toSet()
        assertTrue(Rule.Id in used && Rule.EI in used)
    }
}
