package com.therefore.logic.games

import com.therefore.logic.content.Curriculum
import com.therefore.logic.engine.Checker
import com.therefore.logic.engine.Formula
import com.therefore.logic.engine.Generator
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.and
import com.therefore.logic.engine.atom
import com.therefore.logic.engine.iff
import com.therefore.logic.engine.imp
import com.therefore.logic.engine.not
import com.therefore.logic.engine.or
import kotlin.random.Random

/** Every assignment of true/false to [letters]. */
fun assignments(letters: List<Char>): Sequence<Map<Char, Boolean>> = sequence {
    for (m in 0 until (1 shl letters.size)) yield(letters.withIndex().associate { (i, l) -> l to (m and (1 shl i) != 0) })
}

fun letters(fs: List<Formula>) = fs.flatMap { it.atoms() }.distinct().sorted()

// ======================= Truth-tellers & Liars =======================

/** What one islander says, in words and as a formula about who tells the truth (atom = "is a truth-teller"). */
data class Statement(val speaker: Int, val text: String, val formula: Formula)

data class IslandPuzzle(val names: List<String>, val statements: List<Statement>, val solution: List<Boolean>) {
    val atoms get() = names.indices.map { 'A' + it }
    /** The speaker tells the truth exactly when the statement is true. */
    fun constraint(s: Statement): Formula = iff(atom(atoms[s.speaker]), s.formula)
}

object TruthTellers {
    private val NAMES = listOf("Ada", "Bo", "Cy", "Dee", "Eli", "Fay", "Gus", "Ivy", "Jo", "Kit")

    fun generate(people: Int, rng: Random): IslandPuzzle {
        repeat(5000) {
            val names = NAMES.shuffled(rng).take(people)
            val statements = names.indices.map { statementFor(it, names, rng) }
            val puzzle = IslandPuzzle(names, statements, emptyList())
            val atoms = puzzle.atoms
            val solutions = assignments(atoms).filter { v -> statements.all { puzzle.constraint(it).eval(v) } }.take(2).toList()
            if (solutions.size == 1) return puzzle.copy(solution = atoms.map { solutions[0].getValue(it) })
        }
        error("No island puzzle found")
    }

    private fun statementFor(i: Int, names: List<String>, rng: Random): Statement {
        val others = names.indices.filter { it != i }.shuffled(rng)
        val j = others[0]
        val k = others.getOrNull(1)
        val me = atom('A' + i)
        val a = atom('A' + j)
        val nj = names[j]
        val options = mutableListOf(
            "$nj is a liar." to not(a),
            "$nj is a truth-teller." to a,
            "$nj and I are both liars." to and(not(me), not(a)),
            "At least one of $nj and me is a liar." to or(not(me), not(a)),
            "$nj and I are the same kind." to iff(me, a),
            "If I'm a truth-teller, then so is $nj." to imp(me, a),
        )
        if (k != null) {
            val b = atom('A' + k)
            val nk = names[k]
            options += listOf(
                "$nj and $nk are the same kind." to iff(a, b),
                "Exactly one of $nj and $nk is a truth-teller." to not(iff(a, b)),
                "$nj is a liar, or $nk is a truth-teller." to or(not(a), b),
                "$nj and $nk are both truth-tellers." to and(a, b),
                "If $nj is a truth-teller, then $nk is a liar." to imp(a, not(b)),
            )
        }
        val (text, f) = options[rng.nextInt(options.size)]
        return Statement(i, text, f)
    }

    /** Why an answer is wrong: the first islander whose statement doesn't fit the answer. */
    fun mistake(p: IslandPuzzle, answer: List<Boolean>): String? {
        val v = p.atoms.withIndex().associate { (i, a) -> a to answer[i] }
        for (s in p.statements) {
            val name = p.names[s.speaker]
            val truthful = answer[s.speaker]
            val holds = s.formula.eval(v)
            if (truthful && !holds) return "You have $name as a truth-teller, but with your answer, what $name said (\"${s.text}\") would be false."
            if (!truthful && holds) return "You have $name as a liar, but with your answer, what $name said (\"${s.text}\") would be true. Liars never say anything true."
        }
        return null
    }

