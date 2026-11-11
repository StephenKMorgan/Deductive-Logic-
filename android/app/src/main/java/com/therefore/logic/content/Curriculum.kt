package com.therefore.logic.content

import com.therefore.logic.content.LessonStep.Guided
import com.therefore.logic.content.LessonStep.Practice
import com.therefore.logic.content.LessonStep.Quiz
import com.therefore.logic.content.LessonStep.Read
import com.therefore.logic.content.LessonStep.Table
import com.therefore.logic.engine.GoalShape.AND
import com.therefore.logic.engine.GoalShape.ATOM
import com.therefore.logic.engine.GoalShape.IMP
import com.therefore.logic.engine.GoalShape.NEG
import com.therefore.logic.engine.GoalShape.OR
import com.therefore.logic.engine.Method
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.Rule.*
import com.therefore.logic.engine.Spec

data class Course(
    val id: String,
    val title: String,
    val short: String,
    val blurb: String,
    val spec: Spec,
    val lessons: List<LessonDef>,
    val advanced: Boolean = false,
) {
    /** The puzzle settings for free play on one lesson: same course, focused on that lesson's rules. */
    fun specFor(lesson: LessonDef?): Spec {
        val focus = lesson?.rules?.filter { it.isStandard && it in spec.rules }.orEmpty()
        return if (focus.isEmpty()) spec else spec.copy(focus = focus)
    }
}

object Curriculum {
    private val INF = listOf(MP, MT, HS, DS, Simp, Conj, Add, CD)
    private val REP1 = listOf(DN, Com, Assoc)
    private val REP2 = listOf(DeM, Dist)
    private val REP3 = listOf(Trans, Impl, Exp, Equiv, Taut)
    private val ALL = INF + REP1 + REP2 + REP3

    // ---------------- Course 1 ----------------
    private val tutorial = LessonDef(
        "ifthen-1", "Your first proof", "A hands-on tour: cite lines, pick a rule, write a line.", listOf(MP),
        listOf(
            Read(
                "What a proof is",
                listOf(
                    "A proof starts from premises: statements you are given as true. Your job is to reach the conclusion, marked with ∴, one line at a time.",
                    "Every new line must follow from earlier lines by a rule. You name the rule and the lines you used, and the app checks the step.",
                ),
                Example(listOf("P → Q" to "Premise", "P" to "Premise", "Q" to "1, 2, MP"), "Q"),
            ),
            Quiz(
                "Check", "In the proof above, which line is the conclusion you had to reach?",
                listOf("P → Q", "P", "Q"), 2,
                "The ∴ symbol marks the conclusion. P → Q and P are the premises you start from, and line 3 reaches Q.",
            ),
            Guided(
                "Write your first line", listOf("P → Q", "P"), "Q", listOf(MP),
                listOf(
                    Coach("The goal is at the top: prove Q. Start by tapping line 1 to cite it.") { 1 in it.cites },
                    Coach("Now tap line 2. Modus Ponens uses a conditional and its \"if\" part, so you cite both lines.") { it.citing(1, 2) },
                    Coach("Pick the rule: tap MP.") { it.rule == MP },
                    Coach("Write the new line with the keypad: tap Q.") { it.typed("Q") },
                    Coach("Tap Add line. The app checks your step against the rule.") { it.done },
                ),
                "Proof complete. Line 3 reads \"Q   1, 2, MP\": the formula, the lines it came from, and the rule.",
                solution = listOf("Q" to "1, 2, MP"),
            ),
            Guided(
                "Mistakes, undo and hints", listOf("P → Q", "Q → R", "P"), "R", listOf(MP),
                listOf(
                    Coach("Mistakes cost nothing here, so make one on purpose. Cite only line 1, choose MP, write Q and tap Add line.") { it.mistakes.isNotEmpty() },
                    Coach("The red message explains what's missing. Fix it: also cite line 3, then tap Add line again.") { it.has("Q") },
                    Coach("Changed your mind about a line? Tap Undo to remove it.") { it.mistakes.isNotEmpty() && !it.has("Q") },
                    Coach("Now tap Hint. Each tap reveals more: first which lines, then which rule, then the whole line.") { it.hints > 0 },
                    Coach("Use hints or your own reasoning to finish the proof of R.") { it.done },
                ),
                "Done. Hints lower your grade a little in practice puzzles, so try working backward from the conclusion before you ask.",
                solution = listOf("Q" to "1, 3, MP", "R" to "2, 4, MP"),
            ),
            Read(
                "Handy tools",
                listOf(
                    "Tap any symbol in the input to move the cursor next to it. ◀ and ▶ move it too, and ⌫ deletes the symbol before it.",
                    "When a line is cited, its Copy button pastes it into the input. That helps when the new line is a small change to an old one.",
                    "Practice puzzles raise your level in a course. Free play lets you replay any level you have reached.",
                ),
            ),
        ),
    )

