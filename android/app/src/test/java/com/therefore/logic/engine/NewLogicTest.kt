package com.therefore.logic.engine

import com.therefore.logic.content.Curriculum
import com.therefore.logic.content.KLogic
import com.therefore.logic.content.LessonStep
import com.therefore.logic.content.WorldsGoal
import com.therefore.logic.content.f
import com.therefore.logic.ext.Drills
import com.therefore.logic.ext.Kripke
import com.therefore.logic.ext.Semantics
import com.therefore.logic.ext.Shape
import com.therefore.logic.ext.Trees
import com.therefore.logic.ext.XParser
import com.therefore.logic.ext.satisfies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Truth trees, modal, temporal and constructive logic: the engine, every lesson step, and the practice sets. */
class NewLogicTest {
    private val steps get() = Curriculum.allCourses.flatMap { it.lessons }.flatMap { it.steps }

    @Test fun formulasParseAndPrint() {
        for (s in listOf("□(P → Q) → (□P → □Q)", "◇P ∧ ◇¬P", "P U Q", "○○P", "¬(P U Q)", "□¬(P ∧ Q)")) {
            val x = XParser.parse(s)
            assertNotNull(s, x)
            assertEquals(s, x!!.show())
        }
        assertNull(XParser.parse("P ∧ Q ∨ R"))   // mixed connectives need brackets
        assertNull(XParser.parse("□"))
        assertEquals(XParser.of("□P → ◇P"), XParser.parse("[]P -> <>P"))
    }

    @Test fun semantics() {
        // w1 sees w2 and w3; P at w2 only.
        val m = Kripke(3, setOf(0 to 1, 0 to 2), mapOf('P' to setOf(1)))
        assertTrue(Semantics.modal(XParser.of("◇P"), m, 0))
        assertFalse(Semantics.modal(XParser.of("□P"), m, 0))
        assertTrue("a dead end makes every □ true", Semantics.modal(XParser.of("□P ∧ □¬P"), m, 1))
        // Timeline: P now and at 1, Q at 2.
        val trace = listOf(setOf('P'), setOf('P'), setOf('Q'), emptySet(), emptySet())
        assertTrue(Semantics.temporal(XParser.of("P U Q"), trace, 0))
        assertTrue(Semantics.temporal(XParser.of("○P"), trace, 0))
        assertFalse(Semantics.temporal(XParser.of("○P"), trace, 4))
        assertFalse(Semantics.temporal(XParser.of("□P"), trace, 0))
        assertTrue(Semantics.temporal(XParser.of("◇Q"), trace, 0))
        // Constructive: excluded middle fails at w1 when P is proved only later.
        val k = Kripke(2, setOf(0 to 1), mapOf('P' to setOf(1)))
        assertFalse(Semantics.intuitionistic(XParser.of("P ∨ ¬P"), k, 0))
        assertTrue(Semantics.intuitionistic(XParser.of("P ∨ ¬P"), k, 1))
        assertNull(k.persistenceBreak())
        assertNotNull(Kripke(2, setOf(0 to 1), mapOf('P' to setOf(0))).persistenceBreak())
        assertTrue(Kripke(2, setOf(0 to 0, 1 to 1), emptyMap()).reflexive)
        assertFalse(Kripke(3, setOf(0 to 1, 1 to 2), emptyMap()).transitive)
    }

    @Test fun treeRulesAndMistakes() {
        var s = Trees.start(listOf(f("P → Q"), f("P")), f("Q"))
        s = Trees.decompose(s, 0, Shape.STACK)
        assertEquals(1, s.mistakes)
        assertTrue(s.message!!.contains("branch"))
        s = Trees.decompose(s, 0, Shape.BRANCH)
        assertEquals(2, s.leaves.size)
        val notP = s.leaves.first { s.node(it).f == f("~P") }
        s = Trees.close(s, notP)
        assertTrue(notP in s.closed)
        assertFalse(s.finished)
        s = Trees.close(s, s.leaves.first { it != notP })
        assertTrue(s.allClosed)
        s = Trees.judge(s, false)
        assertNull(s.verdict)
        s = Trees.judge(s, true)
        assertEquals(true, s.verdict)
        // Closing a branch with no contradiction is a mistake.
        val open = Trees.start(listOf(f("P ∨ Q")), f("P"))
        assertEquals(1, Trees.close(open, open.leaves.single()).mistakes)
    }

    @Test fun everyTreeStepReachesItsVerdict() {
        val trees = steps.filterIsInstance<LessonStep.TreeStep>()
        assertTrue(trees.size >= 9)
        for (t in trees) {
            val premises = t.premises.map(::f)
            val done = Drills.autoSolve(Trees.start(premises, f(t.conclusion)))
            assertTrue(t.title, done.finished)
            assertEquals(t.title, t.valid, done.allClosed)
            assertEquals(t.title, t.valid, !satisfiable(premises + not(f(t.conclusion))))
        }
    }

