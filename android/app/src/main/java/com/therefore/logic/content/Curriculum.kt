package com.therefore.logic.content

import com.therefore.logic.engine.GoalShape.AND
import com.therefore.logic.engine.GoalShape.ATOM
import com.therefore.logic.engine.GoalShape.IMP
import com.therefore.logic.engine.GoalShape.NEG
import com.therefore.logic.engine.GoalShape.OR
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.Rule.*
import com.therefore.logic.engine.Spec

/** A worked example: each line is formula + justification ("Premise" or "2, 3, MP"). */
data class Example(val lines: List<Pair<String, String>>, val conclusion: String)

data class Lesson(val intro: List<String>, val rules: List<Rule>, val example: Example, val tip: String)

data class Course(
    val id: String,
    val title: String,
    val short: String,
    val blurb: String,
    val spec: Spec,
    val lesson: Lesson?,
)

object Curriculum {
    private val INF = listOf(MP, MT, HS, DS, Simp, Conj, Add, CD)
    private val REP1 = listOf(DN, Com, Assoc)
    private val REP2 = listOf(DeM, Dist)
    private val REP3 = listOf(Trans, Impl, Exp, Equiv, Taut)
    private val ALL = INF + REP1 + REP2 + REP3

    val courses = listOf(
        Course(
            "ifthen", "If–Then Basics", "MP · MT", "Use a conditional forwards and backwards.",
            Spec(listOf(MP, MT), emptyList(), 2..3, 2..4, 4, 0.2, 6, 0.1, mapOf(ATOM to 3.0, NEG to 3.0)),
            Lesson(
                listOf(
                    "A conditional, P → Q, promises that whenever P is true, Q is true. It says nothing about what happens when P is false.",
                    "Modus Ponens runs the conditional forwards: you have P, so you get Q. Modus Tollens runs it backwards: Q is false, so P could not have been true.",
                ),
                listOf(MP, MT),
                Example(listOf("P → Q" to "Premise", "R → ¬Q" to "Premise", "R" to "Premise", "¬Q" to "2, 3, MP", "¬P" to "1, 4, MT"), "¬P"),
                "Watch for the two traps. From P → Q and Q you cannot conclude P (affirming the consequent). From P → Q and ¬P you cannot conclude ¬Q (denying the antecedent).",
            ),
        ),
        Course(
            "chains", "Chains & Choices", "HS · DS", "Link conditionals and eliminate options.",
            Spec(listOf(MP, MT, HS, DS), listOf(HS, DS), 2..4, 2..5, 5, 0.2, 7, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 2.0)),
            Lesson(
                listOf(
                    "Hypothetical Syllogism chains two conditionals. If P leads to Q and Q leads to R, then P leads to R. The middle part has to match exactly.",
                    "Disjunctive Syllogism works by elimination. \"P or Q\" is true and P is false, so Q must be the true one. In this system the \"not\" line must deny the left side of the \"or\".",
                ),
                listOf(HS, DS),
                Example(listOf("P → Q" to "Premise", "Q → R" to "Premise", "S ∨ P" to "Premise", "¬S" to "Premise", "P" to "3, 4, DS", "P → R" to "1, 2, HS", "R" to "6, 5, MP"), "R"),
                "Work backward from the conclusion. Ask which line, if you had it, would give you the conclusion in one step.",
            ),
        ),
        Course(
            "andor", "And, Or", "Simp · Conj · Add", "Take statements apart and build new ones.",
            Spec(listOf(MP, MT, HS, DS, Simp, Conj, Add), listOf(Simp, Conj, Add), 2..4, 3..6, 5, 0.2, 7, 0.12, mapOf(ATOM to 2.0, NEG to 1.0, AND to 3.0, OR to 3.0)),
            Lesson(
                listOf(
                    "Simplification takes an \"and\" apart: if P ∧ Q is true, P is true. It only gives you the left side.",
                    "Conjunction does the reverse and joins two lines with ∧. Addition attaches anything with ∨: if P is true, then \"P or whatever you like\" is true.",
                ),
                listOf(Simp, Conj, Add),
                Example(listOf("P ∧ Q" to "Premise", "R" to "Premise", "P" to "1, Simp", "P ∧ R" to "3, 2, Conj", "(P ∧ R) ∨ S" to "4, Add"), "(P ∧ R) ∨ S"),
                "If the conclusion has a letter that appears in no premise, Addition is almost always how it gets in.",
            ),
        ),
        Course(
            "dilemma", "Dilemmas", "CD", "Reason through two possibilities at once.",
            Spec(INF, listOf(CD), 2..4, 2..6, 5, 0.15, 9, 0.12, mapOf(ATOM to 1.0, OR to 4.0)),
            Lesson(
                listOf(
                    "Constructive Dilemma combines two conditionals with an \"or\". If P leads to Q, R leads to S, and either P or R is true, then either Q or S is true.",
                    "The \"or\" line must list the two \"if\" parts in the same order as the conditionals.",
                ),
                listOf(CD),
                Example(listOf("(P → Q) ∧ (R → S)" to "Premise", "P ∨ R" to "Premise", "¬Q" to "Premise", "Q ∨ S" to "1, 2, CD", "S" to "4, 3, DS"), "S"),
                "If you need (P → Q) ∧ (R → S) and have the two conditionals on separate lines, join them with Conjunction first.",
            ),
        ),
        Course(
            "rearrange", "Flip & Regroup", "DN · Com · Assoc", "Your first replacement rules.",
            Spec(INF + REP1, REP1, 2..4, 3..6, 5, 0.3, 8, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, OR to 2.0, AND to 1.0)),
            Lesson(
                listOf(
                    "Replacement rules swap a formula for one that means the same thing. Unlike the rules of inference, they work on part of a line as well as the whole line, and in either direction.",
                    "Double Negation adds or removes ¬¬. Commutation swaps the sides of ∧ or ∨. Association moves the brackets in a chain of the same connective.",
                ),
                REP1,
                Example(listOf("P → ¬Q" to "Premise", "Q" to "Premise", "¬¬Q" to "2, DN", "¬P" to "1, 3, MT"), "¬P"),
                "Simplification, Disjunctive Syllogism and Addition all work on the left side only. Commutation is how you reach the right side.",
            ),
        ),
        Course(
            "demorgan", "De Morgan & Distribution", "DeM · Dist", "Move negations and brackets around.",
            Spec(INF + REP1 + REP2, REP2, 2..4, 3..7, 5, 0.25, 9, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, OR to 2.0, AND to 2.0)),
            Lesson(
                listOf(
                    "De Morgan's rule moves a negation through brackets and flips the connective: ¬(P ∧ Q) becomes ¬P ∨ ¬Q, and ¬(P ∨ Q) becomes ¬P ∧ ¬Q.",
                    "Distribution shares the outside part with each part inside the brackets: P ∧ (Q ∨ R) becomes (P ∧ Q) ∨ (P ∧ R).",
                ),
                REP2,
                Example(listOf("¬(P ∨ Q)" to "Premise", "R → P" to "Premise", "¬P ∧ ¬Q" to "1, DeM", "¬P" to "3, Simp", "¬R" to "2, 4, MT"), "¬R"),
                "A negation in front of brackets blocks most rules. Use De Morgan to push it inside first.",
            ),
        ),
        Course(
            "disguise", "Conditionals in Disguise", "Trans · Impl · Exp · Equiv", "Rewrite if-then statements to unlock a proof.",
            Spec(ALL, listOf(Trans, Impl, Exp, Equiv), 3..4, 3..8, 5, 0.25, 9, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 2.0, OR to 1.0)),
            Lesson(
                listOf(
                    "A conditional can wear several outfits. Transposition: P → Q equals ¬Q → ¬P. Material Implication: P → Q equals ¬P ∨ Q. Exportation: (P ∧ Q) → R equals P → (Q → R).",
                    "Material Equivalence unpacks P ↔ Q into (P → Q) ∧ (Q → P). Tautology says P ∨ P and P ∧ P are just P.",
                ),
                REP3,
                Example(
                    listOf(
                        "(P ∧ Q) → R" to "Premise", "P" to "Premise", "¬R ∨ S" to "Premise", "¬S" to "Premise", "S ∨ ¬R" to "3, Com",
                        "¬R" to "5, 4, DS", "P → (Q → R)" to "1, Exp", "Q → R" to "7, 2, MP", "¬Q" to "8, 6, MT",
                    ),
                    "¬Q",
                ),
                "When a premise has an \"and\" inside the \"if\" part, Exportation splits it so Modus Ponens can use one piece at a time.",
            ),
        ),
        Course(
            "master", "Grandmaster", "All 18 rules", "Long proofs that mix every rule.",
            Spec(ALL, emptyList(), 4..5, 5..10, 6, 0.25, 9, 0.08, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 1.0, OR to 1.0, AND to 1.0)),
            Lesson(
                listOf(
                    "Every rule is available. These proofs are longer, and there is usually more than one route to the conclusion.",
                    "Plan first. Find the premise that contains the conclusion, then work out which rules will free it.",
                ),
                emptyList(),
                Example(listOf("P ↔ Q" to "Premise", "¬Q" to "Premise", "(P → Q) ∧ (Q → P)" to "1, Equiv", "P → Q" to "3, Simp", "¬P" to "4, 2, MT"), "¬P"),
                "Not every premise has to be used, but in these puzzles each premise usually plays a part. A premise you have not touched is a clue.",
            ),
        ),
    )

    const val DAILY_ID = "daily"
    const val DAILY_LEVEL = 3

    val daily = Course(
        DAILY_ID, "Daily Proof", "Mixed", "A new mixed proof every day.",
        Spec(ALL, emptyList(), 3..4, 4..8, 5, 0.25, 9, 0.1, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 1.0, OR to 1.0, AND to 1.0)),
        null,
    )

    fun byId(id: String): Course = if (id == DAILY_ID) daily else courses.first { it.id == id }
}
