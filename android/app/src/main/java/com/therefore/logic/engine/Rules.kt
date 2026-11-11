package com.therefore.logic.engine

import com.therefore.logic.engine.Formula.Bin
import com.therefore.logic.engine.Formula.Not

enum class RuleKind { INFERENCE, REPLACEMENT }

/** The 18 rules, with the Copi/Hurley conventions (Simp, DS and Add work on the left side). */
enum class Rule(
    val title: String,
    val kind: RuleKind,
    val lines: Int,
    val from: String,
    val to: String,
    val plain: String,
) {
    MP("Modus Ponens", RuleKind.INFERENCE, 2, "P → Q, P", "Q", "If P then Q; P is true; so Q."),
    MT("Modus Tollens", RuleKind.INFERENCE, 2, "P → Q, ¬Q", "¬P", "If P then Q; Q is false; so P is false."),
    HS("Hypothetical Syllogism", RuleKind.INFERENCE, 2, "P → Q, Q → R", "P → R", "Chain two ifs together."),
    DS("Disjunctive Syllogism", RuleKind.INFERENCE, 2, "P ∨ Q, ¬P", "Q", "One of them is true; it isn't P; so Q."),
    Simp("Simplification", RuleKind.INFERENCE, 1, "P ∧ Q", "P", "Both are true, so the left one is."),
    Conj("Conjunction", RuleKind.INFERENCE, 2, "P, Q", "P ∧ Q", "Two true lines can be joined with \"and\"."),
    Add("Addition", RuleKind.INFERENCE, 1, "P", "P ∨ Q", "If P is true, \"P or anything\" is true."),
    CD("Constructive Dilemma", RuleKind.INFERENCE, 2, "(P → Q) ∧ (R → S), P ∨ R", "Q ∨ S", "Either cause happens, so one of the results does."),
    DN("Double Negation", RuleKind.REPLACEMENT, 1, "P", "¬¬P", "Two negations cancel out."),
    Com("Commutation", RuleKind.REPLACEMENT, 1, "P ∨ Q", "Q ∨ P (same for ∧)", "Swap the two sides of ∨ or ∧."),
    Assoc("Association", RuleKind.REPLACEMENT, 1, "(P ∨ Q) ∨ R", "P ∨ (Q ∨ R) (same for ∧)", "Regroup the brackets in a chain of ∨ or of ∧."),
    DeM("De Morgan's", RuleKind.REPLACEMENT, 1, "¬(P ∧ Q) / ¬(P ∨ Q)", "¬P ∨ ¬Q / ¬P ∧ ¬Q", "Push a negation inside the brackets and flip ∧ and ∨."),
    Dist("Distribution", RuleKind.REPLACEMENT, 1, "P ∧ (Q ∨ R) / P ∨ (Q ∧ R)", "(P ∧ Q) ∨ (P ∧ R) / (P ∨ Q) ∧ (P ∨ R)", "Share the outside letter with each part inside the brackets."),
    Trans("Transposition", RuleKind.REPLACEMENT, 1, "P → Q", "¬Q → ¬P", "Swap the sides of a conditional and negate both."),
    Impl("Material Implication", RuleKind.REPLACEMENT, 1, "P → Q", "¬P ∨ Q", "\"If P then Q\" means \"not P, or Q\"."),
    Equiv("Material Equivalence", RuleKind.REPLACEMENT, 1, "P ↔ Q", "(P → Q) ∧ (Q → P), or (P ∧ Q) ∨ (¬P ∧ ¬Q)", "\"P if and only if Q\" means each implies the other."),
    Exp("Exportation", RuleKind.REPLACEMENT, 1, "(P ∧ Q) → R", "P → (Q → R)", "\"If P and Q, then R\" means \"if P, then if Q then R\"."),
    Taut("Tautology", RuleKind.REPLACEMENT, 1, "P", "P ∨ P (same for ∧)", "Saying the same thing twice adds nothing.");

    val abbr: String get() = name
}

/** Why a line was rejected, so the results screen can teach the matching lesson. */
enum class Trap { AFFIRM, DENY, DS_RIGHT, SIMP_RIGHT, ADD_RIGHT, DN, ALT, OTHER }

