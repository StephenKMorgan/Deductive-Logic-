package com.therefore.logic.games

import kotlin.random.Random

/** A literal: letter number [v], plain or negated. */
data class Lit(val v: Int, val positive: Boolean) {
    fun neg() = Lit(v, !positive)
}

/** Clauses (literals joined by ∨), all of which must be true: conjunctive normal form. */
data class Cnf(val letters: List<Char>, val clauses: List<List<Lit>>) {
    fun show(l: Lit) = (if (l.positive) "" else "¬") + letters[l.v]
    fun show(clause: List<Lit>) = clause.joinToString(" ∨ ") { show(it) }

    companion object {
        /** Reads clauses written like "¬P ∨ Q ∨ R". */
        fun parse(clauses: List<String>): Cnf {
            val parts = clauses.map { c -> c.split("∨").map { it.trim() } }
            val letters = parts.flatten().map { it.trimStart('¬', '~').single() }.distinct().sorted()
            return Cnf(letters, parts.map { c ->
                c.map { s -> Lit(letters.indexOf(s.trimStart('¬', '~').single()), !(s.startsWith("¬") || s.startsWith("~"))) }
            })
        }
    }
}

/** Why a letter has its value: a guess, forced by a clause, or the other value of a guess that failed. */
enum class Reason { GUESS, FORCED, FLIPPED }

data class Assigned(val lit: Lit, val reason: Reason, val clause: Int? = null)

enum class ClauseStatus { SATISFIED, CONFLICT, UNIT, OPEN }

/**
 * A DPLL search done by hand: guess a letter, set the letters that are forced, and on a conflict
 * undo the latest guess and try its other value. Running out of guesses to undo proves the clauses unsatisfiable.
 */
data class SolverState(
    val cnf: Cnf,
    val trail: List<Assigned> = emptyList(),
    val guesses: Int = 0,
    val backtracks: Int = 0,
    val unsat: Boolean = false,
) {
    val values: Map<Int, Boolean> get() = trail.associate { it.lit.v to it.lit.positive }

    fun value(l: Lit): Boolean? = values[l.v]?.let { it == l.positive }

    fun status(i: Int): ClauseStatus {
        val c = cnf.clauses[i]
        val vs = c.map { value(it) }
        return when {
            vs.any { it == true } -> ClauseStatus.SATISFIED
            vs.all { it == false } -> ClauseStatus.CONFLICT
            vs.count { it == null } == 1 -> ClauseStatus.UNIT
            else -> ClauseStatus.OPEN
        }
    }

    /** The literal a unit clause forces. */
    fun forced(i: Int): Lit? = if (status(i) == ClauseStatus.UNIT) cnf.clauses[i].first { value(it) == null } else null

    val conflict: Int? get() = cnf.clauses.indices.firstOrNull { status(it) == ClauseStatus.CONFLICT }
    val solved: Boolean get() = !unsat && cnf.clauses.indices.all { status(it) == ClauseStatus.SATISFIED }
    val units: List<Int> get() = cnf.clauses.indices.filter { status(it) == ClauseStatus.UNIT }
    val finished: Boolean get() = solved || unsat
}

object Solver {
    fun guess(s: SolverState, v: Int, value: Boolean): SolverState {
        if (s.finished || s.conflict != null || v in s.values) return s
        return s.copy(trail = s.trail + Assigned(Lit(v, value), Reason.GUESS), guesses = s.guesses + 1)
    }

    /** Sets the letter that clause [i] forces. */
    fun force(s: SolverState, i: Int): SolverState {
        if (s.finished || s.conflict != null) return s
        val l = s.forced(i) ?: return s
        return s.copy(trail = s.trail + Assigned(l, Reason.FORCED, i))
    }

    /** Sets forced letters until none are left (or a conflict appears). */
    fun propagate(s: SolverState): SolverState {
        var cur = s
        while (!cur.finished && cur.conflict == null) {
            val i = cur.units.firstOrNull() ?: break
            cur = force(cur, i)
        }
        return cur
    }

    /** Undoes back to the latest guess and gives that letter its other value. With no guess left to undo, the clauses are unsatisfiable. */
    fun backtrack(s: SolverState): SolverState {
        // Only a conflict justifies undoing: backtracking from a consistent state would "prove" anything unsatisfiable.
        if (s.finished || s.conflict == null) return s
        val i = s.trail.indexOfLast { it.reason == Reason.GUESS }
        if (i < 0) return s.copy(unsat = true)
        val flipped = Assigned(s.trail[i].lit.neg(), Reason.FLIPPED)
        return s.copy(trail = s.trail.take(i) + flipped, backtracks = s.backtracks + 1)
    }

    fun satisfiable(cnf: Cnf): Boolean {
        val n = cnf.letters.size
        return (0 until (1 shl n)).any { m -> cnf.clauses.all { c -> c.any { l -> ((m shr l.v) and 1 == 1) == l.positive } } }
    }

    /**
     * How many guesses a sensible solver needs: propagate, then guess the letter in the most open clauses,
     * trying the value that satisfies more of them first. This is the par for the Solver game.
     */
    fun par(cnf: Cnf): Int {
        var s = propagate(SolverState(cnf))
        var steps = 0
        while (!s.finished && steps++ < 10_000) {
            if (s.conflict != null) { s = propagate(backtrack(s)); continue }
            val open = cnf.clauses.indices.filter { s.status(it) == ClauseStatus.OPEN }.flatMap { i -> cnf.clauses[i].filter { s.value(it) == null } }
            val best = open.groupingBy { it }.eachCount()
            val lit = best.maxByOrNull { it.value }?.key ?: break
            s = propagate(guess(s, lit.v, lit.positive))
        }
        return s.guesses
    }

    /** Random clauses of three different letters each, with no clause repeated. */
    fun random3Sat(n: Int, m: Int, rng: Random): Cnf {
        val letters = "PQRSTUWZ".take(n).toList()
        val clauses = LinkedHashSet<Set<Lit>>()
        while (clauses.size < m) {
            val vs = (0 until n).shuffled(rng).take(3).sorted()
            clauses += vs.map { Lit(it, rng.nextBoolean()) }.toSet()
        }
        return Cnf(letters, clauses.map { it.toList() })
    }

    /** A game board: harder as more are solved. Roughly half turn out unsatisfiable, so the player can't assume either way. */
    fun board(solved: Int, rng: Random): Cnf {
        val (n, m) = when {
            solved < 3 -> 4 to 12
            solved < 8 -> 5 to 20
            solved < 15 -> 6 to 26
            else -> 7 to 30
        }
        repeat(200) {
            val cnf = random3Sat(n, m, rng)
            // Skip boards that solve themselves without a single guess.
            if (par(cnf) >= 1) return cnf
        }
        return random3Sat(n, m, rng)
    }
}