    private fun allArrowSets(n: Int): Sequence<Set<Pair<Int, Int>>> = sequence {
        val pairs = (0 until n).flatMap { i -> (0 until n).map { j -> i to j } }
        for (mask in 0 until (1 shl pairs.size)) yield(pairs.filterIndexed { k, _ -> (mask shr k) and 1 == 1 }.toSet())
    }

    private fun meets(step: LessonStep.KripkeStep, arrows: Set<Pair<Int, Int>>): Boolean {
        val m0 = Kripke(step.worlds, arrows, emptyMap())
        if (!step.frame.all { m0.satisfies(it) }) return false
        return Drills.kripkeFeasible(step.formulas, step.goal, step.logic, step.worlds, arrows)
    }

    @Test fun everyWorldsStepIsDoableOrTrulyImpossible() {
        val ks = steps.filterIsInstance<LessonStep.KripkeStep>()
        assertTrue(ks.size >= 12)
        for (k in ks) {
            val arrowChoices = if (k.fixedArrows) sequenceOf(k.arrows) else allArrowSets(k.worlds)
            val possible = arrowChoices.any { meets(k, it) }
            assertEquals("${k.title}: impossible = ${k.impossible}", !k.impossible, possible)
            // The starting model must not already meet the goal.
            val xs = k.formulas.map(XParser::of)
            val start = Kripke(k.worlds, k.arrows, xs.flatMap { it.atoms() }.associateWith { emptySet() })
            val v = xs.map { if (k.logic == KLogic.MODAL) Semantics.modal(it, start, 0) else Semantics.intuitionistic(it, start, 0) }
            val met = k.frame.all { start.satisfies(it) } && (if (k.goal == WorldsGoal.ALL_TRUE) v.all { it } else !v[0])
            assertFalse("${k.title} starts solved", met && !k.impossible)
        }
    }

    @Test fun everyTimelineStepIsDoable() {
        val ts = steps.filterIsInstance<LessonStep.TimelineStep>()
        assertTrue(ts.size >= 9)
        for (t in ts) {
            assertTrue(t.title, Drills.timelineFeasible(t.formulas, t.goal, t.moments))
            val trace = List(t.moments) { m -> t.start.getOrElse(m) { "" }.toSet() }
            val v = t.formulas.map { Semantics.temporal(XParser.of(it), trace, 0) }
            assertFalse("${t.title} starts solved", if (t.goal == WorldsGoal.ALL_TRUE) v.all { it } else !v[0])
        }
    }

    @Test fun constructiveClaimsAreRight() {
        for (c in Drills.CLAIMS) {
            val x = XParser.of(c.formula)
            val letters = x.atoms().toList()
            // Search every model on up to 3 states, any arrows, persistent letters.
            var counter = false
            outer@ for (n in 1..3) for (arrows in allArrowSets(n)) {
                for (mask in 0 until (1 shl (letters.size * n))) {
                    val truth = letters.withIndex().associate { (i, l) -> l to (0 until n).filter { (mask shr (i * n + it)) and 1 == 1 }.toSet() }
                    val m = Kripke(n, arrows, truth)
                    if (m.persistenceBreak() != null) continue
                    if (!Semantics.intuitionistic(x, m, 0)) { counter = true; break@outer }
                }
            }
            assertEquals("${c.formula} valid = ${c.valid}", !c.valid, counter)
            // Each claim is classically valid, so the countermodels really show the difference.
            assertTrue(c.formula, (0 until (1 shl letters.size)).all { mask ->
                Semantics.modal(x, Kripke(1, emptySet(), letters.withIndex().associate { (i, l) -> l to if ((mask shr i) and 1 == 1) setOf(0) else emptySet() }), 0)
            })
            if (!c.valid) assertTrue("${c.formula} countermodel frame", Drills.kripkeFeasible(listOf(c.formula), WorldsGoal.FALSE, KLogic.INTUITIONISTIC, c.worlds, c.arrows))
        }
    }

    @Test fun practiceSetsAreWellFormed() {
        for (seed in 1L..30L) {
            val rng = Random(seed)
            for (t in Drills.trees(rng).filterIsInstance<LessonStep.TreeStep>()) {
                val done = Drills.autoSolve(Trees.start(t.premises.map(::f), f(t.conclusion)))
                assertTrue(done.finished)
                assertEquals(t.valid, done.allClosed)
                assertTrue(done.leaves.size <= 6)
            }
            for (k in Drills.modal(rng).filterIsInstance<LessonStep.KripkeStep>()) assertTrue(Drills.kripkeFeasible(k.formulas, k.goal, k.logic, k.worlds, k.arrows))
            for (t in Drills.temporal(rng).filterIsInstance<LessonStep.TimelineStep>()) assertTrue(Drills.timelineFeasible(t.formulas, t.goal, t.moments))
            val int = Drills.intuitionistic(rng)
            assertEquals(5, int.size)
        }
        // Practice-set ids open as lessons.
        for (c in Curriculum.extraCourses.filter { !it.practisesProofs }) {
            val (course, lesson) = Curriculum.lesson(Curriculum.practiceSetId(c.id, 42))
            assertEquals(c.id, course.id)
            assertTrue(lesson.steps.isNotEmpty())
        }
    }
}
