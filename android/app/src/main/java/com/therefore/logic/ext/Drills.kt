package com.therefore.logic.ext

import com.therefore.logic.content.KLogic
import com.therefore.logic.content.LessonStep
import com.therefore.logic.content.WorldsGoal
import com.therefore.logic.engine.Formula
import com.therefore.logic.engine.Op
import com.therefore.logic.engine.atom
import com.therefore.logic.engine.not
import com.therefore.logic.engine.satisfiable
import kotlin.random.Random

/** Generated practice sets for the courses that aren't practised with proofs. */
object Drills {
    // ---------------- Truth trees ----------------

    private fun literal(rng: Random, letters: String): Formula = atom(letters[rng.nextInt(letters.length)]).let { if (rng.nextDouble() < 0.3) not(it) else it }

    private fun small(rng: Random, letters: String, depth: Int): Formula {
        if (depth == 0) return literal(rng, letters)
        val op = listOf(Op.AND, Op.OR, Op.IMP, Op.IMP, Op.IFF).let { if (rng.nextDouble() < 0.85) it.dropLast(1) else it }[rng.nextInt(4)]
        val f = Formula.Bin(op, small(rng, letters, depth - 1), small(rng, letters, if (rng.nextBoolean()) 0 else depth - 1))
        return if (rng.nextDouble() < 0.15) not(f) else f
    }

    /** Builds the whole tree mechanically: stacks first, closing branches as soon as they can be. */
    fun autoSolve(start: TreeState): TreeState {
        var s = start
        repeat(200) {
            s.leaves.firstOrNull { s.open(it) && s.closable(it) != null }?.let { s = Trees.close(s, it); return@repeat }
            if (s.finished) return s
            val todo = s.nodes.filter { n ->
                n.id !in s.decomposed && Trees.shape(n.f) != Shape.LITERAL && s.leaves.any { l -> s.open(l) && s.branch(l).any { it.id == n.id } }
            }
            val next = todo.firstOrNull { Trees.shape(it.f) == Shape.STACK } ?: todo.firstOrNull() ?: return s
            s = Trees.decompose(s, next.id, Trees.shape(next.f))
        }
        return s
    }

    fun treeStep(rng: Random, wantValid: Boolean): LessonStep.TreeStep {
        repeat(500) {
            val letters = "PQR".take(2 + rng.nextInt(2))
            val premises = List(1 + rng.nextInt(2)) { small(rng, letters, 1) }
            val conclusion = small(rng, letters, rng.nextInt(2))
            if (conclusion in premises || !satisfiable(premises) || !satisfiable(listOf(not(conclusion)))) return@repeat
            val valid = !satisfiable(premises + not(conclusion))
            if (valid != wantValid) return@repeat
            val done = autoSolve(Trees.start(premises, conclusion))
            // Keep trees phone-sized.
            if (!done.finished || done.leaves.size > 6 || done.nodes.size > 22) return@repeat
            return LessonStep.TreeStep(
                "Tree", "Is this argument valid? Build the tree and decide.",
                premises.map { it.show() }, conclusion.show(), valid,
                success = if (valid) "Valid: every branch closed." else "Invalid: the open branch is a counterexample.",
            )
        }
        return LessonStep.TreeStep("Tree", "Is this argument valid?", listOf("P → Q", "P"), "Q", true, "Valid: every branch closed.")
    }

    fun trees(rng: Random): List<LessonStep> = listOf(true, false, true, false).shuffled(rng).map { treeStep(rng, it) }

    // ---------------- Possible worlds ----------------

    private val MODAL_POOL = listOf(
        "□P", "◇P", "□¬P", "◇P ∧ ◇¬P", "□(P ∨ Q)", "□P ∨ □Q", "◇(P ∧ Q)", "◇P ∧ ◇Q", "□P → ◇P",
        "□(P → Q)", "◇P ∧ □Q", "¬◇Q", "□(P ↔ Q)", "◇¬P ∧ □Q",
    )