data class Check(val ok: Boolean, val message: String = "", val trap: Trap? = null, val alt: Rule? = null)

object Checker {
    private val OK = Check(true)
    private fun fail(msg: String, trap: Trap? = null) = Check(false, msg, trap)
    private fun s(f: Formula) = f.show()

    /** Formulas that `rule` turns `f` into when applied to the whole of `f` (either direction). */
    fun rootRewrites(rule: Rule, f: Formula): List<Formula> {
        val out = ArrayList<Formula>()
        val bin = f as? Bin
        when (rule) {
            Rule.DN -> {
                out += not(not(f))
                if (f is Not && f.a is Not) out += f.a.a
            }
            Rule.Com -> if (bin != null && (bin.op == Op.AND || bin.op == Op.OR)) out += Bin(bin.op, bin.b, bin.a)
            Rule.Assoc -> if (bin != null && (bin.op == Op.AND || bin.op == Op.OR)) {
                val l = bin.a as? Bin; val r = bin.b as? Bin
                if (l != null && l.op == bin.op) out += Bin(bin.op, l.a, Bin(bin.op, l.b, bin.b))
                if (r != null && r.op == bin.op) out += Bin(bin.op, Bin(bin.op, bin.a, r.a), r.b)
            }
            Rule.DeM -> {
                if (f is Not && f.a is Bin && (f.a.op == Op.AND || f.a.op == Op.OR))
                    out += Bin(if (f.a.op == Op.AND) Op.OR else Op.AND, not(f.a.a), not(f.a.b))
                if (bin != null && (bin.op == Op.AND || bin.op == Op.OR) && bin.a is Not && bin.b is Not)
                    out += not(Bin(if (bin.op == Op.AND) Op.OR else Op.AND, bin.a.a, bin.b.a))
            }
            Rule.Dist -> if (bin != null) {
                for ((o, i) in listOf(Op.AND to Op.OR, Op.OR to Op.AND)) {
                    val rb = bin.b as? Bin
                    if (bin.op == o && rb != null && rb.op == i) out += Bin(i, Bin(o, bin.a, rb.a), Bin(o, bin.a, rb.b))
                    val l = bin.a as? Bin; val r = bin.b as? Bin
                    if (bin.op == i && l != null && r != null && l.op == o && r.op == o && l.a == r.a)
                        out += Bin(o, l.a, Bin(i, l.b, r.b))
                }
            }
            Rule.Trans -> if (bin != null && bin.op == Op.IMP) {
                out += imp(not(bin.b), not(bin.a))
                if (bin.a is Not && bin.b is Not) out += imp(bin.b.a, bin.a.a)
            }
            Rule.Impl -> {
                if (bin != null && bin.op == Op.IMP) out += or(not(bin.a), bin.b)
                if (bin != null && bin.op == Op.OR && bin.a is Not) out += imp(bin.a.a, bin.b)
            }
            Rule.Equiv -> if (bin != null) {
                if (bin.op == Op.IFF) {
                    out += and(imp(bin.a, bin.b), imp(bin.b, bin.a))
                    out += or(and(bin.a, bin.b), and(not(bin.a), not(bin.b)))
                }
                val l = bin.a as? Bin; val r = bin.b as? Bin
                if (bin.op == Op.AND && l != null && r != null && l.op == Op.IMP && r.op == Op.IMP && l.a == r.b && l.b == r.a)
                    out += iff(l.a, l.b)
                if (bin.op == Op.OR && l != null && r != null && l.op == Op.AND && r.op == Op.AND &&
                    r.a is Not && r.b is Not && l.a == r.a.a && l.b == r.b.a
                ) out += iff(l.a, l.b)
            }
            Rule.Exp -> if (bin != null && bin.op == Op.IMP) {
                val l = bin.a as? Bin; val r = bin.b as? Bin
                if (l != null && l.op == Op.AND) out += imp(l.a, imp(l.b, bin.b))
                if (r != null && r.op == Op.IMP) out += imp(and(bin.a, r.a), r.b)
            }
            Rule.Taut -> {
                out += or(f, f); out += and(f, f)
                if (bin != null && (bin.op == Op.OR || bin.op == Op.AND) && bin.a == bin.b) out += bin.a
            }
            else -> {}
        }
        return out
    }

