package com.therefore.logic.content

import com.therefore.logic.content.LessonStep.KripkeStep
import com.therefore.logic.content.LessonStep.Practice
import com.therefore.logic.content.LessonStep.Quiz
import com.therefore.logic.content.LessonStep.Read
import com.therefore.logic.content.LessonStep.TimelineStep
import com.therefore.logic.content.LessonStep.TreeStep
import com.therefore.logic.engine.GoalShape
import com.therefore.logic.engine.Rule.*
import com.therefore.logic.engine.Spec
import com.therefore.logic.ext.Drills
import com.therefore.logic.ext.FrameProp

/** Courses practised with exercise sets need no proof generator; this placeholder is never used to generate. */
private val NO_PROOFS = Spec(emptyList(), emptyList(), 1..1, 1..1, 2, 0.0, 4, 0.0, mapOf(GoalShape.ATOM to 1.0))

// ======================= Truth trees =======================

private val treeBasics = LessonDef(
    "tree-1", "How a tree works", "Hunt for a counterexample, one branch at a time.", emptyList(),
    listOf(
        Read(
            "Looking for a counterexample",
            listOf(
                "A truth tree asks: could the premises be true and the conclusion false, all at once? It writes down the premises and the negated conclusion, then breaks them into simpler pieces.",
                "Some formulas are true in only one way: P ∧ Q needs both P and Q. Their pieces go straight down the branch: that's a stack.",
                "Others are true in two ways: P ∨ Q needs P or Q. The tree splits so it can follow both possibilities: that's a branch.",
                "A branch with a formula and its negation, like P and ¬P, is impossible, so it closes (✗). If every branch closes, no counterexample exists and the argument is valid. A branch that stays open with nothing left to break down describes a counterexample.",
            ),
        ),
        TreeStep(
            "Your first tree", "Tap line 1, P → Q. A conditional is true in two ways (the \"if\" part false, or the \"then\" part true), so branch it. Then close both branches.",
            listOf("P → Q", "P"), "Q", valid = true,
            success = "Both branches closed: the left has P and ¬P, the right has Q and ¬Q. There's no way for the premises to be true and the conclusion false. That's Modus Ponens, checked by tree.",
        ),
        TreeStep(
            "A tree that stays open", "Same idea, a different argument. Break line 1 down and see what happens.",
            listOf("P → Q", "Q"), "P", valid = false,
            success = "An open branch with nothing left to break down: P false and Q true makes both premises true and the conclusion false. This is the fallacy of affirming the consequent, caught by the tree.",
        ),
        Quiz(
            "Check", "What does an open, finished branch tell you?",
            listOf("The argument is valid", "There's a way to make the premises true and the conclusion false", "You made a mistake"), 1,
            "An open branch is a consistent story: read its letters off and you have a counterexample. Valid arguments have trees where every branch closes.",
        ),
    ),
)

private val treeRules = LessonDef(
    "tree-2", "The rules", "Which formulas stack and which branch.", emptyList(),
    listOf(
        Read(
            "Stack or branch",
            listOf(
                "Stack (one way to be true): P ∧ Q gives P, Q. ¬(P ∨ Q) gives ¬P, ¬Q. ¬(P → Q) gives P, ¬Q. ¬¬P gives P.",
                "Branch (two ways): P ∨ Q gives P | Q. P → Q gives ¬P | Q. ¬(P ∧ Q) gives ¬P | ¬Q. P ↔ Q gives P, Q | ¬P, ¬Q. ¬(P ↔ Q) gives P, ¬Q | ¬P, Q.",
                "Tip: do the stacks first. Branching early copies everything below into both sides and makes the tree bigger.",
            ),
        ),
        Quiz(
            "Check", "How does ¬(P → Q) break down?",
            listOf("Stack: P and ¬Q", "Branch: ¬P or Q", "Stack: ¬P and Q"), 0,
            "A conditional is false in exactly one case: the \"if\" part true and the \"then\" part false. One way, so stack P and ¬Q.",
        ),
        TreeStep(
            "De Morgan, by tree", "Stack line 2 first, keep stacking the double negations, then branch line 1.",
            listOf("¬(P ∧ Q)"), "¬P ∨ ¬Q", valid = true,
            success = "Every branch closed, so ¬(P ∧ Q) really does give ¬P ∨ ¬Q. You've just checked one of De Morgan's laws without a proof.",
        ),
        TreeStep(
            "Disjunctive Syllogism", "Branch the \"or\", then close what you can.",
            listOf("P ∨ Q", "¬P"), "Q", valid = true,
            success = "Valid: the P branch clashes with ¬P, and the Q branch clashes with the negated conclusion.",
        ),
        TreeStep(
            "Stack first", "Stack the negated conclusion before branching anything.",
            listOf("(P ∧ Q) → R", "P"), "Q → R", valid = true,
            success = "Valid. That's Exportation at work: \"if P and Q, then R\" with P gives \"if Q, then R\".",
        ),
    ),
)