    /** Every way to switch the letters on and off in [n] worlds. */
    private fun valuations(letters: List<Char>, n: Int): Sequence<Map<Char, Set<Int>>> = sequence {
        val bits = letters.size * n
        for (mask in 0 until (1 shl bits)) yield(letters.withIndex().associate { (i, l) -> l to (0 until n).filter { w -> (mask shr (i * n + w)) and 1 == 1 }.toSet() })
    }

    /** Whether some letter switching meets the goal with these fixed arrows. */
    fun kripkeFeasible(formulas: List<String>, goal: WorldsGoal, logic: KLogic, n: Int, arrows: Set<Pair<Int, Int>>): Boolean {
        val xs = formulas.map(XParser::of)
        val letters = xs.flatMap { it.atoms() }.distinct()
        return valuations(letters, n).any { t ->
            val m = Kripke(n, arrows, t)
            if (logic == KLogic.INTUITIONISTIC && m.persistenceBreak() != null) return@any false
            val v = xs.map { if (logic == KLogic.MODAL) Semantics.modal(it, m, 0) else Semantics.intuitionistic(it, m, 0) }
            if (goal == WorldsGoal.ALL_TRUE) v.all { it } else !v[0]
        }
    }

    fun modalStep(rng: Random): LessonStep.KripkeStep {
        repeat(300) {
            val f = MODAL_POOL[rng.nextInt(MODAL_POOL.size)]
            val n = 2 + rng.nextInt(2)
            val arrows = (0 until n).flatMap { i -> (0 until n).map { j -> i to j } }.filter { rng.nextDouble() < 0.4 }.toSet()
            if ((0 until n).none { (0 to it) in arrows }) return@repeat // w1 should see something
            val goal = if (rng.nextBoolean()) WorldsGoal.ALL_TRUE else WorldsGoal.FALSE
            val x = XParser.of(f)
            val empty = Kripke(n, arrows, x.atoms().associateWith { emptySet() })
            val already = Semantics.modal(x, empty, 0).let { if (goal == WorldsGoal.ALL_TRUE) it else !it }
            if (already || !kripkeFeasible(listOf(f), goal, KLogic.MODAL, n, arrows)) return@repeat
            return LessonStep.KripkeStep(
                "Worlds", if (goal == WorldsGoal.ALL_TRUE) "The arrows are fixed. Switch letters to make $f true at w1." else "The arrows are fixed. Switch letters to make $f false at w1.",
                n, listOf(f), goal, arrows = arrows, fixedArrows = true, success = "Done.",
            )
        }
        return LessonStep.KripkeStep("Worlds", "Make □P true at w1.", 2, listOf("□P"), WorldsGoal.ALL_TRUE, arrows = setOf(0 to 1), fixedArrows = true, success = "Done.")
    }

    fun modal(rng: Random): List<LessonStep> = List(5) { modalStep(rng) }

    // ---------------- Timelines ----------------

    private val TIME_POOL = listOf(
        "□P", "◇P", "○P", "○○P", "P U Q", "□(P → ○Q)", "◇(P ∧ Q)", "□P ∨ □Q", "◇P ∧ ¬◇Q", "¬P U Q",
        "□(P → ◇Q)", "□(P → Q)", "◇P ∧ ○¬P", "○(P U Q)", "□¬(P ∧ Q)",
    )

    fun timelineFeasible(formulas: List<String>, goal: WorldsGoal, moments: Int): Boolean {
        val xs = formulas.map(XParser::of)
        val letters = xs.flatMap { it.atoms() }.distinct()
        val bits = letters.size * moments
        return (0 until (1 shl bits)).any { mask ->
            val trace = List(moments) { t -> letters.withIndex().filter { (i, _) -> (mask shr (i * moments + t)) and 1 == 1 }.map { it.value }.toSet() }
            val v = xs.map { Semantics.temporal(it, trace, 0) }
            if (goal == WorldsGoal.ALL_TRUE) v.all { it } else !v[0]
        }
    }