    private val mpLesson = LessonDef(
        "ifthen-2", "Modus Ponens", "When is \"if P then Q\" true, and what can you do with it?", listOf(MP),
        listOf(
            Read(
                "A conditional is a promise",
                listOf(
                    "P → Q promises that whenever P is true, Q is true as well. The promise is only broken when P is true and Q is false.",
                    "When P is false, the promise isn't tested, so P → Q still counts as true.",
                ),
            ),
            Table(
                "Explore", "Flip P and Q. Find the one combination that makes P → Q false.",
                listOf("P → Q"), TableGoal.FALSE_ROW,
                success = "Right: P true and Q false is the only way to break the promise.",
            ),
            Read("The rule", listOf("Modus Ponens uses the promise. If you have P → Q and you also have P, you may write Q."), rules = listOf(MP)),
            Quiz("Check", "From S → T and S, what can you write by Modus Ponens?", listOf("T", "S → T", "¬S"), 0, "S is the \"if\" part, so Modus Ponens gives the \"then\" part, T."),
            Practice("Your turn", "Use Modus Ponens twice.", listOf("P → (Q → R)", "P", "Q"), "R", listOf(MP), solution = listOf("Q → R" to "1, 2, MP", "R" to "4, 3, MP")),
        ),
    )

    private val mtLesson = LessonDef(
        "ifthen-3", "Modus Tollens", "Run a conditional backwards.", listOf(MT),
        listOf(
            Read(
                "Backwards reasoning",
                listOf(
                    "Modus Tollens runs a conditional backwards. If P → Q is true and Q is false, then P can't be true: if it were, Q would be true too.",
                    "So from P → Q and ¬Q you may write ¬P.",
                ),
                rules = listOf(MT),
            ),
            Table(
                "Try to break it", "Look for a row where both premises are true but the conclusion ¬P is false. Check all four rows.",
                listOf("P → Q", "¬Q", "¬P"), TableGoal.ALL_ROWS, argument = true,
                success = "No row works. Whenever both premises are true, ¬P is true. That's what makes Modus Tollens valid.",
            ),
            Quiz("Check", "From A → B and ¬B, what follows?", listOf("¬A", "B", "A"), 0, "B is the \"then\" part. It's false, so the \"if\" part, A, must be false too."),
            Practice("Your turn", "Get ¬Q first, then use Modus Tollens.", listOf("P → Q", "R → ¬Q", "R"), "¬P", listOf(MP, MT), solution = listOf("¬Q" to "2, 3, MP", "¬P" to "1, 4, MT")),
        ),
    )

    private val trapsLesson = LessonDef(
        "ifthen-4", "Spotting the traps", "Two arguments that look valid but aren't.", listOf(MP, MT),
        listOf(
            Read(
                "Look-alikes",
                listOf(
                    "Two invalid patterns look a lot like Modus Ponens and Modus Tollens. A counterexample proves an argument invalid: a situation where the premises are true but the conclusion is false.",
                ),
            ),
            Table(
                "Affirming the consequent", "P → Q, Q ∴ P. Find a row where both premises are true but the conclusion is false.",
                listOf("P → Q", "Q", "P"), TableGoal.COUNTEREXAMPLE, argument = true,
                success = "Found it: P false, Q true. Q can be true for some other reason, so knowing Q tells you nothing about P.",
            ),
            Table(
                "Denying the antecedent", "P → Q, ¬P ∴ ¬Q. Find a counterexample.",
                listOf("P → Q", "¬P", "¬Q"), TableGoal.COUNTEREXAMPLE, argument = true,
                success = "Found it: P false, Q true again. The conditional says nothing about what happens when P is false.",
            ),
            Quiz(
                "Real-world check", "\"If it rains, the street gets wet. The street is wet. So it rained.\" Is this argument valid?",
                listOf("Valid: Modus Ponens", "Invalid: affirming the consequent", "Valid: Modus Tollens"), 1,
                "A street cleaner could have wet the street. Knowing the \"then\" part doesn't tell you the \"if\" part.",
            ),
            Quiz(
                "Real-world check", "\"If the alarm is set, it rings at 7. It didn't ring at 7. So the alarm wasn't set.\"",
                listOf("Valid: Modus Tollens", "Invalid: denying the antecedent", "Invalid: affirming the consequent"), 0,
                "The \"then\" part is false, so the \"if\" part must be false. That's Modus Tollens.",
            ),
        ),
    )

