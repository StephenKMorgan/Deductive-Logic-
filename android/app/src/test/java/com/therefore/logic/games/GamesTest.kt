package com.therefore.logic.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GamesTest {
    @Test fun islandPuzzlesHaveExactlyOneAnswer() {
        val rng = Random(1)
        for (people in 2..4) repeat(60) {
            val p = TruthTellers.generate(people, rng)
            val atoms = p.atoms
            val solutions = assignments(atoms).filter { v -> p.statements.all { p.constraint(it).eval(v) } }.toList()
            assertEquals("one solution", 1, solutions.size)
            assertEquals(p.solution, atoms.map { solutions[0].getValue(it) })
            assertNull("the right answer has no mistake", TruthTellers.mistake(p, p.solution))
            // Every wrong answer is explained.
            for (m in 0 until (1 shl people)) {
                val answer = (0 until people).map { m and (1 shl it) != 0 }
                if (answer != p.solution) assertNotNull(TruthTellers.mistake(p, answer))
            }
            assertEquals(people, TruthTellers.explanation(p).size)
        }
    }

    @Test fun validOrNotAnswersAreRight() {
        val rng = Random(2)
        var yes = 0
        repeat(400) { round ->
            val q = ValidOrNot.next(rng, round)
            when (q) {
                is Question.Argument -> {
                    val ce = ValidOrNot.counterexample(q.premises, q.conclusion)
                    assertEquals("argument ${q.premises} ∴ ${q.conclusion}", ce == null, q.answer)
                }
                is Question.Equivalent -> {
                    val diff = ValidOrNot.difference(q.left, q.right)
                    assertEquals("pair ${q.left} / ${q.right}", diff == null, q.answer)
                }
            }
            if (q.answer) { yes++; assertNull(q.counterexample) } else {
                val v = q.counterexample!!
                when (q) {
                    is Question.Argument -> assertTrue(q.premises.all { it.eval(v) } && !q.conclusion.eval(v))
                    is Question.Equivalent -> assertTrue(q.left.eval(v) != q.right.eval(v))
                }
            }
        }
        assertTrue("a fair mix of yes and no", yes in 120..280)
    }

    @Test fun switchboardsHaveExactlyOneSolution() {
        val rng = Random(3)
        for (n in 3..6) repeat(40) {
            val b = Switchboard.generate(n, rng)
            val models = assignments(b.letters).filter { v -> b.constraints.all { it.eval(v) } }.toList()
            assertEquals(1, models.size)
            assertEquals(b.solution, models[0])
            assertFalse("all switches off isn't the answer", b.constraints.all { it.eval(b.letters.associateWith { false }) })
            assertTrue(b.constraints.size <= n + 3)
        }
    }
}
