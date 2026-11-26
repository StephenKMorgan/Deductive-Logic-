package com.therefore.logic.engine

import com.therefore.logic.engine.Formula.Bin
import com.therefore.logic.engine.Formula.Not
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class GoalShape { ATOM, NEG, AND, OR, IMP }

/** How the model proof reaches the conclusion: directly, or through an assumption. */
enum class Method { DIRECT, CONDITIONAL, INDIRECT }

/**
 * Ways to turn a propositional puzzle into a predicate-logic one.
 * INSTANCE: "all F are G, a is F, so a is G". UNIVERSAL: all-to-all, ends with UG.
 * EXISTENTIAL: one "some" premise, EI then EG. NEGATION: a premise written with ¬∃ / ¬∀, needs QN.
 * CONDITIONAL: one "all" premise becomes the if-part of the conclusion (∀x Fx → ∀x Gx), proved by CP.
 */
enum class Lift { INSTANCE, UNIVERSAL, EXISTENTIAL, NEGATION, CONDITIONAL }

/** How a course generates puzzles. */
data class Spec(
    val rules: List<Rule>,
    /** At least one of these must appear in the model proof (empty = any). */
    val focus: List<Rule>,
    /** Search depth at level 1 and level 5. */
    val depth: IntRange,
    /** Model-proof length bounds. */
    val steps: IntRange,
    val letters: Int,
    val negP: Double,
    val maxSize: Int,
    val stop: Double,
    val goals: Map<GoalShape, Double>,
    val method: Method = Method.DIRECT,
    /** Non-empty for predicate-logic courses: how puzzles are lifted into quantified form. */
    val lift: Set<Lift> = emptySet(),
    /** Predicate-logic puzzles built from argument patterns: "relations" or "identity". */
    val patterns: String? = null,
)

@Serializable
data class Step(val formula: Formula, val rule: Rule, val from: List<Formula>)

@Serializable
data class Puzzle(val premises: List<Formula>, val conclusion: Formula, val solution: List<Step>)

/**
 * Builds puzzles backward: start from a conclusion, pick a rule that could produce it,
 * and recurse on what that rule needs. The leaves become the premises.
 */
object Generator {
    private class Ctx(val rng: Random, val spec: Spec) {
        val letters = "PQRSTU".take(spec.letters).toList()
        val used = HashSet<Char>()
        val known = HashSet<Formula>()
        val inProgress = HashSet<Formula>()
        val premises = ArrayList<Formula>()
        val steps = ArrayList<Step>()
        /** The open assumption, if the model proof is inside a subproof. */
        var assumption: Formula? = null
    }

    private class Option(val rule: Rule, val subs: List<Formula>)

    private fun <T> Random.pick(list: List<T>) = list[nextInt(list.size)]

    private fun freshAtom(ctx: Ctx): Formula {
        val unused = ctx.letters.filter { it !in ctx.used }
        val l = if (unused.isNotEmpty() && ctx.rng.nextDouble() < 0.8) ctx.rng.pick(unused) else ctx.rng.pick(ctx.letters)
        ctx.used += l
        return atom(l)
    }

    private fun fresh(ctx: Ctx): Formula {
        val a = freshAtom(ctx)
        return if (ctx.rng.nextDouble() < ctx.spec.negP) not(a) else a
    }

    /** Every sub-formula with its path ('a' = left/only child, 'b' = right). */
    private fun positions(f: Formula, path: List<Char> = emptyList(), out: MutableList<Pair<List<Char>, Formula>> = ArrayList()): List<Pair<List<Char>, Formula>> {
        out += path to f
        when (f) {
            is Not -> positions(f.a, path + 'a', out)
            is Bin -> { positions(f.a, path + 'a', out); positions(f.b, path + 'b', out) }
            else -> {}
        }
        return out
    }

    private fun setAt(f: Formula, path: List<Char>, g: Formula): Formula {
        if (path.isEmpty()) return g
        val rest = path.drop(1)
        return when (f) {
            is Not -> Not(setAt(f.a, rest, g))
            is Bin -> if (path[0] == 'a') f.copy(a = setAt(f.a, rest, g)) else f.copy(b = setAt(f.b, rest, g))
            else -> f
        }
    }

    /** Silly shapes: P ∧ P, P → ¬P, ¬¬¬P … */
    private fun degenerate(f: Formula, allowSame: Boolean): Boolean = when (f) {
        is Not -> (f.a is Not && f.a.a is Not) || degenerate(f.a, allowSame)
        is Bin -> (!allowSame && f.a == f.b) || f.a == not(f.b) || not(f.a) == f.b ||
            degenerate(f.a, allowSame) || degenerate(f.b, allowSame)
        else -> false
    }