    // ---------------- Course 2 ----------------
    private val hsLesson = LessonDef(
        "chains-1", "Chaining conditionals", "Hypothetical Syllogism links if-thens together.", listOf(HS),
        listOf(
            Read(
                "Links in a chain",
                listOf("If P leads to Q and Q leads to R, then P leads to R. The middle part has to match exactly: the right side of the first must be the left side of the second."),
                rules = listOf(HS),
            ),
            Quiz("Check", "Which pair can be chained by Hypothetical Syllogism?", listOf("P → Q and Q → R", "P → Q and R → Q", "Q → P and Q → R"), 0, "Only the first pair has the same middle part: P → Q ends with Q, and Q → R starts with Q."),
            Practice("Your turn", "Build a chain from P all the way to S.", listOf("P → Q", "Q → R", "R → S"), "P → S", listOf(HS), solution = listOf("P → R" to "1, 2, HS", "P → S" to "4, 3, HS")),
        ),
    )

    private val dsLesson = LessonDef(
        "chains-2", "Elimination", "Disjunctive Syllogism: one option is ruled out, so it's the other.", listOf(DS),
        listOf(
            Read("\"Or\" means at least one", listOf("P ∨ Q is true when P is true, when Q is true, or when both are. It's an inclusive \"or\".")),
            Table("Explore", "Find the only row that makes P ∨ Q false.", listOf("P ∨ Q"), TableGoal.FALSE_ROW, success = "Right: an \"or\" is only false when both sides are false."),
            Read(
                "The rule",
                listOf(
                    "If P ∨ Q is true and P is false, Q must be the true one. So from P ∨ Q and ¬P you may write Q.",
                    "In this system the \"not\" line must deny the left side of the \"or\". Later you'll learn Commutation, which swaps the sides.",
                ),
                rules = listOf(DS),
            ),
            Practice("Your turn", "Eliminate S, then finish with Modus Ponens.", listOf("S ∨ P", "¬S", "P → R"), "R", listOf(MP, DS), solution = listOf("P" to "1, 2, DS", "R" to "3, 4, MP")),
        ),
    )

    // ---------------- Course 3 ----------------
    private val simpConjLesson = LessonDef(
        "andor-1", "Taking apart and joining", "Simplification and Conjunction.", listOf(Simp, Conj),
        listOf(
            Read(
                "Two sides of \"and\"",
                listOf(
                    "Simplification takes an \"and\" apart: from P ∧ Q you may write P. It only gives the left side.",
                    "Conjunction does the reverse: two lines you have can be joined into one with ∧.",
                ),
                rules = listOf(Simp, Conj),
            ),
            Guided(
                "Guided proof", listOf("P ∧ Q", "R"), "P ∧ R", listOf(Simp, Conj),
                listOf(
                    Coach("Take P out of line 1: cite line 1, choose Simp, write P and add it.") { it.has("P") },
                    Coach("Now join P with R: cite lines 2 and 3, choose Conj, write P ∧ R.") { it.done },
                ),
                "Done. Simp and Conj often work as a pair: take parts out, then join the ones you need.",
                solution = listOf("P" to "1, Simp", "P ∧ R" to "3, 2, Conj"),
            ),
            Quiz("Check", "What does Simplification give from (A ∨ B) ∧ C?", listOf("A ∨ B", "C", "A"), 0, "The main connective is ∧, and Simplification takes its left side: A ∨ B."),
        ),
    )

