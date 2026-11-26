package com.therefore.logic.ext

import com.therefore.logic.engine.Formula
import com.therefore.logic.engine.Op
import com.therefore.logic.engine.not

/** How a formula breaks down in a truth tree: in one column (stack), into two (branch), or not at all (a literal). */
enum class Shape { STACK, BRANCH, LITERAL }

/** One line of the tree. [from] is the line it was broken out of. */
data class TNode(val id: Int, val f: Formula, val parent: Int?, val from: Int?)

/**
 * A truth tree (semantic tableau). Start with the premises and the negated conclusion; break formulas down
 * until every branch closes (contains something and its negation) or one stays open with nothing left to break.
 * All closed: the argument is valid. An open finished branch: it describes a counterexample.
 */
data class TreeState(
    val nodes: List<TNode>,
    val decomposed: Set<Int> = emptySet(),
    val closed: Set<Int> = emptySet(),
    val mistakes: Int = 0,
    val message: String? = null,
    val messageBad: Boolean = false,
    val selected: Int? = null,
    /** The learner's verdict once the tree is finished: true = valid. */
    val verdict: Boolean? = null,
) {
    fun node(id: Int) = nodes[id]
    fun children(id: Int) = nodes.filter { it.parent == id }
    val leaves: List<Int> get() = nodes.filter { n -> nodes.none { it.parent == n.id } }.map { it.id }

    /** The lines from the top down to [leaf]. */
    fun branch(leaf: Int): List<TNode> = generateSequence(node(leaf)) { it.parent?.let(::node) }.toList().reversed()

    fun open(leaf: Int) = leaf !in closed
    fun closable(leaf: Int): Pair<TNode, TNode>? {
        val b = branch(leaf)
        for (x in b) for (y in b) if (y.f == not(x.f)) return x to y
        return null
    }

    /** Open, and nothing on it left to break down. */
    fun complete(leaf: Int) = open(leaf) && branch(leaf).all { Trees.shape(it.f) == Shape.LITERAL || it.id in decomposed } && closable(leaf) == null

    val allClosed get() = leaves.all { it in closed }
    val openComplete get() = leaves.firstOrNull { complete(it) }
    val finished get() = allClosed || openComplete != null

    /** The counterexample an open finished branch describes: each letter true, false, or either. */
    fun valuation(leaf: Int): Map<Char, Boolean?> {
        val b = branch(leaf).map { it.f }
        val letters = nodes.flatMap { it.f.atoms() }.toSortedSet()
        return letters.associateWith { l ->
            when {
                Formula.Atom(l) in b -> true
                not(Formula.Atom(l)) in b -> false
                else -> null
            }
        }
    }
}

object Trees {
    fun start(premises: List<Formula>, conclusion: Formula?): TreeState {
        val trunk = premises + listOfNotNull(conclusion?.let(::not))
        return TreeState(trunk.mapIndexed { i, f -> TNode(i, f, if (i == 0) null else i - 1, null) })
    }

    fun shape(f: Formula): Shape = parts(f).first

    /** The shape and the pieces: one list for a stack, two for a branch. */
    fun parts(f: Formula): Pair<Shape, List<List<Formula>>> = when {
        f is Formula.Bin -> when (f.op) {
            Op.AND -> Shape.STACK to listOf(listOf(f.a, f.b))
            Op.OR -> Shape.BRANCH to listOf(listOf(f.a), listOf(f.b))
            Op.IMP -> Shape.BRANCH to listOf(listOf(not(f.a)), listOf(f.b))
            Op.IFF -> Shape.BRANCH to listOf(listOf(f.a, f.b), listOf(not(f.a), not(f.b)))
        }
        f is Formula.Not && f.a is Formula.Not -> Shape.STACK to listOf(listOf(f.a.a))
        f is Formula.Not && f.a is Formula.Bin -> {
            val g = f.a
            when (g.op) {
                Op.AND -> Shape.BRANCH to listOf(listOf(not(g.a)), listOf(not(g.b)))
                Op.OR -> Shape.STACK to listOf(listOf(not(g.a), not(g.b)))
                Op.IMP -> Shape.STACK to listOf(listOf(g.a, not(g.b)))
                Op.IFF -> Shape.BRANCH to listOf(listOf(g.a, not(g.b)), listOf(not(g.a), g.b))
            }
        }
        else -> Shape.LITERAL to emptyList()
    }