    private fun options(ctx: Ctx, g: Formula): List<Option> {
        val allow = ctx.spec.rules.toSet()
        val opts = ArrayList<Option>()
        val gb = g as? Bin
        if (Rule.MP in allow) {
            // Inside a subproof, lean on the assumption so the subproof actually needs it.
            val asm = ctx.assumption
            val a = if (asm != null && asm != g && ctx.rng.nextDouble() < 0.5) asm else fresh(ctx)
            opts += Option(Rule.MP, listOf(imp(a, g), a))
        }
        if (Rule.MT in allow && g is Not) { val b = fresh(ctx); opts += Option(Rule.MT, listOf(imp(g.a, b), not(b))) }
        if (Rule.HS in allow && gb?.op == Op.IMP) { val b = fresh(ctx); opts += Option(Rule.HS, listOf(imp(gb.a, b), imp(b, gb.b))) }
        if (Rule.DS in allow) {
            val asm = ctx.assumption
            val a = if (asm is Not && asm.a != g && ctx.rng.nextDouble() < 0.4) asm.a else freshAtom(ctx)
            opts += Option(Rule.DS, listOf(or(a, g), not(a)))
        }
        if (Rule.Simp in allow) { val b = fresh(ctx); opts += Option(Rule.Simp, listOf(and(g, b))) }
        if (Rule.Conj in allow && gb?.op == Op.AND) opts += Option(Rule.Conj, listOf(gb.a, gb.b))
        if (Rule.Add in allow && gb?.op == Op.OR) opts += Option(Rule.Add, listOf(gb.a))
        if (Rule.CD in allow && gb?.op == Op.OR) {
            val c = freshAtom(ctx); val d = freshAtom(ctx)
            opts += Option(Rule.CD, listOf(and(imp(c, gb.a), imp(d, gb.b)), or(c, d)))
        }
        for (rule in ctx.spec.rules) {
            if (rule.kind != RuleKind.REPLACEMENT) continue
            val cands = ArrayList<Formula>()
            for ((path, sub) in positions(g)) for (w in Checker.rootRewrites(rule, sub)) {
                if (rule == Rule.Taut && w.size > sub.size && ctx.rng.nextDouble() < 0.5) continue
                cands += setAt(g, path, w)
            }
            // DN: prefer stripping a ¬¬ the proof needs; only sometimes put ¬¬ into a premise
            val pool = if (rule == Rule.DN) {
                val shrink = cands.filter { it.size < g.size }
                if (shrink.isNotEmpty()) shrink else if (ctx.rng.nextDouble() < 0.2) cands else emptyList()
            } else cands
            if (pool.isNotEmpty()) opts += Option(rule, listOf(ctx.rng.pick(pool)))
        }
        return opts.filter { o ->
            o.subs.all { x -> x.size <= ctx.spec.maxSize && !degenerate(x, o.rule == Rule.Taut) && x !in ctx.inProgress && x != g }
        }
    }

    private fun weight(ctx: Ctx, r: Rule) = when {
        r in ctx.spec.focus -> 5.0
        r == Rule.Taut -> 0.15
        r.kind == RuleKind.REPLACEMENT -> 0.6
        else -> 1.5
    }

    private fun derive(ctx: Ctx, g: Formula, d: Int, top: Boolean) {
        if (g in ctx.known) return
        val opts = if (d > 0 && (top || ctx.rng.nextDouble() > ctx.spec.stop)) options(ctx, g) else emptyList()
        if (opts.isEmpty()) { ctx.known += g; ctx.premises += g; return }
        var x = ctx.rng.nextDouble() * opts.sumOf { weight(ctx, it.rule) }
        var choice = opts[0]
        for (o in opts) { x -= weight(ctx, o.rule); if (x <= 0) { choice = o; break } }
        ctx.inProgress += g
        for (sub in choice.subs) derive(ctx, sub, d - 1 - (if (ctx.rng.nextDouble() < 0.25) 1 else 0), false)
        ctx.inProgress -= g
        ctx.steps += Step(g, choice.rule, choice.subs)
        ctx.known += g
    }

    private fun pickGoal(ctx: Ctx): Formula {
        var x = ctx.rng.nextDouble() * ctx.spec.goals.values.sum()
        var shape = GoalShape.ATOM
        for ((k, w) in ctx.spec.goals) { x -= w; if (x <= 0) { shape = k; break } }
        val a = freshAtom(ctx)
        return when (shape) {
            GoalShape.ATOM -> a
            GoalShape.NEG -> not(a)
            else -> {
                var b = freshAtom(ctx)
                // Never P → P, P ∧ P and the like.
                if (b == a) b = atom(ctx.letters.filter { it != (a as Formula.Atom).v }.let { it[ctx.rng.nextInt(it.size)] })
                val left = if (ctx.rng.nextDouble() < 0.2) not(a) else a
                Bin(when (shape) { GoalShape.AND -> Op.AND; GoalShape.OR -> Op.OR; else -> Op.IMP }, left, b)
            }
        }
    }

    private fun depthFor(spec: Spec, level: Int): Int {
        val span = spec.depth.last - spec.depth.first + 1
        return maxOf(1, spec.depth.first + (span * (level - 1)) / 5)
    }

    /** Checks shared by every method: sizes, consistency, focus rules, and that every model step is valid. */
    private fun acceptable(spec: Spec, ctx: Ctx, steps: List<Step>, conclusion: Formula, level: Int): Boolean {
        val minSteps = spec.steps.first + (level - 1) / 2
        val n = steps.size
        if (n < minOf(minSteps, spec.steps.last) || n > spec.steps.last) return false
        if (ctx.premises.size !in 2..6) return false
        if (spec.focus.isNotEmpty() && steps.none { it.rule in spec.focus }) return false
        if (!satisfiable(ctx.premises)) return false
        if (ctx.premises.any { !satisfiable(listOf(not(it))) }) return false
        if (conclusion in ctx.premises) return false
        return true
    }

