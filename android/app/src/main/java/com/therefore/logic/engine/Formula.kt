package com.therefore.logic.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

enum class Op(val symbol: Char) { AND('∧'), OR('∨'), IMP('→'), IFF('↔') }

enum class Quantifier(val symbol: Char) { ALL('∀'), SOME('∃') }

/** Variables stand for "any thing"; names (constants) pick out one particular thing. */
fun isVariable(c: Char) = c in "xyz"
fun isName(c: Char) = c in 'a'..'w' && c != 'v' // lowercase v is "or"
fun isTerm(c: Char) = isVariable(c) || isName(c)

/**
 * A formula of propositional or predicate logic. Data classes give structural equality, so two
 * formulas are equal exactly when they are written the same way.
 */
@Serializable(with = FormulaSerializer::class)
sealed class Formula {
    // Data classes generate their own toString(), so each one must point back to show().
    data class Atom(val v: Char) : Formula() { override fun toString() = show() }
    data class Not(val a: Formula) : Formula() { override fun toString() = show() }
    data class Bin(val op: Op, val a: Formula, val b: Formula) : Formula() { override fun toString() = show() }
    /** A one-place predicate applied to a term: Fx, Ga. */
    data class Pred(val p: Char, val term: Char) : Formula() { override fun toString() = show() }
    /** ∀x B or ∃x B. */
    data class Quant(val q: Quantifier, val v: Char, val body: Formula) : Formula() { override fun toString() = show() }

    /** Fully bracketed except at the top level, e.g. `(P ∧ Q) → R`, `∀x(Fx → Gx)`. */
    fun show(top: Boolean = true): String = when (this) {
        is Atom -> v.toString()
        is Pred -> "$p$term"
        is Not -> "¬" + a.show(false)
        is Quant -> "${q.symbol}$v" + (if (body is Bin) "" else " ") + body.show(false)
        is Bin -> {
            val s = "${a.show(false)} ${op.symbol} ${b.show(false)}"
            if (top) s else "($s)"
        }
    }

    val size: Int
        get() = when (this) {
            is Atom, is Pred -> 1
            is Not -> 1 + a.size
            is Quant -> 1 + body.size
            is Bin -> 1 + a.size + b.size
        }

    /** Statement letters (propositional atoms). */
    fun atoms(into: MutableSet<Char> = sortedSetOf()): Set<Char> {
        when (this) {
            is Atom -> into.add(v)
            is Pred -> {}
            is Not -> a.atoms(into)
            is Quant -> body.atoms(into)
            is Bin -> { a.atoms(into); b.atoms(into) }
        }
        return into
    }

    /** Predicate letters. */
    fun predicates(into: MutableSet<Char> = sortedSetOf()): Set<Char> {
        when (this) {
            is Atom -> {}
            is Pred -> into.add(p)
            is Not -> a.predicates(into)
            is Quant -> body.predicates(into)
            is Bin -> { a.predicates(into); b.predicates(into) }
        }
        return into
    }

    /** Every term written anywhere (names and variables, bound or free). */
    fun terms(into: MutableSet<Char> = sortedSetOf()): Set<Char> {
        when (this) {
            is Atom -> {}
            is Pred -> into.add(term)
            is Not -> a.terms(into)
            is Quant -> { into.add(v); body.terms(into) }
            is Bin -> { a.terms(into); b.terms(into) }
        }
        return into
    }

    fun names(): Set<Char> = terms().filter(::isName).toSet()

    fun freeVariables(): Set<Char> = when (this) {
        is Atom -> emptySet()
        is Pred -> if (isVariable(term)) setOf(term) else emptySet()
        is Not -> a.freeVariables()
        is Quant -> body.freeVariables() - v
        is Bin -> a.freeVariables() + b.freeVariables()
    }

    /** Replaces the free occurrences of variable [v] with term [t]. */
    fun subst(v: Char, t: Char): Formula = when (this) {
        is Atom -> this
        is Pred -> if (term == v) copy(term = t) else this
        is Not -> Not(a.subst(v, t))
        is Quant -> if (this.v == v) this else copy(body = body.subst(v, t))
        is Bin -> copy(a = a.subst(v, t), b = b.subst(v, t))
    }

    val isPredicateLogic: Boolean get() = when (this) {
        is Atom -> false
        is Pred, is Quant -> true
        is Not -> a.isPredicateLogic
        is Bin -> a.isPredicateLogic || b.isPredicateLogic
    }