private val treeCounter = LessonDef(
    "tree-3", "Counterexamples and limits", "Read counterexamples off open branches, and see what trees can't do.", emptyList(),
    listOf(
        TreeStep(
            "Read the open branch", "Break it down and find the open branch.",
            listOf("P ∨ Q"), "P", valid = false,
            success = "Invalid: P false and Q true makes P ∨ Q true and P false.",
        ),
        TreeStep(
            "Denying the antecedent", "A classic fallacy. Let the tree find the problem.",
            listOf("P → Q", "¬P"), "¬Q", valid = false,
            success = "Invalid: P false and Q true. Knowing the \"if\" part is false tells you nothing about the \"then\" part.",
        ),
        TreeStep(
            "If and only if", "P ↔ Q branches into two stacks: both true, or both false.",
            listOf("P ↔ Q", "Q"), "P", valid = true,
            success = "Valid: in the both-true branch P clashes with ¬P; in the both-false branch ¬Q clashes with Q.",
        ),
        TreeStep(
            "Two branchings", "Stack the negated conclusion, then branch line 1, then branch again.",
            listOf("P → (Q ∨ R)", "¬Q"), "P → R", valid = true,
            success = "Valid. Every one of the three branches closed.",
        ),
        Read(
            "Trees and proofs",
            listOf(
                "For propositional logic a tree always finishes, and it always gives the right answer. It's a decision procedure: no insight needed, just patience.",
                "Proofs need ideas, but they explain why a conclusion follows, and they're what mathematicians write.",
                "Trees work for predicate logic too, with rules for ∀ and ∃. But there a tree can go on forever: no method can decide every predicate-logic argument. That surprising fact was proved by Church and Turing in 1936.",
            ),
        ),
    ),
)

// ======================= Modal logic =======================

private val modalBasics = LessonDef(
    "modal-1", "Necessarily and possibly", "□ and ◇, read in possible worlds.", emptyList(),
    listOf(
        Read(
            "Ways things could be",
            listOf(
                "Modal logic adds two operators: □P, \"necessarily P\", and ◇P, \"possibly P\".",
                "A possible world is a complete way things could be. Arrows show which worlds count as possible from where: if w1 has an arrow to w2, then w2 is a possibility for w1.",
                "□P is true at a world when P is true in every world it sees. ◇P is true when P is true in at least one.",
            ),
        ),
        KripkeStep(
            "Necessarily", "w1 sees w2 and w3. Switch letters until □P is true at w1.",
            3, listOf("□P"), WorldsGoal.ALL_TRUE, arrows = setOf(0 to 1, 0 to 2), fixedArrows = true,
            success = "P is true in every world w1 sees, so it's necessary at w1. What's true at w1 itself doesn't matter: w1 doesn't see itself here.",
        ),
        KripkeStep(
            "Possible, not necessary", "Now make P possible but not necessary at w1.",
            3, listOf("◇P", "¬□P"), WorldsGoal.ALL_TRUE, arrows = setOf(0 to 1, 0 to 2), fixedArrows = true,
            success = "P holds in one world w1 sees but not the other: possible, not necessary.",
        ),
        KripkeStep(
            "True here, impossible there", "Make P true at w1 while □¬P is true at w1 too.",
            3, listOf("P", "□¬P"), WorldsGoal.ALL_TRUE, arrows = setOf(0 to 1, 0 to 2), fixedArrows = true,
            success = "Strange but allowed: w1 doesn't count itself as a possibility, so P can be actual yet \"impossible\". Most systems add arrows from each world to itself to rule this out.",
        ),
        Quiz(
            "Two sides of one coin", "Which means the same as ¬◇P (\"P isn't possible\")?",
            listOf("□¬P", "◇¬P", "¬□P"), 0,
            "If P is true in no world you can see, then ¬P is true in all of them. □ and ◇ swap through a negation, just like ∀ and ∃.",
        ),
    ),
)