    private val addLesson = LessonDef(
        "andor-2", "Addition", "Attach anything with \"or\".", listOf(Add),
        listOf(
            Read(
                "Weakening a statement",
                listOf(
                    "If P is true, then \"P or anything\" is true. Addition lets you write P ∨ Q from P, whatever Q is.",
                    "It's the usual way a new letter enters a proof. If the conclusion has a letter no premise has, think Addition.",
                ),
                rules = listOf(Add),
            ),
            Quiz("Check", "Why is Addition always valid?", listOf("If P is true, \"P or anything\" is true", "Because Q must also be true", "It isn't always valid"), 0, "An \"or\" only needs one true side, and P is already true."),
            Practice("Your turn", "The conditional needs P ∨ Q before you can use it.", listOf("P", "(P ∨ Q) → R"), "R", listOf(Add, MP), solution = listOf("P ∨ Q" to "1, Add", "R" to "2, 3, MP")),
        ),
    )

    // ---------------- Course 4 ----------------
    private val cdLesson = LessonDef(
        "dilemma-1", "Constructive Dilemma", "Two conditionals and an \"or\".", listOf(CD),
        listOf(
            Read(
                "Either way",
                listOf(
                    "If P leads to Q, R leads to S, and at least one of P or R is true, then at least one of Q or S is true.",
                    "The two conditionals are joined with ∧, and the \"or\" line lists their \"if\" parts in the same order.",
                ),
                rules = listOf(CD),
            ),
            Practice("Your turn", "All the parts are there.", listOf("(P → Q) ∧ (R → S)", "P ∨ R"), "Q ∨ S", listOf(CD), solution = listOf("Q ∨ S" to "1, 2, CD")),
            Practice("Build the pieces", "Join the two conditionals with Conj first.", listOf("P → Q", "R → S", "P ∨ R"), "Q ∨ S", listOf(Conj, CD), solution = listOf("(P → Q) ∧ (R → S)" to "1, 2, Conj", "Q ∨ S" to "4, 3, CD")),
        ),
    )

    // ---------------- Course 5 ----------------
    private val dnComLesson = LessonDef(
        "rearrange-1", "Double Negation and Commutation", "Your first replacement rules.", listOf(DN, Com),
        listOf(
            Read(
                "Rewriting without changing meaning",
                listOf(
                    "Replacement rules swap a formula for one that means the same thing. They work in either direction and on part of a line, not just the whole line.",
                    "Double Negation: P and ¬¬P are the same. Commutation: P ∨ Q is the same as Q ∨ P, and likewise for ∧.",
                ),
                rules = listOf(DN, Com),
            ),
            Guided(
                "Exact negations", listOf("P → ¬Q", "Q"), "¬P", listOf(MT, DN),
                listOf(
                    Coach("Try Modus Tollens straight away: cite lines 1 and 2, choose MT, write ¬P and add it.") { it.mistakes.isNotEmpty() },
                    Coach("MT needs the exact negation of ¬Q, which is ¬¬Q. Cite line 2, choose DN, write ¬¬Q.") { it.has("¬¬Q") },
                    Coach("Now Modus Tollens works: cite lines 1 and 3, choose MT, write ¬P.") { it.done },
                ),
                "Done. Double Negation is the glue that makes negations match exactly.",
                solution = listOf("¬¬Q" to "2, DN", "¬P" to "1, 3, MT"),
            ),
            Guided(
                "Copy, edit, swap", listOf("P ∨ Q", "¬Q"), "P", listOf(DS, Com),
                listOf(
                    Coach("DS only cancels the left side, so swap the sides of line 1 first. Tap line 1, then its Copy button.") { it.typed("P ∨ Q") },
                    Coach("Edit the copy into Q ∨ P: tap a symbol to move the cursor, ⌫ deletes the symbol before it, and the keypad adds new ones.") { it.typed("Q ∨ P") },
                    Coach("Choose Com and add the line (keep line 1 cited).") { it.has("Q ∨ P") },
                    Coach("Finish with Disjunctive Syllogism on lines 3 and 2.") { it.done },
                ),
                "Done. Commutation is how you reach the right side of an \"or\" or an \"and\".",
                solution = listOf("Q ∨ P" to "1, Com", "P" to "3, 2, DS"),
            ),
        ),
    )

