package com.therefore.logic.content

import com.therefore.logic.content.LessonStep.OrderStep
import com.therefore.logic.content.LessonStep.Practice
import com.therefore.logic.content.LessonStep.Quiz
import com.therefore.logic.content.LessonStep.Read
import com.therefore.logic.content.LessonStep.Table
import com.therefore.logic.engine.GoalShape
import com.therefore.logic.engine.Rule.*
import com.therefore.logic.engine.Spec
import kotlin.random.Random

private val QUIZ_ONLY = Spec(emptyList(), emptyList(), 1..1, 1..1, 2, 0.0, 4, 0.0, mapOf(GoalShape.ATOM to 1.0))

/** A practice set: [n] questions from a pool, in a random order, with their options shuffled. */
private fun quizSet(pool: List<Quiz>, n: Int, rng: Random, extra: List<LessonStep> = emptyList()): List<LessonStep> =
    pool.shuffled(rng).take(n).map { q ->
        val order = q.options.indices.shuffled(rng)
        q.copy(options = order.map { q.options[it] }, answer = order.indexOf(q.answer))
    } + extra.shuffled(rng).take(1)

// ======================= Descriptions and counting (Predicate tab) =======================

private val theLesson = LessonDef(
    "pred-17", "The F", "Russell's theory of descriptions.", listOf(EI, EG),
    listOf(
        Read(
            "The king of France",
            listOf(
                "\"The king of France is bald.\" There is no king of France, so is this sentence true, false, or neither?",
                "Bertrand Russell's answer: \"the F is G\" says three things. Something is F; nothing else is F; and it is G.",
                "In symbols: ∃x((Fx ∧ ∀y(Fy → y = x)) ∧ Gx). With no king of France the first part fails, so the sentence is simply false.",
            ),
        ),
        Quiz(
            "Check", "On Russell's reading, which part of \"the F is G\" says there's only one F?",
            listOf("∀y(Fy → y = x)", "Fx", "Gx"), 0,
            "∀y(Fy → y = x) says anything that's F is x itself: uniqueness. Fx gives existence, and Gx says it is G.",
        ),
        Practice(
            "The F is G, so some F is G", "Name the F with EI, pull out Fa and Ga with Simp (and Com), join them, and generalize.",
            listOf("∃x((Fx ∧ ∀y(Fy → y = x)) ∧ Gx)"), "∃x(Fx ∧ Gx)", listOf(Simp, Conj, Com, EI, EG),
            listOf(
                "(Fa ∧ ∀y(Fy → y = a)) ∧ Ga" to "1, EI", "Fa ∧ ∀y(Fy → y = a)" to "2, Simp", "Fa" to "3, Simp",
                "Ga ∧ (Fa ∧ ∀y(Fy → y = a))" to "2, Com", "Ga" to "5, Simp", "Fa ∧ Ga" to "4, 6, Conj", "∃x(Fx ∧ Gx)" to "7, EG",
            ),
        ),
        Practice(
            "Uniqueness at work", "b is F, and the F is G. Use the uniqueness part to show b is the F, then swap names with Id.",
            listOf("∃x((Fx ∧ ∀y(Fy → y = x)) ∧ Gx)", "Fb"), "Gb", listOf(MP, Simp, Com, EI, UI, Id),
            listOf(
                "(Fa ∧ ∀y(Fy → y = a)) ∧ Ga" to "1, EI", "Fa ∧ ∀y(Fy → y = a)" to "3, Simp", "∀y(Fy → y = a) ∧ Fa" to "4, Com",
                "∀y(Fy → y = a)" to "5, Simp", "Fb → b = a" to "6, UI", "b = a" to "7, 2, MP",
                "Ga ∧ (Fa ∧ ∀y(Fy → y = a))" to "3, Com", "Ga" to "9, Simp", "Gb" to "10, 8, Id",
            ),
        ),
    ),
)