private val modalArrows = LessonDef(
    "modal-2", "Arrows matter", "Dead ends, and how arrow patterns make axioms true.", emptyList(),
    listOf(
        Read(
            "Arrows decide everything",
            listOf(
                "Change the arrows and you change what's necessary. A world with no arrows at all is a dead end: every □ is true there (nothing to check), and every ◇ is false.",
                "You can draw and erase arrows in the next steps with the grid under the worlds.",
            ),
        ),
        KripkeStep(
            "A dead end", "Make both □P and □¬P true at w1. It sounds impossible…",
            2, listOf("□P", "□¬P"), WorldsGoal.ALL_TRUE, arrows = setOf(0 to 1),
            success = "With no arrows out of w1, \"every world w1 sees\" is no worlds at all, so both are true. Vacuous truth, just like \"all unicorns can fly\".",
        ),
        KripkeStep(
            "Necessary but false?", "Make □P → P false at w1: P necessary, yet false.",
            2, listOf("□P → P"), WorldsGoal.FALSE, arrows = setOf(0 to 1),
            success = "It fails when w1 doesn't see itself: P is true everywhere w1 looks, but not at w1.",
        ),
        KripkeStep(
            "Every world sees itself", "Now only reflexive models count: every world needs an arrow to itself. Try to make □P → P false again.",
            2, listOf("□P → P"), WorldsGoal.FALSE, arrows = setOf(0 to 0, 1 to 1, 0 to 1), frame = setOf(FrameProp.REFLEXIVE), impossible = true,
            success = "Right: once w1 sees itself, □P means P is true at w1 too. This is axiom T, and it holds exactly in reflexive models.",
        ),
    ),
)

private val modalSystems = LessonDef(
    "modal-3", "Axioms and systems", "K, T, 4, B and S5, matched to arrow patterns.", emptyList(),
    listOf(
        Read(
            "Axioms meet arrows",
            listOf(
                "K: □(P → Q) → (□P → □Q). True in every model, whatever the arrows. It's the base of all normal modal logics.",
                "T: □P → P. True when every world sees itself (reflexive).",
                "4: □P → □□P. True when arrows chain: if w1 sees w2 and w2 sees w3, then w1 sees w3 (transitive).",
                "B: P → □◇P. True when every arrow has one back (symmetric).",
                "S5 has all of them: every world sees every world in its group, so \"possible\" means possible in any way at all.",
            ),
        ),
        KripkeStep(
            "Axiom 4 can fail", "w1 sees w2, and w2 sees w3. Make □P → □□P false at w1.",
            3, listOf("□P → □□P"), WorldsGoal.FALSE, arrows = setOf(0 to 1, 1 to 2),
            success = "P is necessary at w1 (true at w2), but not necessarily necessary: w2 sees w3, where P is false.",
        ),
        KripkeStep(
            "Add the shortcut", "Only transitive models count now. Can you still make □P → □□P false?",
            3, listOf("□P → □□P"), WorldsGoal.FALSE, arrows = setOf(0 to 1, 1 to 2, 0 to 2), frame = setOf(FrameProp.TRANSITIVE), impossible = true,
            success = "Correct: with the shortcut arrow, everything w2 sees, w1 sees too, so P is true there as well. Axiom 4 holds in transitive models.",
        ),
        KripkeStep(
            "K never fails", "Try any arrows and letters you like. Can you make K false at w1?",
            2, listOf("□(P → Q) → (□P → □Q)"), WorldsGoal.FALSE, arrows = setOf(0 to 1), impossible = true,
            success = "K holds in every model: in each world w1 sees, P → Q and P are true, so Q is too.",
        ),
        Quiz(
            "Check", "Which arrow pattern makes □P → P true everywhere?",
            listOf("Reflexive: every world sees itself", "Symmetric: every arrow has one back", "No arrows at all"), 0,
            "If w sees itself, then \"P in every world w sees\" includes w. That's axiom T.",
        ),
    ),
)

