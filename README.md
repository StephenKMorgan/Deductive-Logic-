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

## Android app

`android/` is a native Android app written in Kotlin with Jetpack Compose. It isn't a web wrapper: the logic engine, curriculum and screens are all native.

- **Engine** (`engine/`): formula parser, checker for all 18 rules, puzzle generator, hints and explanations. It's a port of `engine.js`, covered by the same tests.
- **Storage** (`data/`): a Room database keeps every finished puzzle, and course levels, best grades and streaks are worked out from it. The puzzle in progress is saved after every change, so closing the app loses nothing. Solve time counts only active time: the clock stops while the app is in the background, and one idle stretch adds at most 5 minutes. Settings are stored with DataStore.
- **Phone features**: vibration on key presses, accepted lines, mistakes and solved proofs; an optional daily reminder notification (WorkManager) that is skipped if you've already solved today's proof; sharing your daily result through the Android share sheet; the back gesture; light and dark themes.
- **Courses and lessons** (`content/`): 10 courses, the last two advanced (Conditional Proof and Indirect Proof, with indented subproofs). Each course page has **Practice**, which raises your level, and a list of hands-on **lessons**. A lesson is a sequence of steps: short reading, quick quizzes, truth-table explorers (flip P and Q, hunt for counterexamples), and coached proofs on the real board where each instruction moves on as you do it. The first lesson is a full tutorial of the controls. Every lesson also has **Free play**: generated puzzles focused on that lesson's rules, at any level you have reached.
- **Predicate logic** (its own tab): predicates and relations (`Fx`, `Lxy`), names, variables, ∀ and ∃, and the rules UI, EG, EI, UG and QN, with their restrictions checked (EI needs a new name; UG never from a name, a variable an open assumption is about, or one free in a line from EI). Six courses: Quantifiers, All and Some, Quantifier Negation, Quantifiers and Assumptions, Relations, and Identity (a = b, with reflexivity, symmetry and substitution, and counting: "at least two", "exactly one"), with a world explorer for building models and counterexamples.
- **Games** (its own tab): *Truth-tellers & Liars* (generated islander puzzles with one answer, explained in logic), *Valid or Not?* (a 60-second round of valid/invalid arguments and same/different formulas, with counterexamples), and *Switchboard* (flip switches until every statement is true; one solution per board).
- **Daily proofs**: a propositional and a predicate puzzle each day, the same for everyone, with par and a rare S rank for beating it. Either one keeps the streak going. The Daily tab is a month calendar: each day shows a dot for each puzzle solved (gold for an S rank) and a border when it was solved on the day. Tap a past day to catch up on a missed puzzle; catch-up solves are graded but don't count toward the streak.
- **Screens** (`ui/`): Learn (courses), course pages, the lesson player, the puzzle screen, graded results with an explained model proof, Daily, Rules, Settings (notation, seven accent colors including an all-grey Graphite, text size, light, dark or true-black theme, vibration, reminder, reset) and Your stats (typical solve time as a median, charts of proofs per day, grades, mistakes per proof by week, trickiest and most-used rules, and practice by course).

**Updates keep your progress:** debug builds are signed with a fixed key (`android/app/therefore-debug.keystore`), so a newer APK installs over the old one. That key is only for sideloading; Google Play needs your own release key.

**Download:** https://stephenkmorgan.github.io/therefore/ always has the latest release. To publish one, edit
`RELEASE_NOTES.md`, then push a tag like `v3.1.0` (or, once the workflow is on the default branch, use Actions →
"Publish release"). The version must be higher than the last one: `versionCode` = major×10000 + minor×100 + patch.

**Get an APK without installing anything:** every push runs the **Android APK** workflow. It runs the tests, builds the app, and uploads **therefore-debug-apk** plus the test **screenshots**. Open the run under the repo's **Actions** tab and download them from *Artifacts*. Unzip the APK onto your phone and open it; the first time, Android asks you to allow installs from that app (Files or Chrome).

**Build it yourself:** open the `android/` folder in Android Studio and press Run with your phone plugged in (USB debugging on), or run `cd android && ./gradlew assembleDebug` (needs JDK 17 and the Android SDK).

**Tests:** `cd android && ./gradlew testDebugUnitTest`. These run the engine tests (every generated puzzle and every lesson proof is replayed through the checker and solved using hints alone), a game-logic and database test, and Robolectric UI tests that tap through the real app: the full tutorial lesson, a Conditional Proof lesson, practice and free play, the rule menu, every tab, and dark mode. The UI tests save screenshots to `android/app/build/screenshots/`.

**Publishing to Google Play** needs a signed release bundle (`./gradlew bundleRelease`) and a signing key. Android Studio's *Build → Generate Signed Bundle* walks you through it.

## Files

| File | Purpose |
| --- | --- |
| `engine.js` | Formula parser, rule checker for all 18 rules, puzzle generator, hints |
| `content.js` | Courses, generator settings and lessons |
| `app.js` | The user interface |
| `index.html` | Page and styles |
| `sw.js`, `manifest.webmanifest`, `icon*` | Offline support and home-screen install |
| `android/` | Native Android app (Kotlin, Jetpack Compose, Room) |
| `tests/engine.test.js` | `node tests/engine.test.js`: checks the rules and generates over 1,000 puzzles, verifying every model proof |

## How puzzles are generated

The generator works backward from a random conclusion. At each step it picks a rule that could produce the current goal (for example, to get `¬Q` by Modus Tollens it needs `Q → X` and `¬X`), then recurses on those new goals. The remaining leaves become the premises. Each puzzle is then filtered: the premises must be consistent, the course's focus rule must appear, and the length must suit your level. Every model step is re-verified by the checker. The model proof becomes the source of hints and of the explained solution.
