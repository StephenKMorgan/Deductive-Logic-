package com.therefore.logic.ext

import com.therefore.logic.engine.Op

/**
 * Formulas for the "new kinds of logic" courses. One language, read three ways:
 *  - modal logic: □ necessarily, ◇ possibly, over possible worlds;
 *  - temporal logic: □ always, ◇ eventually, ○ next, U until, along a timeline;
 *  - intuitionistic logic: ordinary connectives, read over states of knowledge that only grow.
 */
sealed class X {
    data class Atom(val v: Char) : X()
    data class Not(val a: X) : X()
    data class Bin(val op: Op, val a: X, val b: X) : X()
    data class Box(val a: X) : X()
    data class Dia(val a: X) : X()
    data class Next(val a: X) : X()
    data class Until(val a: X, val b: X) : X()

    fun show(top: Boolean = true): String = when (this) {
        is Atom -> "$v"
        is Not -> "¬" + a.show(false)
        is Box -> "□" + a.show(false)
        is Dia -> "◇" + a.show(false)
        is Next -> "○" + a.show(false)
        is Bin -> "${a.show(false)} ${op.symbol} ${b.show(false)}".let { if (top) it else "($it)" }
        is Until -> "${a.show(false)} U ${b.show(false)}".let { if (top) it else "($it)" }
    }

    override fun toString() = show()

    fun atoms(into: MutableSet<Char> = sortedSetOf()): Set<Char> {
        when (this) {
            is Atom -> into += v
            is Not -> a.atoms(into)
            is Box -> a.atoms(into)
            is Dia -> a.atoms(into)
            is Next -> a.atoms(into)
            is Bin -> { a.atoms(into); b.atoms(into) }
            is Until -> { a.atoms(into); b.atoms(into) }
        }
        return into
    }
}

/** Reads formulas like "□(P → Q) → (□P → □Q)" or "P U Q". Letters P–T; mixed binary connectives need brackets. */
object XParser {
    private class Fail : Exception()

    private class Reader(val t: String) {
        var i = 0
        fun peek() = t.getOrNull(i)

        fun unary(): X {
            val c = peek() ?: throw Fail()
            i++
            return when (c) {
                '¬' -> X.Not(unary())
                '□' -> X.Box(unary())
                '◇' -> X.Dia(unary())
                '○' -> X.Next(unary())
                '(' -> binary().also { if (peek() != ')') throw Fail(); i++ }
                in 'P'..'T' -> X.Atom(c)
                else -> throw Fail()
            }
        }

        fun binary(): X {
            val a = unary()
            val c = peek() ?: return a
            val op = when (c) { '∧' -> Op.AND; '∨', 'v' -> Op.OR; '→' -> Op.IMP; '↔' -> Op.IFF; 'U' -> null; else -> return a }
            i++
            val b = unary()
            return if (op == null) X.Until(a, b) else X.Bin(op, a, b)
        }
    }

    fun parse(s: String): X? = try {
        val t = s.replace("<->", "↔").replace("->", "→").replace("[]", "□").replace("<>", "◇")
            .replace('~', '¬').replace('&', '∧').replace('|', '∨').filter { !it.isWhitespace() }
        val r = Reader(t)
        val f = r.binary()
        if (r.i != t.length) null else f
    } catch (e: Fail) { null }

    fun of(s: String): X = parse(s) ?: error("Bad formula: $s")
}

/** A Kripke model: worlds 0 until [n], arrows between them, and which letters are true where. */
data class Kripke(val n: Int, val arrows: Set<Pair<Int, Int>>, val truth: Map<Char, Set<Int>>) {
    fun sees(i: Int, j: Int) = (i to j) in arrows
    fun has(p: Char, w: Int) = w in (truth[p] ?: emptySet())

    val reflexive get() = (0 until n).all { sees(it, it) }
    val symmetric get() = arrows.all { (a, b) -> sees(b, a) }
    val transitive get() = arrows.all { (a, b) -> arrows.filter { it.first == b }.all { (_, c) -> sees(a, c) } }

