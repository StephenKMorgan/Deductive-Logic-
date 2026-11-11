package com.therefore.logic.engine

import kotlinx.serialization.Serializable

/** One line of a proof. `rule` is null for premises. */
@Serializable
data class ProofLine(val formula: Formula, val rule: Rule?, val cites: List<Int> = emptyList()) {
    val justification: String
        get() = when {
            rule == null -> "Premise"
            rule.kind == RuleKind.ASSUMPTION -> rule.abbr
            rule.kind == RuleKind.CLOSE && cites.size == 2 -> "${cites[0]}–${cites[1]}, ${rule.abbr}"
            else -> "${cites.joinToString(", ")}, ${rule.abbr}"
        }
}

data class LineCheck(val ok: Boolean, val message: String = "", val trap: Trap? = null, val alt: Rule? = null, val cites: List<Int> = emptyList())

data class Hint(val step: Step, val cites: List<Int>)

data class NumberedLine(val line: ProofLine, val from: List<Formula>, val depth: Int) {
    val formula get() = line.formula
    val rule get() = line.rule
    val cites get() = line.cites
}

/**
 * Subproofs: an assumption (ACP or AIP) opens a block, CP or IP closes it. Lines inside a closed
 * block can no longer be cited. A line's scope is the stack of open assumption line numbers.
 */
object Proofs {
    data class Scopes(val perLine: List<List<Int>>, val open: List<Int>) {
        fun depth(lineNumber: Int) = perLine[lineNumber - 1].size
        /** Whether line n can be cited from the current position. */
        fun available(n: Int): Boolean {
            val s = perLine.getOrNull(n - 1) ?: return false
            return s.size <= open.size && open.subList(0, s.size) == s
        }
    }

    fun scopes(lines: List<ProofLine>): Scopes {
        val out = ArrayList<List<Int>>()
        var stack = emptyList<Int>()
        lines.forEachIndexed { i, l ->
            when (l.rule?.kind) {
                RuleKind.ASSUMPTION -> { stack = stack + (i + 1); out += stack }
                RuleKind.CLOSE -> { stack = stack.dropLast(1); out += stack }
                else -> out += stack
            }
        }
        return Scopes(out, stack)
    }

    private fun Formula.isContradiction() =
        this is Formula.Bin && op == Op.AND && (b == not(a) || a == not(b))

    /** Checks a new line against the proof so far. On success, `cites` holds the citations to record. */
    fun checkLine(lines: List<ProofLine>, rule: Rule, cites: List<Int>, formula: Formula): LineCheck {
        val sc = scopes(lines)
        return when (rule.kind) {
            RuleKind.ASSUMPTION -> LineCheck(true)
            RuleKind.CLOSE -> {
                if (sc.open.isEmpty()) return LineCheck(false, "There's no open assumption to close. Start a subproof with ACP or AIP first.")
                val a = sc.open.last()
                val asm = lines[a - 1]
                val needed = if (rule == Rule.CP) Rule.ACP else Rule.AIP
                if (asm.rule != needed) return LineCheck(
                    false, "Line $a was assumed with ${asm.rule?.abbr}, so close it with ${if (asm.rule == Rule.ACP) "CP" else "IP"}.",
                )
                // Lines of the current block (not inside a nested, already-closed block).
                val block = (a..lines.size).filter { sc.perLine[it - 1] == sc.open }.map { lines[it - 1].formula }
                val range = listOf(a, lines.size)
                if (rule == Rule.CP) {
                    val f = formula as? Formula.Bin
                    when {
                        f != null && f.op == Op.IMP && f.a == asm.formula && f.b in block -> LineCheck(true, cites = range)
                        f != null && f.op == Op.IMP && f.a == asm.formula ->
                            LineCheck(false, "Conditional Proof closes with ${asm.formula} → (a line you derived after the assumption). ${f.b} isn't one of those lines yet.")
                        else -> LineCheck(false, "Conditional Proof turns the assumption on line $a into the \"if\" part. The new line must look like ${asm.formula.show(false)} → …")
                    }
                } else {
                    if (block.none { it.isContradiction() })
                        return LineCheck(false, "Indirect Proof needs a contradiction inside the assumption first: a line like Q ∧ ¬Q. Derive Q and ¬Q, then join them with Conjunction.")
                    val targets = listOfNotNull(not(asm.formula), (asm.formula as? Formula.Not)?.a)
                    if (formula in targets) LineCheck(true, cites = range)
                    else LineCheck(false, "The assumption on line $a led to a contradiction, so it must be false. Write ${targets.last()}.")
                }
            }
            else -> {
                for (c in cites) if (!sc.available(c))
                    return LineCheck(false, "Line $c is inside an assumption that has already been closed, so it can't be cited any more.")
                val r = Checker.check(rule, cites.map { lines[it - 1].formula }, formula)
                LineCheck(r.ok, r.message, r.trap, r.alt, cites)
            }
        }
    }