private val modalReadings = LessonDef(
    "modal-4", "Other readings of □", "Knowledge, obligation, and choosing the right axioms.", emptyList(),
    listOf(
        Read(
            "One logic, many uses",
            listOf(
                "The same □ can mean different things, and each reading wants different axioms.",
                "Knowledge: □P as \"I know P\". T fits: you can't know something false. Belief doesn't fit T: people believe false things.",
                "Obligation: □P as \"P is required\" and ◇P as \"P is permitted\". T fails here, sadly: what's required doesn't always happen.",
                "Time and proof have their own readings too: the next course reads □ as \"always\".",
            ),
        ),
        Quiz(
            "Check", "If □ means \"it is obligatory\", should □P → P hold?",
            listOf("Yes: obligations always get done", "No: something can be required and still not happen", "Only on weekends"), 1,
            "Obligation logics drop T. They keep a weaker axiom instead: □P → ◇P, \"if it's required, it's permitted\".",
        ),
        Quiz(
            "Check", "Axiom 4, □P → □□P, read as knowledge, says…",
            listOf("If you know P, you know that you know P", "If P is true, you know it", "You know everything"), 0,
            "Philosophers call this positive introspection, and they argue about whether real knowers have it.",
        ),
    ),
)

// ======================= Temporal logic =======================

private val timeBasics = LessonDef(
    "time-1", "Always and eventually", "□ and ◇ read as time.", emptyList(),
    listOf(
        Read(
            "The same symbols, read in time",
            listOf(
                "Temporal logic reads the moments of a timeline as worlds, with arrows pointing to the future.",
                "□P: P is true now and at every later moment, \"always\". ◇P: P is true now or at some later moment, \"eventually\".",
                "Our timelines have 5 moments, starting at now. Goals are about now.",
            ),
        ),
        TimelineStep(
            "Always", "Make □P true now.", 5, listOf("□P"), WorldsGoal.ALL_TRUE,
            success = "P at every moment from now on: always P.",
        ),
        TimelineStep(
            "Eventually, not always", "Make Q happen at some point, but not all the time.", 5, listOf("◇Q", "¬□Q"), WorldsGoal.ALL_TRUE,
            success = "Eventually Q, but not always Q.",
        ),
        Quiz(
            "Check", "Which means the same as ¬□P (\"not always P\")?",
            listOf("◇¬P: at some point, not P", "□¬P: never P", "¬◇P: P never happens"), 0,
            "The duality you met in modal logic, now in time: \"not always\" means \"at some point not\".",
        ),
        TimelineStep(
            "Whenever", "Make P happen at least once, and Q true at every moment P is.", 5, listOf("□(P → Q)", "◇P"), WorldsGoal.ALL_TRUE,
            success = "□(P → Q) says \"whenever P, also Q\". It doesn't care about moments without P.",
        ),
    ),
)

private val timeNext = LessonDef(
    "time-2", "Next and until", "○ for the next moment, U for until.", emptyList(),
    listOf(
        Read(
            "Two more operators",
            listOf(
                "○P: P is true at the next moment. At the last moment there is no next one, so ○P is false there.",
                "P U Q (\"P until Q\"): Q happens at some moment, and P is true at every moment before that.",
            ),
        ),
        TimelineStep(
            "Next", "Make P false now but true at the next moment.", 5, listOf("○P", "¬P"), WorldsGoal.ALL_TRUE,
            success = "Not yet, but next.",
        ),
        TimelineStep(
            "Until", "Make P U Q true now, with Q false now.", 5, listOf("P U Q", "¬Q"), WorldsGoal.ALL_TRUE,
            success = "P holds until Q arrives. After Q, P can do whatever it likes.",
        ),
        TimelineStep(
            "Broken until", "Now Q should still happen eventually, but P U Q should be false.", 5, listOf("◇Q", "¬(P U Q)"), WorldsGoal.ALL_TRUE,
            success = "Q comes, but P stopped before it did, so \"P until Q\" fails.",
        ),
        Quiz(
            "Check", "At the last moment of the timeline, is ○P true?",
            listOf("Yes, if P is true", "No: there's no next moment", "Only if P is always true"), 1,
            "On a finite timeline the last moment has no successor, so every ○ claim is false there. Real tools often use endless timelines instead.",
        ),
    ),
)

