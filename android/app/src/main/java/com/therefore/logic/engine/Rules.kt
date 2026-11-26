package com.therefore.logic.engine

import com.therefore.logic.engine.Formula.Bin
import com.therefore.logic.engine.Formula.Not

/** ASSUMPTION opens a subproof (ACP, AIP); CLOSE ends one (CP, IP). Neither is cited by tapping lines. */
enum class RuleKind { INFERENCE, REPLACEMENT, ASSUMPTION, CLOSE }

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
    Taut("Tautology", RuleKind.REPLACEMENT, 1, "P", "P ∨ P (same for ∧)", "Saying the same thing twice adds nothing."),
    ACP("Assume for Conditional Proof", RuleKind.ASSUMPTION, 0, "anything", "an assumption P", "Suppose P, to see what follows from it."),
    CP("Conditional Proof", RuleKind.CLOSE, 0, "assume P … derive Q", "P → Q", "If assuming P leads to Q, then P → Q."),
    AIP("Assume for Indirect Proof", RuleKind.ASSUMPTION, 0, "anything", "an assumption ¬P", "Suppose the opposite of what you want to prove."),
    IP("Indirect Proof", RuleKind.CLOSE, 0, "assume ¬P … derive Q ∧ ¬Q", "P", "If an assumption leads to a contradiction, it is false."),
    UI("Universal Instantiation", RuleKind.INFERENCE, 1, "∀x Fx", "Fa (or Fx)", "If everything is F, then any particular thing is F."),
    EG("Existential Generalization", RuleKind.INFERENCE, 1, "Fa", "∃x Fx", "If this thing is F, then something is F."),
    EI("Existential Instantiation", RuleKind.INFERENCE, 1, "∃x Fx", "Fa (a new name)", "Something is F, so give it a name, but one that hasn't been used yet."),
    UG("Universal Generalization", RuleKind.INFERENCE, 1, "Fx", "∀x Fx", "What you showed for an arbitrary x holds for everything. Never from a name like a."),
    IdR("Identity: Reflexivity", RuleKind.INFERENCE, 0, "nothing", "a = a", "Anything is identical to itself. Cite no lines."),
    IdS("Identity: Symmetry", RuleKind.REPLACEMENT, 1, "a = b", "b = a", "If a is b, then b is a."),
    Id("Identity: Substitution", RuleKind.INFERENCE, 2, "Fa, a = b", "Fb", "If a and b are the same thing, whatever is true of a is true of b."),
    QN("Quantifier Negation", RuleKind.REPLACEMENT, 1, "¬∀x Fx / ¬∃x Fx", "∃x ¬Fx / ∀x ¬Fx", "\"Not everything is F\" means \"something isn't F\", and the other way round."),
    Res("Resolution", RuleKind.INFERENCE, 2, "P ∨ A, ¬P ∨ B", "A ∨ B", "P is true or false. Either way one of the leftovers holds, so cancel P and ¬P and join the rest.");

    val isStandard: Boolean get() = kind == RuleKind.INFERENCE || kind == RuleKind.REPLACEMENT

    /** The predicate-logic rules, grouped on their own in menus. */
    val isQuantifierRule: Boolean get() = this == UI || this == EG || this == EI || this == UG || this == QN

    val isIdentityRule: Boolean get() = this == IdR || this == IdS || this == Id

    /** Resolution: the one rule SAT solvers use. Taught in its own course, so kept out of the usual menus. */
    val isSatRule: Boolean get() = this == Res

    val abbr: String get() = name
}