    private val assocLesson = LessonDef(
        "rearrange-2", "Association", "Moving brackets in a chain.", listOf(Assoc),
        listOf(
            Read("Regrouping", listOf("(P ∨ Q) ∨ R means the same as P ∨ (Q ∨ R). Association moves the brackets in a chain of the same connective (all ∨ or all ∧)."), rules = listOf(Assoc)),
            Quiz("Check", "Which can Association rewrite?", listOf("(P ∧ Q) ∧ R", "(P ∧ Q) ∨ R", "P → (Q → R)"), 0, "Only the first is a chain of one connective. Mixing ∧ and ∨ needs Distribution instead."),
            Practice("Your turn", "Regroup so ¬P can cancel the left side.", listOf("(P ∨ Q) ∨ R", "¬P"), "Q ∨ R", listOf(Assoc, DS), solution = listOf("P ∨ (Q ∨ R)" to "1, Assoc", "Q ∨ R" to "3, 2, DS")),
        ),
    )

    // ---------------- Course 6 ----------------
    private val demLesson = LessonDef(
        "demorgan-1", "De Morgan's rule", "Push a negation through brackets.", listOf(DeM),
        listOf(
            Read(
                "Not both, neither",
                listOf(
                    "¬(P ∧ Q), \"not both\", means ¬P ∨ ¬Q: at least one is false.",
                    "¬(P ∨ Q), \"neither\", means ¬P ∧ ¬Q: both are false. The connective flips as the negation moves inside.",
                ),
                rules = listOf(DeM),
            ),
            Table(
                "See it", "Flip P and Q through every row. The two formulas always match, which is why you can swap them.",
                listOf("¬(P ∧ Q)", "¬P ∨ ¬Q"), TableGoal.ALL_ROWS, success = "They agree on every row. Formulas like this are equivalent.",
            ),
            Quiz("Check", "¬(A ∨ B) becomes:", listOf("¬A ∧ ¬B", "¬A ∨ ¬B", "A ∧ B"), 0, "The negation moves inside and ∨ flips to ∧: neither A nor B."),
            Practice("Your turn", "Push the negation inside, then take out ¬P.", listOf("¬(P ∨ Q)", "R → P"), "¬R", listOf(DeM, Simp, MT), solution = listOf("¬P ∧ ¬Q" to "1, DeM", "¬P" to "3, Simp", "¬R" to "2, 4, MT")),
        ),
    )

    private val distLesson = LessonDef(
        "demorgan-2", "Distribution", "Share one part across brackets.", listOf(Dist),
        listOf(
            Read(
                "Sharing",
                listOf("P ∧ (Q ∨ R) means (P ∧ Q) ∨ (P ∧ R): P goes with each option. It works the other way round too: P ∨ (Q ∧ R) means (P ∨ Q) ∧ (P ∨ R)."),
                rules = listOf(Dist),
            ),
            Practice("Your turn", "Distribute, then eliminate the option that line 2 rules out.", listOf("P ∧ (Q ∨ R)", "¬(P ∧ Q)"), "P ∧ R", listOf(Dist, DS), solution = listOf("(P ∧ Q) ∨ (P ∧ R)" to "1, Dist", "P ∧ R" to "3, 2, DS")),
        ),
    )

    // ---------------- Course 7 ----------------
    private val transImplLesson = LessonDef(
        "disguise-1", "Transposition and Implication", "Three ways to write the same conditional.", listOf(Trans, Impl),
        listOf(
            Read(
                "Same promise, new outfit",
                listOf("P → Q, ¬Q → ¬P and ¬P ∨ Q all say the same thing. Transposition swaps and negates both sides. Material Implication turns \"if P then Q\" into \"not P, or Q\"."),
                rules = listOf(Trans, Impl),
            ),
            Table("See it", "Step through every row and compare the three formulas.", listOf("P → Q", "¬Q → ¬P", "¬P ∨ Q"), TableGoal.ALL_ROWS, success = "All three agree on every row: they're equivalent."),
            Practice("Your turn", "Turn line 1 into a conditional, then chain.", listOf("¬P ∨ Q", "Q → R"), "P → R", listOf(Impl, HS), solution = listOf("P → Q" to "1, Impl", "P → R" to "3, 2, HS")),
        ),
    )