    /** Why a formula stacks or branches, in a sentence. */
    fun why(f: Formula): String {
        val s = f.show()
        return when {
            f is Formula.Bin && f.op == Op.AND -> "$s is true only if both sides are, so both go on the branch: stack."
            f is Formula.Bin && f.op == Op.OR -> "$s can be true in two ways, either side, so the tree splits: branch."
            f is Formula.Bin && f.op == Op.IMP -> "$s is true when its \"if\" part is false or its \"then\" part is true: two ways, so branch."
            f is Formula.Bin && f.op == Op.IFF -> "$s is true when both sides are true or both are false: two ways, so branch."
            f is Formula.Not && f.a is Formula.Not -> "Two negations cancel: one line, so stack."
            f is Formula.Not && (f.a as? Formula.Bin)?.op == Op.AND -> "$s says they aren't both true: one side or the other is false, so branch."
            f is Formula.Not && (f.a as? Formula.Bin)?.op == Op.OR -> "$s says neither side is true, so both negations go down: stack."
            f is Formula.Not && (f.a as? Formula.Bin)?.op == Op.IMP -> "A conditional is false only when the \"if\" part is true and the \"then\" part false: stack both."
            f is Formula.Not && (f.a as? Formula.Bin)?.op == Op.IFF -> "$s says the sides differ: true-false or false-true, so branch."
            else -> "$s is a letter or a negated letter. It can't be broken down further."
        }
    }

    fun select(s: TreeState, id: Int?) = s.copy(selected = id, message = null)

    /** Breaks node [id] down as a stack or a branch, under every open branch it belongs to. */
    fun decompose(s: TreeState, id: Int, choice: Shape): TreeState {
        val n = s.node(id)
        val (shape, parts) = parts(n.f)
        if (shape == Shape.LITERAL) return s.copy(message = why(n.f), messageBad = false)
        if (id in s.decomposed) return s.copy(message = "Line ${id + 1} is already broken down (it's ticked).", messageBad = false)
        if (choice != shape) return s.copy(mistakes = s.mistakes + 1, message = "Not quite. " + why(n.f), messageBad = true)
        val targets = s.leaves.filter { leaf -> s.open(leaf) && s.branch(leaf).any { it.id == id } }
        if (targets.isEmpty()) return s.copy(message = "Every branch through line ${id + 1} is closed, so there's nothing to add.", messageBad = false)
        val nodes = s.nodes.toMutableList()
        for (leaf in targets) for (col in parts) {
            var parent = leaf
            for (f in col) { val nn = TNode(nodes.size, f, parent, id); nodes += nn; parent = nn.id }
        }
        return s.copy(nodes = nodes, decomposed = s.decomposed + id, selected = null, message = null)
    }

    /** Closes the branch ending at [leaf], if it contains a formula and its negation. */
    fun close(s: TreeState, leaf: Int): TreeState {
        if (!s.open(leaf)) return s
        val pair = s.closable(leaf)
            ?: return s.copy(mistakes = s.mistakes + 1, messageBad = true, message = "This branch has no formula together with its negation (like P and ¬P), so it stays open.")
        return s.copy(closed = s.closed + leaf, message = "Closed: lines ${pair.first.id + 1} and ${pair.second.id + 1} contradict each other.", messageBad = false)
    }

    fun judge(s: TreeState, valid: Boolean): TreeState {
        if (!s.finished) return s
        val right = valid == s.allClosed
        return if (right) s.copy(verdict = valid, message = null)
        else s.copy(
            mistakes = s.mistakes + 1, messageBad = true,
            message = if (s.allClosed) "Every branch closed: there's no way for the premises to be true and the conclusion false. That means valid."
            else "An open branch with nothing left to break down describes a way for the premises to be true and the conclusion false: a counterexample. That means invalid.",
        )
    }
}