private val howManyLesson = LessonDef(
    "pred-18", "At least, at most, exactly", "Counting without numbers.", listOf(AIP, IP),
    listOf(
        Read(
            "Counting with = alone",
            listOf(
                "At least two things are F: ∃x∃y((Fx ∧ Fy) ∧ ¬(x = y)).",
                "At most one thing is F: ∀x∀y((Fx ∧ Fy) → x = y). Any two F's are the same thing.",
                "Exactly one: at least one, and at most one. That's the uniqueness you met in \"the F\".",
            ),
        ),
        Quiz(
            "Check", "Which says \"at most one thing is F\"?",
            listOf("∀x∀y((Fx ∧ Fy) → x = y)", "∃x∃y((Fx ∧ Fy) ∧ ¬(x = y))", "∃x Fx"), 0,
            "If any two F's are identical, there can't be two different ones. The second formula says at least two; the third, at least one.",
        ),
        Practice(
            "Only one seat", "At most one thing is F, a is F, and a isn't b. Assume Fb and find the contradiction.",
            listOf("∀x∀y((Fx ∧ Fy) → x = y)", "Fa", "¬(a = b)"), "¬Fb", listOf(MP, Conj, UI, AIP, IP),
            listOf(
                "Fb" to "AIP", "∀y((Fa ∧ Fy) → a = y)" to "1, UI", "(Fa ∧ Fb) → a = b" to "5, UI", "Fa ∧ Fb" to "2, 4, Conj",
                "a = b" to "6, 7, MP", "(a = b) ∧ ¬(a = b)" to "8, 3, Conj", "¬Fb" to "IP",
            ),
        ),
    ),
)

