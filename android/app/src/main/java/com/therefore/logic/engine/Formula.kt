package com.therefore.logic.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

enum class Op(val symbol: Char) { AND('∧'), OR('∨'), IMP('→'), IFF('↔') }

/**
 * A propositional formula. Data classes give structural equality, so two formulas are
 * equal exactly when they are written the same way.
 */
@Serializable(with = FormulaSerializer::class)
sealed class Formula {
    // Data classes generate their own toString(), so each one must point back to show().
    data class Atom(val v: Char) : Formula() { override fun toString() = show() }
    data class Not(val a: Formula) : Formula() { override fun toString() = show() }
    data class Bin(val op: Op, val a: Formula, val b: Formula) : Formula() { override fun toString() = show() }

    /** Fully bracketed except at the top level, e.g. `(P ∧ Q) → R`. */
    fun show(top: Boolean = true): String = when (this) {
        is Atom -> v.toString()
        is Not -> "¬" + a.show(false)
        is Bin -> {
            val s = "${a.show(false)} ${op.symbol} ${b.show(false)}"
            if (top) s else "($s)"
        }
    }

    val size: Int
        get() = when (this) {
            is Atom -> 1
            is Not -> 1 + a.size
            is Bin -> 1 + a.size + b.size
        }

    fun atoms(into: MutableSet<Char> = sortedSetOf()): Set<Char> {
        when (this) {
            is Atom -> into.add(v)
            is Not -> a.atoms(into)
            is Bin -> { a.atoms(into); b.atoms(into) }
        }
        return into
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
    }
}

fun atom(c: Char): Formula = Formula.Atom(c)
fun not(a: Formula): Formula = Formula.Not(a)
fun and(a: Formula, b: Formula): Formula = Formula.Bin(Op.AND, a, b)
fun or(a: Formula, b: Formula): Formula = Formula.Bin(Op.OR, a, b)
fun imp(a: Formula, b: Formula): Formula = Formula.Bin(Op.IMP, a, b)
fun iff(a: Formula, b: Formula): Formula = Formula.Bin(Op.IFF, a, b)

val Formula.isAnd get() = this is Formula.Bin && op == Op.AND
val Formula.isOr get() = this is Formula.Bin && op == Op.OR
val Formula.isImp get() = this is Formula.Bin && op == Op.IMP
val Formula.isNot get() = this is Formula.Not

/** True when the formulas can all be true at once (truth table over at most 6 letters). */
fun satisfiable(fs: List<Formula>): Boolean {
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
                t in 'A'..'Z' -> { p++; atom(t) }
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
