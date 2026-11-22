package com.therefore.logic.content

import com.therefore.logic.content.LessonStep.Guided
import com.therefore.logic.content.LessonStep.Practice
import com.therefore.logic.content.LessonStep.Quiz
import com.therefore.logic.content.LessonStep.Read
import com.therefore.logic.content.LessonStep.Table
import com.therefore.logic.content.LessonStep.WorldStep
import com.therefore.logic.engine.Lift
import com.therefore.logic.engine.GoalShape.AND
import com.therefore.logic.engine.GoalShape.ATOM
import com.therefore.logic.engine.GoalShape.IMP
import com.therefore.logic.engine.GoalShape.NEG
import com.therefore.logic.engine.GoalShape.OR
import com.therefore.logic.engine.Method
import com.therefore.logic.engine.Rule
import com.therefore.logic.engine.Rule.*
import com.therefore.logic.engine.Spec

enum class Track { PROPOSITIONAL, PREDICATE }

data class Course(
    val id: String,
    val title: String,
    val short: String,
    val blurb: String,
    val spec: Spec,
    val lessons: List<LessonDef>,
    val advanced: Boolean = false,
    val track: Track = Track.PROPOSITIONAL,
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
                    "To prove P → Q, you may assume P (ACP) and see what follows. Lines after an assumption are indented: they depend on it. Picking ACP fills in the \"if\" part for you.",
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
                    Coach("The goal is a conditional, so assume its \"if\" part. Tap ACP: it fills in P for you. Then tap Add line.") { it.hasAssumed("P", ACP) },
                    Coach("Line 2 is indented: it's an assumption. Use MP on lines 1 and 2.") { it.has("Q ∧ R") },
                    Coach("Take out Q with Simp.") { it.has("Q") },
                    Coach("Close the subproof: tap CP. It fills in P → Q, and you don't need to cite lines because CP uses the whole subproof. Tap Add line.") { it.done },
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

    private val cpNested = LessonDef(
        "cp-3", "Assumptions inside assumptions", "Nest one subproof in another.", listOf(ACP, CP),
        listOf(
            Read(
                "Nesting",
                listOf(
                    "You can open an assumption while another is still open. Each one adds a bar on the left.",
                    "Close them in reverse order: the most recent assumption always closes first. Picking ACP fills in the \"if\" part of whatever conditional you're working toward, and CP fills in the line that closes it.",
                ),
                Example(
                    listOf(
                        "(P ∧ Q) → R" to "Premise", "P" to "ACP", "Q" to "ACP", "P ∧ Q" to "2, 3, Conj", "R" to "1, 4, MP",
                        "Q → R" to "3–5, CP", "P → (Q → R)" to "2–6, CP",
                    ),
                    "P → (Q → R)", depths = listOf(0, 1, 2, 2, 2, 1, 0),
                ),
            ),
            Guided(
                "Two levels deep", listOf("(P ∧ Q) → R"), "P → (Q → R)", listOf(Conj, MP, ACP, CP),
                listOf(
                    Coach("The goal is P → (Q → R). Choose ACP: it fills in P. Tap Add line.") { it.hasAssumed("P", ACP) },
                    Coach("Inside, you're now proving Q → R, another conditional. Choose ACP again: it fills in Q.") { it.hasAssumed("Q", ACP) },
                    Coach("Two bars mean two open assumptions. Join P and Q: cite lines 2 and 3 and choose Conj.") { it.has("P ∧ Q") },
                    Coach("Use MP with line 1 to get R.") { it.has("R") },
                    Coach("Close the inner assumption: choose CP (it fills in Q → R) and add it.") { it.has("Q → R") },
                    Coach("Close the outer assumption the same way.") { it.done },
                ),
                "Done. Each CP closed one assumption, most recent first.",
                solution = listOf("P" to "ACP", "Q" to "ACP", "P ∧ Q" to "2, 3, Conj", "R" to "1, 4, MP", "Q → R" to "CP", "P → (Q → R)" to "CP"),
            ),
            Quiz("Check", "With two assumptions open, which one must you close first?", listOf("The most recent one", "The first one", "Either one"), 0, "Subproofs nest like brackets: the innermost one closes first."),
        ),
    )

    private val cpBiconditional = LessonDef(
        "cp-4", "Proving a biconditional", "Two conditional proofs, one for each direction.", listOf(CP, Equiv),
        listOf(
            Read(
                "Both directions",
                listOf(
                    "P ↔ Q says each side implies the other. To prove it, prove P → Q and Q → P with two separate conditional proofs.",
                    "Then join the two conditionals with Conj and turn them into P ↔ Q with Material Equivalence.",
                ),
                rules = listOf(Equiv),
            ),
            Practice(
                "Your turn", "Prove P → Q, then Q → P, then join them.", listOf("P → (Q ∧ R)", "Q → (P ∧ S)"), "P ↔ Q", listOf(MP, Simp, Conj, Equiv, ACP, CP),
                solution = listOf(
                    "P" to "ACP", "Q ∧ R" to "1, 3, MP", "Q" to "4, Simp", "P → Q" to "CP",
                    "Q" to "ACP", "P ∧ S" to "2, 7, MP", "P" to "8, Simp", "Q → P" to "CP",
                    "(P → Q) ∧ (Q → P)" to "6, 10, Conj", "P ↔ Q" to "11, Equiv",
                ),
            ),
        ),
    )

    private val cpTheorems = LessonDef(
        "cp-5", "Proofs with no premises", "Some statements are true no matter what.", listOf(CP),
        listOf(
            Read(
                "Theorems",
                listOf(
                    "A theorem is true whatever the letters mean, so it can be proved from no premises at all.",
                    "The trick is to start with an assumption. Conditional Proof lets you build the whole theorem out of subproofs.",
                ),
            ),
            Guided(
                "Proving Transposition", emptyList(), "(P → Q) → (¬Q → ¬P)", listOf(MT, ACP, CP),
                listOf(
                    Coach("There are no premises. The goal is a conditional, so choose ACP: it fills in P → Q. Add it.") { it.hasAssumed("P → Q", ACP) },
                    Coach("Inside, the goal is ¬Q → ¬P. Choose ACP again: it fills in ¬Q.") { it.hasAssumed("¬Q", ACP) },
                    Coach("Use MT on lines 1 and 2.") { it.has("¬P") },
                    Coach("Close both assumptions with CP, the inner one first.") { it.done },
                ),
                "Done. You proved the Transposition rule itself, from nothing.",
                solution = listOf("P → Q" to "ACP", "¬Q" to "ACP", "¬P" to "1, 2, MT", "¬Q → ¬P" to "CP", "(P → Q) → (¬Q → ¬P)" to "CP"),
            ),
            Practice(
                "Your turn", "Three nested conditionals. Assume each \"if\" part in turn.", emptyList(), "(P → Q) → ((Q → R) → (P → R))", listOf(HS, ACP, CP),
                solution = listOf("P → Q" to "ACP", "Q → R" to "ACP", "P → R" to "1, 2, HS", "(Q → R) → (P → R)" to "CP", "(P → Q) → ((Q → R) → (P → R))" to "CP"),
            ),
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
                    "To prove P itself, assume ¬P instead. You may assume anything, but the opposite of what you want is almost always the right choice, so AIP fills it in for you.",
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
                    Coach("To prove ¬P, assume the opposite. Choose AIP: it fills in P for you. Tap Add line.") { it.hasAssumed("P", AIP) },
                    Coach("Use Modus Ponens twice to get both Q and ¬Q.") { it.has("Q") && it.has("¬Q") },
                    Coach("Join them with Conj into Q ∧ ¬Q: a contradiction.") { it.has("Q ∧ ¬Q") || it.has("¬Q ∧ Q") },
                    Coach("The assumption led to the impossible, so it's false. Choose IP: it fills in ¬P.") { it.done },
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

    private val ipPositive = LessonDef(
        "ip-3", "Proving a positive", "Assume the negation to prove P itself.", listOf(AIP, IP),
        listOf(
            Read(
                "Assume the opposite",
                listOf(
                    "To prove P, assume ¬P. If that leads to a contradiction, ¬P is impossible, so P is true.",
                    "When two lines contradict each other, both turn red. That's your cue to join them with Conj and close with IP.",
                ),
            ),
            Guided(
                "Watch for red", listOf("¬P → Q", "¬Q"), "P", listOf(MP, Conj, AIP, IP),
                listOf(
                    Coach("Choose AIP: it fills in ¬P, the opposite of P. Add it.") { it.hasAssumed("¬P", AIP) },
                    Coach("Use MP on lines 1 and 3.") { it.has("Q") },
                    Coach("Lines 2 and 4 turned red: they contradict each other. Join them with Conj.") { it.has("Q ∧ ¬Q") || it.has("¬Q ∧ Q") },
                    Coach("Close with IP: it fills in P.") { it.done },
                ),
                "Done. ¬P led to the impossible, so P.",
                solution = listOf("¬P" to "AIP", "Q" to "1, 3, MP", "Q ∧ ¬Q" to "4, 2, Conj", "P" to "IP"),
            ),
            Quiz("Check", "You want to prove S by Indirect Proof. What do you assume?", listOf("¬S", "S", "¬¬S"), 0, "Assume the opposite of what you want. If ¬S leads to a contradiction, S must be true."),
        ),
    )

    private val ipDisguise = LessonDef(
        "ip-4", "Contradictions in disguise", "Bigger statements can contradict too.", listOf(IP),
        listOf(
            Read(
                "Not just letters",
                listOf(
                    "A contradiction is any statement next to its exact negation: P ∧ Q against ¬(P ∧ Q) counts just as much as P against ¬P.",
                    "Sometimes you need a replacement rule, like De Morgan, before the clash shows.",
                ),
            ),
            Practice(
                "Spot it", "Build P ∧ Q, then look at line 1.", listOf("¬(P ∧ Q)", "P"), "¬Q", listOf(Conj, AIP, IP),
                solution = listOf("Q" to "AIP", "P ∧ Q" to "2, 3, Conj", "(P ∧ Q) ∧ ¬(P ∧ Q)" to "4, 1, Conj", "¬Q" to "IP"),
            ),
            Practice(
                "Unmask it", "Use De Morgan on line 1 to find ¬P.", listOf("¬(P ∨ Q)", "R → P"), "¬R", listOf(MP, DeM, Simp, Conj, AIP, IP),
                solution = listOf("R" to "AIP", "P" to "2, 3, MP", "¬P ∧ ¬Q" to "1, DeM", "¬P" to "5, Simp", "P ∧ ¬P" to "4, 6, Conj", "¬R" to "IP"),
            ),
        ),
    )

    private val ipInsideCp = LessonDef(
        "ip-5", "Mixing assumption types", "An indirect proof inside a conditional proof.", listOf(ACP, AIP, CP, IP),
        listOf(
            Read(
                "Combining methods",
                listOf(
                    "To prove P → ¬Q, assume P (ACP). Inside, you need ¬Q, and you can prove that indirectly: assume Q (AIP) and find a contradiction.",
                    "Close the inner assumption with IP, then the outer one with CP.",
                ),
            ),
            Guided(
                "Two methods at once", listOf("(P ∧ Q) → R", "¬R"), "P → ¬Q", listOf(Conj, MP, ACP, CP, AIP, IP),
                listOf(
                    Coach("Choose ACP: it fills in P, the \"if\" part of the goal.") { it.hasAssumed("P", ACP) },
                    Coach("Inside, the goal is ¬Q. Choose AIP: it fills in Q, the opposite.") { it.hasAssumed("Q", AIP) },
                    Coach("Join lines 3 and 4 with Conj, then use MP with line 1.") { it.has("R") },
                    Coach("R contradicts line 2. Join them with Conj.") { it.has("R ∧ ¬R") || it.has("¬R ∧ R") },
                    Coach("Close the inner assumption: IP fills in ¬Q.") { it.has("¬Q") },
                    Coach("Close the outer assumption: CP fills in P → ¬Q.") { it.done },
                ),
                "Done. IP closed the inner assumption, then CP closed the outer one.",
                solution = listOf(
                    "P" to "ACP", "Q" to "AIP", "P ∧ Q" to "3, 4, Conj", "R" to "1, 5, MP", "R ∧ ¬R" to "6, 2, Conj", "¬Q" to "IP", "P → ¬Q" to "CP",
                ),
            ),
            Quiz(
                "Check", "ACP on line 3 and AIP on line 4 are both open. What do you close first?",
                listOf("The AIP on line 4, with IP", "The ACP on line 3, with CP"), 0,
                "The most recent assumption closes first, so the indirect proof inside finishes before the conditional proof around it.",
            ),
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
            listOf(cpLesson, cpPractice, cpNested, cpBiconditional, cpTheorems), advanced = true,
        ),
        Course(
            "ip", "Indirect Proof", "AIP · IP", "Prove something by showing its opposite is impossible.",
            Spec(ALL + listOf(ACP, CP, AIP, IP), emptyList(), 2..4, 5..10, 5, 0.2, 9, 0.1, mapOf(ATOM to 1.0), Method.INDIRECT),
            listOf(ipLesson, ipPractice, ipPositive, ipDisguise, ipInsideCp), advanced = true,
        ),
    )

    // ================= Predicate logic =================

    private val thingsLesson = LessonDef(
        "pred-1", "Talking about things", "Predicates, names, and the two quantifiers.", emptyList(),
        listOf(
            Read(
                "Looking inside statements",
                listOf(
                    "Propositional logic treats \"Socrates is mortal\" as one letter. Predicate logic looks inside it: Ms says that the thing s has the property M.",
                    "Capital letters are predicates (properties like M, \"is mortal\"). Lowercase a, b, c are names of particular things. x, y and z are variables: they stand for any thing at all.",
                ),
            ),
            Read(
                "All and some",
                listOf(
                    "∀x Fx means \"everything is F\". ∃x Fx means \"at least one thing is F\".",
                    "\"All F are G\" is written ∀x(Fx → Gx): for any thing, if it's F then it's G. \"Some F are G\" is ∃x(Fx ∧ Gx): at least one thing is both.",
                ),
            ),
            WorldStep(
                "Explore", "This world has three things, a, b and c. Tap the squares to give them property F. Make ∀x Fx true.",
                listOf("∀x Fx"), WorldGoal.ALL_TRUE, success = "\"Everything is F\" is only true when every single thing has F.",
            ),
            WorldStep(
                "Explore", "Now make ∃x Fx true but ∀x Fx false.",
                listOf("∃x Fx", "∀x Fx"), WorldGoal.COUNTEREXAMPLE, success = "Right: something is F, but not everything is.",
            ),
            WorldStep(
                "Break it", "∀x(Fx → Gx) says \"all F are G\". Make it false.",
                listOf("∀x(Fx → Gx)"), WorldGoal.FALSE, success = "One thing that's F but not G is enough to break \"all F are G\".",
            ),
            Quiz(
                "Translate", "\"All cats are mammals\" (Cx: x is a cat, Mx: x is a mammal)",
                listOf("∀x(Cx → Mx)", "∀x(Cx ∧ Mx)", "∃x(Cx → Mx)"), 0,
                "For anything, if it's a cat then it's a mammal. ∀x(Cx ∧ Mx) would claim that everything is a cat.",
            ),
            Quiz(
                "Translate", "\"Some cats are black\" (Cx: x is a cat, Bx: x is black)",
                listOf("∃x(Cx ∧ Bx)", "∃x(Cx → Bx)", "∀x(Cx → Bx)"), 0,
                "At least one thing is both a cat and black. ∃x(Cx → Bx) is true as soon as anything at all isn't a cat.",
            ),
        ),
    )

    private val uiLesson = LessonDef(
        "pred-2", "Universal Instantiation", "What's true of everything is true of each thing.", listOf(UI),
        listOf(
            Read("Dropping ∀", listOf("If everything is F, then any particular thing is F. From ∀x Fx you may write Fa, or Fb, or Fx: the same statement about one thing."), rules = listOf(UI)),
            Guided(
                "Socrates", listOf("∀x(Hx → Mx)", "Hs"), "Ms", listOf(UI, MP),
                listOf(
                    Coach("Line 1 says every human is mortal. Cite it, choose UI, and write Hs → Ms: the same statement about s (Socrates).") { it.has("Hs → Ms") },
                    Coach("Now Modus Ponens with line 2 finishes the proof.") { it.done },
                ),
                "Done: the most famous argument in logic. Socrates is mortal.",
                solution = listOf("Hs → Ms" to "1, UI", "Ms" to "3, 2, MP"),
            ),
            Practice(
                "Your turn", "Instantiate both \"all\" statements with a.", listOf("∀x(Fx → Gx)", "∀x(Gx → Hx)", "Fa"), "Ha", listOf(UI, MP, HS),
                solution = listOf("Fa → Ga" to "1, UI", "Ga" to "4, 3, MP", "Ga → Ha" to "2, UI", "Ha" to "6, 5, MP"),
            ),
        ),
    )

    private val egLesson = LessonDef(
        "pred-3", "Existential Generalization", "If this thing is F, something is.", listOf(EG),
        listOf(
            Read("Adding ∃", listOf("If a particular thing is F, then something is F. From Fa you may write ∃x Fx."), rules = listOf(EG)),
            Practice(
                "Your turn", "Get Ga, then generalize.", listOf("∀x(Fx → Gx)", "Fa"), "∃x Gx", listOf(UI, MP, EG),
                solution = listOf("Fa → Ga" to "1, UI", "Ga" to "3, 2, MP", "∃x Gx" to "4, EG"),
            ),
            Quiz(
                "Check", "From Fa ∧ Gb, which line can EG give?", listOf("∃x(Fx ∧ Gb)", "∃x(Fx ∧ Gx)", "∀x(Fx ∧ Gb)"), 0,
                "EG replaces one name with the variable. a and b are different things, so they can't both become x.",
            ),
        ),
    )

    private val ugLesson = LessonDef(
        "pred-4", "Proving \"all\"", "Universal Generalization from an arbitrary x.", listOf(UG),
        listOf(
            Read(
                "Arbitrary things",
                listOf(
                    "To prove ∀x Fx, show Fx, where x is an arbitrary thing you know nothing special about. Then UG lets you write ∀x Fx.",
                    "Never generalize from a name. Fa might be true only of a.",
                ),
                rules = listOf(UG),
            ),
            Guided(
                "All F are H", listOf("∀x(Fx → Gx)", "∀x(Gx → Hx)"), "∀x(Fx → Hx)", listOf(UI, HS, UG),
                listOf(
                    Coach("Cite line 1, choose UI, and instantiate with the variable x: write Fx → Gx.") { it.has("Fx → Gx") },
                    Coach("Do the same with line 2.") { it.has("Gx → Hx") },
                    Coach("Chain them with HS.") { it.has("Fx → Hx") },
                    Coach("x was arbitrary, so generalize: cite line 5, choose UG and write ∀x(Fx → Hx).") { it.done },
                ),
                "Done: if all F are G and all G are H, then all F are H.",
                solution = listOf("Fx → Gx" to "1, UI", "Gx → Hx" to "2, UI", "Fx → Hx" to "3, 4, HS", "∀x(Fx → Hx)" to "5, UG"),
            ),
            Quiz("Check", "From Fa, can UG give ∀x Fx?", listOf("No: a is one particular thing", "Yes"), 0, "Knowing that one thing is F says nothing about everything else."),
        ),
    )

    private val eiLesson = LessonDef(
        "pred-5", "Naming a \"some\"", "Existential Instantiation and the new-name rule.", listOf(EI),
        listOf(
            Read(
                "Give it a name",
                listOf(
                    "From ∃x Fx you may name the thing that's F: write Fa. But the name must be new. If a already appears in the proof, you'd be assuming that the F thing is that same a.",
                ),
                rules = listOf(EI),
            ),
            WorldStep(
                "Why the name must be new", "\"Something is F. Something is G. So something is both.\" Find a world where both premises are true but the conclusion is false.",
                listOf("∃x Fx", "∃x Gx", "∃x(Fx ∧ Gx)"), WorldGoal.COUNTEREXAMPLE, argument = true,
                success = "One thing is F and a different thing is G. Reusing one name for both would \"prove\" this false conclusion.",
            ),
            Guided(
                "EI first", listOf("∀x(Fx → Gx)", "∃x Fx"), "∃x Gx", listOf(EI, UI, MP, EG),
                listOf(
                    Coach("Do EI first, while every name is still new: cite line 2, choose EI and write Fa.") { it.has("Fa") },
                    Coach("Now use UI on line 1 with the same name: Fa → Ga.") { it.has("Fa → Ga") },
                    Coach("MP gives Ga.") { it.has("Ga") },
                    Coach("Finish with EG.") { it.done },
                ),
                "Done. EI picked the name, UI followed it, EG generalized at the end.",
                solution = listOf("Fa" to "2, EI", "Fa → Ga" to "1, UI", "Ga" to "4, 3, MP", "∃x Gx" to "5, EG"),
            ),
            Quiz(
                "Check", "Why do EI before UI?", listOf("UI can use any name, but EI needs a new one", "UI only works on new names", "The order doesn't matter"), 0,
                "If UI used a first, a would no longer be new, and EI couldn't use it.",
            ),
        ),
    )

    private val qnLesson = LessonDef(
        "pred-6", "Not all, not any", "Quantifier Negation: De Morgan for ∀ and ∃.", listOf(QN),
        listOf(
            Read(
                "Moving the negation",
                listOf(
                    "¬∀x Fx (\"not everything is F\") means ∃x ¬Fx (\"something isn't F\").",
                    "¬∃x Fx (\"nothing is F\") means ∀x ¬Fx (\"everything is not F\"). QN swaps the quantifier as the negation passes through it.",
                ),
                rules = listOf(QN),
            ),
            WorldStep(
                "See it", "Try every world: tap the squares in different combinations. The two statements always agree.",
                listOf("¬∀x Fx", "∃x ¬Fx"), WorldGoal.EVERY_WORLD, success = "They agree in every world, so you can always swap one for the other.",
            ),
            Quiz("Check", "¬∃x Fx says:", listOf("Nothing is F", "Something isn't F", "Not everything is F"), 0, "There isn't anything that's F: everything is not F."),
            Practice(
                "Your turn", "QN, then UI, then unpack it with De Morgan.", listOf("¬∃x(Fx ∧ ¬Gx)", "Fa"), "Ga", listOf(QN, UI, DeM, DN, Impl, MP),
                solution = listOf(
                    "∀x ¬(Fx ∧ ¬Gx)" to "1, QN", "¬(Fa ∧ ¬Ga)" to "3, UI", "¬Fa ∨ ¬¬Ga" to "4, DeM", "¬Fa ∨ Ga" to "5, DN", "Fa → Ga" to "6, Impl", "Ga" to "7, 2, MP",
                ),
            ),
        ),
    )

    private val translateLesson = LessonDef(
        "pred-7", "Harder translations", "\"No\", \"only\" and \"not all\".", emptyList(),
        listOf(
            Read(
                "Four tricky words",
                listOf(
                    "\"No F are G\": ∀x(Fx → ¬Gx). Anything that's F is not G.",
                    "\"Only F are G\": ∀x(Gx → Fx). It's about the G things: each one must be F. The arrow points the opposite way from \"all F are G\".",
                    "\"Not all F are G\": ¬∀x(Fx → Gx), which QN turns into ∃x(Fx ∧ ¬Gx): some F isn't G.",
                ),
            ),
            Quiz("Translate", "\"No dogs are cats\" (Dx, Cx)", listOf("∀x(Dx → ¬Cx)", "¬∀x(Dx → Cx)", "∀x(¬Dx → Cx)"), 0, "Anything that's a dog is not a cat. ¬∀x(Dx → Cx) only says that not every dog is a cat."),
            Quiz("Translate", "\"Only members may vote\" (Mx: x is a member, Vx: x may vote)", listOf("∀x(Vx → Mx)", "∀x(Mx → Vx)", "∃x(Mx ∧ Vx)"), 0, "Anyone who may vote must be a member. ∀x(Mx → Vx) says every member may vote, which is a different claim."),
            Quiz("Translate", "\"Not every bird can fly\" (Bx, Fx)", listOf("¬∀x(Bx → Fx)", "∀x(Bx → ¬Fx)", "¬∃x(Bx ∧ Fx)"), 0, "The other two both say that no bird can fly."),
            WorldStep(
                "Explore", "∀x(Fx → ¬Gx) says no F are G. Make it true while something is F and something is G.",
                listOf("∀x(Fx → ¬Gx)", "∃x Fx", "∃x Gx"), WorldGoal.ALL_TRUE, success = "The F things and the G things have to be different things.",
            ),
        ),
    )

    private val invalidLesson = LessonDef(
        "pred-8", "Invalid arguments", "Prove an argument fails by building a counterexample.", emptyList(),
        listOf(
            Read(
                "Counterexamples",
                listOf(
                    "A proof shows an argument is valid. To show one is invalid, describe a world where every premise is true and the conclusion is false. That world is a counterexample.",
                    "If one exists, no proof is possible, however hard you try.",
                ),
            ),
            WorldStep(
                "All F are G, so all G are F?", "Find a world where the premise is true but the conclusion is false.",
                listOf("∀x(Fx → Gx)", "∀x(Gx → Fx)"), WorldGoal.COUNTEREXAMPLE, argument = true,
                success = "Something that's G but not F does it. \"All dogs are animals\" doesn't make every animal a dog.",
            ),
            WorldStep(
                "Affirming the consequent, again", "All F are G, a is G, so a is F? Find a counterexample.",
                listOf("∀x(Fx → Gx)", "Ga", "Fa"), WorldGoal.COUNTEREXAMPLE, argument = true,
                success = "a can be G without being F. It's the same trap as in course 1, now with quantifiers.",
            ),
            WorldStep(
                "Some and some", "Some F are G, some G are H, so some F are H? Find a counterexample.",
                listOf("∃x(Fx ∧ Gx)", "∃x(Gx ∧ Hx)", "∃x(Fx ∧ Hx)"), WorldGoal.COUNTEREXAMPLE, argument = true,
                success = "The F-and-G thing and the G-and-H thing can be different things.",
            ),
            Quiz(
                "Check", "You couldn't find a counterexample in a three-thing world. Is the argument valid?",
                listOf("Not necessarily: a counterexample might need more things", "Yes, always"), 0,
                "Some counterexamples need bigger worlds. To be sure an argument is valid, prove it.",
            ),
        ),
    )

    private val cpQuantLesson = LessonDef(
        "pred-9", "Conditional proof with quantifiers", "Assume \"everything is F\" and see what follows.", listOf(ACP, CP, UG),
        listOf(
            Read(
                "Assuming a quantified statement",
                listOf(
                    "To prove ∀x Fx → ∀x Gx, assume ∀x Fx (ACP), derive ∀x Gx inside, and close with CP.",
                    "UG still works inside the subproof, as long as the assumption isn't about the variable you generalize. ∀x Fx is fine: its x is bound. An assumption like Fx would block UG on x.",
                ),
            ),
            Guided(
                "All F to all G", listOf("∀x(Fx → Gx)"), "∀x Fx → ∀x Gx", listOf(UI, MP, UG, ACP, CP),
                listOf(
                    Coach("Tap ACP: it fills in ∀x Fx. Add it.") { it.hasAssumed("∀x Fx", ACP) },
                    Coach("Use UI on line 2 with the variable x: write Fx.") { it.has("Fx") },
                    Coach("Use UI on line 1 with x as well: Fx → Gx.") { it.has("Fx → Gx") },
                    Coach("MP gives Gx.") { it.has("Gx") },
                    Coach("Use UG to write ∀x Gx. It's allowed because the assumption doesn't mention a free x.") { it.has("∀x Gx") },
                    Coach("Close the subproof with CP.") { it.done },
                ),
                "Done. If everything is F, then everything is G.",
                solution = listOf("∀x Fx" to "ACP", "Fx" to "2, UI", "Fx → Gx" to "1, UI", "Gx" to "4, 3, MP", "∀x Gx" to "5, UG", "∀x Fx → ∀x Gx" to "CP"),
            ),
            Quiz("Check", "Inside a subproof that assumed Fx, can you use UG on x?", listOf("No: the assumption is about x, so x isn't arbitrary", "Yes"), 0, "Under the assumption Fx, x is a particular thing that you've supposed is F, not an arbitrary one."),
            Practice(
                "Your turn", "Assume ∀x Fx, chain the conditionals at x, and generalize.", listOf("∀x(Fx → Gx)", "∀x(Gx → Hx)"), "∀x Fx → ∀x Hx", listOf(UI, MP, HS, UG, ACP, CP),
                solution = listOf("∀x Fx" to "ACP", "Fx" to "3, UI", "Fx → Gx" to "1, UI", "Gx → Hx" to "2, UI", "Fx → Hx" to "5, 6, HS", "Hx" to "7, 4, MP", "∀x Hx" to "8, UG", "∀x Fx → ∀x Hx" to "CP"),
            ),
        ),
    )

    private val ipQuantLesson = LessonDef(
        "pred-10", "Indirect proof with quantifiers", "Prove \"nothing is F\" by assuming something is.", listOf(AIP, IP),
        listOf(
            Read(
                "Assume the opposite, again",
                listOf("To prove ¬∃x Fx (\"nothing is F\"), assume ∃x Fx. EI names the F thing, and a contradiction about that named thing closes the proof."),
            ),
            Guided(
                "Nothing is F", listOf("∀x(Fx → Gx)", "¬∃x Gx"), "¬∃x Fx", listOf(EI, UI, MP, QN, Conj, AIP, IP),
                listOf(
                    Coach("Tap AIP: it fills in ∃x Fx, the opposite of the goal. Add it.") { it.hasAssumed("∃x Fx", AIP) },
                    Coach("Name the F thing with EI: write Fa.") { it.has("Fa") },
                    Coach("Use UI on line 1 with a, then MP, to get Ga.") { it.has("Ga") },
                    Coach("Line 2 says nothing is G. QN turns it into ∀x ¬Gx.") { it.has("∀x ¬Gx") },
                    Coach("Use UI with a to get ¬Ga. Watch it turn red.") { it.has("¬Ga") },
                    Coach("Join Ga and ¬Ga with Conj, then close with IP.") { it.done },
                ),
                "Done. Assuming something is F led to a contradiction, so nothing is F.",
                solution = listOf("∃x Fx" to "AIP", "Fa" to "3, EI", "Fa → Ga" to "1, UI", "Ga" to "5, 4, MP", "∀x ¬Gx" to "2, QN", "¬Ga" to "7, UI", "Ga ∧ ¬Ga" to "6, 8, Conj", "¬∃x Fx" to "IP"),
            ),
        ),
    )

    private val relationsLesson = LessonDef(
        "pred-11", "Two-place predicates", "Relations between things, where order matters.", emptyList(),
        listOf(
            Read(
                "Relations",
                listOf(
                    "Some predicates relate two things. Lab can mean \"a loves b\". Order matters: Lba says b loves a, which is a different claim. Laa says a loves itself.",
                    "With quantifiers: ∀x∃y Lxy is \"everyone loves someone\". ∃y∀x Lxy is \"there's someone everyone loves\".",
                ),
            ),
            WorldStep(
                "Explore", "Each square says whether the row thing relates to the column thing. Make Lab true but Lba false.",
                listOf("Lab", "Lba"), WorldGoal.COUNTEREXAMPLE, success = "Right: a loving b doesn't mean b loves a.",
            ),
            WorldStep(
                "Everyone loves someone", "Make ∀x∃y Lxy true: every row needs at least one square on.",
                listOf("∀x∃y Lxy"), WorldGoal.ALL_TRUE, success = "Each thing relates to something, though not necessarily the same thing.",
            ),
            Quiz(
                "Translate", "\"Someone is loved by everyone\"", listOf("∃y∀x Lxy", "∀x∃y Lxy", "∀y∃x Lxy"), 0,
                "There is one y such that every x loves it. ∀x∃y Lxy lets each x love a different someone.",
            ),
        ),
    )

    private val orderLesson = LessonDef(
        "pred-12", "Quantifier order", "Why ∀x∃y and ∃y∀x are different.", listOf(EI, UG),
        listOf(
            WorldStep(
                "Find the gap", "Everyone loves someone, so someone is loved by everyone? Find a counterexample.",
                listOf("∀x∃y Lxy", "∃y∀x Lxy"), WorldGoal.COUNTEREXAMPLE, argument = true,
                success = "Everyone loves someone, but no one thing is loved by all. The argument is invalid.",
            ),
            Read(
                "The rule that stops a fake proof",
                listOf(
                    "A tempting \"proof\": from ∀x∃y Lxy, use UI with x to get ∃y Lxy, then EI to get Lxa, then UG to get ∀x Lxa, then EG to get ∃y∀x Lxy.",
                    "The UG step is illegal. The name a came from EI while x was free, so a may depend on x: each x may love a different thing. The app blocks UG on a variable that was free in a line from EI.",
                    "The other direction is valid: if someone is loved by everyone, then everyone loves someone.",
                ),
            ),
            Guided(
                "The valid direction", listOf("∃x∀y Lxy"), "∀y∃x Lxy", listOf(EI, UI, EG, UG),
                listOf(
                    Coach("Use EI on line 1 with a new name: write ∀y Lay.") { it.has("∀y Lay") },
                    Coach("Use UI with the variable y: Lay.") { it.has("Lay") },
                    Coach("Use EG, putting x in place of a: ∃x Lxy.") { it.has("∃x Lxy") },
                    Coach("Use UG on y: ∀y∃x Lxy. This is allowed because y wasn't free in the line that came from EI.") { it.done },
                ),
                "Done. ∃x∀y gives ∀y∃x, but never the other way round.",
                solution = listOf("∀y Lay" to "1, EI", "Lay" to "2, UI", "∃x Lxy" to "3, EG", "∀y∃x Lxy" to "4, UG"),
            ),
        ),
    )

    private val relProofsLesson = LessonDef(
        "pred-13", "Proofs with relations", "Symmetry, transitivity and friends.", listOf(UI),
        listOf(
            Read("One quantifier at a time", listOf("UI removes the outermost quantifier only. For ∀x∀y φ, use UI once for x, then again for y.")),
            Guided(
                "Transitivity", listOf("∀x∀y∀z((Lxy ∧ Lyz) → Lxz)", "Lab", "Lbc"), "Lac", listOf(UI, Conj, MP),
                listOf(
                    Coach("Use UI on line 1 with a: ∀y∀z((Lay ∧ Lyz) → Laz).") { it.has("∀y∀z((Lay ∧ Lyz) → Laz)") },
                    Coach("Again with b: ∀z((Lab ∧ Lbz) → Laz).") { it.has("∀z((Lab ∧ Lbz) → Laz)") },
                    Coach("And with c: (Lab ∧ Lbc) → Lac.") { it.has("(Lab ∧ Lbc) → Lac") },
                    Coach("Join lines 2 and 3 with Conj, then finish with MP.") { it.done },
                ),
                "Done: a relates to b, b to c, so a to c.",
                solution = listOf("∀y∀z((Lay ∧ Lyz) → Laz)" to "1, UI", "∀z((Lab ∧ Lbz) → Laz)" to "4, UI", "(Lab ∧ Lbc) → Lac" to "5, UI", "Lab ∧ Lbc" to "2, 3, Conj", "Lac" to "6, 7, MP"),
            ),
            Practice(
                "Symmetry and \"some\"", "Use EI first, then UI twice.", listOf("∀x∀y(Lxy → Lyx)", "∃y Lay"), "∃y Lya", listOf(EI, UI, MP, EG),
                solution = listOf("Lab" to "2, EI", "∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "4, UI", "Lba" to "5, 3, MP", "∃y Lya" to "6, EG"),
            ),
        ),
    )

    private val identityLesson = LessonDef(
        "pred-14", "Same thing, two names", "Identity, and swapping one name for another.", listOf(Id, IdS),
        listOf(
            Read(
                "Identity",
                listOf(
                    "a = b says that a and b are the same thing under two names, like Mark Twain and Samuel Clemens.",
                    "If they're the same thing, whatever is true of one is true of the other. From Fa and a = b, Substitution (Id) lets you write Fb. Symmetry (IdS) turns a = b into b = a.",
                ),
                rules = listOf(Id, IdS),
            ),
            Guided(
                "Twain and Clemens", listOf("Wt", "t = c"), "Wc", listOf(Id),
                listOf(Coach("Wt: Twain was a writer. t = c: Twain is Clemens. Cite both lines, choose Id and write Wc.") { it.done }),
                "Done. Same person, so the same facts apply.",
                solution = listOf("Wc" to "1, 2, Id"),
            ),
            Quiz("Check", "From Fa and a = b, what does Substitution give?", listOf("Fb", "Fa ∧ Fb", "b = a"), 0, "Swap a for b in Fa. (b = a would come from Symmetry instead.)"),
            Practice(
                "Your turn", "UI and MP get you Ga. Then switch names.", listOf("∀x(Fx → Gx)", "Fa", "a = b"), "Gb", listOf(UI, MP, Id),
                solution = listOf("Fa → Ga" to "1, UI", "Ga" to "4, 2, MP", "Gb" to "5, 3, Id"),
            ),
        ),
    )

    private val differentLesson = LessonDef(
        "pred-15", "Different things", "Reflexivity, and proving two things are not the same.", listOf(IdR, IP),
        listOf(
            Read(
                "Not the same",
                listOf(
                    "¬(a = b) says a and b are different things. You prove it like any negation: assume a = b and find a contradiction.",
                    "Reflexivity (IdR) is the simplest rule there is: you may always write a = a, citing nothing.",
                ),
                rules = listOf(IdR),
            ),
            Guided(
                "Telling them apart", listOf("Fa", "¬Fb"), "¬(a = b)", listOf(Id, Conj, AIP, IP),
                listOf(
                    Coach("Tap AIP: it fills in a = b, the opposite of the goal. Add it.") { it.hasAssumed("a = b", AIP) },
                    Coach("If a is b, then Fa makes b F too. Use Id on lines 1 and 3 to write Fb.") { it.has("Fb") },
                    Coach("Fb contradicts line 2. Join them with Conj, then close with IP.") { it.done },
                ),
                "Done. a is F and b isn't, so they can't be the same thing.",
                solution = listOf("a = b" to "AIP", "Fb" to "1, 3, Id", "Fb ∧ ¬Fb" to "4, 2, Conj", "¬(a = b)" to "IP"),
            ),
            Practice(
                "Using reflexivity", "UI with a, then IdR gives the if-part for free.", listOf("∀x(x = a → Fx)"), "Fa", listOf(UI, IdR, MP),
                solution = listOf("a = a → Fa" to "1, UI", "a = a" to "IdR", "Fa" to "2, 3, MP"),
            ),
        ),
    )

    private val countingLesson = LessonDef(
        "pred-16", "Counting with logic", "\"Only\", \"at least two\", \"at most one\", \"exactly one\".", emptyList(),
        listOf(
            Read(
                "Numbers without numbers",
                listOf(
                    "Only a is F: Fa ∧ ∀x(Fx → x = a).",
                    "At least two things are F: ∃x∃y((Fx ∧ Fy) ∧ ¬(x = y)).",
                    "At most one thing is F: ∀x∀y((Fx ∧ Fy) → x = y).",
                    "Exactly one thing is F: ∃x(Fx ∧ ∀y(Fy → y = x)).",
                ),
            ),
            WorldStep(
                "At least two", "Make \"at least two things are F\" true.",
                listOf("∃x∃y((Fx ∧ Fy) ∧ ¬(x = y))"), WorldGoal.ALL_TRUE, success = "Two different things are F, so x and y can be two distinct things.",
            ),
            WorldStep(
                "Exactly one", "Now make \"exactly one thing is F\" true.",
                listOf("∃x(Fx ∧ ∀y(Fy → y = x))"), WorldGoal.ALL_TRUE, success = "One F thing, and anything that's F is that same thing.",
            ),
            Quiz(
                "Translate", "\"At most one thing is F\"", listOf("∀x∀y((Fx ∧ Fy) → x = y)", "∃x∃y((Fx ∧ Fy) ∧ ¬(x = y))", "∃x Fx"), 0,
                "Any two F things you pick turn out to be the same thing. The second option says at least two.",
            ),
            Practice(
                "At most one", "UI twice, join a and b with Conj, then MP.", listOf("∀x∀y((Fx ∧ Fy) → x = y)", "Fa", "Fb"), "a = b", listOf(UI, Conj, MP),
                solution = listOf("∀y((Fa ∧ Fy) → a = y)" to "1, UI", "(Fa ∧ Fb) → a = b" to "4, UI", "Fa ∧ Fb" to "2, 3, Conj", "a = b" to "5, 6, MP"),
            ),
        ),
    )

    private val PRED_INNER = listOf(MP, MT, HS, DS, Simp, Conj)

    val predicateCourses = listOf(
        Course(
            "pred-quant", "Quantifiers", "∀ · ∃ · UI · EG", "What \"all\" and \"some\" mean, and how to use them.",
            Spec(PRED_INNER + listOf(UI, EG), emptyList(), 1..3, 1..4, 3, 0.2, 6, 0.15, mapOf(ATOM to 3.0, NEG to 1.0), lift = setOf(Lift.INSTANCE)),
            listOf(thingsLesson, translateLesson, uiLesson, egLesson), track = Track.PREDICATE,
        ),
        Course(
            "pred-allsome", "All and Some", "UG · EI", "Prove \"all\" statements and reason from \"some\".",
            Spec(PRED_INNER + listOf(Add, UI, EG, EI, UG), emptyList(), 1..3, 1..5, 3, 0.2, 6, 0.15, mapOf(ATOM to 2.0, NEG to 1.0, IMP to 2.0), lift = setOf(Lift.UNIVERSAL, Lift.EXISTENTIAL)),
            listOf(ugLesson, eiLesson, invalidLesson), track = Track.PREDICATE,
        ),
        Course(
            "pred-qn", "Quantifier Negation", "QN", "\"Not all\" and \"none\": moving negations past quantifiers.",
            Spec(PRED_INNER + listOf(Add, DN, DeM, Impl, UI, EG, EI, UG, QN), emptyList(), 1..3, 1..5, 3, 0.2, 6, 0.15, mapOf(ATOM to 2.0, NEG to 1.0, IMP to 2.0), lift = setOf(Lift.NEGATION)),
            listOf(qnLesson), track = Track.PREDICATE,
        ),
        Course(
            "pred-assume", "Quantifiers and Assumptions", "CP · IP with ∀ ∃", "Conditional and indirect proof in predicate logic.",
            Spec(PRED_INNER + listOf(Add, UI, EG, EI, UG, QN, ACP, CP, AIP, IP), emptyList(), 1..3, 1..5, 3, 0.2, 6, 0.15, mapOf(ATOM to 2.0, NEG to 1.0, IMP to 2.0), lift = setOf(Lift.CONDITIONAL)),
            listOf(cpQuantLesson, ipQuantLesson), track = Track.PREDICATE,
        ),
        Course(
            "pred-rel", "Relations", "Lxy · quantifier order", "Two-place predicates, and why ∀x∃y isn't ∃y∀x.",
            Spec(listOf(MP, MT, Conj, Impl, Taut, UI, EG, EI, UG), emptyList(), 1..1, 1..9, 3, 0.0, 9, 0.0, mapOf(ATOM to 1.0), patterns = "relations"),
            listOf(relationsLesson, orderLesson, relProofsLesson), track = Track.PREDICATE,
        ),
        Course(
            "pred-id", "Identity", "= · Id · IdR · IdS", "Same thing or different things, and counting with logic.",
            Spec(listOf(MP, Simp, Conj, Com, UI, EI, EG, IdR, IdS, Id, AIP, IP), emptyList(), 1..1, 1..9, 3, 0.0, 9, 0.0, mapOf(ATOM to 1.0), patterns = "identity"),
            listOf(identityLesson, differentLesson, countingLesson), track = Track.PREDICATE,
        ),
    )

    const val DAILY_PRED_ID = "daily-pred"

    /** The daily predicate proof: a mixed predicate puzzle, the same for everyone each day. */
    val dailyPredicate = Course(
        DAILY_PRED_ID, "Daily Predicate Proof", "Mixed", "A new predicate-logic proof every day.",
        Spec(PRED_INNER + listOf(Add, DN, DeM, Impl, UI, EG, EI, UG, QN), emptyList(), 2..3, 2..5, 3, 0.2, 6, 0.15, mapOf(ATOM to 2.0, NEG to 1.0, IMP to 2.0),
            lift = setOf(Lift.INSTANCE, Lift.UNIVERSAL, Lift.EXISTENTIAL, Lift.NEGATION)),
        emptyList(), track = Track.PREDICATE,
    )

    /** Every course, both tabs. */
    val allCourses get() = courses + predicateCourses

    const val DAILY_ID = "daily"
    const val DAILY_LEVEL = 3

    val daily = Course(
        DAILY_ID, "Daily Proof", "Mixed", "A new mixed proof every day.",
        Spec(ALL, emptyList(), 3..4, 4..8, 5, 0.25, 9, 0.1, mapOf(ATOM to 2.0, NEG to 2.0, IMP to 1.0, OR to 1.0, AND to 1.0)),
        emptyList(),
    )

    fun byId(id: String): Course = when (id) {
        DAILY_ID -> daily
        DAILY_PRED_ID -> dailyPredicate
        else -> allCourses.first { it.id == id }
    }

    fun isDaily(id: String) = id == DAILY_ID || id == DAILY_PRED_ID

    fun lesson(id: String): Pair<Course, LessonDef> =
        allCourses.firstNotNullOf { c -> c.lessons.firstOrNull { it.id == id }?.let { c to it } }

    /** All rules a board should offer, in menu order. */
    fun boardRules(rules: List<Rule>): List<Rule> = Rule.entries.filter { it in rules }
}