private val manyQuantifiersLesson = LessonDef(
    "pred-19", "Several quantifiers", "Functions as relations, and proofs that peel off one ∀ at a time.", listOf(UI),
    listOf(
        Read(
            "Functions, the logician's way",
            listOf(
                "\"The mother of a\" is a function: each person has exactly one. Our language has no function symbols, but a relation with uniqueness does the job: ∀x∃y(Myx ∧ ∀z(Mzx → z = y)), \"everyone has exactly one mother\".",
                "Proofs with several ∀s peel them off one at a time with UI, one name per step.",
            ),
        ),
        Practice(
            "Symmetry", "UI twice (first a, then b), then MP.",
            listOf("∀x∀y(Lxy → Lyx)", "Lab"), "Lba", listOf(MP, UI),
            listOf("∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "3, UI", "Lba" to "4, 2, MP"),
        ),
        Practice(
            "Symmetric and transitive", "Get Lba from symmetry, then use transitivity with a, b, a.",
            listOf("∀x∀y(Lxy → Lyx)", "∀x∀y∀z((Lxy ∧ Lyz) → Lxz)", "Lab"), "Laa", listOf(MP, Conj, UI),
            listOf(
                "∀y(Lay → Lya)" to "1, UI", "Lab → Lba" to "4, UI", "Lba" to "5, 3, MP", "∀y∀z((Lay ∧ Lyz) → Laz)" to "2, UI",
                "∀z((Lab ∧ Lbz) → Laz)" to "7, UI", "(Lab ∧ Lba) → Laa" to "8, UI", "Lab ∧ Lba" to "3, 6, Conj", "Laa" to "9, 10, MP",
            ),
        ),
    ),
)

val descriptionsCourse = Course(
    "pred-desc", "Descriptions and Counting", "the F · at most one", "\"The\", \"exactly one\", and proofs with several quantifiers.",
    Spec(listOf(MP, Simp, Conj, Com, UI, EI, EG, Id, AIP, IP), emptyList(), 1..1, 1..12, 3, 0.0, 12, 0.0, mapOf(GoalShape.ATOM to 1.0), patterns = "descriptions"),
    listOf(theLesson, howManyLesson, manyQuantifiersLesson), track = Track.PREDICATE,
)

// ======================= Logic about logic =======================

private val META_POOL = listOf(
    Quiz("Soundness", "Soundness says that…", listOf("everything provable is valid", "everything valid is provable", "every statement is true or false"), 0,
        "Sound rules never take you from true premises to a false conclusion, so anything you can prove really follows."),
    Quiz("Completeness", "Completeness says that…", listOf("every valid argument has a proof", "every proof is short", "every statement is provable"), 0,
        "If an argument is valid, some proof of it exists. Gödel proved this for predicate logic in 1929."),
    Quiz("Counterexamples", "An argument has a counterexample. What does soundness tell you?", listOf("It has no proof at all", "It has a long proof", "Nothing"), 0,
        "Provable means valid, and a counterexample means invalid, so no proof can exist, however clever."),
    Quiz("Deciding", "Which can a computer always decide, given enough time?", listOf("Validity in propositional logic", "Validity in predicate logic", "Every statement of arithmetic"), 0,
        "Truth tables, trees and SAT solvers always finish for propositional logic. Predicate logic is undecidable (Church and Turing, 1936)."),
    Quiz("Searching for proofs", "If a predicate-logic argument is valid, a program that tries every proof in turn will…", listOf("eventually find one", "never find one", "find one in under a second"), 0,
        "By completeness a proof exists, so a patient search finds it. The trouble is invalid arguments: the search may run forever."),
    Quiz("Halting", "The halting problem shows that…", listOf("no program can decide, for every program, whether it halts", "every program halts", "computers can't do arithmetic"), 0,
        "Any supposed halting-checker can be turned against itself, which is the same trick as in Gödel's proof."),
    Quiz("Gödel's sentence", "The Gödel sentence says, in effect…", listOf("\"This sentence is not provable\"", "\"This sentence is false\"", "\"1 + 1 = 2\""), 0,
        "\"This sentence is false\" is a paradox. \"This sentence is not provable\" is not: it's true and unprovable (in a consistent system)."),
    Quiz("Incompleteness", "The first incompleteness theorem says a consistent system for arithmetic, with checkable rules…", listOf("has true statements it can't prove", "can prove everything true", "is inconsistent"), 0,
        "Some true statement about the numbers escapes every such system. Adding it as an axiom just creates a new one."),
    Quiz("Second theorem", "The second incompleteness theorem says such a system can't prove…", listOf("its own consistency", "1 + 1 = 2", "the Pythagorean theorem"), 0,
        "A system strong enough for arithmetic can't prove, by its own rules, that it never proves a contradiction."),
    Quiz("Two kinds of complete", "Doesn't incompleteness contradict completeness?", listOf("No: completeness is about truth in every model, incompleteness about truth in the standard numbers", "Yes, one of them is wrong", "They're the same theorem"), 0,
        "Gödel's sentence is true of the ordinary numbers but false in some strange models of the axioms, so it isn't logically valid, and completeness doesn't apply."),
    Quiz("What it means", "Does incompleteness mean mathematics is broken?", listOf("No: it shows a limit of any single formal system", "Yes, nothing can be proved", "Only geometry is affected"), 0,
        "Mathematicians still prove theorems every day. Incompleteness says no one rulebook captures every truth about numbers."),
)

private val haltingOrder = OrderStep(
    "The halting argument", "Put the proof that no halting-checker can exist in order.",
    listOf(
        "Suppose a program H could tell, for any program and input, whether it halts.",
        "Build a program D that asks H about D running on itself.",
        "D does the opposite: if H says it halts, D loops forever; if H says it loops, D halts.",
        "Whatever H answers about D is wrong.",
        "So no such H can exist.",
    ),
    why = "Start by assuming the checker exists, build the troublemaker from it, then find the contradiction.",
    success = "That's a proof by contradiction, with self-reference doing the work. Gödel's proof uses the same idea on provability.",
)

private val godelOrder = OrderStep(
    "Gödel's argument", "Put the outline of Gödel's first incompleteness theorem in order.",
    listOf(
        "Code every formula and proof as a number, so arithmetic can talk about proofs.",
        "Build a sentence G that says \"G is not provable\".",
        "If G were provable, the system would prove something false, so it wouldn't be sound.",
        "So, if the system is sound, G is not provable.",
        "But that is exactly what G says, so G is true.",
        "G is true but unprovable: the system is incomplete.",
    ),
    why = "Coding comes first, then the self-referential sentence, then the case analysis.",
    success = "That's the heart of one of the most famous results in logic (1931).",
)

private val metaSound = LessonDef(
    "meta-1", "Do the rules work?", "Soundness and completeness: proofs match validity.", emptyList(),
    listOf(
        Read(
            "Two ways to be a good argument",
            listOf(
                "You've met two tests. Provable: a proof exists using the rules. Valid: no row, world or model makes the premises true and the conclusion false.",
                "Soundness: everything provable is valid. The rules never lead from truth to falsehood.",
                "Completeness: everything valid is provable. Kurt Gödel proved this for predicate logic in 1929.",
                "Together they say the rules capture validity exactly: no more, no less.",
            ),
        ),
        Table(
            "Explore", "Find the counterexample to P → Q, Q ∴ P.",
            listOf("P → Q", "Q", "P"), TableGoal.COUNTEREXAMPLE, argument = true,
            success = "A counterexample, so the argument is invalid. By soundness, no proof of it exists, however clever. That's how logicians show something can't be proved.",
        ),
        META_POOL[0], META_POOL[1], META_POOL[2],
    ),
)

private val metaDecide = LessonDef(
    "meta-2", "What can be decided?", "Decision procedures, undecidability and the halting problem.", emptyList(),
    listOf(
        Read(
            "Decision procedures",
            listOf(
                "A decision procedure is a mechanical method that always finishes with the right yes-or-no answer.",
                "Propositional logic has several: truth tables, truth trees, SAT solvers. They might be slow, but they always finish.",
                "Predicate logic has none. In 1936 Alonzo Church and Alan Turing proved that no program can decide every predicate-logic argument.",
                "The key idea is Turing's halting problem: no program can tell, for every program and input, whether it eventually stops.",
            ),
        ),
        haltingOrder,
        META_POOL[3], META_POOL[4],
    ),
)

private val metaGodel = LessonDef(
    "meta-3", "Gödel's incompleteness", "True statements that no system can prove.", emptyList(),
    listOf(
        Read(
            "A sentence about itself",
            listOf(
                "In 1931 Gödel showed how arithmetic can talk about its own proofs: give every formula and proof a code number, and \"x is a proof of y\" becomes a statement about numbers.",
                "Then he built a sentence G that says \"G is not provable\".",
                "If the system could prove G, it would prove something false. So a sound system can't prove G, which makes G true.",
            ),
        ),
        godelOrder,
        META_POOL[6], META_POOL[7], META_POOL[8], META_POOL[9],
    ),
)

val metaCourse = Course(
    "meta", "Logic about Logic", "Soundness · decidability · Gödel", "Why the rules work, and the limits no rules can pass.",
    QUIZ_ONLY, listOf(metaSound, metaDecide, metaGodel), track = Track.EXTRA, section = Curriculum.SECTION_META,
    drills = { rng -> quizSet(META_POOL, 5, rng, listOf(haltingOrder, godelOrder)) },
)

// ======================= Induction =======================

private val INDUCTION_POOL = listOf(
    Quiz("Two parts", "A proof by induction on n needs…", listOf("a base case and an inductive step", "only a base case", "a truth table"), 0,
        "Base case: it holds for 0 (or 1). Step: if it holds for n, it holds for n + 1. Then it holds for every n."),
    Quiz("The step", "In the inductive step, you assume the statement for…", listOf("one particular but arbitrary n", "every number", "n + 1"), 0,
        "You assume it for n (the induction hypothesis) and prove it for n + 1. Assuming it for every number would beg the question."),
    Quiz("Missing base case", "\"Assume n = n + 1. Then n + 1 = n + 2.\" What's wrong with concluding n = n + 1 for every n?", listOf("The base case fails: 0 isn't 1", "The step is invalid", "Nothing"), 0,
        "The step really does work, but with no true starting point the dominoes never start falling."),
    Quiz("Horses", "The \"all horses are the same colour\" proof fails at…", listOf("the step from 1 horse to 2: the two groups don't overlap", "the base case", "every step"), 0,
        "From n to n + 1 it uses a horse in both groups. With 2 horses, the groups of 1 share no horse, so the step breaks."),
    Quiz("Strong induction", "Strong induction lets you assume the statement for…", listOf("every number below n + 1", "only n", "nothing"), 0,
        "Assume it for all smaller numbers. That's what you need when n + 1 splits into smaller pieces, as with prime factors."),
    Quiz("Recursion", "Induction is the mirror image of…", listOf("recursion: defining something for 0, then n + 1 from n", "truth tables", "De Morgan's laws"), 0,
        "A recursive definition builds each value from the one before; induction proves facts about it the same way."),
    Quiz("Sum", "The sum 1 + 2 + … + 10 is…", listOf("55", "100", "45"), 0,
        "n(n + 1)/2 with n = 10 gives 55: the formula proved by induction in this course."),
)

private val sumOrder = OrderStep(
    "1 + 2 + … + n", "Prove 1 + 2 + … + n = n(n + 1)/2 for every n. Put the proof in order.",
    listOf(
        "Base case: for n = 0 the sum is empty, 0, and 0 · 1 / 2 = 0.",
        "Inductive step: assume 1 + … + n = n(n + 1)/2 for some n.",
        "Add n + 1 to both sides: 1 + … + (n + 1) = n(n + 1)/2 + (n + 1).",
        "Simplify the right side: (n + 1)(n + 2)/2.",
        "That's the formula for n + 1, so the step holds.",
        "By induction, the formula holds for every n.",
    ),
    extra = listOf("Assume the formula holds for every n."),
    why = "Base case first, then assume it for n, then work towards n + 1.",
    success = "A complete induction proof. The step used the formula for n to get it for n + 1.",
)

private val primesOrder = OrderStep(
    "Prime factors", "Prove every number from 2 up is a product of primes, by strong induction.",
    listOf(
        "Take a number n ≥ 2, and assume every number from 2 up to n − 1 is a product of primes.",
        "If n is prime, it's a product of one prime, and we're done.",
        "Otherwise n = a · b with a and b both between 2 and n − 1.",
        "By the assumption, a and b are products of primes.",
        "So n = a · b is a product of primes too.",
    ),
    why = "Strong induction: assume it for everything smaller, then split into cases.",
    success = "Strong induction at work: the pieces a and b are smaller than n, though not n − 1.",
)

private val inductionDominoes = LessonDef(
    "ind-1", "Dominoes", "Base case, inductive step, every number.", emptyList(),
    listOf(
        Read(
            "Infinitely many cases in two steps",
            listOf(
                "To prove something for every number 0, 1, 2, …, you can't check them one by one. Induction does it in two steps.",
                "Base case: show it's true for 0. Inductive step: show that if it's true for some n, it's true for n + 1.",
                "Like dominoes: the first falls, and each one knocks over the next, so they all fall.",
                "In logic terms, the step proves P(n) → P(n + 1) for an arbitrary n, the way UG does. Induction is the extra rule that arithmetic needs.",
            ),
        ),
        sumOrder,
        INDUCTION_POOL[0], INDUCTION_POOL[1],
    ),
)

private val inductionWrong = LessonDef(
    "ind-2", "Getting it wrong", "Broken inductions, and how to spot them.", emptyList(),
    listOf(
        Read(
            "All horses are the same colour?",
            listOf(
                "Base case: one horse is the same colour as itself.",
                "Step: given n + 1 horses, the first n are one colour (by assumption), and so are the last n. They overlap, so all n + 1 share a colour.",
                "So all horses are the same colour. Something has gone wrong. Where?",
            ),
        ),
        INDUCTION_POOL[3], INDUCTION_POOL[2],
    ),
)

private val inductionStrong = LessonDef(
    "ind-3", "Strong induction and recursion", "Assume everything smaller.", emptyList(),
    listOf(
        Read(
            "Using all the smaller cases",
            listOf(
                "Sometimes n + 1 depends on cases much smaller than n. Strong induction lets you assume the statement for every number below the one you're proving.",
                "Recursion is the same pattern in programs: factorial(0) = 1, factorial(n + 1) = (n + 1) · factorial(n). Proofs about recursive programs are inductions.",
            ),
        ),
        primesOrder,
        INDUCTION_POOL[4], INDUCTION_POOL[5],
    ),
)

val inductionCourse = Course(
    "induction", "Proofs About Numbers", "Induction", "Prove something for every number at once.",
    QUIZ_ONLY, listOf(inductionDominoes, inductionWrong, inductionStrong), track = Track.EXTRA, section = Curriculum.SECTION_MATH,
    drills = { rng -> quizSet(INDUCTION_POOL, 4, rng, listOf(sumOrder, primesOrder)) },
)

// ======================= Lean =======================

private const val LEAN_EDITOR = "https://live.lean-lang.org"

private val LEAN_POOL = listOf(
    Quiz("Modus Ponens", "With h1 : P → Q and h2 : P, which Lean term proves Q?", listOf("h1 h2", "h2 h1", "⟨h1, h2⟩"), 0,
        "A proof of P → Q is a function, and MP is applying it: h1 h2."),
    Quiz("Simplification", "With h : P ∧ Q, which term proves P?", listOf("h.1", "h.2", "Or.inl h"), 0,
        "A proof of P ∧ Q is a pair; .1 takes the left part."),
    Quiz("Conjunction", "With hp : P and hq : Q, which term proves P ∧ Q?", listOf("⟨hp, hq⟩", "hp hq", "Or.inr hq"), 0,
        "The angle brackets build the pair."),
    Quiz("Addition", "With hp : P, which term proves P ∨ Q?", listOf("Or.inl hp", "Or.inr hp", "hp.1"), 0,
        "Or.inl puts the proof on the left side of the ∨."),
    Quiz("Conditional proof", "Which tactic matches ACP, \"assume P\"?", listOf("intro", "exact", "constructor"), 0,
        "intro h turns a goal P → Q into the goal Q, with h : P assumed."),
    Quiz("Finishing", "Which tactic closes a goal with a term you already have?", listOf("exact", "intro", "cases"), 0,
        "exact h says \"h is exactly what the goal asks for\"."),
    Quiz("Indirect proof", "Which Lean tactic is the classical indirect proof (assume the opposite)?", listOf("by_contra", "intro", "rfl"), 0,
        "by_contra h assumes ¬goal and asks for a contradiction. It relies on classical logic."),
    Quiz("Induction", "In a Lean induction on n : Nat, the two cases are called…", listOf("zero and succ", "true and false", "left and right"), 0,
        "zero is the base case; succ k ih is the step, with ih the induction hypothesis for k."),
)

private val leanChainOrder = OrderStep(
    "A proof in Lean", "Assemble this Lean proof of R from P → Q, Q → R and P.",
    listOf(
        "example (P Q R : Prop) (h1 : P → Q) (h2 : Q → R) (h3 : P) : R := by",
        "  have h4 : Q := h1 h3",
        "  have h5 : R := h2 h4",
        "  exact h5",
    ),
    extra = listOf("  have h5 : R := h4 h2"),
    why = "The statement comes first, and each have can only use lines above it.",
    code = true,
    success = "Exactly the proof you'd write in the app: line 4 by MP from 1 and 3, line 5 by MP from 2 and 4.",
)

private val leanSwapOrder = OrderStep(
    "Tactics", "Assemble a tactic proof that P ∧ Q gives Q ∧ P.",
    listOf(
        "example (P Q : Prop) (h : P ∧ Q) : Q ∧ P := by",
        "  constructor",
        "  · exact h.2",
        "  · exact h.1",
    ),
    why = "constructor splits the goal Q ∧ P into Q, then P.",
    code = true,
    success = "constructor is Conj run backwards: to prove Q ∧ P, prove Q and then P.",
)

private val leanInductionOrder = OrderStep(
    "Induction in Lean", "Assemble the proof that 0 + n = n for every natural number n.",
    listOf(
        "theorem zero_add' (n : Nat) : 0 + n = n := by",
        "  induction n with",
        "  | zero => rfl",
        "  | succ k ih => rw [Nat.add_succ, ih]",
    ),
    why = "State the theorem, start the induction, then the base case before the step.",
    code = true,
    success = "zero is the base case (0 + 0 = 0 by computation); succ k ih is the step, using ih : 0 + k = k.",
)

private val leanMeet = LessonDef(
    "lean-1", "Meet Lean", "A language where the computer checks every step.", emptyList(),
    listOf(
        Read(
            "Proofs a computer can check",
            listOf(
                "Lean is a programming language and proof assistant. You write a theorem and a proof, and Lean checks every step, the way the app checks your lines.",
                "Statements are types and proofs are programs. A proof of P → Q is a function that turns a proof of P into a proof of Q, so Modus Ponens is just applying that function.",
            ),
            code = "example (P Q : Prop) (h1 : P → Q) (h2 : P) : Q := h1 h2",
            link = "Open Lean's web editor" to LEAN_EDITOR,
        ),
        LEAN_POOL[0],
        Read(
            "Your rules in Lean",
            listOf(
                "MP: h1 h2. Simp: h.1 (or h.2). Conj: ⟨h1, h2⟩. Add: Or.inl h.",
                "HS and MT are small functions: fun a => h2 (h1 a).",
                "In tactic mode (after by), each have is one line of your proof, and exact finishes.",
            ),
        ),
        leanChainOrder,
        LEAN_POOL[1], LEAN_POOL[2],
    ),
)

private val leanTactics = LessonDef(
    "lean-2", "Tactics", "intro is ACP, constructor is Conj backwards.", emptyList(),
    listOf(
        Read(
            "Working backwards",
            listOf(
                "Tactics change the goal step by step. intro h assumes the \"if\" part: it's ACP. exact closes a goal. constructor splits an ∧ goal into two. left and right pick a side of an ∨ goal. cases splits a hypothesis like h : P ∨ Q into two cases.",
                "Here's Hypothetical Syllogism as a conditional proof:",
            ),
            code = "example (P Q R : Prop) (h1 : P → Q) (h2 : Q → R) : P → R := by\n  intro hp\n  exact h2 (h1 hp)",
        ),
        LEAN_POOL[4], LEAN_POOL[5],
        leanSwapOrder,
    ),
)

private val leanYours = LessonDef(
    "lean-3", "Classical, constructive, and yours", "Where indirect proof lives, and your own proofs in Lean.", emptyList(),
    listOf(
        Read(
            "Constructive at heart",
            listOf(
                "Lean's core logic is constructive. Excluded middle comes from an axiom, Classical.em, and by_contra (indirect proof) uses it, so you can always see which proofs need classical logic.",
                "Your own proofs translate too. After you finish a puzzle in a propositional course, open \"Your proof in Lean\" on the results screen. Rules like DeM become the tactic tauto, with the rule named in a comment.",
            ),
            code = "example (P : Prop) : P ∨ ¬P := Classical.em P",
        ),
        LEAN_POOL[6],
        Read(
            "Induction in Lean",
            listOf("Lean's natural numbers are built from zero and succ (\"plus one\"), so induction has exactly those two cases."),
            code = "theorem zero_add' (n : Nat) : 0 + n = n := by\n  induction n with\n  | zero => rfl\n  | succ k ih => rw [Nat.add_succ, ih]",
            link = "Try it in Lean's web editor" to LEAN_EDITOR,
        ),
        leanInductionOrder,
        LEAN_POOL[7],
    ),
)

val leanCourse = Course(
    "lean", "Your Proofs in Lean", "Proofs as programs", "The language mathematicians use to have computers check proofs.",
    QUIZ_ONLY, listOf(leanMeet, leanTactics, leanYours), track = Track.EXTRA, section = Curriculum.SECTION_MATH,
    drills = { rng -> quizSet(LEAN_POOL, 4, rng, listOf(leanChainOrder, leanSwapOrder, leanInductionOrder)) },
)

// ======================= Everyday arguments =======================

private val FALLACY_POOL = listOf(
    Quiz("Name it", "\"You can't trust Sam's argument about taxes. He didn't even finish school.\"", listOf("Ad hominem", "Straw man", "False dilemma"), 0,
        "It attacks the person instead of the argument. Sam's argument is good or bad on its own merits."),
    Quiz("Name it", "\"My opponent wants to reduce military spending. Clearly she wants to leave the country defenceless.\"", listOf("Straw man", "Ad hominem", "Slippery slope"), 0,
        "It replaces her actual position with a weaker, extreme one that's easier to knock down."),
    Quiz("Name it", "\"Either you support this plan, or you don't care about the planet.\"", listOf("False dilemma", "Begging the question", "Hasty generalization"), 0,
        "It pretends there are only two options when there are plenty of others."),
    Quiz("Name it", "\"If we let students redo one test, soon they'll redo every test, and grades will mean nothing.\"", listOf("Slippery slope", "Post hoc", "Appeal to authority"), 0,
        "It claims a chain of consequences without showing why each step would follow."),
    Quiz("Name it", "\"I wore my lucky socks and we won. The socks work!\"", listOf("Post hoc (after this, so because of this)", "Straw man", "False dilemma"), 0,
        "One thing happening after another doesn't show the first caused the second."),
    Quiz("Name it", "\"This book is true because it says so, and it wouldn't say so if it weren't true.\"", listOf("Begging the question", "Ad hominem", "Slippery slope"), 0,
        "The conclusion is assumed in the premises: the argument goes in a circle."),
    Quiz("Name it", "\"I met two rude people from that city. Everyone there must be rude.\"", listOf("Hasty generalization", "Post hoc", "Begging the question"), 0,
        "Two examples are far too few to support a claim about everyone."),
    Quiz("Name it", "\"A famous actor says this diet works, so it must.\"", listOf("Appeal to an irrelevant authority", "False dilemma", "Straw man"), 0,
        "Fame isn't expertise. Citing a real expert in the field can be fine; citing a celebrity isn't evidence."),
    Quiz("Name the form", "\"If it rained, the street is wet. The street is wet. So it rained.\"", listOf("Affirming the consequent (invalid)", "Modus Ponens (valid)", "Modus Tollens (valid)"), 0,
        "P → Q and Q don't give P: a street cleaner could have wet the street."),
    Quiz("Name the form", "\"If you're in Paris, you're in France. You're not in Paris. So you're not in France.\"", listOf("Denying the antecedent (invalid)", "Modus Tollens (valid)", "Disjunctive Syllogism (valid)"), 0,
        "P → Q and ¬P don't give ¬Q: you could be in Lyon."),
    Quiz("Name the form", "\"If the alarm is set, it will ring. It didn't ring. So it wasn't set.\"", listOf("Modus Tollens (valid)", "Affirming the consequent (invalid)", "Denying the antecedent (invalid)"), 0,
        "P → Q and ¬Q give ¬P. Valid, though the premise \"it will ring\" could still be false."),
    Quiz("Name the form", "\"Either the battery is dead or the fuse blew. The battery is fine. So the fuse blew.\"", listOf("Disjunctive Syllogism (valid)", "False dilemma (always a fallacy)", "Post hoc"), 0,
        "P ∨ Q and ¬P give Q. It's only a false dilemma if there really are other possibilities."),
)

private val standardForm = OrderStep(
    "Standard form", "Put this argument in standard form: premises first, conclusion last. \"Since every member gets a vote, and Ana is a member, Ana gets a vote.\"",
    listOf("Every member gets a vote.", "Ana is a member.", "Therefore, Ana gets a vote."),
    why = "Premises come before the conclusion; \"therefore\" marks the conclusion.",
    success = "Words like since, because and given that mark premises; therefore, so and hence mark the conclusion.",
)

private val everydayFind = LessonDef(
    "arg-1", "Finding the argument", "Premises, conclusions, and the premises people leave out.", emptyList(),
    listOf(
        Read(
            "Arguments in the wild",
            listOf(
                "Real arguments don't come labelled. Indicator words help: since, because, given that introduce premises; therefore, so, hence, thus introduce the conclusion.",
                "People also leave premises unstated. \"Socrates is a man, so he's mortal\" quietly relies on \"all men are mortal\". Spotting the hidden premise is often where the real disagreement is.",
            ),
        ),
        standardForm,
        Quiz(
            "Hidden premise", "\"The museum is free on Sundays, so we should go tomorrow.\" What's the unstated premise?",
            listOf("Tomorrow is Sunday", "Museums are interesting", "We like free things"), 0,
            "Without \"tomorrow is Sunday\" the conclusion doesn't follow. The other two might be true, but they aren't what links the premise to the conclusion.",
        ),
        Quiz(
            "Conclusion", "\"We should leave now, because the last train goes at ten and it's a long walk.\" What's the conclusion?",
            listOf("We should leave now", "The last train goes at ten", "It's a long walk"), 0,
            "\"Because\" introduces the reasons; what they support is \"we should leave now\".",
        ),
    ),
)

private val everydayFormal = LessonDef(
    "arg-2", "Forms in words", "Valid forms and formal fallacies in everyday language.", emptyList(),
    listOf(
        Read(
            "Same forms, ordinary words",
            listOf(
                "Every argument you proved has the same shape in English. Translate it, check the form, and you know whether it's valid, whatever it's about.",
                "Watch for the two classic formal fallacies: affirming the consequent and denying the antecedent.",
            ),
        ),
        FALLACY_POOL[8], FALLACY_POOL[9], FALLACY_POOL[10], FALLACY_POOL[11],
    ),
)

private val everydayInformal = LessonDef(
    "arg-3", "Informal fallacies", "Mistakes in content rather than form.", emptyList(),
    listOf(
        Read(
            "Common traps",
            listOf(
                "Ad hominem: attacking the person, not the argument. Straw man: attacking a distorted version of the view.",
                "False dilemma: pretending there are only two options. Slippery slope: an unsupported chain of consequences.",
                "Post hoc: after, therefore because of. Begging the question: assuming the conclusion.",
                "Hasty generalization: too few examples. Appeal to authority: citing someone who isn't an expert on the question.",
            ),
        ),
        FALLACY_POOL[0], FALLACY_POOL[1], FALLACY_POOL[2], FALLACY_POOL[3], FALLACY_POOL[4],
    ),
)

private val everydayGood = LessonDef(
    "arg-4", "What makes an argument good", "Valid, sound, and strong.", emptyList(),
    listOf(
        Read(
            "Three kinds of good",
            listOf(
                "Valid: if the premises were true, the conclusion would have to be. That's what proofs show.",
                "Sound: valid, and the premises really are true. Only sound arguments guarantee their conclusions.",
                "Strong: for arguments from evidence (\"every swan I've seen is white…\"), the premises make the conclusion likely but not certain. That's inductive reasoning, and most everyday arguments are this kind.",
            ),
        ),
        Quiz(
            "Check", "Can a valid argument have a false conclusion?",
            listOf("Yes, if a premise is false", "No, never", "Only in predicate logic"), 0,
            "\"All fish can fly; a salmon is a fish; so a salmon can fly\" is valid with a false conclusion. Validity only promises truth in, truth out.",
        ),
        Quiz(
            "Check", "What does a sound argument need?",
            listOf("A valid form and true premises", "A true conclusion only", "Many premises"), 0,
            "Valid plus true premises, and then the conclusion must be true too.",
        ),
        FALLACY_POOL[5], FALLACY_POOL[6], FALLACY_POOL[7],
    ),
)

val everydayCourse = Course(
    "everyday", "Everyday Arguments", "Fallacies · hidden premises", "Spot good and bad reasoning in real writing and talk.",
    QUIZ_ONLY, listOf(everydayFind, everydayFormal, everydayInformal, everydayGood), track = Track.EXTRA, section = Curriculum.SECTION_LIFE,
    drills = { rng -> quizSet(FALLACY_POOL, 5, rng, listOf(standardForm)) },
)

/** The courses after the new kinds of logic, in Learn-tab order. */
val laterCourses: List<Course> get() = listOf(metaCourse, inductionCourse, leanCourse, everydayCourse)