    /** `b` follows from `a` by applying `rule` at one or more separate places. */
    fun rewritesTo(rule: Rule, a: Formula, b: Formula): Boolean {
        if (a == b) return true
        if (rootRewrites(rule, a).any { it == b }) return true
        return when {
            a is Not && b is Not -> rewritesTo(rule, a.a, b.a)
            a is Bin && b is Bin && a.op == b.op -> rewritesTo(rule, a.a, b.a) && rewritesTo(rule, a.b, b.b)
            else -> false
        }
    }

    private fun inference(rule: Rule, c: List<Formula>, r: Formula): Check {
        val perms = if (c.size == 2) listOf(c[0] to c[1], c[1] to c[0]) else listOf(c[0] to c[0])
        fun imps() = perms.mapNotNull { (x, y) -> (x as? Bin)?.takeIf { it.op == Op.IMP }?.let { it to y } }
        fun ors() = perms.mapNotNull { (x, y) -> (x as? Bin)?.takeIf { it.op == Op.OR }?.let { it to y } }
        when (rule) {
            Rule.MP -> {
                imps().firstOrNull { (x, y) -> x.a == y }?.let { (x, y) ->
                    return if (r == x.b) OK else fail("From ${s(x)} and ${s(y)}, Modus Ponens gives the right side of the conditional: ${s(x.b)}.")
                }
                imps().firstOrNull { (x, y) -> x.b == y }?.let { (x, y) ->
                    return fail("This is the trap called affirming the consequent. Knowing the \"then\" part (${s(y)}) does not tell you the \"if\" part. MP needs the \"if\" part, ${s(x.a)}.", Trap.AFFIRM)
                }
                if (c.none { it.isImp }) return fail("Modus Ponens needs a conditional line, P → Q, as one of the lines you cite. Neither line is an if-then as a whole.")
                return fail("Modus Ponens needs a conditional P → Q and a second line that is exactly its left side, P.")
            }
            Rule.MT -> {
                imps().firstOrNull { (x, y) -> y is Not && y.a == x.b }?.let { (x, y) ->
                    return if (r == not(x.a)) OK else fail("From ${s(x)} and ${s(y)}, Modus Tollens gives the negation of the left side: ${s(not(x.a))}.")
                }
                imps().firstOrNull { (x, y) -> y is Not && y.a == x.a }?.let { (x, y) ->
                    return fail("This is the trap called denying the antecedent. ${s(y)} denies the \"if\" part, which tells you nothing about ${s(x.b)}. MT needs the negation of the \"then\" part: ${s(not(x.b))}.", Trap.DENY)
                }
                imps().firstOrNull { (x, y) -> x.b is Not && y == x.b.a }?.let { (x, y) ->
                    return fail("MT needs the exact negation of ${s(x.b)}, which is ${s(not(x.b))}. Use Double Negation to turn ${s(y)} into ${s(not(not(y)))} first.", Trap.DN)
                }
                if (c.none { it.isImp }) return fail("Modus Tollens needs a conditional line, P → Q, as one of the lines you cite.")
                return fail("Modus Tollens needs a conditional P → Q and a line that is exactly ¬Q, the negation of its right side.")
            }
            Rule.HS -> {
                imps().firstOrNull { (x, y) -> y is Bin && y.op == Op.IMP && x.b == y.a }?.let { (x, y) ->
                    val want = imp(x.a, (y as Bin).b)
                    return if (r == want) OK else fail("Chaining ${s(x)} and ${s(y)} gives ${s(want)}.")
                }
                return fail("Hypothetical Syllogism needs two conditionals where the right side of one is exactly the left side of the other: P → Q and Q → R.")
            }
            Rule.DS -> {
                ors().firstOrNull { (x, y) -> y is Not && y.a == x.a }?.let { (x, y) ->
                    return if (r == x.b) OK else fail("From ${s(x)} and ${s(y)}, Disjunctive Syllogism gives the right side: ${s(x.b)}.")
                }
                ors().firstOrNull { (x, y) -> y is Not && y.a == x.b }?.let { (x, y) ->
                    return fail("DS only cancels the left side of an \"or\". ${s(y)} denies the right side of ${s(x)}. Use Commutation first to get ${s(or(x.b, x.a))}, then DS.", Trap.DS_RIGHT)
                }
                ors().firstOrNull { (x, y) -> x.a is Not && y == x.a.a }?.let { (x, y) ->
                    return fail("DS needs the exact negation of ${s(x.a)}, which is ${s(not(x.a))}. Use Double Negation on ${s(y)} first.", Trap.DN)
                }
                if (c.none { it.isOr }) return fail("Disjunctive Syllogism needs an \"or\" line, P ∨ Q, as one of the lines you cite.")
                return fail("Disjunctive Syllogism needs P ∨ Q and a line that is exactly ¬P, the negation of the left side.")
            }
            Rule.Simp -> {
                val x = c[0]
                if (x !is Bin || x.op != Op.AND) return fail("Simplification works only when the whole line is an \"and\". The main connective of ${s(x)} isn't ∧. Rules of inference apply to whole lines only.")
                if (r == x.a) return OK
                if (r == x.b) return fail("Simplification takes only the left side, ${s(x.a)}. To get ${s(x.b)}, use Commutation first to write ${s(and(x.b, x.a))}.", Trap.SIMP_RIGHT)
                return fail("Simplification on ${s(x)} gives its left side: ${s(x.a)}.")
            }
            Rule.Conj -> {
                if (r is Bin && r.op == Op.AND && perms.any { (x, y) -> r.a == x && r.b == y }) return OK
                return fail("Conjunction joins the two cited lines with ∧: ${s(and(c[0], c[1]))} or ${s(and(c[1], c[0]))}.")
            }
            Rule.Add -> {
                val x = c[0]
                if (r is Bin && r.op == Op.OR && r.a == x) return OK
                if (r is Bin && r.op == Op.OR && r.b == x) return fail("Addition keeps the original line on the left: write ${s(or(x, r.a))}, then use Commutation if you need it the other way round.", Trap.ADD_RIGHT)
                return fail("Addition turns ${s(x)} into \"${s(x)} or something\": ${x.show(false)} ∨ …")
            }
            Rule.CD -> {
                for ((x, y) in perms) {
                    if (x is Bin && x.op == Op.AND && x.a.isImp && x.b.isImp && y is Bin && y.op == Op.OR) {
                        val p = x.a as Bin; val q = x.b as Bin
                        if (y.a == p.a && y.b == q.a) {
                            val want = or(p.b, q.b)
                            return if (r == want) OK else fail("From ${s(x)} and ${s(y)}, Constructive Dilemma gives ${s(want)}.")
                        }
                    }
                }
                return fail("Constructive Dilemma needs (P → Q) ∧ (R → S) and P ∨ R, with the \"or\" line matching the two \"if\" parts in order.")
            }
            else -> return fail("Unknown rule.")
        }
    }