    private val expEquivLesson = LessonDef(
        "disguise-2", "Exportation and Equivalence", "Split an \"and\" in the if-part; unpack ↔.", listOf(Exp, Equiv),
        listOf(
            Read(
                "Two more outfits",
                listOf(
                    "Exportation: (P ∧ Q) → R means P → (Q → R). It lets Modus Ponens use one piece of the \"if\" part at a time.",
                    "Material Equivalence: P ↔ Q means (P → Q) ∧ (Q → P).",
                ),
                rules = listOf(Exp, Equiv),
            ),
            Practice("The opening puzzle", "This is the puzzle that started it all.", listOf("(P ∧ Q) → R", "P", "¬R ∨ S", "¬S"), "¬Q", listOf(Com, DS, Exp, MP, MT), solution = listOf("S ∨ ¬R" to "3, Com", "¬R" to "5, 4, DS", "P → (Q → R)" to "1, Exp", "Q → R" to "7, 2, MP", "¬Q" to "8, 6, MT")),
            Practice("Unpack a biconditional", "Equiv, then Simp, then Modus Tollens.", listOf("P ↔ Q", "¬Q"), "¬P", listOf(Equiv, Simp, MT), solution = listOf("(P → Q) ∧ (Q → P)" to "1, Equiv", "P → Q" to "3, Simp", "¬P" to "4, 2, MT")),
        ),
    )

    // ---------------- Course 8 ----------------
    private val strategyLesson = LessonDef(
        "master-1", "Proof strategy", "How to plan a long proof.", emptyList(),
        listOf(
            Read(
                "Plan before you write",
                listOf(
                    "Work backward. Find where the conclusion's letters appear in the premises, and ask which rule could produce the conclusion in one step.",
                    "Simplify the premises. Push negations inside with De Morgan, take \"and\"s apart, and rewrite conditionals into the shape a rule needs.",
                    "Use every premise. In these puzzles each premise usually plays a part, so one you haven't touched is a clue.",
                ),
            ),
            Quiz(
                "Check", "The conclusion is S, and the only premise containing S is R → S. What should you aim for next?",
                listOf("Get R, then use Modus Ponens", "Get ¬S", "Use Addition on R → S"), 0,
                "R → S gives S as soon as you have R. Now the question becomes how to get R.",
            ),
            Practice("Plan it", "Work backward from S.", listOf("P ∨ (Q ∧ R)", "¬P", "R → S"), "S", listOf(DS, Com, Simp, MP), solution = listOf("Q ∧ R" to "1, 2, DS", "R ∧ Q" to "4, Com", "R" to "5, Simp", "S" to "3, 6, MP")),
        ),
    )

    // ---------------- Course 9 (advanced) ----------------
    private val cpLesson = LessonDef(
        "cp-1", "Assumptions", "Prove an if-then by assuming the \"if\" part.", listOf(ACP, CP),
        listOf(
            Read(
                "Conditional Proof",
                listOf(
                    "To prove P → Q, you may assume P (ACP) and see what follows. Lines after an assumption are indented: they depend on it.",
                    "Once you reach Q, close the assumption with CP and write P → Q. After closing, the indented lines can't be cited any more, because they only held under the assumption.",
                ),
                Example(
                    listOf("P → (Q ∧ R)" to "Premise", "P" to "ACP", "Q ∧ R" to "1, 2, MP", "Q" to "3, Simp", "P → Q" to "2–4, CP"),
                    "P → Q", depths = listOf(0, 1, 1, 1, 0),
                ),
                rules = listOf(ACP, CP),
            ),
            Guided(
                "Your first subproof", listOf("P → (Q ∧ R)"), "P → Q", listOf(MP, Simp, ACP, CP),
                listOf(
                    Coach("The goal is a conditional, so assume its \"if\" part. Choose ACP from the rule menu, write P and tap Add line.") { it.hasAssumed("P", ACP) },
                    Coach("Line 2 is indented: it's an assumption. Use MP on lines 1 and 2.") { it.has("Q ∧ R") },
                    Coach("Take out Q with Simp.") { it.has("Q") },
                    Coach("Close the subproof: choose CP and write P → Q. You don't need to cite lines; CP uses the whole subproof.") { it.done },
                ),
                "Done. Line 5 sits back at the left margin: it no longer depends on any assumption.",
                solution = listOf("P" to "ACP", "Q ∧ R" to "1, 2, MP", "Q" to "3, Simp", "P → Q" to "CP"),
            ),
            Quiz("Check", "After you close a subproof with CP, can you cite the lines inside it?", listOf("Yes", "No"), 1, "Those lines only held under the assumption. Once it's closed, only the conditional you proved carries on."),
        ),
    )

