/* Therefore — curriculum: categories, generator settings and lessons. */
(function (root) {
  'use strict';
  const INF = ['MP', 'MT', 'HS', 'DS', 'Simp', 'Conj', 'Add', 'CD'];
  const REP1 = ['DN', 'Com', 'Assoc'];
  const REP2 = ['DeM', 'Dist'];
  const REP3 = ['Trans', 'Impl', 'Exp', 'Equiv', 'Taut'];

  // spec: rules the puzzles may use, focus = at least one must appear in the model proof,
  // depth = [easiest, hardest] search depth, steps = [min, max] model-proof length.
  const CATEGORIES = [
    {
      id: 'ifthen', title: 'If–Then Basics', short: 'MP · MT',
      blurb: 'Use a conditional forwards and backwards.',
      spec: { rules: ['MP', 'MT'], focus: [], depth: [2, 3], steps: [2, 4], letters: 4, negP: 0.2, maxSize: 6, stop: 0.1, goals: { atom: 3, neg: 3 } },
      lesson: {
        intro: [
          'A conditional, P → Q, promises that whenever P is true, Q is true. It says nothing about what happens when P is false.',
          'Modus Ponens runs the conditional forwards: you have P, so you get Q. Modus Tollens runs it backwards: Q is false, so P could not have been true.',
        ],
        rules: ['MP', 'MT'],
        example: { lines: [['P → Q', 'Premise'], ['R → ¬Q', 'Premise'], ['R', 'Premise'], ['¬Q', '2, 3, MP'], ['¬P', '1, 4, MT']], concl: '¬P' },
        tip: 'Watch for the two traps. From P → Q and Q you cannot conclude P (affirming the consequent). From P → Q and ¬P you cannot conclude ¬Q (denying the antecedent).',
      },
    },
    {
      id: 'chains', title: 'Chains & Choices', short: 'HS · DS',
      blurb: 'Link conditionals and eliminate options.',
      spec: { rules: ['MP', 'MT', 'HS', 'DS'], focus: ['HS', 'DS'], depth: [2, 4], steps: [2, 5], letters: 5, negP: 0.2, maxSize: 7, stop: 0.12, goals: { atom: 2, neg: 2, imp: 2 } },
      lesson: {
        intro: [
          'Hypothetical Syllogism chains two conditionals. If P leads to Q and Q leads to R, then P leads to R. The middle part has to match exactly.',
          'Disjunctive Syllogism works by elimination. "P or Q" is true and P is false, so Q must be the true one. In this system the "not" line must deny the left side of the "or".',
        ],
        rules: ['HS', 'DS'],
        example: { lines: [['P → Q', 'Premise'], ['Q → R', 'Premise'], ['S ∨ P', 'Premise'], ['¬S', 'Premise'], ['P', '3, 4, DS'], ['P → R', '1, 2, HS'], ['R', '6, 5, MP']], concl: 'R' },
        tip: 'Work backward from the conclusion. Ask which line, if you had it, would give you the conclusion in one step.',
      },
    },
    {
      id: 'andor', title: 'And, Or', short: 'Simp · Conj · Add',
      blurb: 'Take statements apart and build new ones.',
      spec: { rules: ['MP', 'MT', 'HS', 'DS', 'Simp', 'Conj', 'Add'], focus: ['Simp', 'Conj', 'Add'], depth: [2, 4], steps: [3, 6], letters: 5, negP: 0.2, maxSize: 7, stop: 0.12, goals: { atom: 2, neg: 1, and: 3, or: 3 } },
      lesson: {
        intro: [
          'Simplification takes an "and" apart: if P ∧ Q is true, P is true. It only gives you the left side.',
          'Conjunction does the reverse and joins two lines with ∧. Addition attaches anything with ∨: if P is true, then "P or whatever you like" is true.',
        ],
        rules: ['Simp', 'Conj', 'Add'],
        example: { lines: [['P ∧ Q', 'Premise'], ['R', 'Premise'], ['P', '1, Simp'], ['P ∧ R', '3, 2, Conj'], ['(P ∧ R) ∨ S', '4, Add']], concl: '(P ∧ R) ∨ S' },
        tip: 'If the conclusion has a letter that appears in no premise, Addition is almost always how it gets in.',
      },
    },
    {
      id: 'dilemma', title: 'Dilemmas', short: 'CD',
      blurb: 'Reason through two possibilities at once.',
      spec: { rules: INF, focus: ['CD'], depth: [2, 4], steps: [2, 6], letters: 5, negP: 0.15, maxSize: 9, stop: 0.12, goals: { atom: 1, or: 4 } },
      lesson: {
        intro: [
          'Constructive Dilemma combines two conditionals with an "or". If P leads to Q, R leads to S, and either P or R is true, then either Q or S is true.',
          'The "or" line must list the two "if" parts in the same order as the conditionals.',
        ],
        rules: ['CD'],
        example: { lines: [['(P → Q) ∧ (R → S)', 'Premise'], ['P ∨ R', 'Premise'], ['¬Q', 'Premise'], ['Q ∨ S', '1, 2, CD'], ['S', '4, 3, DS']], concl: 'S' },
        tip: 'If you need (P → Q) ∧ (R → S) and have the two conditionals on separate lines, join them with Conjunction first.',
      },
    },
    {
      id: 'rearrange', title: 'Flip & Regroup', short: 'DN · Com · Assoc',
      blurb: 'Your first replacement rules.',
      spec: { rules: INF.concat(REP1), focus: REP1, depth: [2, 4], steps: [3, 6], letters: 5, negP: 0.3, maxSize: 8, stop: 0.12, goals: { atom: 2, neg: 2, or: 2, and: 1 } },
      lesson: {
        intro: [
          'Replacement rules swap a formula for one that means the same thing. Unlike the rules of inference, they work on part of a line as well as the whole line, and in either direction.',
          'Double Negation adds or removes ¬¬. Commutation swaps the sides of ∧ or ∨. Association moves the brackets in a chain of the same connective.',
        ],
        rules: REP1,
        example: { lines: [['P → ¬Q', 'Premise'], ['Q', 'Premise'], ['¬¬Q', '2, DN'], ['¬P', '1, 3, MT']], concl: '¬P' },
        tip: 'Simplification, Disjunctive Syllogism and Addition all work on the left side only. Commutation is how you reach the right side.',
      },
    },
    {
      id: 'demorgan', title: 'De Morgan & Distribution', short: 'DeM · Dist',
      blurb: 'Move negations and brackets around.',
      spec: { rules: INF.concat(REP1, REP2), focus: REP2, depth: [2, 4], steps: [3, 7], letters: 5, negP: 0.25, maxSize: 9, stop: 0.12, goals: { atom: 2, neg: 2, or: 2, and: 2 } },
      lesson: {
        intro: [
          "De Morgan's rule moves a negation through brackets and flips the connective: ¬(P ∧ Q) becomes ¬P ∨ ¬Q, and ¬(P ∨ Q) becomes ¬P ∧ ¬Q.",
          'Distribution shares the outside part with each part inside the brackets: P ∧ (Q ∨ R) becomes (P ∧ Q) ∨ (P ∧ R).',
        ],
        rules: REP2,
        example: { lines: [['¬(P ∨ Q)', 'Premise'], ['R → P', 'Premise'], ['¬P ∧ ¬Q', '1, DeM'], ['¬P', '3, Simp'], ['¬R', '2, 4, MT']], concl: '¬R' },
        tip: 'A negation in front of brackets blocks most rules. Use De Morgan to push it inside first.',
      },
    },
    {
      id: 'disguise', title: 'Conditionals in Disguise', short: 'Trans · Impl · Exp · Equiv',
      blurb: 'Rewrite if-then statements to unlock a proof.',
      spec: { rules: INF.concat(REP1, REP2, REP3), focus: ['Trans', 'Impl', 'Exp', 'Equiv'], depth: [3, 4], steps: [3, 8], letters: 5, negP: 0.25, maxSize: 9, stop: 0.12, goals: { atom: 2, neg: 2, imp: 2, or: 1 } },
      lesson: {
        intro: [
          'A conditional can wear several outfits. Transposition: P → Q equals ¬Q → ¬P. Material Implication: P → Q equals ¬P ∨ Q. Exportation: (P ∧ Q) → R equals P → (Q → R).',
          'Material Equivalence unpacks P ↔ Q into (P → Q) ∧ (Q → P). Tautology says P ∨ P and P ∧ P are just P.',
        ],
        rules: ['Trans', 'Impl', 'Exp', 'Equiv', 'Taut'],
        example: {
          lines: [['(P ∧ Q) → R', 'Premise'], ['P', 'Premise'], ['¬R ∨ S', 'Premise'], ['¬S', 'Premise'], ['S ∨ ¬R', '3, Com'], ['¬R', '5, 4, DS'], ['P → (Q → R)', '1, Exp'], ['Q → R', '7, 2, MP'], ['¬Q', '8, 6, MT']],
          concl: '¬Q',
        },
        tip: 'When a premise has an "and" inside the "if" part, Exportation splits it so Modus Ponens can use one piece at a time.',
      },
    },
    {
      id: 'master', title: 'Grandmaster', short: 'All 18 rules',
      blurb: 'Long proofs that mix every rule.',
      spec: { rules: INF.concat(REP1, REP2, REP3), focus: [], depth: [4, 5], steps: [5, 10], letters: 6, negP: 0.25, maxSize: 9, stop: 0.08, goals: { atom: 2, neg: 2, imp: 1, or: 1, and: 1 } },
      lesson: {
        intro: [
          'Every rule is available. These proofs are longer, and there is usually more than one route to the conclusion.',
          'Plan first. Find the premise that contains the conclusion, then work out which rules will free it.',
        ],
        rules: [],
        example: { lines: [['P ↔ Q', 'Premise'], ['¬Q', 'Premise'], ['(P → Q) ∧ (Q → P)', '1, Equiv'], ['P → Q', '3, Simp'], ['¬P', '4, 2, MT']], concl: '¬P' },
        tip: 'Not every premise has to be used, but in these puzzles each premise usually plays a part. A premise you have not touched is a clue.',
      },
    },
  ];

  const DAILY = {
    id: 'daily', title: 'Daily Proof', short: 'Mixed',
    spec: { rules: INF.concat(REP1, REP2, REP3), focus: [], depth: [3, 4], steps: [4, 8], letters: 5, negP: 0.25, maxSize: 9, stop: 0.1, goals: { atom: 2, neg: 2, imp: 1, or: 1, and: 1 } },
  };

  const api = { CATEGORIES, DAILY };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Content = api;
})(typeof window !== 'undefined' ? window : globalThis);