    /** Whether one standard rule gets from the premises straight to the conclusion. */
    private fun oneStep(premises: List<Formula>, conclusion: Formula): Boolean {
        val rules = Rule.entries.filter { it.isStandard && !it.isSatRule }
        for (p in premises) if (rules.any { Checker.works(it, listOf(p), conclusion) }) return true
        for (p in premises) for (q in premises) if (p != q && rules.any { Checker.works(it, listOf(p, q), conclusion) }) return true
        return false
    }

    private fun verify(puzzle: Puzzle): Boolean {
        var lines = puzzle.premises.map { ProofLine(it, null) }
        for (st in puzzle.solution) {
            val sc = Proofs.scopes(lines)
            val cites = if (st.rule.isStandard) st.from.map { f ->
                (1..lines.size).lastOrNull { sc.available(it) && lines[it - 1].formula == f } ?: return false
            } else emptyList()
            val r = Proofs.checkLine(lines, st.rule, cites, st.formula, puzzle.conclusion)
            if (!r.ok) return false
            lines = lines + ProofLine(st.formula, st.rule, r.cites)
        }
        return lines.last().formula == puzzle.conclusion && Proofs.scopes(lines).open.isEmpty()
    }

    /** Prove A → X by assuming A and deriving X with its help. */
    private fun attemptConditional(spec: Spec, rng: Random, level: Int): Puzzle? {
        val ctx = Ctx(rng, spec)
        val a = fresh(ctx)
        var x = pickGoal(ctx)
        if (x == a || x == not(a) || a == not(x)) x = freshAtom(ctx)
        ctx.assumption = a
        ctx.known += a
        derive(ctx, x, depthFor(spec, level), true)
        if (ctx.steps.none { a in it.from }) return null
        val conclusion = imp(a, x)
        val steps = listOf(Step(a, Rule.ACP, emptyList())) + ctx.steps + Step(conclusion, Rule.CP, emptyList())
        if (!acceptable(spec, ctx, steps, conclusion, level) || oneStep(ctx.premises, conclusion)) return null
        return Puzzle(ctx.premises.shuffled(rng), conclusion, steps).takeIf(::verify)
    }

    /** Prove G by assuming its opposite and deriving a contradiction X ∧ ¬X. */
    private fun attemptIndirect(spec: Spec, rng: Random, level: Int): Puzzle? {
        val ctx = Ctx(rng, spec)
        val g = if (rng.nextBoolean()) freshAtom(ctx) else not(freshAtom(ctx))
        val a = if (g is Not) g.a else not(g)
        var x = freshAtom(ctx)
        if (x == g || x == a || not(x) == a) x = freshAtom(ctx)
        ctx.assumption = a
        ctx.known += a
        val d = maxOf(1, depthFor(spec, level) - 1)
        derive(ctx, x, d, true)
        derive(ctx, not(x), d, true)
        if (ctx.steps.none { a in it.from }) return null
        val contradiction = and(x, not(x))
        val steps = listOf(Step(a, Rule.AIP, emptyList())) + ctx.steps +
            Step(contradiction, Rule.Conj, listOf(x, not(x))) + Step(g, Rule.IP, emptyList())
        if (!acceptable(spec, ctx, steps, g, level) || oneStep(ctx.premises, g)) return null
        return Puzzle(ctx.premises.shuffled(rng), g, steps).takeIf(::verify)
    }

    private fun attempt(spec: Spec, rng: Random, level: Int): Puzzle? = when (spec.method) {
        Method.CONDITIONAL -> attemptConditional(spec, rng, level)
        Method.INDIRECT -> attemptIndirect(spec, rng, level)
        Method.DIRECT -> attemptDirect(spec, rng, level)
    }

    private fun attemptDirect(spec: Spec, rng: Random, level: Int): Puzzle? {
        val ctx = Ctx(rng, spec)
        val goal = pickGoal(ctx)
        val span = spec.depth.last - spec.depth.first + 1
        val depth = spec.depth.first + (span * (level - 1)) / 5
        derive(ctx, goal, maxOf(1, depth), true)
        val minSteps = spec.steps.first + (level - 1) / 2
        val n = ctx.steps.size
        if (n < minOf(minSteps, spec.steps.last) || n > spec.steps.last) return null
        if (ctx.premises.size !in 2..6) return null
        if (spec.focus.isNotEmpty() && ctx.steps.none { it.rule in spec.focus }) return null
        if (!satisfiable(ctx.premises)) return null
        if (ctx.premises.any { !satisfiable(listOf(not(it))) }) return null // no tautological premises
        if (ctx.steps.any { !Checker.check(it.rule, it.from, it.formula).ok }) return null
        return Puzzle(ctx.premises.shuffled(rng), goal, ctx.steps.toList())
    }

    // ---------------- Predicate logic ----------------

    private val PREDICATE_FOR = mapOf('P' to 'F', 'Q' to 'G', 'R' to 'H', 'S' to 'K', 'T' to 'L', 'U' to 'M')