    private val cpPractice = LessonDef(
        "cp-2", "Conditional proof practice", "Subproofs that need a few steps.", listOf(CP),
        listOf(
            Practice("Two consequences", "Assume P, get Q and R, join them, then close.", listOf("P → Q", "P → R"), "P → (Q ∧ R)", listOf(MP, Conj, ACP, CP), solution = listOf("P" to "ACP", "Q" to "1, 3, MP", "R" to "2, 3, MP", "Q ∧ R" to "4, 5, Conj", "P → (Q ∧ R)" to "CP")),
            Practice("Using Addition", "Inside the subproof, build P ∨ Q from P.", listOf("(P ∨ Q) → R"), "P → R", listOf(Add, MP, ACP, CP), solution = listOf("P" to "ACP", "P ∨ Q" to "2, Add", "R" to "1, 3, MP", "P → R" to "CP")),
        ),
    )

    // ---------------- Course 10 (advanced) ----------------
    private val ipLesson = LessonDef(
        "ip-1", "Proof by contradiction", "Assume the opposite and show it's impossible.", listOf(AIP, IP),
        listOf(
            Read(
                "Indirect Proof",
                listOf(
                    "To prove ¬P, assume P (AIP). If that leads to a contradiction, a line like Q ∧ ¬Q, the assumption must be false, so ¬P. Close it with IP.",
                    "To prove P itself, assume ¬P instead.",
                ),
                Example(
                    listOf("P → Q" to "Premise", "P → ¬Q" to "Premise", "P" to "AIP", "Q" to "1, 3, MP", "¬Q" to "2, 3, MP", "Q ∧ ¬Q" to "4, 5, Conj", "¬P" to "3–6, IP"),
                    "¬P", depths = listOf(0, 0, 1, 1, 1, 1, 0),
                ),
                rules = listOf(AIP, IP),
            ),
            Guided(
                "Find the contradiction", listOf("P → Q", "P → ¬Q"), "¬P", listOf(MP, Conj, AIP, IP),
                listOf(
                    Coach("To prove ¬P, assume the opposite. Choose AIP and write P.") { it.hasAssumed("P", AIP) },
                    Coach("Use Modus Ponens twice to get both Q and ¬Q.") { it.has("Q") && it.has("¬Q") },
                    Coach("Join them with Conj into Q ∧ ¬Q: a contradiction.") { it.has("Q ∧ ¬Q") || it.has("¬Q ∧ Q") },
                    Coach("The assumption led to the impossible, so it's false. Choose IP and write ¬P.") { it.done },
                ),
                "Done. Indirect Proof can prove almost anything that is provable, so it's a good fallback when you're stuck.",
                solution = listOf("P" to "AIP", "Q" to "1, 3, MP", "¬Q" to "2, 3, MP", "Q ∧ ¬Q" to "4, 5, Conj", "¬P" to "IP"),
            ),
            Quiz("Check", "If assuming ¬R leads to a contradiction, Indirect Proof proves:", listOf("R", "¬R", "Nothing"), 0, "The assumption ¬R must be false, so R is true."),
        ),
    )

    private val ipPractice = LessonDef(
        "ip-2", "Indirect proof practice", "Contradictions that take some finding.", listOf(IP),
        listOf(
            Practice("Both roads lead to R", "Assume ¬R and follow it back through both conditionals.", listOf("P ∨ Q", "P → R", "Q → R"), "R", listOf(MT, DS, MP, Conj, AIP, IP), solution = listOf("¬R" to "AIP", "¬P" to "2, 4, MT", "Q" to "1, 5, DS", "R" to "3, 6, MP", "R ∧ ¬R" to "7, 4, Conj", "R" to "IP")),
        ),
    )