    /** One line per islander explaining the solution. */
    fun explanation(p: IslandPuzzle): List<String> = p.statements.map { s ->
        val name = p.names[s.speaker]
        if (p.solution[s.speaker]) "$name tells the truth, and \"${s.text}\" is true."
        else "$name is a liar, and \"${s.text}\" is false."
    }
}

// ======================= Valid or Not? =======================

sealed interface Question {
    val answer: Boolean
    /** A row of the truth table showing why the answer is "no" (null when the answer is "yes"). */
    val counterexample: Map<Char, Boolean>?
    val explanation: String

    data class Argument(val premises: List<Formula>, val conclusion: Formula, override val answer: Boolean, override val counterexample: Map<Char, Boolean>?, override val explanation: String) : Question
    data class Equivalent(val left: Formula, val right: Formula, override val answer: Boolean, override val counterexample: Map<Char, Boolean>?, override val explanation: String) : Question
}

object ValidOrNot {
    private val P = atom('P'); private val Q = atom('Q'); private val R = atom('R')

    fun describe(v: Map<Char, Boolean>) = v.entries.sortedBy { it.key }.joinToString(", ") { "${it.key} ${if (it.value) "true" else "false"}" }

    fun counterexample(premises: List<Formula>, conclusion: Formula) =
        assignments(letters(premises + conclusion)).firstOrNull { v -> premises.all { it.eval(v) } && !conclusion.eval(v) }

    fun difference(a: Formula, b: Formula) = assignments(letters(listOf(a, b))).firstOrNull { a.eval(it) != b.eval(it) }

    fun next(rng: Random, round: Int): Question = if (rng.nextBoolean()) argument(rng, round) else equivalence(rng)

    private fun argument(rng: Random, round: Int): Question {
        val valid = rng.nextBoolean()
        // Classic invalid forms appear alongside generated ones.
        if (!valid && rng.nextDouble() < 0.5) {
            val (prem, concl, name) = listOf(
                Triple(listOf(imp(P, Q), Q), P, "affirming the consequent"),
                Triple(listOf(imp(P, Q), not(P)), not(Q), "denying the antecedent"),
                Triple(listOf(or(P, Q), P), not(Q), "\"or\" is inclusive: both can be true"),
                Triple(listOf(imp(P, Q), imp(R, Q)), imp(P, R), "two causes of Q don't link P to R"),
                Triple(listOf(not(and(P, Q))), not(P), "\"not both\" doesn't say which one is false"),
            )[rng.nextInt(5)]
            val ce = counterexample(prem, concl)!!
            return Question.Argument(prem, concl, false, ce, "Invalid: $name. Counterexample: ${describe(ce)}.")
        }
        val course = listOf("ifthen", "chains", "andor", "rearrange", "demorgan")[rng.nextInt(minOf(5, 2 + round / 4))]
        val puzzle = Generator.generate(Curriculum.byId(course).spec, 1, rng.nextLong())
        if (valid) return Question.Argument(puzzle.premises, puzzle.conclusion, true, null, "Valid: it can be proved, so no row makes the premises true and the conclusion false.")
        // Swap in a conclusion that doesn't follow.
        val ls = letters(puzzle.premises).map(::atom)
        repeat(50) {
            val c = small(rng, ls)
            val ce = counterexample(puzzle.premises, c)
            if (ce != null && assignments(letters(listOf(c))).any { !c.eval(it) }) return Question.Argument(puzzle.premises, c, false, ce, "Invalid. Counterexample: ${describe(ce)}.")
        }
        return argument(rng, round)
    }