    private fun replacement(rule: Rule, a: Formula, b: Formula): Check {
        if (a == b) return fail("This line is identical to the line it comes from. A replacement rule has to change something.")
        if (rewritesTo(rule, a, b)) return OK
        return fail("${rule.title} doesn't turn ${s(a)} into ${s(b)}. It swaps ${rule.from} with ${rule.to}.")
    }

    private fun raw(rule: Rule, cited: List<Formula>, result: Formula): Check =
        if (rule.kind == RuleKind.INFERENCE) inference(rule, cited, result) else replacement(rule, cited[0], result)

    fun works(rule: Rule, cited: List<Formula>, result: Formula) = cited.size == rule.lines && raw(rule, cited, result).ok

    /** Checks one proof line. On failure, names another rule that would justify it, if any. */
    fun check(rule: Rule, cited: List<Formula>, result: Formula): Check {
        if (cited.size != rule.lines) {
            val need = if (rule.lines == 1) "one line" else "two lines"
            return fail("${rule.title} uses $need, and you cited ${cited.size}. Tap lines in the proof to cite them.")
        }
        val res = raw(rule, cited, result)
        if (res.ok) return res
        val alt = Rule.entries.firstOrNull { it != rule && works(it, cited, result) } ?: return res
        return res.copy(message = res.message + " Your line is still correct, though: it follows by ${alt.title} (${alt.abbr}).", alt = alt)
    }
}