    val courses = listOf(
        Course(
            "ifthen", "If–Then Basics", "MP · MT", "Use a conditional forwards and backwards.",
            Spec(listOf(MP, MT), emptyList(), 2..3, 2..4, 4, 0.2, 6, 0.1, mapOf(ATOM to 3.0, NEG to 3.0)),
            listOf(tutorial, mpLesson, mtLesson, trapsLesson),
        ),
        Course(
            "chains", "Chains & Choices", "HS · DS", "Link conditionals and eliminate options.",
            Spec(listOf(MP, MT, HS, DS), listOf(HS, DS), 2..4, 2..5, 5, 0.2, 7, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 2.0)),
            listOf(hsLesson, dsLesson),
        ),
        Course(
            "andor", "And, Or", "Simp · Conj · Add", "Take statements apart and build new ones.",
            Spec(listOf(MP, MT, HS, DS, Simp, Conj, Add), listOf(Simp, Conj, Add), 2..4, 3..6, 5, 0.2, 7, 0.12, mapOf(ATOM to 2.0, NEG to 1.0, AND to 3.0, OR to 3.0)),
            listOf(simpConjLesson, addLesson),
        ),
        Course(
            "dilemma", "Dilemmas", "CD", "Reason through two possibilities at once.",
            Spec(INF, listOf(CD), 2..4, 2..6, 5, 0.15, 9, 0.12, mapOf(ATOM to 1.0, OR to 4.0)),
            listOf(cdLesson),
        ),
        Course(
            "rearrange", "Flip & Regroup", "DN · Com · Assoc", "Your first replacement rules.",
            Spec(INF + REP1, REP1, 2..4, 3..6, 5, 0.3, 8, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, OR to 2.0, AND to 1.0)),
            listOf(dnComLesson, assocLesson),
        ),
        Course(
            "demorgan", "De Morgan & Distribution", "DeM · Dist", "Move negations and brackets around.",
            Spec(INF + REP1 + REP2, REP2, 2..4, 3..7, 5, 0.25, 9, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, OR to 2.0, AND to 2.0)),
            listOf(demLesson, distLesson),
        ),
        Course(
            "disguise", "Conditionals in Disguise", "Trans · Impl · Exp · Equiv", "Rewrite if-then statements to unlock a proof.",
            Spec(ALL, listOf(Trans, Impl, Exp, Equiv), 3..4, 3..8, 5, 0.25, 9, 0.12, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 2.0, OR to 1.0)),
            listOf(transImplLesson, expEquivLesson),
        ),
        Course(
            "master", "Grandmaster", "All 18 rules", "Long proofs that mix every rule.",
            Spec(ALL, emptyList(), 4..5, 5..10, 6, 0.25, 9, 0.08, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 1.0, OR to 1.0, AND to 1.0)),
            listOf(strategyLesson),
        ),
        Course(
            "cp", "Conditional Proof", "ACP · CP", "Prove an if-then by assuming its first half.",
            Spec(ALL + listOf(ACP, CP), emptyList(), 2..4, 4..9, 5, 0.2, 9, 0.1, mapOf(ATOM to 2.0, NEG to 1.0, OR to 1.0, AND to 1.0), Method.CONDITIONAL),
            listOf(cpLesson, cpPractice), advanced = true,
        ),
        Course(
            "ip", "Indirect Proof", "AIP · IP", "Prove something by showing its opposite is impossible.",
            Spec(ALL + listOf(ACP, CP, AIP, IP), emptyList(), 2..4, 5..10, 5, 0.2, 9, 0.1, mapOf(ATOM to 1.0), Method.INDIRECT),
            listOf(ipLesson, ipPractice), advanced = true,
        ),
    )

    const val DAILY_ID = "daily"
    const val DAILY_LEVEL = 3

    val daily = Course(
        DAILY_ID, "Daily Proof", "Mixed", "A new mixed proof every day.",
        Spec(ALL, emptyList(), 3..4, 4..8, 5, 0.25, 9, 0.1, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 1.0, OR to 1.0, AND to 1.0)),
        emptyList(),
    )

    fun byId(id: String): Course = if (id == DAILY_ID) daily else courses.first { it.id == id }

    fun lesson(id: String): Pair<Course, LessonDef> =
        courses.firstNotNullOf { c -> c.lessons.firstOrNull { it.id == id }?.let { c to it } }

    /** All rules a board should offer, in menu order. */
    fun boardRules(rules: List<Rule>): List<Rule> = Rule.entries.filter { it in rules }
}