private val timeSpecs = LessonDef(
    "time-3", "Checking systems", "Safety, liveness, and model checking.", emptyList(),
    listOf(
        Read(
            "Specifications",
            listOf(
                "Engineers write what a system must do as temporal formulas, then let software check every possible run. That's called model checking, and it's used for chips, train signals and spacecraft code.",
                "Safety: something bad never happens. □¬(P ∧ Q): two trains (P and Q) are never on the same track at once.",
                "Liveness: something good eventually happens. □(P → ◇Q): every request (P) is eventually granted (Q).",
            ),
        ),
        TimelineStep(
            "A request ignored", "Find a run where some request (P) is never granted (Q) afterwards.", 5, listOf("□(P → ◇Q)"), WorldsGoal.FALSE,
            success = "That run breaks the liveness property. A model checker would print it as a counterexample trace.",
        ),
        TimelineStep(
            "Every request granted", "Now make at least one request happen, and grant every request.", 5, listOf("□(P → ◇Q)", "◇P"), WorldsGoal.ALL_TRUE,
            success = "Each P is followed (now or later) by a Q.",
        ),
        TimelineStep(
            "Sharing the track", "Both trains must run at some point, but never at the same moment.", 5, listOf("□¬(P ∧ Q)", "◇P", "◇Q"), WorldsGoal.ALL_TRUE,
            success = "Safe and useful: both trains run, never together.",
        ),
        Quiz(
            "Check", "Which is a safety property?",
            listOf("□¬(P ∧ Q)", "◇Q", "□(P → ◇Q)"), 0,
            "Safety says something bad never happens, and a single bad moment breaks it. The other two promise that something good eventually happens: liveness.",
        ),
    ),
)

// ======================= Intuitionistic logic =======================

private val constructive = LessonDef(
    "int-1", "Proof as construction", "Why P ∨ ¬P isn't free.", listOf(AIP, IP),
    listOf(
        Read(
            "Truth means a proof",
            listOf(
                "Intuitionistic (or constructive) logic, begun by L. E. J. Brouwer, counts a statement as true only once you can show it.",
                "A proof of P ∧ Q is a proof of P and a proof of Q. A proof of P ∨ Q is a proof of one side, plus which side it is.",
                "A proof of P → Q is a method that turns any proof of P into a proof of Q. A proof of ¬P is a method that turns any proof of P into a contradiction.",
                "So P ∨ ¬P isn't automatic: you'd need to prove P or refute it, and for open problems nobody can do either yet.",
            ),
        ),
        Quiz(
            "Check", "Nobody has proved or refuted Goldbach's conjecture. Can a constructivist assert \"Goldbach's conjecture is true or it's false\"?",
            listOf("Yes, every statement is true or false", "Not yet: that needs a proof of one side", "Only if a computer checks it"), 1,
            "Constructively, asserting an \"or\" means being able to say which side holds. For Goldbach, nobody can yet.",
        ),
        Read(
            "What stays and what goes",
            listOf(
                "Still fine: MP, MT, HS, DS, Simp, Conj, Add, Conditional Proof, and proving a negation by contradiction (assume P, reach a contradiction, conclude ¬P).",
                "Gone: excluded middle (P ∨ ¬P), removing a double negation (from ¬¬P to P), indirect proof of a positive statement (assume ¬P, reach a contradiction, conclude P), and some directions of De Morgan and Material Implication.",
            ),
        ),
        Practice(
            "Proving a negation", "Assume P, reach a contradiction, and close with IP to get ¬P. Constructively this is fine: it's what ¬P means.",
            listOf("P → Q", "¬Q"), "¬P", listOf(MP, Conj, AIP, IP),
            listOf("P" to "AIP", "Q" to "1, 3, MP", "Q ∧ ¬Q" to "4, 2, Conj", "¬P" to "IP"),
        ),
    ),
)