    fun eval(v: Map<Char, Boolean>): Boolean = when (this) {
        is Atom -> v.getValue(this.v)
        is Not -> !a.eval(v)
        is Bin -> when (op) {
            Op.AND -> a.eval(v) && b.eval(v)
            Op.OR -> a.eval(v) || b.eval(v)
            Op.IMP -> !a.eval(v) || b.eval(v)
            Op.IFF -> a.eval(v) == b.eval(v)
        }
        is Pred, is Quant -> error("Predicate formulas need a world to be evaluated in")
    }

    /** Truth in a small world: [has] says whether thing i has property p; names point at things. */
    fun eval(world: World, env: Map<Char, Int> = emptyMap()): Boolean = when (this) {
        is Atom -> world.atoms[v] ?: false
        is Pred -> world.has(p, env[term] ?: world.names[term] ?: 0)
        is Not -> !a.eval(world, env)
        is Bin -> {
            val x = a.eval(world, env)
            val y = b.eval(world, env)
            when (op) { Op.AND -> x && y; Op.OR -> x || y; Op.IMP -> !x || y; Op.IFF -> x == y }
        }
        is Quant -> (0 until world.size).let { all ->
            if (q == Quantifier.ALL) all.all { body.eval(world, env + (v to it)) } else all.any { body.eval(world, env + (v to it)) }
        }
    }
}

/** A tiny model: [size] things, which properties each has, and which thing each name points at. */
data class World(val size: Int, val props: Map<Char, Set<Int>>, val names: Map<Char, Int> = emptyMap(), val atoms: Map<Char, Boolean> = emptyMap()) {
    fun has(p: Char, thing: Int) = thing in (props[p] ?: emptySet())
}

/** Whether predicate formulas can all be true together, searching worlds of up to [maxSize] things. */
fun satisfiableInSomeWorld(fs: List<Formula>, maxSize: Int = 2): Boolean {
    val preds = fs.flatMap { it.predicates() }.distinct()
    val names = fs.flatMap { it.names() }.distinct()
    for (size in 1..maxSize) {
        val propBits = preds.size * size
        if (propBits > 12) continue
        for (m in 0 until (1 shl propBits)) {
            val props = preds.withIndex().associate { (pi, p) -> p to (0 until size).filter { m and (1 shl (pi * size + it)) != 0 }.toSet() }
            var assign = 0
            val total = Math.pow(size.toDouble(), names.size.toDouble()).toInt()
            while (assign < total) {
                var a = assign
                val nm = names.associateWith { val t = a % size; a /= size; t }
                if (fs.all { it.eval(World(size, props, nm)) }) return true
                assign++
            }
        }
    }
    return false
}

fun atom(c: Char): Formula = Formula.Atom(c)
fun not(a: Formula): Formula = Formula.Not(a)
fun and(a: Formula, b: Formula): Formula = Formula.Bin(Op.AND, a, b)
fun or(a: Formula, b: Formula): Formula = Formula.Bin(Op.OR, a, b)
fun imp(a: Formula, b: Formula): Formula = Formula.Bin(Op.IMP, a, b)
fun iff(a: Formula, b: Formula): Formula = Formula.Bin(Op.IFF, a, b)
fun pred(p: Char, t: Char): Formula = Formula.Pred(p, t)
fun all(v: Char, body: Formula): Formula = Formula.Quant(Quantifier.ALL, v, body)
fun some(v: Char, body: Formula): Formula = Formula.Quant(Quantifier.SOME, v, body)

val Formula.isAnd get() = this is Formula.Bin && op == Op.AND
val Formula.isOr get() = this is Formula.Bin && op == Op.OR
val Formula.isImp get() = this is Formula.Bin && op == Op.IMP
val Formula.isNot get() = this is Formula.Not

/** True when the formulas can all be true at once (truth table over at most 6 letters). */
fun satisfiable(fs: List<Formula>): Boolean {
    if (fs.any { it.isPredicateLogic }) return satisfiableInSomeWorld(fs)
    val letters = fs.flatMap { it.atoms() }.distinct()
    for (m in 0 until (1 shl letters.size)) {
        val v = letters.withIndex().associate { (i, l) -> l to (m and (1 shl i) != 0) }
        if (fs.all { it.eval(v) }) return true
    }
    return false
}

