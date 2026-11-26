package com.therefore.logic.engine


/**
 * Writes a finished propositional proof as Lean 4 code. Each line becomes a `have` with the matching Lean term:
 * MP is function application, Simp is `.1`, Conj is `⟨a, b⟩`, a conditional proof is `intro`, an indirect proof is
 * `by_contra`. Replacement rules and Resolution are left to the `tauto` tactic, with the rule named in a comment.
 */
object Lean {
    /** Lean can show propositional proofs this way; predicate logic needs types and is left out. */
    fun supports(premises: List<Formula>, lines: List<ProofLine>): Boolean =
        (premises + lines.map { it.formula }).none { it.isPredicateLogic } &&
            lines.none { it.rule?.isQuantifierRule == true || it.rule?.isIdentityRule == true }

    private fun t(f: Formula) = f.show()

    fun proof(premises: List<Formula>, lines: List<ProofLine>, conclusion: Formula): String {
        val letters = (premises + lines.map { it.formula } + conclusion).flatMap { it.atoms() }.toSortedSet()
        val out = StringBuilder("import Mathlib.Tactic\n\n")
        out.append("example (${letters.joinToString(" ")} : Prop)")
        premises.forEachIndexed { i, p -> out.append(" (h${i + 1} : ${t(p)})") }
        out.append(" : ${t(conclusion)} := by\n")
        val body = ArrayList<String>()
        emit(lines, premises.size, lines.size, 1, body)
        body.forEach { out.append(it).append('\n') }
        val last = lines.indices.last { i -> lines[i].formula == conclusion && depthOk(lines, i) }
        out.append("  exact h${last + 1}\n")
        return out.toString()
    }

    /** Whether line [i] is outside every subproof (so it can be used at the end). */
    private fun depthOk(lines: List<ProofLine>, i: Int): Boolean {
        var depth = 0
        for (j in 0..i) {
            when (lines[j].rule?.kind) {
                RuleKind.ASSUMPTION -> depth++
                RuleKind.CLOSE -> depth--
                else -> {}
            }
        }
        return depth == 0 && lines[i].rule?.kind != RuleKind.ASSUMPTION
    }

    /** The line that closes the assumption opened at [open]. */
    private fun closer(lines: List<ProofLine>, open: Int): Int {
        var depth = 0
        for (j in open until lines.size) {
            when (lines[j].rule?.kind) {
                RuleKind.ASSUMPTION -> depth++
                RuleKind.CLOSE -> { depth--; if (depth == 0) return j }
                else -> {}
            }
        }
        return lines.lastIndex
    }

    private fun emit(lines: List<ProofLine>, from: Int, until: Int, indent: Int, out: MutableList<String>) {
        val pad = "  ".repeat(indent)
        var i = from
        while (i < until) {
            val l = lines[i]
            val rule = l.rule
            if (rule?.kind == RuleKind.ASSUMPTION) {
                val c = closer(lines, i)
                val closing = lines[c]
                out += "$pad-- lines ${i + 1}–${c + 1}: assume ${t(l.formula)} (${rule.abbr})"
                out += "${pad}have h${c + 1} : ${t(closing.formula)} := by"
                val inner = "  ".repeat(indent + 1)
                val proving = closing.formula
                out += if (closing.rule == Rule.CP || proving == not(l.formula)) "${inner}intro h${i + 1}" else "${inner}by_contra h${i + 1}"
                emit(lines, i + 1, c, indent + 1, out)
                val end = lines[c - 1]
                val last = "h$c"
                out += if (closing.rule == Rule.CP) "${inner}exact $last"
                else {
                    val f = end.formula as? Formula.Bin
                    when {
                        f != null && f.op == Op.AND && f.b == not(f.a) -> "${inner}exact $last.2 $last.1"
                        f != null && f.op == Op.AND && f.a == not(f.b) -> "${inner}exact $last.1 $last.2"
                        else -> "${inner}tauto"
                    }
                }
                i = c + 1
                continue
            }
            out += haveLine(lines, i, indent)
            i++
        }
    }

    private fun haveLine(lines: List<ProofLine>, i: Int, indent: Int): String {
        val l = lines[i]
        val pad = "  ".repeat(indent)
        return "${pad}have h${i + 1} : ${t(l.formula)} := ${term(lines, l)}"
    }

    /** The Lean term for one line, from the lines it cites. */
    private fun term(lines: List<ProofLine>, l: ProofLine): String {
        val rule = l.rule ?: return "by tauto"
        val c = l.cites
        fun f(n: Int) = lines[n - 1].formula
        fun h(n: Int) = "h$n"
        val r = l.formula
        val tactic = "by tauto  -- ${rule.abbr}" + if (c.isNotEmpty()) ", from ${c.joinToString(" and ")}" else ""
        return when (rule) {
            Rule.MP -> {
                val (cond, arg) = c.firstNotNullOfOrNull { a -> c.firstOrNull { b -> b != a && f(a) == imp(f(b), r) }?.let { a to it } } ?: return tactic
                "${h(cond)} ${h(arg)}"
            }
            Rule.MT -> {
                val cond = c.firstOrNull { (f(it) as? Formula.Bin)?.op == Op.IMP } ?: return tactic
                val neg = c.first { it != cond }
                "fun a => ${h(neg)} (${h(cond)} a)"
            }
            Rule.HS -> {
                val first = c.firstOrNull { a -> c.any { b -> b != a && (f(a) as? Formula.Bin)?.b == (f(b) as? Formula.Bin)?.a } } ?: return tactic
                val second = c.first { it != first }
                "fun a => ${h(second)} (${h(first)} a)"
            }
            Rule.DS -> {
                val disj = c.firstOrNull { (f(it) as? Formula.Bin)?.op == Op.OR } ?: return tactic
                val neg = c.first { it != disj }
                val d = f(disj) as Formula.Bin
                if (f(neg) == not(d.a)) "${h(disj)}.resolve_left ${h(neg)}" else "${h(disj)}.resolve_right ${h(neg)}"
            }
            Rule.Simp -> {
                val a = f(c[0]) as? Formula.Bin ?: return tactic
                if (r == a.a) "${h(c[0])}.1" else "${h(c[0])}.2"
            }
            Rule.Conj -> {
                val b = r as? Formula.Bin ?: return tactic
                val left = c.firstOrNull { f(it) == b.a } ?: return tactic
                val right = c.lastOrNull { it != left || c.size == 1 } ?: left
                "⟨${h(left)}, ${h(right)}⟩"
            }
            Rule.Add -> {
                val b = r as? Formula.Bin ?: return tactic
                if (b.a == f(c[0])) "Or.inl ${h(c[0])}" else "Or.inr ${h(c[0])}"
            }
            Rule.CD -> {
                val conj = c.firstOrNull { (f(it) as? Formula.Bin)?.op == Op.AND } ?: return tactic
                val disj = c.first { it != conj }
                "${h(disj)}.elim (fun a => Or.inl (${h(conj)}.1 a)) (fun b => Or.inr (${h(conj)}.2 b))"
            }
            else -> tactic
        }
    }
}