/** Why a line was rejected, so the results screen can teach the matching lesson. */
enum class Trap { AFFIRM, DENY, DS_RIGHT, SIMP_RIGHT, ADD_RIGHT, DN, ALT, OTHER, UG_NAME, EI_NOT_NEW, WHOLE_LINE }

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
            Rule.IdS -> if (f is Formula.Eq) out += Formula.Eq(f.t, f.s)
            Rule.QN -> {
                val qf = f as? Formula.Quant
                val nq = (f as? Not)?.a as? Formula.Quant
                fun flip(q: Quantifier) = if (q == Quantifier.ALL) Quantifier.SOME else Quantifier.ALL
                // ¬∀x B ⟺ ∃x ¬B and ¬∃x B ⟺ ∀x ¬B (both directions)
                if (nq != null) out += Formula.Quant(flip(nq.q), nq.v, not(nq.body))
                if (qf != null && qf.body is Not) out += not(Formula.Quant(flip(qf.q), qf.v, qf.body.a))
                // ∀x B ⟺ ¬∃x ¬B and ∃x B ⟺ ¬∀x ¬B
                if (qf != null) out += not(Formula.Quant(flip(qf.q), qf.v, not(qf.body)))
                if (nq != null && nq.body is Not) out += Formula.Quant(flip(nq.q), nq.v, nq.body.a)
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
            a is Formula.Quant && b is Formula.Quant && a.q == b.q && a.v == b.v -> rewritesTo(rule, a.body, b.body)
            a is Bin && b is Bin && a.op == b.op -> rewritesTo(rule, a.a, b.a) && rewritesTo(rule, a.b, b.b)
            else -> false
        }
    }

    /** The literals of a clause (letters or negated letters joined by ∨), or null if [f] isn't one. */
    fun clause(f: Formula): Set<Formula>? = when {
        f is Formula.Atom -> setOf(f)
        f is Not && f.a is Formula.Atom -> setOf(f)
        f is Bin && f.op == Op.OR -> clause(f.a)?.let { a -> clause(f.b)?.let { b -> a + b } }
        else -> null
    }

    private fun complement(l: Formula) = if (l is Not) l.a else not(l)

    private fun resolution(c: List<Formula>, r: Formula): Check {
        val a = clause(c[0]); val b = clause(c[1])
        if (a == null || b == null)
            return fail("Resolution works on clauses: letters or negated letters joined by ∨, like ¬P ∨ Q. Put the line in that form first with Impl, DeM or DN.")
        val pivots = a.filter { complement(it) in b }
        if (pivots.isEmpty())
            return fail("Resolution needs a letter that is plain in one line and negated in the other, like P in one and ¬P in the other.")
        fun join(lits: Set<Formula>) = lits.reduce { x, y -> or(x, y) }
        val want = pivots.map { p -> (a - p) + (b - complement(p)) }
        val got = clause(r)
        if (got != null && want.any { it == got }) return OK
        if (want.all { it.isEmpty() })
            return fail("${s(c[0])} and ${s(c[1])} clash completely, so they can't both be true. There's nothing left to write: join them with Conj to show the contradiction.")
        if (pivots.size > 1 && got != null && got == (a - pivots.toSet()) + (b - pivots.map(::complement).toSet()))
            return fail("Resolution cancels one letter at a time. Cancelling two at once isn't valid: P ∨ Q and ¬P ∨ ¬Q are both true when P is true and Q is false.")
        val p = pivots.first()
        val w = want.first()
        return if (w.isEmpty()) fail("Cancelling ${s(p)} and ${s(complement(p))} leaves nothing at all: the two lines contradict each other.")
        else fail("Resolve on ${s(p)}: drop ${s(p)} and ${s(complement(p))}, and join everything left with ∨: ${s(join(w))}.")
    }

    private fun inference(rule: Rule, c: List<Formula>, r: Formula): Check {
        if (rule == Rule.Res) return resolution(c, r)
        // Two cited lines can fit a rule in both orders (P → Q and Q → P chain either way), so the
        // line is accepted if either order gives it; only then fall through to explain the mistake.
        if (c.size == 1) return inferenceOrdered(rule, c[0], c[0], r, single = true)
        if (rule != Rule.Conj && inferenceOrdered(rule, c[1], c[0], r).ok) return OK
        return inferenceOrdered(rule, c[0], c[1], r)
    }

    private fun inferenceOrdered(rule: Rule, first: Formula, second: Formula, r: Formula, single: Boolean = false): Check {
        val c = if (single) listOf(first) else listOf(first, second)
        val perms = if (single) listOf(first to first) else listOf(first to second, second to first)
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
            Rule.Id -> {
                for ((x, e) in perms) if (e is Formula.Eq && x != r && (substitutes(x, r, e.s, e.t) || substitutes(x, r, e.t, e.s))) return OK
                if (c.none { it is Formula.Eq }) return fail("Substitution needs an identity line like a = b as one of the lines you cite.")
                val e = c.first { it is Formula.Eq } as Formula.Eq
                return fail("Substitution swaps ${e.s} for ${e.t} (or ${e.t} for ${e.s}) in the other line. Your line has to match it except for those names.")
            }
            Rule.UI -> {
                val x = c[0] as? Formula.Quant
                if (x == null || x.q != Quantifier.ALL) return fail("Universal Instantiation works on a line that starts with ∀ as a whole, like ∀x(Fx → Gx).", Trap.WHOLE_LINE)
                val terms = r.terms() + x.v
                if (terms.any { x.body.subst(x.v, it) == r }) return OK
                return fail("Universal Instantiation drops the ∀x and puts one name or variable everywhere x was: for example ${x.body.subst(x.v, 'a')}.")
            }
            Rule.EG -> {
                val x = c[0]
                val e = r as? Formula.Quant
                if (e == null || e.q != Quantifier.SOME) return fail("Existential Generalization writes a line that starts with ∃, like ∃x Fx.")
                if ((x.terms() + e.v).any { e.body.subst(e.v, it) == x }) return OK
                return fail("For Existential Generalization, putting a name back in place of ${e.v} in ${r} must give exactly ${x}.")
            }
            Rule.EI -> {
                val x = c[0] as? Formula.Quant
                if (x == null || x.q != Quantifier.SOME) return fail("Existential Instantiation works on a line that starts with ∃ as a whole, like ∃x Fx.", Trap.WHOLE_LINE)
                val names = r.names()
                if (names.any { x.body.subst(x.v, it) == r }) return OK
                if (r.terms().filter(::isVariable).any { x.body.subst(x.v, it) == r })
                    return fail("Existential Instantiation must use a name (a constant like a or b), not a variable: you only know that some particular thing is ${x.body.show(false)}, not an arbitrary one.", Trap.EI_NOT_NEW)
                return fail("Existential Instantiation drops the ∃x and puts a new name everywhere x was: for example ${x.body.subst(x.v, 'a')}.")
            }
            Rule.UG -> {
                val x = c[0]
                val u = r as? Formula.Quant
                if (u == null || u.q != Quantifier.ALL) return fail("Universal Generalization writes a line that starts with ∀, like ∀x Fx.")
                val vars = (x.terms() + u.v).filter(::isVariable)
                if (vars.any { u.body.subst(u.v, it) == x }) return OK
                if (x.names().any { u.body.subst(u.v, it) == x })
                    return fail("You can't generalize from a name. ${x} is about one particular thing, and what's true of one thing needn't be true of everything. UG needs a line about an arbitrary x.", Trap.UG_NAME)
                return fail("For Universal Generalization, putting the variable back in place of ${u.v} in ${r} must give exactly ${x}.")
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

    fun works(rule: Rule, cited: List<Formula>, result: Formula) = rule.isStandard && cited.size == rule.lines && raw(rule, cited, result).ok

    /** Checks one proof line. On failure, names another rule that would justify it, if any. */
    /** Whether [r] is [a] with some occurrences of name/variable [s] replaced by [t]. */
    fun substitutes(a: Formula, r: Formula, s: Char, t: Char): Boolean {
        fun term(x: Char, y: Char) = x == y || (x == s && y == t)
        return when {
            a is Formula.Pred && r is Formula.Pred -> a.p == r.p && a.args.length == r.args.length && a.args.indices.all { term(a.args[it], r.args[it]) }
            a is Formula.Eq && r is Formula.Eq -> term(a.s, r.s) && term(a.t, r.t)
            a is Formula.Atom && r is Formula.Atom -> a == r
            a is Not && r is Not -> substitutes(a.a, r.a, s, t)
            a is Bin && r is Bin -> a.op == r.op && substitutes(a.a, r.a, s, t) && substitutes(a.b, r.b, s, t)
            a is Formula.Quant && r is Formula.Quant -> a.q == r.q && a.v == r.v && a.v != s && a.v != t && substitutes(a.body, r.body, s, t)
            else -> false
        }
    }

    fun check(rule: Rule, cited: List<Formula>, result: Formula): Check {
        require(rule.isStandard) { "Assumption rules are checked by Proofs.checkLine" }
        if (rule == Rule.IdR) {
            if (cited.isNotEmpty()) return fail("Reflexivity cites no lines: just write something like a = a.")
            return if (result is Formula.Eq && result.s == result.t) OK else fail("Reflexivity can only write a name identical to itself, like a = a.")
        }
        if (cited.size != rule.lines) {
            val need = if (rule.lines == 1) "one line" else "two lines"
            return fail("${rule.title} uses $need, and you cited ${cited.size}. Tap lines in the proof to cite them.")
        }
        val res = raw(rule, cited, result)
        if (res.ok) return res
        val alt = Rule.entries.firstOrNull { it != rule && !it.isSatRule && works(it, cited, result) } ?: return res
        return res.copy(message = res.message + " Your line is still correct, though: it follows by ${alt.title} (${alt.abbr}).", alt = alt)
    }
}
