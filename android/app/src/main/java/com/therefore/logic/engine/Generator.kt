package com.therefore.logic.engine

import com.therefore.logic.engine.Formula.Bin
import com.therefore.logic.engine.Formula.Not
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class GoalShape { ATOM, NEG, AND, OR, IMP }

/** How the model proof reaches the conclusion: directly, or through an assumption. */
enum class Method { DIRECT, CONDITIONAL, INDIRECT }

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
                val b = freshAtom(ctx)
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
        val rules = Rule.entries.filter { it.isStandard }
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
            val r = Proofs.checkLine(lines, st.rule, cites, st.formula)
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

    fun generate(spec: Spec, level: Int, seed: Long): Puzzle {
        val rng = Random(seed)
        repeat(3000) { i -> attempt(spec, rng, if (i < 400) level else 1)?.let { return it } }
        // Practically unreachable; fall back to a fresh seed rather than fail.
        return generate(spec, 1, seed + 7919)
    }
}