    /** Replaces each statement letter with a predicate applied to [t]: P becomes Ft, and so on. */
    private fun Formula.lifted(t: Char): Formula = when (this) {
        is Formula.Atom -> pred(PREDICATE_FOR.getValue(v), t)
        is Not -> Not(a.lifted(t))
        is Bin -> copy(a = a.lifted(t), b = b.lifted(t))
        else -> this
    }

    private fun Step.lifted(t: Char) = Step(formula.lifted(t), rule, from.map { it.lifted(t) })

    private fun attemptPredicate(spec: Spec, rng: Random, level: Int): Puzzle? {
        val inner = spec.copy(
            rules = spec.rules.filter { it.isStandard && !it.isQuantifierRule },
            focus = emptyList(), lift = emptySet(), method = Method.DIRECT, letters = minOf(spec.letters, 4),
        )
        val base = attemptDirect(inner, rng, level) ?: return null
        val lift = spec.lift.toList()[rng.nextInt(spec.lift.size)]
        val x = 'x'
        val a = 'a'
        val puzzle = when (lift) {
            Lift.INSTANCE -> {
                // Some premises become "all" statements; the rest are about a.
                val general = base.premises.map { rng.nextDouble() < 0.6 }.toMutableList()
                if (general.none { it }) general[rng.nextInt(general.size)] = true
                val premises = base.premises.mapIndexed { i, p -> if (general[i]) all(x, p.lifted(x)) else p.lifted(a) }
                val ui = base.premises.withIndex().filter { general[it.index] }.map { (_, p) -> Step(p.lifted(a), Rule.UI, listOf(all(x, p.lifted(x)))) }
                Puzzle(premises, base.conclusion.lifted(a), ui + base.solution.map { it.lifted(a) })
            }
            Lift.UNIVERSAL -> universalOrExistential(base, rng, existential = false)
            Lift.EXISTENTIAL -> universalOrExistential(base, rng, existential = true)
            // Either kind, with one premise disguised as "nothing is…".
            Lift.NEGATION -> negateOnePremise(universalOrExistential(base, rng, existential = rng.nextBoolean()), rng) ?: return null
            Lift.CONDITIONAL -> conditional(base, rng) ?: return null
        }
        if (!satisfiable(puzzle.premises)) return null
        if (puzzle.conclusion in puzzle.premises) return null
        if (spec.focus.isNotEmpty() && puzzle.solution.none { it.rule in spec.focus }) return null
        return Puzzle(puzzle.premises.shuffled(rng), puzzle.conclusion, puzzle.solution).takeIf(::verify)
    }

    /** All premises universal (or one existential), conclusion universal (or existential). */
    private fun universalOrExistential(base: Puzzle, rng: Random, existential: Boolean): Puzzle {
        val x = 'x'
        if (!existential) {
            val premises = base.premises.map { all(x, it.lifted(x)) }
            val ui = base.premises.map { Step(it.lifted(x), Rule.UI, listOf(all(x, it.lifted(x)))) }
            val c = base.conclusion.lifted(x)
            return Puzzle(premises, all(x, c), ui + base.solution.map { it.lifted(x) } + Step(all(x, c), Rule.UG, listOf(c)))
        }
        val a = 'a'
        val e = rng.nextInt(base.premises.size)
        val premises = base.premises.mapIndexed { i, p -> if (i == e) some(x, p.lifted(x)) else all(x, p.lifted(x)) }
        // EI first (the name must be new), then UI the "all" premises with the same name.
        val ei = Step(base.premises[e].lifted(a), Rule.EI, listOf(some(x, base.premises[e].lifted(x))))
        val ui = base.premises.withIndex().filter { it.index != e }.map { (_, p) -> Step(p.lifted(a), Rule.UI, listOf(all(x, p.lifted(x)))) }
        val c = base.conclusion.lifted(a)
        val conclusion = some(x, base.conclusion.lifted(x))
        return Puzzle(premises, conclusion, listOf(ei) + ui + base.solution.map { it.lifted(a) } + Step(conclusion, Rule.EG, listOf(c)))
    }

    /** Moves one premise into the conclusion: from the rest, prove ∀x p(x) → ∀x c(x) by Conditional Proof. */
    private fun conditional(base: Puzzle, rng: Random): Puzzle? {
        if (base.premises.size < 2) return null
        val x = 'x'
        val k = rng.nextInt(base.premises.size)
        val moved = all(x, base.premises[k].lifted(x))
        val rest = base.premises.filterIndexed { i, _ -> i != k }
        val c = base.conclusion.lifted(x)
        val conclusion = imp(moved, all(x, c))
        val steps = listOf(Step(moved, Rule.ACP, emptyList()), Step(base.premises[k].lifted(x), Rule.UI, listOf(moved))) +
            rest.map { Step(it.lifted(x), Rule.UI, listOf(all(x, it.lifted(x)))) } +
            base.solution.map { it.lifted(x) } +
            Step(all(x, c), Rule.UG, listOf(c)) + Step(conclusion, Rule.CP, emptyList())
        return Puzzle(rest.map { all(x, it.lifted(x)) }, conclusion, steps)
    }