private val knowledgeWorlds = LessonDef(
    "int-2", "States of knowledge", "Countermodels where knowledge grows.", emptyList(),
    listOf(
        Read(
            "Knowledge that grows",
            listOf(
                "Kripke gave intuitionistic logic a picture: worlds are states of knowledge, and arrows lead to later states where more has been proved. Once something is proved, it stays proved.",
                "¬P holds at a state when P will never be proved later. P → Q holds when every later state with P also has Q. P ∨ ¬P needs P now, or a guarantee that P never comes.",
            ),
        ),
        KripkeStep(
            "No excluded middle", "w1 is now, w2 is later. Make P ∨ ¬P fail at w1.",
            2, listOf("P ∨ ¬P"), WorldsGoal.FALSE, KLogic.INTUITIONISTIC, arrows = setOf(0 to 1), fixedArrows = true,
            success = "P isn't proved yet at w1, so P fails; but it gets proved at w2, so ¬P fails too. Neither side holds now.",
        ),
        KripkeStep(
            "No double negation", "Same states. Make ¬¬P → P fail at w1.",
            2, listOf("¬¬P → P"), WorldsGoal.FALSE, KLogic.INTUITIONISTIC, arrows = setOf(0 to 1), fixedArrows = true,
            success = "At w1 you know P can't be ruled out (it's proved later), but you don't have P yet. Knowing \"not not P\" isn't having P.",
        ),
        KripkeStep(
            "Half of De Morgan", "Two possible futures. Make ¬(P ∧ Q) → (¬P ∨ ¬Q) fail at w1.",
            3, listOf("¬(P ∧ Q) → (¬P ∨ ¬Q)"), WorldsGoal.FALSE, KLogic.INTUITIONISTIC, arrows = setOf(0 to 1, 0 to 2), fixedArrows = true,
            success = "P and Q are never proved together, but you can't yet say which one will fail: each might still be proved, in different futures.",
        ),
        KripkeStep(
            "This direction holds", "Try to make P → ¬¬P fail at w1.",
            2, listOf("P → ¬¬P"), WorldsGoal.FALSE, KLogic.INTUITIONISTIC, arrows = setOf(0 to 1), fixedArrows = true, impossible = true,
            success = "Right: if you have P, it can never be refuted. Adding a double negation is fine; only removing one is the problem.",
        ),
    ),
)

private val proofsPrograms = LessonDef(
    "int-3", "Proofs are programs", "Why computer scientists love constructive logic.", emptyList(),
    listOf(
        Read(
            "Proofs as programs",
            listOf(
                "Read constructively, a proof of P → Q is a function that turns evidence for P into evidence for Q. A proof that something exists comes with the thing itself.",
                "This match between proofs and programs is called the Curry–Howard correspondence. It's the idea behind proof assistants like Lean, which check proofs the way a compiler checks code.",
                "Lean's core logic is constructive. Classical reasoning is added on top as an axiom, so you can always see which proofs rely on it.",
            ),
        ),
        Quiz(
            "Check", "Which rule would a constructivist reject?",
            listOf("Modus Ponens", "Indirect proof of a positive statement: assume ¬P, reach a contradiction, conclude P", "Conjunction"), 1,
            "A contradiction from ¬P only shows that P can't be refuted. It doesn't build a proof of P.",
        ),
        Quiz(
            "Check", "A constructive proof that \"some number has property F\" gives you…",
            listOf("A particular number, with a proof that it has F", "Only a guarantee that it's not impossible", "Nothing extra"), 0,
            "Constructive existence proofs come with a witness. Classical ones can prove something exists without saying which.",
        ),
    ),
)

/** The truth-tree course and the three "new kinds of logic" courses. */
val newLogicCourses: List<Course> = listOf(
    Course(
        "trees", "Truth Trees", "Stack · Branch · Close", "A second way to test any argument: break formulas apart until every branch closes.",
        NO_PROOFS, listOf(treeBasics, treeRules, treeCounter), track = Track.EXTRA, section = Curriculum.SECTION_TREES, drills = Drills::trees,
    ),
    Course(
        "modal", "Possible Worlds", "□ · ◇ · Kripke models", "Necessity and possibility, read with worlds and arrows.",
        NO_PROOFS, listOf(modalBasics, modalArrows, modalSystems, modalReadings), track = Track.EXTRA, section = Curriculum.SECTION_NEW, drills = Drills::modal,
    ),
    Course(
        "temporal", "Logic of Time", "□ always · ◇ eventually · ○ · U", "Always, eventually, next and until: the logic engineers use to check systems.",
        NO_PROOFS, listOf(timeBasics, timeNext, timeSpecs), track = Track.EXTRA, section = Curriculum.SECTION_NEW, drills = Drills::temporal,
    ),
    Course(
        "intuit", "Constructive Logic", "No P ∨ ¬P for free", "Logic where proving means showing how, and proofs become programs.",
        NO_PROOFS, listOf(constructive, knowledgeWorlds, proofsPrograms), track = Track.EXTRA, section = Curriculum.SECTION_NEW, drills = Drills::intuitionistic,
    ),
)