    /** The next step of the model proof that the player can take from where they are. */
    fun hint(puzzle: Puzzle, lines: List<ProofLine>): Hint? {
        val sc = scopes(lines)
        val avail = HashMap<Formula, Int>()
        lines.forEachIndexed { i, l -> if (sc.available(i + 1)) avail.putIfAbsent(l.formula, i + 1) }
        val openFormulas = sc.open.map { lines[it - 1].formula }
        val modelStack = ArrayList<Formula>()
        for (st in puzzle.solution) {
            when (st.rule.kind) {
                RuleKind.ASSUMPTION -> {
                    modelStack += st.formula
                    if (st.formula in openFormulas) continue
                    return Hint(st, emptyList())
                }
                RuleKind.CLOSE -> {
                    val asm = modelStack.removeAt(modelStack.lastIndex)
                    if (lines.any { it.formula == st.formula && it.rule == st.rule }) continue
                    if (sc.open.isNotEmpty() && lines[sc.open.last() - 1].formula == asm) return Hint(st, listOf(sc.open.last(), lines.size))
                }
                else -> {
                    if (st.formula in avail) continue
                    if (st.from.all { it in avail }) return Hint(st, st.from.map { avail.getValue(it) })
                }
            }
        }
        return null
    }

    /** The model proof with line numbers and citations, as the player would write it. */
    fun numbered(puzzle: Puzzle): List<NumberedLine> {
        val lines = puzzle.premises.map { ProofLine(it, null) }.toMutableList()
        val from = puzzle.premises.map { emptyList<Formula>() }.toMutableList()
        for (st in puzzle.solution) {
            val sc = scopes(lines)
            val cites = when (st.rule.kind) {
                RuleKind.ASSUMPTION -> emptyList()
                RuleKind.CLOSE -> listOf(sc.open.last(), lines.size)
                else -> st.from.map { f -> (1..lines.size).last { sc.available(it) && lines[it - 1].formula == f } }
            }
            lines += ProofLine(st.formula, st.rule, cites)
            from += st.from
        }
        val sc = scopes(lines)
        return lines.mapIndexed { i, l -> NumberedLine(l, from[i], sc.depth(i + 1)) }
    }

    /** One plain-English sentence explaining a model step. */
    fun explain(line: NumberedLine): String {
        val l = line.cites
        val a = line.from.getOrNull(0)
        val ab = a as? Formula.Bin
        return when (line.rule) {
            null -> "Premise."
            Rule.MP -> "Line ${l[0]} says \"if ${ab?.a}, then ${ab?.b}\", and line ${l[1]} gives exactly the \"if\" part. So the \"then\" part follows."
            Rule.MT -> "Line ${l[0]} says \"if ${ab?.a}, then ${ab?.b}\". Line ${l[1]} says the \"then\" part is false, so the \"if\" part must be false too."
            Rule.HS -> "Line ${l[0]} ends where line ${l[1]} begins (${ab?.b}), so the two conditionals chain into one."
            Rule.DS -> "Line ${l[0]} says at least one of ${ab?.a} and ${ab?.b} is true. Line ${l[1]} rules out the left one, so the right one is true."
            Rule.Simp -> "Line ${l[0]} is an \"and\", so its left side is true on its own."
            Rule.Conj -> if (line.formula is Formula.Bin && (line.formula as Formula.Bin).b == not((line.formula as Formula.Bin).a))
                "Lines ${l[0]} and ${l[1]} contradict each other. Joining them shows the assumption leads to something impossible."
            else "Lines ${l[0]} and ${l[1]} are both true, so they can be joined with ∧."
            Rule.Add -> "Line ${l[0]} is true, so \"$a or anything\" is true. Addition lets you attach whatever you need on the right."
            Rule.CD -> "Line ${l[0]} gives two conditionals, and line ${l[1]} says one of their \"if\" parts holds. So one of the two results holds."
            Rule.ACP -> "Suppose ${line.formula} is true, to see what follows. Everything indented below depends on this assumption."
            Rule.AIP -> "Suppose ${line.formula}, the opposite of what we want, and look for a contradiction."
            Rule.CP -> "Lines ${l[0]}–${l[1]} show that assuming ${(line.formula as? Formula.Bin)?.a} leads to ${(line.formula as? Formula.Bin)?.b}. So the conditional is proved, and the assumption is closed."
            Rule.IP -> "The assumption on line ${l[0]} led to a contradiction, so it must be false. That proves ${line.formula}."
            else -> {
                val how = when (line.rule) {
                    Rule.DN -> "adds or removes a double negation"
                    Rule.Com -> "swaps the two sides"
                    Rule.Assoc -> "moves the brackets"
                    Rule.DeM -> "moves the negation through the brackets and flips ∧ and ∨"
                    Rule.Dist -> "distributes one part over the brackets"
                    Rule.Trans -> "swaps the sides of the conditional and negates both"
                    Rule.Impl -> "rewrites a conditional as an \"or\" (or back again)"
                    Rule.Equiv -> "unpacks or repacks the biconditional"
                    Rule.Exp -> "moves between (P ∧ Q) → R and P → (Q → R)"
                    else -> "removes or adds a repeated part"
                }
                "${line.rule!!.title} $how, turning line ${l[0]} into this equivalent form."
            }
        }
    }
}