    private fun small(rng: Random, ls: List<Formula>): Formula {
        fun lit() = ls[rng.nextInt(ls.size)].let { if (rng.nextDouble() < 0.3) not(it) else it }
        return when (rng.nextInt(4)) {
            0 -> lit()
            1 -> imp(lit(), lit())
            2 -> or(lit(), lit())
            else -> and(lit(), lit())
        }
    }

    private fun equivalence(rng: Random): Question {
        val base = listOf(
            imp(P, Q), not(and(P, Q)), not(or(P, Q)), or(P, Q), imp(and(P, Q), R), and(P, or(Q, R)), imp(P, not(Q)), or(not(P), Q),
        )[rng.nextInt(8)]
        if (rng.nextBoolean()) {
            // A genuine equivalent: one replacement rule applied somewhere.
            val rules = listOf(Rule.DeM, Rule.Impl, Rule.Trans, Rule.Com, Rule.Dist, Rule.Exp, Rule.DN)
            for (r in rules.shuffled(rng)) {
                val out = Checker.rootRewrites(r, base).filter { it != base && it.size <= base.size + 2 }
                if (out.isNotEmpty()) return Question.Equivalent(base, out[rng.nextInt(out.size)], true, null, "Equivalent, by ${r.title}.")
            }
        }
        // A tempting near miss.
        val misses = mutableListOf<Pair<Formula, String>>()
        val b = base as? Formula.Bin
        if (b != null && b.op == com.therefore.logic.engine.Op.IMP) {
            misses += imp(b.b, b.a) to "the converse"
            misses += imp(not(b.a), not(b.b)) to "the inverse"
        }
        if (base is Formula.Not && base.a is Formula.Bin) {
            val inner = base.a
            misses += Formula.Bin(inner.op, not(inner.a), not(inner.b)) to "De Morgan without flipping ∧ and ∨"
        }
        if (b != null && b.op == com.therefore.logic.engine.Op.OR) misses += and(b.a, b.b) to "\"or\" changed to \"and\""
        misses += not(base) to "the opposite"
        val (wrong, why) = misses[rng.nextInt(misses.size)]
        val diff = difference(base, wrong) ?: return equivalence(rng)
        return Question.Equivalent(base, wrong, false, diff, "Not equivalent ($why). They differ when ${describe(diff)}.")
    }
}

// ======================= Switchboard =======================

data class Board(val letters: List<Char>, val constraints: List<Formula>, val solution: Map<Char, Boolean>)

object Switchboard {
    fun generate(switches: Int, rng: Random): Board {
        val ls = "PQRSTU".take(switches).toList()
        repeat(2000) {
            val hidden = ls.associateWith { rng.nextBoolean() }.let { h -> if (h.values.none { it }) h + (ls[rng.nextInt(ls.size)] to true) else h }
            val constraints = ArrayList<Formula>()
            var models = assignments(ls).toList()
            while (models.size > 1 && constraints.size <= switches + 2) {
                val c = constraint(rng, ls)
                if (!c.eval(hidden)) continue
                val remaining = models.filter { c.eval(it) }
                if (remaining.size == models.size) continue // adds nothing
                constraints += c
                models = remaining
            }
            if (models.size == 1) return Board(ls, constraints.shuffled(rng), hidden)
        }
        error("No board found")
    }

    private fun constraint(rng: Random, ls: List<Char>): Formula {
        val pick = ls.shuffled(rng)
        fun lit(i: Int) = atom(pick[i % pick.size]).let { if (rng.nextDouble() < 0.35) not(it) else it }
        return when (rng.nextInt(7)) {
            0 -> imp(lit(0), lit(1))
            1 -> or(lit(0), lit(1))
            2 -> not(and(atom(pick[0]), atom(pick[1])))
            3 -> iff(lit(0), lit(1))
            4 -> imp(and(lit(0), lit(1)), lit(2))
            5 -> or(lit(0), and(lit(1), lit(2)))
            else -> lit(0)
        }
    }
}
