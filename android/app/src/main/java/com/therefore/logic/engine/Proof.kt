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

    fun Formula.isContradiction() =
        this is Formula.Bin && op == Op.AND && (b == not(a) || a == not(b))

    /**
     * Lines that clash: a line X ∧ ¬X, or two citable lines X and ¬X. Only lines the player can still
     * cite count, so a contradiction inside a closed subproof doesn't light up.
     */
    fun contradictions(lines: List<ProofLine>): Map<Int, Int?> {
        val sc = scopes(lines)
        val at = HashMap<Formula, Int>()
        lines.forEachIndexed { i, l -> if (sc.available(i + 1)) at.putIfAbsent(l.formula, i + 1) }
        val out = LinkedHashMap<Int, Int?>()
        for ((f, n) in at) {
            if (f.isContradiction()) out[n] = null
            at[not(f)]?.let { m -> out[n] = m; out[m] = n }
        }
        return out
    }

    /**
     * What the player is working toward at this point: the conclusion, or inside a Conditional Proof
     * the "then" part of the conditional being proved. Null inside an Indirect Proof (the aim there is
     * a contradiction) or when the assumptions don't follow the goal.
     */
    fun currentGoal(conclusion: Formula, lines: List<ProofLine>): Formula? {
        var goal: Formula = conclusion
        for (n in scopes(lines).open) {
            val l = lines[n - 1]
            val g = goal as? Formula.Bin
            goal = if (l.rule == Rule.ACP && g != null && g.op == Op.IMP && g.a == l.formula) g.b else return null
        }
        return goal
    }

    /** The line an assumption rule most likely wants, so the board can fill it in (the player can still edit it). */
    fun suggestion(conclusion: Formula, lines: List<ProofLine>, rule: Rule): Pair<Formula, String>? {
        val sc = scopes(lines)
        val innermost = sc.open.lastOrNull()?.let { lines[it - 1] }
        return when (rule) {
            Rule.ACP -> (currentGoal(conclusion, lines) as? Formula.Bin)?.takeIf { it.op == Op.IMP }?.let {
                it.a to "Filled in ${it.a}, the \"if\" part of ${it}. You can edit it."
            }
            Rule.AIP -> currentGoal(conclusion, lines)?.let { g ->
                val opposite = if (g is Formula.Not) g.a else not(g)
                opposite to "Filled in $opposite, the opposite of $g, which you're trying to prove. You can edit it."
            }
            Rule.CP -> innermost?.takeIf { it.rule == Rule.ACP && lines.last() !== it }?.let { a ->
                val r = imp(a.formula, lines.last().formula)
                r to "Filled in $r: the assumption, then the last line. Edit the right side to use another line from the subproof."
            }
            Rule.IP -> innermost?.takeIf { it.rule == Rule.AIP }?.let { a ->
                val r = (a.formula as? Formula.Not)?.a ?: not(a.formula)
                r to "Filled in $r, the opposite of the assumption."
            }
            else -> null
        }
    }

    /** Checks a new line against the proof so far. On success, `cites` holds the citations to record. */
    fun checkLine(lines: List<ProofLine>, rule: Rule, cites: List<Int>, formula: Formula, conclusion: Formula? = null): LineCheck {
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
                        // The "then" part may be any line that can still be cited here (inside the subproof or before it).
                        f != null && f.op == Op.IMP && f.a == asm.formula && (1..lines.size).any { sc.available(it) && lines[it - 1].formula == f.b } ->
                            LineCheck(true, cites = range)
                        f != null && f.op == Op.IMP && f.a == asm.formula ->
                            LineCheck(false, "Conditional Proof closes with ${asm.formula} → (a line you can cite here). ${f.b} isn't one of those lines yet.")
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
                if (r.ok) quantifierRestriction(lines, sc, rule, cites, formula, conclusion)?.let { return it }
                LineCheck(r.ok, r.message, r.trap, r.alt, cites)
            }
        }
    }

    /**
     * The restrictions that make EI and UG safe:
     * EI must introduce a name that appears nowhere earlier in the proof or in the conclusion;
     * UG can't generalize a variable that is free in an open assumption.
     */
    private fun quantifierRestriction(lines: List<ProofLine>, sc: Scopes, rule: Rule, cites: List<Int>, formula: Formula, conclusion: Formula?): LineCheck? {
        when (rule) {
            Rule.EI -> {
                val src = lines[cites[0] - 1].formula as Formula.Quant
                val name = formula.names().firstOrNull { src.body.subst(src.v, it) == formula } ?: return null
                val usedAt = lines.indexOfFirst { name in it.formula.names() }
                if (usedAt >= 0) return LineCheck(
                    false,
                    "EI needs a brand-new name, but $name already appears on line ${usedAt + 1}. You only know that something is ${src.body.show(false).replace(src.v.toString(), "_")}; it might not be $name. Use a letter that hasn't appeared yet.",
                    Trap.EI_NOT_NEW, cites = cites,
                )
                if (conclusion != null && name in conclusion.names()) return LineCheck(
                    false, "EI can't use $name, because $name appears in the conclusion. Pick a name that appears nowhere else.", Trap.EI_NOT_NEW, cites = cites,
                )
            }
            Rule.UG -> {
                val u = formula as Formula.Quant
                val src = lines[cites[0] - 1].formula
                val variable = (src.terms() + u.v).filter(::isVariable).firstOrNull { u.body.subst(u.v, it) == src } ?: return null
                // A name from EI is tied to the variables free when it was introduced (as in ∀x∃y Lxy → Lxa):
                // the variable is then no longer arbitrary.
                val fromEi = lines.indexOfFirst { it.rule == Rule.EI && variable in it.formula.freeVariables() }
                if (fromEi >= 0) return LineCheck(
                    false,
                    "You can't use UG on $variable here. Line ${fromEi + 1} came from EI while $variable was free, so the name there may depend on $variable. That's how \"everyone loves someone\" would wrongly become \"someone is loved by everyone\".",
                    Trap.UG_NAME, cites = cites,
                )
                val assumption = sc.open.firstOrNull { variable in lines[it - 1].formula.freeVariables() }
                if (assumption != null) return LineCheck(
                    false, "You can't use UG on $variable here: line $assumption assumed something about $variable, so $variable isn't arbitrary inside this subproof.", Trap.UG_NAME, cites = cites,
                )
            }
            else -> {}
        }
        return null
    }

    /** The next step of the model proof that the player can take from where they are. */
    fun hint(puzzle: Puzzle, lines: List<ProofLine>): Hint? {
        val sc = scopes(lines)
        val avail = HashMap<Formula, Int>()
        lines.forEachIndexed { i, l -> if (sc.available(i + 1)) avail.putIfAbsent(l.formula, i + 1) }
        val openFormulas = sc.open.map { lines[it - 1].formula }
        // Pair each model assumption with the step that closes it.
        val closer = HashMap<Int, Step>()
        val pending = ArrayList<Int>()
        puzzle.solution.forEachIndexed { i, st ->
            when (st.rule.kind) {
                RuleKind.ASSUMPTION -> pending += i
                RuleKind.CLOSE -> closer[pending.removeAt(pending.lastIndex)] = st
                else -> {}
            }
        }
        val closedAlready = { st: Step? -> st != null && lines.any { it.formula == st.formula && it.rule == st.rule } }
        val modelStack = ArrayList<Formula>()
        for ((i, st) in puzzle.solution.withIndex()) {
            when (st.rule.kind) {
                RuleKind.ASSUMPTION -> {
                    modelStack += st.formula
                    // Done if it's open now, or if its subproof has already been closed.
                    if (st.formula in openFormulas || closedAlready(closer[i])) continue
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
            Rule.UI -> "Line ${l[0]} holds for everything, so it holds for this one in particular: drop the ∀ and fill in the name or variable."
            Rule.EG -> "Line ${l[0]} is true of a particular thing, so something has that property: replace it with a variable and add ∃."
            Rule.EI -> "Line ${l[0]} says something fits. Give that something a new name that hasn't been used yet, and drop the ∃."
            Rule.UG -> "Line ${l[0]} was shown for an arbitrary x, with nothing special assumed about it, so it holds for everything: add ∀."
            Rule.QN -> "Quantifier Negation moves the ¬ across the quantifier and flips ∀ and ∃, turning line ${l[0]} into this equivalent form."
            Rule.IdR -> "Everything is identical to itself, so this line needs no citation."
            Rule.IdS -> "Identity works both ways: if a = b, then b = a."
            Rule.Id -> "Lines ${l[0]} and ${l[1]} name the same thing, so one name can replace the other."
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
                    Rule.Taut -> "removes or adds a repeated part"
                    else -> "rewrites it"
                }
                "${line.rule!!.title} $how, turning line ${l[0]} into this equivalent form."
            }
        }
    }
}