    /** Everything reachable from [w] in zero or more steps, [w] included: "now or later" in intuitionistic logic. */
    fun later(w: Int): Set<Int> {
        val seen = mutableSetOf(w)
        val todo = ArrayDeque(listOf(w))
        while (todo.isNotEmpty()) { val v = todo.removeFirst(); for (j in 0 until n) if (sees(v, j) && seen.add(j)) todo += j }
        return seen
    }

    /** In intuitionistic logic what you've established stays established: a letter true at a world is true at every later one. */
    fun persistenceBreak(): Pair<Char, Pair<Int, Int>>? {
        for ((p, ws) in truth) for (w in ws) for (v in later(w)) if (v !in ws) return p to (w to v)
        return null
    }
}

enum class FrameProp(val label: String) { REFLEXIVE("reflexive"), SYMMETRIC("symmetric"), TRANSITIVE("transitive") }

fun Kripke.satisfies(p: FrameProp) = when (p) {
    FrameProp.REFLEXIVE -> reflexive
    FrameProp.SYMMETRIC -> symmetric
    FrameProp.TRANSITIVE -> transitive
}

object Semantics {
    /** Modal logic: □A holds at w when A holds at every world w sees; ◇A when at some. */
    fun modal(f: X, m: Kripke, w: Int): Boolean = when (f) {
        is X.Atom -> m.has(f.v, w)
        is X.Not -> !modal(f.a, m, w)
        is X.Bin -> classical(f.op, modal(f.a, m, w), modal(f.b, m, w))
        is X.Box -> (0 until m.n).filter { m.sees(w, it) }.all { modal(f.a, m, it) }
        is X.Dia -> (0 until m.n).filter { m.sees(w, it) }.any { modal(f.a, m, it) }
        is X.Next, is X.Until -> error("Temporal operator in a modal formula")
    }

    /**
     * Intuitionistic logic (Kripke's reading): a world is a state of knowledge, arrows lead to later states.
     * ¬A holds when A never becomes established later; A → B when every later state with A also has B.
     */
    fun intuitionistic(f: X, m: Kripke, w: Int): Boolean = when (f) {
        is X.Atom -> m.has(f.v, w)
        is X.Not -> m.later(w).none { intuitionistic(f.a, m, it) }
        is X.Bin -> when (f.op) {
            Op.AND -> intuitionistic(f.a, m, w) && intuitionistic(f.b, m, w)
            Op.OR -> intuitionistic(f.a, m, w) || intuitionistic(f.b, m, w)
            Op.IMP -> m.later(w).all { !intuitionistic(f.a, m, it) || intuitionistic(f.b, m, it) }
            Op.IFF -> m.later(w).all { intuitionistic(f.a, m, it) == intuitionistic(f.b, m, it) }
        }
        else -> error("Modal operator in an intuitionistic formula")
    }

    /**
     * Temporal logic on a finite timeline (moments 0 until trace.size): □A, A at every moment from now on;
     * ◇A, at some moment from now on; ○A, at the next moment (false at the last); A U B, B at some moment and A until then.
     */
    fun temporal(f: X, trace: List<Set<Char>>, t: Int): Boolean = when (f) {
        is X.Atom -> f.v in trace[t]
        is X.Not -> !temporal(f.a, trace, t)
        is X.Bin -> classical(f.op, temporal(f.a, trace, t), temporal(f.b, trace, t))
        is X.Box -> (t until trace.size).all { temporal(f.a, trace, it) }
        is X.Dia -> (t until trace.size).any { temporal(f.a, trace, it) }
        is X.Next -> t + 1 < trace.size && temporal(f.a, trace, t + 1)
        is X.Until -> (t until trace.size).any { k -> temporal(f.b, trace, k) && (t until k).all { temporal(f.a, trace, it) } }
    }

    private fun classical(op: Op, a: Boolean, b: Boolean) = when (op) {
        Op.AND -> a && b
        Op.OR -> a || b
        Op.IMP -> !a || b
        Op.IFF -> a == b
    }
}
