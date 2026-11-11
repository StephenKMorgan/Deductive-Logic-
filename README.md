# Therefore

A phone-friendly app that generates deductive logic proof puzzles, teaches the rules, and grades your proofs.

```
1. (P ∧ Q) → R     Premise
2. P               Premise
3. ¬R ∨ S          Premise
4. ¬S              Premise   ∴ ¬Q
```

## What it does

- **8 courses, each harder than the last.** Each course introduces new rules with a short lesson and a worked example, then gives you endless generated puzzles that grow longer as you level up (5 levels per course).
  1. If–Then Basics (MP, MT)
  2. Chains & Choices (HS, DS)
  3. And, Or (Simp, Conj, Add)
  4. Dilemmas (CD)
  5. Flip & Regroup (DN, Com, Assoc)
  6. De Morgan & Distribution (DeM, Dist)
  7. Conditionals in Disguise (Trans, Impl, Exp, Equiv, Taut)
  8. Grandmaster (all 18 rules)
- **Daily proof.** Everyone gets the same puzzle each day, and your streak is tracked.
- **Line-by-line checking.** Tap the lines you cite, pick a rule, and write the new line on the logic keypad. Every line is checked against the rule. A wrong line gets an explanation, for example: *"DS only cancels the left side of an 'or'. Use Commutation first."* The checker also catches the classic fallacies (affirming the consequent, denying the antecedent) and tells you when your line is right but you named the wrong rule.
- **Hints in 3 steps.** Each hint goes further: which lines to look at, then which rule to use, then the full line.
- **Grading and teaching.** When you finish you get a grade (A–D) and stars, based on mistakes, hints and proof length compared with the model proof. You also see lessons on the mistakes you made, a model proof with a plain-English explanation of every line, and cards for the rules involved.
- **Rules reference.** The full cheat sheet, plus a setting to show formulas as symbols (`¬ ∧ ∨ →`) or plain text (`~ & v ->`).
- Works offline once installed. Progress is saved on the device.

The rules follow the standard Copi/Hurley system: Simplification, Disjunctive Syllogism and Addition work on the left side only, and replacement rules may be applied to part of a line.

## Install it on your phone

The app is a static web app (PWA), so any static host works. With GitHub Pages:

1. In the repository on GitHub, go to **Settings → Pages**, choose **Deploy from a branch**, then pick the branch and the `/ (root)` folder.
2. Open the Pages URL on your phone.
3. **iPhone (Safari):** Share → *Add to Home Screen*. **Android (Chrome):** menu → *Install app* / *Add to Home screen*.

To run it locally: `python3 -m http.server` in this folder, then open http://localhost:8000.

## Files

| File | Purpose |
| --- | --- |
| `engine.js` | Formula parser, rule checker for all 18 rules, puzzle generator, hints |
| `content.js` | Courses, generator settings and lessons |
| `app.js` | The user interface |
| `index.html` | Page and styles |
| `sw.js`, `manifest.webmanifest`, `icon*` | Offline support and home-screen install |
| `tests/engine.test.js` | `node tests/engine.test.js`: checks the rules and generates over 1,000 puzzles, verifying every model proof |

## How puzzles are generated

The generator works backward from a random conclusion. At each step it picks a rule that could produce the current goal (for example, to get `¬Q` by Modus Tollens it needs `Q → X` and `¬X`), then recurses on those new goals. The remaining leaves become the premises. Each puzzle is then filtered: the premises must be consistent, the course's focus rule must appear, and the length must suit your level. Every model step is re-verified by the checker. The model proof becomes the source of hints and of the explained solution.