/** Saved games store formulas as text and parse them back. */
object FormulaSerializer : KSerializer<Formula> {
    override val descriptor = PrimitiveSerialDescriptor("Formula", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Formula) = encoder.encodeString(value.show())
    override fun deserialize(decoder: Decoder): Formula {
        val text = decoder.decodeString()
        return when (val r = Parser.parse(text)) {
            is ParseResult.Ok -> r.formula
            is ParseResult.Error -> error("Bad saved formula '$text': ${r.message}")
        }
    }
}

sealed interface ParseResult {
    data class Ok(val formula: Formula) : ParseResult
    data class Error(val message: String) : ParseResult
}

/**
 * Reads formulas written with symbols (¬ ∧ ∨ → ↔) or plain text (~ & v -> <->).
 * Statement letters are capitals. Two connectives side by side need brackets.
 */
object Parser {
    private class Fail(msg: String) : Exception(msg)

    fun tokenize(s: String): List<Char> {
        val out = ArrayList<Char>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                s.startsWith("<->", i) || s.startsWith("<=>", i) -> { out.add('↔'); i += 3 }
                s.startsWith("->", i) || s.startsWith("=>", i) -> { out.add('→'); i += 2 }
                c in "↔≡" -> { out.add('↔'); i++ }
                c in "→⊃>" -> { out.add('→'); i++ }
                c in "¬~!-" -> { out.add('¬'); i++ }
                c in "∧&^·*" -> { out.add('∧'); i++ }
                c in "∨v|+" -> { out.add('∨'); i++ }
                c in "([{" -> { out.add('('); i++ }
                c in ")]}" -> { out.add(')'); i++ }
                c in 'A'..'Z' -> { out.add(c); i++ }
                c in "∀∃" -> { out.add(c); i++ }
                isTerm(c) -> { out.add(c); i++ }
                else -> throw Fail("\"$c\" isn't a symbol I know. Use capital letters for statements and the connective keys.")
            }
        }
        return out
    }

    private val BIN = mapOf('∧' to Op.AND, '∨' to Op.OR, '→' to Op.IMP, '↔' to Op.IFF)

    fun parse(s: String): ParseResult = try {
        val toks = tokenize(s)
        if (toks.isEmpty()) throw Fail("Write a formula first.")
        var p = 0
        lateinit var formula: () -> Formula
        fun unary(): Formula {
            val t = toks.getOrNull(p) ?: throw Fail("The formula stops too early. Something is missing at the end.")
            return when {
                t == '¬' -> { p++; not(unary()) }
                t == '(' -> {
                    p++
                    val f = formula()
                    if (toks.getOrNull(p) != ')') throw Fail("A bracket is missing: every \"(\" needs a matching \")\".")
                    p++
                    f
                }
                t == '∀' || t == '∃' -> {
                    p++
                    val v = toks.getOrNull(p)
                    if (v == null || !isVariable(v)) throw Fail("\"$t\" needs a variable after it, like ${t}x.")
                    p++
                    Formula.Quant(if (t == '∀') Quantifier.ALL else Quantifier.SOME, v, unary())
                }
                t in 'A'..'Z' -> {
                    p++
                    val next = toks.getOrNull(p)
                    if (next != null && isTerm(next)) { p++; pred(t, next) } else atom(t)
                }
                isTerm(t) -> throw Fail("\"$t\" needs a predicate letter before it, like F$t.")
                t == ')' -> throw Fail("There is a \")\" with nothing inside or before it.")
                else -> throw Fail("\"$t\" needs a statement on its left side.")
            }
        }
        formula = {
            val left = unary()
            val op = toks.getOrNull(p)?.let { BIN[it] }
            if (op != null) {
                p++
                val right = unary()
                if (toks.getOrNull(p)?.let { BIN[it] } != null)
                    throw Fail("Add brackets to show the grouping, like (P ∧ Q) ∨ R. Two connectives side by side need brackets.")
                Formula.Bin(op, left, right)
            } else left
        }
        val f = formula()
        if (p < toks.size) throw Fail(
            if (toks[p] == ')') "There is an extra \")\"." else "\"${toks[p]}\" is out of place. Is a connective or bracket missing?"
        )
        ParseResult.Ok(f)
    } catch (e: Fail) {
        ParseResult.Error(e.message ?: "Couldn't read that formula.")
    }

    fun parseOrNull(s: String): Formula? = (parse(s) as? ParseResult.Ok)?.formula
}