    fun timelineStep(rng: Random): LessonStep.TimelineStep {
        repeat(300) {
            val f = TIME_POOL[rng.nextInt(TIME_POOL.size)]
            val goal = if (rng.nextBoolean()) WorldsGoal.ALL_TRUE else WorldsGoal.FALSE
            val x = XParser.of(f)
            val letters = x.atoms().toList()
            val start = List(5) { letters.filter { rng.nextDouble() < 0.4 }.joinToString("") }
            val already = Semantics.temporal(x, start.map { it.toSet() }, 0).let { if (goal == WorldsGoal.ALL_TRUE) it else !it }
            if (already || !timelineFeasible(listOf(f), goal, 5)) return@repeat
            return LessonStep.TimelineStep(
                "Timeline", if (goal == WorldsGoal.ALL_TRUE) "Change the timeline so $f is true now." else "Change the timeline so $f is false now.",
                5, listOf(f), goal, start, success = "Done.",
            )
        }
        return LessonStep.TimelineStep("Timeline", "Make □P true now.", 5, listOf("□P"), WorldsGoal.ALL_TRUE, success = "Done.")
    }

    fun temporal(rng: Random): List<LessonStep> = List(5) { timelineStep(rng) }

    // ---------------- Constructive logic ----------------

    /** A formula, whether constructive logic proves it, why, and (if it fails) a countermodel's states and arrows. */
    data class Claim(val formula: String, val valid: Boolean, val why: String, val worlds: Int = 2, val arrows: Set<Pair<Int, Int>> = setOf(0 to 1))

    val CLAIMS = listOf(
        Claim("P → ¬¬P", true, "If you have P, any refutation of P would give a contradiction."),
        Claim("¬¬P → P", false, "Knowing P can't be refuted isn't the same as having P."),
        Claim("P ∨ ¬P", false, "You'd need to prove P or refute it, and you may be able to do neither yet."),
        Claim("¬(P ∧ ¬P)", true, "A proof of P together with a refutation of P gives a contradiction."),
        Claim("¬(P ∨ Q) → (¬P ∧ ¬Q)", true, "A refutation of \"P or Q\" refutes each side."),
        Claim("(¬P ∧ ¬Q) → ¬(P ∨ Q)", true, "Refuting both sides refutes \"P or Q\"."),
        Claim("¬(P ∧ Q) → (¬P ∨ ¬Q)", false, "You can know P and Q never both hold without knowing which one fails.", 3, setOf(0 to 1, 0 to 2)),
        Claim("(P → Q) → (¬Q → ¬P)", true, "Modus Tollens: turn a refutation of Q into a refutation of P."),
        Claim("(¬Q → ¬P) → (P → Q)", false, "Refutations running backward don't build a proof of Q.", 3, setOf(0 to 1, 1 to 2)),
        Claim("(P → Q) → (¬P ∨ Q)", false, "A method for getting Q from P doesn't tell you which of ¬P or Q holds."),
        Claim("¬¬¬P → ¬P", true, "Three negations reduce to one, constructively too."),
        Claim("((P → Q) → P) → P", false, "This is Peirce's law, a classical principle with no constructive proof."),
    )

    fun intuitionistic(rng: Random): List<LessonStep> {
        val picks = CLAIMS.shuffled(rng).take(4)
        val quizzes = picks.map { c ->
            LessonStep.Quiz(
                "Constructive?", "Is ${c.formula} provable in constructive logic?",
                listOf("Yes", "No"), if (c.valid) 0 else 1,
                (if (c.valid) "Yes. " else "No (classically it is). ") + c.why,
            )
        }
        val bad = CLAIMS.filter { !it.valid }.let { it[rng.nextInt(it.size)] }
        val counter = LessonStep.KripkeStep(
            "Countermodel", "Make ${bad.formula} fail at w1.", bad.worlds, listOf(bad.formula), WorldsGoal.FALSE, KLogic.INTUITIONISTIC,
            arrows = bad.arrows, fixedArrows = true, success = bad.why,
        )
        return quizzes + counter
    }
}