    // ---------------- Relations (two-place predicates) ----------------

    /** An argument pattern: premises, conclusion, and its proof as (line, justification) pairs. */
    private class Pattern(val minLevel: Int, val premises: List<String>, val conclusion: String, val proof: List<Pair<String, String>>)

    private val RELATION_PATTERNS = listOf(
        // Symmetry
        Pattern(1, listOf("∀x∀y(Lxy → Lyx)", "Lab"), "Lba", listOf("∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "3, UI", "Lba" to "4, 2, MP")),
        // Relation implies a property
        Pattern(1, listOf("∀x∀y(Lxy → Fx)", "Lab"), "Fa", listOf("∀y(Lay → Fa)" to "1, UI", "Lab → Fa" to "3, UI", "Fa" to "4, 2, MP")),
        // Everyone F relates to everything
        Pattern(1, listOf("∀x(Fx → ∀y Lxy)", "Fa"), "Lab", listOf("Fa → ∀y Lay" to "1, UI", "∀y Lay" to "3, 2, MP", "Lab" to "4, UI")),
        Pattern(2, listOf("∀x(Fx → ∃y Lxy)", "Fa"), "∃y Lay", listOf("Fa → ∃y Lay" to "1, UI", "∃y Lay" to "3, 2, MP")),
        // F things relate to G things
        Pattern(2, listOf("∀x(Fx → ∀y(Gy → Lxy))", "Fa", "Gb"), "Lab",
            listOf("Fa → ∀y(Gy → Lay)" to "1, UI", "∀y(Gy → Lay)" to "4, 2, MP", "Gb → Lab" to "5, UI", "Lab" to "6, 3, MP")),
        // Transitivity
        Pattern(3, listOf("∀x∀y∀z((Lxy ∧ Lyz) → Lxz)", "Lab", "Lbc"), "Lac",
            listOf("∀y∀z((Lay ∧ Lyz) → Laz)" to "1, UI", "∀z((Lab ∧ Lbz) → Laz)" to "4, UI", "(Lab ∧ Lbc) → Lac" to "5, UI", "Lab ∧ Lbc" to "2, 3, Conj", "Lac" to "6, 7, MP")),
        // Symmetry with "some"
        Pattern(3, listOf("∀x∀y(Lxy → Lyx)", "∃y Lay"), "∃y Lya",
            listOf("Lab" to "2, EI", "∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "4, UI", "Lba" to "5, 3, MP", "∃y Lya" to "6, EG")),
        // Quantifier order: the valid direction
        Pattern(3, listOf("∃x∀y Lxy"), "∀y∃x Lxy", listOf("∀y Lay" to "1, EI", "Lay" to "2, UI", "∃x Lxy" to "3, EG", "∀y∃x Lxy" to "4, UG")),
        // Asymmetric relations are irreflexive
        Pattern(4, listOf("∀x∀y(Lxy → ¬Lyx)"), "¬Laa", listOf("∀y(Lay → ¬Lya)" to "1, UI", "Laa → ¬Laa" to "2, UI", "¬Laa ∨ ¬Laa" to "3, Impl", "¬Laa" to "4, Taut")),
        // Everyone relates to someone F, a relates to nothing: so a isn't... (MT with quantified line)
        Pattern(4, listOf("∀x(Fx → ∃y Lxy)", "¬∃y Lay"), "¬Fa", listOf("Fa → ∃y Lay" to "1, UI", "¬Fa" to "3, 2, MT")),
        Pattern(5, listOf("∀x∀y(Lxy → Lyx)", "∀x(∃y Lxy → Fx)", "Lab"), "Fb",
            listOf("∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "4, UI", "Lba" to "5, 3, MP", "∃y Lby" to "6, EG", "∃y Lby → Fb" to "2, UI", "Fb" to "8, 7, MP")),
    )

    private val IDENTITY_PATTERNS = listOf(
        Pattern(1, listOf("Fa", "a = b"), "Fb", listOf("Fb" to "1, 2, Id")),
        Pattern(1, listOf("a = b", "Ga"), "Gb ∧ b = a", listOf("Gb" to "2, 1, Id", "b = a" to "1, IdS", "Gb ∧ b = a" to "3, 4, Conj")),
        Pattern(2, listOf("∀x(Fx → Gx)", "Fa", "a = b"), "Gb", listOf("Fa → Ga" to "1, UI", "Ga" to "4, 2, MP", "Gb" to "5, 3, Id")),
        Pattern(2, listOf("a = b", "b = c"), "a = c", listOf("a = c" to "1, 2, Id")),
        Pattern(3, listOf("∀x(x = a → Fx)"), "Fa", listOf("a = a → Fa" to "1, UI", "a = a" to "IdR", "Fa" to "2, 3, MP")),
        Pattern(3, listOf("Fa", "¬Fb"), "¬(a = b)", listOf("a = b" to "AIP", "Fb" to "1, 3, Id", "Fb ∧ ¬Fb" to "4, 2, Conj", "¬(a = b)" to "IP")),
        Pattern(4, listOf("∃x(Fx ∧ x = a)"), "Fa", listOf("Fb ∧ b = a" to "1, EI", "Fb" to "2, Simp", "b = a ∧ Fb" to "2, Com", "b = a" to "4, Simp", "Fa" to "3, 5, Id")),
        Pattern(4, listOf("∀x(Fx → x = a)", "Fb"), "b = a", listOf("Fb → b = a" to "1, UI", "b = a" to "3, 2, MP")),
        Pattern(5, listOf("∀x∀y((Fx ∧ Fy) → x = y)", "Fa", "Fb"), "a = b",
            listOf("∀y((Fa ∧ Fy) → a = y)" to "1, UI", "(Fa ∧ Fb) → a = b" to "4, UI", "Fa ∧ Fb" to "2, 3, Conj", "a = b" to "5, 6, MP")),
        Pattern(5, listOf("Fa", "¬Ga", "Gb"), "¬(a = b)", listOf("a = b" to "AIP", "Ga" to "3, 4, Id", "Ga ∧ ¬Ga" to "5, 2, Conj", "¬(a = b)" to "IP")),
    )

    /** Definite descriptions ("the F"), counting, and proofs with several quantifiers. */
    private val DESCRIPTION_PATTERNS = listOf(
        Pattern(1, listOf("∀x∀y(Lxy → Lyx)", "Lab"), "Lba", listOf("∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "3, UI", "Lba" to "4, 2, MP")),
        Pattern(2, listOf("∃x((Fx ∧ ∀y(Fy → y = x)) ∧ Gx)"), "∃x(Fx ∧ Gx)", listOf(
            "(Fa ∧ ∀y(Fy → y = a)) ∧ Ga" to "1, EI", "Fa ∧ ∀y(Fy → y = a)" to "2, Simp", "Fa" to "3, Simp",
            "Ga ∧ (Fa ∧ ∀y(Fy → y = a))" to "2, Com", "Ga" to "5, Simp", "Fa ∧ Ga" to "4, 6, Conj", "∃x(Fx ∧ Gx)" to "7, EG",
        )),
        Pattern(3, listOf("∃x((Fx ∧ ∀y(Fy → y = x)) ∧ Gx)", "Fb"), "Gb", listOf(
            "(Fa ∧ ∀y(Fy → y = a)) ∧ Ga" to "1, EI", "Fa ∧ ∀y(Fy → y = a)" to "3, Simp", "∀y(Fy → y = a) ∧ Fa" to "4, Com",
            "∀y(Fy → y = a)" to "5, Simp", "Fb → b = a" to "6, UI", "b = a" to "7, 2, MP",
            "Ga ∧ (Fa ∧ ∀y(Fy → y = a))" to "3, Com", "Ga" to "9, Simp", "Gb" to "10, 8, Id",
        )),
        Pattern(4, listOf("∀x∀y((Fx ∧ Fy) → x = y)", "Fa", "¬(a = b)"), "¬Fb", listOf(
            "Fb" to "AIP", "∀y((Fa ∧ Fy) → a = y)" to "1, UI", "(Fa ∧ Fb) → a = b" to "5, UI", "Fa ∧ Fb" to "2, 4, Conj",
            "a = b" to "6, 7, MP", "(a = b) ∧ ¬(a = b)" to "8, 3, Conj", "¬Fb" to "IP",
        )),
        Pattern(5, listOf("∀x∀y(Lxy → Lyx)", "∀x∀y∀z((Lxy ∧ Lyz) → Lxz)", "Lab"), "Laa", listOf(
            "∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "4, UI", "Lba" to "5, 3, MP", "∀y∀z((Lay ∧ Lyz) → Laz)" to "2, UI",
            "∀z((Lab ∧ Lbz) → Laz)" to "7, UI", "(Lab ∧ Lba) → Laa" to "8, UI", "Lab ∧ Lba" to "3, 6, Conj", "Laa" to "9, 10, MP",
        )),
    )

    /** Builds a puzzle from an argument pattern, with the letters and names shuffled. */
    private fun attemptPattern(spec: Spec, rng: Random, level: Int): Puzzle? {
        val all = when (spec.patterns) { "identity" -> IDENTITY_PATTERNS; "descriptions" -> DESCRIPTION_PATTERNS; else -> RELATION_PATTERNS }
        val options = all.filter { it.minLevel <= level && it.minLevel >= level - 2 }.ifEmpty { all }
        val pat = options[rng.nextInt(options.size)]
        val rel = "LRK"[rng.nextInt(3)]
        val props = "FGH".toList().shuffled(rng)
        val names = "abc".toList().shuffled(rng)
        fun map(t: String) = t.map { ch ->
            when (ch) { 'L' -> rel; 'F' -> props[0]; 'G' -> props[1]; 'a' -> names[0]; 'b' -> names[1]; 'c' -> names[2]; else -> ch }
        }.joinToString("")
        val premises = pat.premises.map { Parser.parseOrNull(map(it)) ?: return null }
        val conclusion = Parser.parseOrNull(map(pat.conclusion)) ?: return null
        val lines = premises.toMutableList()
        val steps = pat.proof.map { (fs, just) ->
            val parts = just.split(",").map { it.trim() }
            val formula = Parser.parseOrNull(map(fs)) ?: return null
            val step = Step(formula, Rule.valueOf(parts.last()), parts.dropLast(1).map { lines[it.toInt() - 1] })
            lines += formula
            step
        }
        return Puzzle(premises.shuffled(rng), conclusion, steps).takeIf(::verify)
    }

    /** Rewrites one "all" premise as "nothing is…" (¬∃), so the proof needs QN first. */
    private fun negateOnePremise(p: Puzzle, rng: Random): Puzzle? {
        val candidates = p.premises.withIndex().filter { (it.value as? Formula.Quant)?.q == Quantifier.ALL }
        if (candidates.isEmpty()) return null
        val (i, f) = candidates[rng.nextInt(candidates.size)]
        val q = f as Formula.Quant
        val disguised = if (q.body is Not) not(some(q.v, q.body.a)) else not(some(q.v, not(q.body)))
        val premises = p.premises.toMutableList().apply { this[i] = disguised }
        return Puzzle(premises, p.conclusion, listOf(Step(f, Rule.QN, listOf(disguised))) + p.solution)
    }

    // ---------------- Clause puzzles (the SAT course) ----------------

    /** Turning a formula into clauses: written with A, B, C, which are swapped for random letters. */
    private val CNF_PATTERNS = listOf(
        Pattern(1, listOf("¬(A ∧ ¬B)"), "¬A ∨ B", listOf("¬A ∨ ¬¬B" to "1, DeM", "¬A ∨ B" to "2, DN")),
        Pattern(1, listOf("A ∨ (B ∧ ¬C)"), "(A ∨ B) ∧ (A ∨ ¬C)", listOf("(A ∨ B) ∧ (A ∨ ¬C)" to "1, Dist")),
        Pattern(2, listOf("A → (B ∧ C)"), "(¬A ∨ B) ∧ (¬A ∨ C)", listOf("¬A ∨ (B ∧ C)" to "1, Impl", "(¬A ∨ B) ∧ (¬A ∨ C)" to "2, Dist")),
        Pattern(2, listOf("¬(A → B)"), "A ∧ ¬B", listOf("¬(¬A ∨ B)" to "1, Impl", "¬¬A ∧ ¬B" to "2, DeM", "A ∧ ¬B" to "3, DN")),
        Pattern(3, listOf("(A ∧ B) → C"), "¬A ∨ (¬B ∨ C)", listOf("¬(A ∧ B) ∨ C" to "1, Impl", "(¬A ∨ ¬B) ∨ C" to "2, DeM", "¬A ∨ (¬B ∨ C)" to "3, Assoc")),
        Pattern(3, listOf("¬(A ∨ B) ∨ C"), "(C ∨ ¬A) ∧ (C ∨ ¬B)", listOf("(¬A ∧ ¬B) ∨ C" to "1, DeM", "C ∨ (¬A ∧ ¬B)" to "2, Com", "(C ∨ ¬A) ∧ (C ∨ ¬B)" to "3, Dist")),
        Pattern(4, listOf("A → ¬(B ∨ C)"), "(¬A ∨ ¬B) ∧ (¬A ∨ ¬C)", listOf("¬A ∨ ¬(B ∨ C)" to "1, Impl", "¬A ∨ (¬B ∧ ¬C)" to "2, DeM", "(¬A ∨ ¬B) ∧ (¬A ∨ ¬C)" to "3, Dist")),
        Pattern(5, listOf("(A ∨ B) → C"), "(¬A ∨ C) ∧ (¬B ∨ C)", listOf("¬(A ∨ B) ∨ C" to "1, Impl", "(¬A ∧ ¬B) ∨ C" to "2, DeM", "C ∨ (¬A ∧ ¬B)" to "3, Com", "(C ∨ ¬A) ∧ (C ∨ ¬B)" to "4, Dist", "(¬A ∨ C) ∧ (C ∨ ¬B)" to "5, Com", "(¬A ∨ C) ∧ (¬B ∨ C)" to "6, Com")),
    )

    private fun attemptCnf(rng: Random, level: Int): Puzzle? {
        val options = CNF_PATTERNS.filter { it.minLevel <= level && it.minLevel >= level - 2 }.ifEmpty { CNF_PATTERNS }
        val pat = options[rng.nextInt(options.size)]
        val letters = "PQRS".toList().shuffled(rng)
        fun map(t: String) = t.map { when (it) { 'A' -> letters[0]; 'B' -> letters[1]; 'C' -> letters[2]; else -> it } }.joinToString("")
        val premises = pat.premises.map { Parser.parseOrNull(map(it)) ?: return null }
        val lines = premises.toMutableList()
        val steps = pat.proof.map { (fs, just) ->
            val parts = just.split(",").map { it.trim() }
            val formula = Parser.parseOrNull(map(fs)) ?: return null
            Step(formula, Rule.valueOf(parts.last()), parts.dropLast(1).map { lines[it.toInt() - 1] }).also { lines += formula }
        }
        return Puzzle(premises, Parser.parseOrNull(map(pat.conclusion)) ?: return null, steps).takeIf(::verify)
    }

    /**
     * A resolution puzzle, built backward like the others: start from the clause to prove, split it into two
     * clauses that resolve to it on a new letter, and repeat. The leaves are the premises. From level 3 one
     * premise is written as a conditional or a negated "and", so it has to be put in clause form first.
     */
    private fun attemptResolution(rng: Random, level: Int): Puzzle? {
        val letters = "PQRSTU".toList()
        val used = HashSet<Char>()
        fun letter(of: Formula) = ((of as? Formula.Not)?.a ?: of).let { (it as Formula.Atom).v }
        fun fresh(exclude: Set<Char>): Char? {
            val ok = letters.filter { it !in exclude }
            if (ok.isEmpty()) return null
            val unused = ok.filter { it !in used }
            return (if (unused.isNotEmpty()) unused else ok).let { it[rng.nextInt(it.size)] }.also { used += it }
        }
        fun lit(c: Char) = if (rng.nextDouble() < 0.4) not(atom(c)) else atom(c)

        class Node(var lits: List<Formula>, var kids: Pair<Node, Node>? = null)
        val root = Node(List(if (rng.nextBoolean()) 1 else 2) { 0 }.fold(emptyList()) { acc, _ ->
            acc + lit(fresh(acc.map(::letter).toSet()) ?: return null)
        })
        val leaves = mutableListOf(root)
        val wanted = (level + rng.nextInt(2)).coerceIn(1, 5)
        repeat(wanted) {
            // Prefer extending recent leaves, which makes chains rather than flat fans.
            val leaf = leaves[maxOf(0, leaves.size - 1 - rng.nextInt(minOf(2, leaves.size)))]
            val x = fresh(leaf.lits.map(::letter).toSet()) ?: return null
            val left = ArrayList<Formula>(); val right = ArrayList<Formula>()
            leaf.lits.forEach { if (rng.nextBoolean()) left += it else right += it }
            val plainLeft = rng.nextBoolean()
            val l = Node(left + (if (plainLeft) atom(x) else not(atom(x))))
            val r = Node(right + (if (plainLeft) not(atom(x)) else atom(x)))
            if (l.lits.size > 3 || r.lits.size > 3) return null
            leaf.kids = l to r
            leaves.remove(leaf); leaves += listOf(l, r).shuffled(rng)
        }
        if (leaves.size !in 2..6) return null
        // Every clause in the tree must be different, or the puzzle has shortcuts.
        val all = ArrayList<Node>()
        fun collect(n: Node) { all += n; n.kids?.let { collect(it.first); collect(it.second) } }
        collect(root)
        if (all.map { it.lits.toSet() }.distinct().size != all.size) return null

        // Optionally disguise one two-literal premise, putting its literals in the order Impl/DeM produce.
        var disguise: Pair<Node, Formula>? = null
        if (level >= 3 && rng.nextDouble() < 0.7) {
            val cand = leaves.filter { n -> n.lits.size == 2 && n.lits.any { it is Formula.Not } }
            if (cand.isNotEmpty()) {
                val n = cand[rng.nextInt(cand.size)]
                n.lits = n.lits.sortedBy { if (it is Formula.Not) 0 else 1 }
                val (p, q) = n.lits
                disguise = n to if (q is Formula.Not) not(Formula.Bin(Op.AND, (p as Formula.Not).a, q.a)) else Formula.Bin(Op.IMP, (p as Formula.Not).a, q)
            }
        }
        fun formula(n: Node) = n.lits.reduce { a, b -> or(a, b) }

        val steps = ArrayList<Step>()
        disguise?.let { (n, d) -> steps += Step(formula(n), if (d is Formula.Not) Rule.DeM else Rule.Impl, listOf(d)) }
        fun emit(n: Node) {
            val (a, b) = n.kids ?: return
            emit(a); emit(b)
            steps += Step(formula(n), Rule.Res, listOf(formula(a), formula(b)))
        }
        emit(root)
        val premises = leaves.map { if (it == disguise?.first) disguise!!.second else formula(it) }
        val conclusion = formula(root)
        if (conclusion in premises || !satisfiable(premises)) return null
        return Puzzle(premises, conclusion, steps).takeIf(::verify)
    }

    fun generate(spec: Spec, level: Int, seed: Long): Puzzle {
        val rng = Random(seed)
        if (spec.patterns == "resolution") {
            // Free play on the clause-form lesson practises conversions; everything else practises resolution.
            val cnfOnly = spec.focus.isNotEmpty() && Rule.Res !in spec.focus
            repeat(3000) { i -> (if (cnfOnly) attemptCnf(rng, level) else attemptResolution(rng, if (i < 400) level else 1))?.let { return it } }
            error("No resolution puzzle verified")
        }
        if (spec.patterns != null) {
            repeat(200) { attemptPattern(spec, rng, level)?.let { return it } }
            error("No ${spec.patterns} pattern verified")
        }
        if (spec.lift.isNotEmpty()) {
            repeat(3000) { i -> attemptPredicate(spec, rng, if (i < 400) level else 1)?.let { return it } }
            return generate(spec, 1, seed + 7919)
        }
        repeat(3000) { i -> attempt(spec, rng, if (i < 400) level else 1)?.let { return it } }
        // Practically unreachable; fall back to a fresh seed rather than fail.
        return generate(spec, 1, seed + 7919)
    }
}
