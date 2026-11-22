package com.therefore.logic.engine

import com.therefore.logic.content.Curriculum
import com.therefore.logic.content.LessonStep
import com.therefore.logic.content.f
import com.therefore.logic.content.puzzle
import com.therefore.logic.data.FeedbackKind
import com.therefore.logic.data.Mode
import com.therefore.logic.game.Session
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun p(s: String): Formula = when (val r = Parser.parse(s)) {
        is ParseResult.Ok -> r.formula
        is ParseResult.Error -> throw AssertionError("$s: ${r.message}")
    }

    @Test fun parserAcceptsBothNotations() {
        assertEquals(p("(P ∧ Q) → R"), p("(P & Q) -> R"))
        assertEquals(p("¬R ∨ S"), p("~R v S"))
        assertTrue(Parser.parse("P ∨ Q ∨ R") is ParseResult.Error)
        assertTrue(Parser.parse("(P ∧ Q") is ParseResult.Error)
        assertEquals("(P ∧ Q) → R", p("(P&Q)->R").show())
    }

    @Test fun solvesTheExamplePuzzle() {
        val lines = mutableListOf("(P & Q) -> R", "P", "~R v S", "~S").map(::p).toMutableList()
        val steps = listOf(
            Triple("S v ~R", Rule.Com, listOf(3)),
            Triple("~R", Rule.DS, listOf(5, 4)),
            Triple("P -> (Q -> R)", Rule.Exp, listOf(1)),
            Triple("Q -> R", Rule.MP, listOf(7, 2)),
            Triple("~Q", Rule.MT, listOf(8, 6)),
        )
        for ((f, rule, cites) in steps) {
            val r = Checker.check(rule, cites.map { lines[it - 1] }, p(f))
            assertTrue("$f by $rule: ${r.message}", r.ok)
            lines += p(f)
        }
    }

    private fun rejects(rule: Rule, cited: List<String>, f: String, trap: Trap? = null) {
        val r = Checker.check(rule, cited.map(::p), p(f))
        assertFalse("$rule should reject $f", r.ok)
        if (trap != null) assertEquals(trap, r.trap)
    }

    @Test fun catchesMistakesWithTheRightLesson() {
        rejects(Rule.MP, listOf("P -> Q", "Q"), "P", Trap.AFFIRM)
        rejects(Rule.MT, listOf("P -> Q", "~P"), "~Q", Trap.DENY)
        rejects(Rule.DS, listOf("~R v S", "~S"), "~R", Trap.DS_RIGHT)
        rejects(Rule.Simp, listOf("P & Q"), "Q", Trap.SIMP_RIGHT)
        rejects(Rule.Add, listOf("P"), "Q v P", Trap.ADD_RIGHT)
        rejects(Rule.MT, listOf("P -> ~Q", "Q"), "~P", Trap.DN)
        rejects(Rule.Com, listOf("P -> Q"), "Q -> P")
        val alt = Checker.check(Rule.MP, listOf(p("P -> Q"), p("~Q")), p("~P"))
        assertEquals(Rule.MT, alt.alt)
    }

    @Test fun replacementRulesWorkInsideLinesAndBothWays() {
        assertTrue(Checker.check(Rule.DN, listOf(p("P -> Q")), p("~~P -> Q")).ok)
        assertTrue(Checker.check(Rule.DeM, listOf(p("~(P v Q)")), p("~P & ~Q")).ok)
        assertTrue(Checker.check(Rule.Trans, listOf(p("~Q -> ~P")), p("P -> Q")).ok)
        assertTrue(Checker.check(Rule.Impl, listOf(p("~P v Q")), p("P -> Q")).ok)
        assertTrue(Checker.check(Rule.Dist, listOf(p("(P & Q) v (P & R)")), p("P & (Q v R)")).ok)
        assertTrue(Checker.check(Rule.CD, listOf(p("(P -> Q) & (R -> S)"), p("P v R")), p("Q v S")).ok)
    }

    @Test fun formulasPrintReadablyInsideSentences() {
        assertEquals("if P → Q", "if ${p("P -> Q")}")
        assertEquals("¬(P ∧ Q)", "${p("~(P & Q)")}")
    }

    @Test fun lessonExamplesAreValidProofs() {
        val examples = Curriculum.courses.flatMap { it.lessons }.flatMap { it.steps }.filterIsInstance<LessonStep.Read>().mapNotNull { it.example }
        for (ex in examples) {
            val ls = ArrayList<Formula>()
            ex.lines.forEachIndexed { i, (f, j) ->
                val formula = p(f)
                if (j != "Premise") {
                    val parts = j.split(",").map { it.trim() }
                    val rule = Rule.valueOf(parts.last())
                    val r = if (rule.isStandard) Checker.check(rule, parts.dropLast(1).map { ls[it.toInt() - 1] }, formula).ok else true
                    assertTrue("example line ${i + 1}: $f", r)
                }
                ls += formula
            }
            assertEquals(p(ex.conclusion), ls.last())
        }
    }

    @Test fun generatorMakesValidPuzzlesForEveryCourseAndLevel() {
        var count = 0
        val t0 = System.currentTimeMillis()
        for (c in Curriculum.courses.filter { !it.advanced } + Curriculum.daily) for (level in 1..5) for (seed in 1..20) {
            val puzzle = Generator.generate(c.spec, level, seed * 7919L + level)
            count++
            assertTrue(satisfiable(puzzle.premises))
            val used = puzzle.solution.map { it.rule }.toSet()
            assertTrue("${c.id} used a rule outside the course", c.spec.rules.containsAll(used))
            if (c.spec.focus.isNotEmpty()) assertTrue("${c.id} missing focus rule", used.any { it in c.spec.focus })
            val lines = Proofs.numbered(puzzle)
            val fs = ArrayList<Formula>()
            for (l in lines) {
                if (l.rule != null) assertTrue("${c.id} model step", Checker.check(l.rule!!, l.cites.map { fs[it - 1] }, l.formula).ok)
                fs += l.formula
            }
            assertEquals(puzzle.conclusion, fs.last())
            assertTrue("${c.id} hints solve it", solveWithHints(puzzle))
        }
        println("$count puzzles in ${System.currentTimeMillis() - t0} ms")
    }

    /** Plays a puzzle by taking every hint to its last stage and filling it in. */
    private fun solveWithHints(puzzle: Puzzle): Boolean {
        var s = Session.newGame("test", 1, Mode.LESSON, puzzle, Rule.entries)
        repeat(40) {
            if (s.done) return true
            repeat(3) { s = Session.hint(s).state }
            if (s.feedback?.fill == null) return false
            s = Session.fillHint(s).state
            s = Session.addLine(s).state
            if (s.feedback?.kind == FeedbackKind.BAD) return false
        }
        return s.done
    }

    @Test fun conditionalAndIndirectProofPuzzles() {
        for (id in listOf("cp", "ip")) {
            val course = Curriculum.byId(id)
            for (level in 1..5) for (seed in 1..15) {
                val puzzle = Generator.generate(course.spec, level, seed * 31L + level)
                val opening = if (id == "cp") Rule.ACP else Rule.AIP
                assertEquals("$id starts with an assumption", opening, puzzle.solution.first().rule)
                // Replay the numbered model proof through the line checker.
                val lines = ArrayList<ProofLine>()
                for (n in Proofs.numbered(puzzle)) {
                    if (n.rule != null) {
                        val r = Proofs.checkLine(lines, n.rule!!, if (n.rule!!.isStandard) n.cites else emptyList(), n.formula)
                        assertTrue("$id L$level model line ${n.formula} by ${n.rule}: ${r.message}", r.ok)
                    }
                    lines += n.line
                }
                assertEquals(puzzle.conclusion, lines.last().formula)
                assertTrue(Proofs.scopes(lines).open.isEmpty())
                assertTrue("$id L$level hints solve it", solveWithHints(puzzle))
            }
        }
    }

    @Test fun subproofLinesCantBeCitedAfterClosing() {
        val lines = listOf(ProofLine(p("P -> Q"), null), ProofLine(p("P"), Rule.ACP), ProofLine(p("Q"), Rule.MP, listOf(1, 2)), ProofLine(p("P -> Q"), Rule.CP, listOf(2, 3)))
        val r = Proofs.checkLine(lines, Rule.Add, listOf(3), p("Q v R"))
        assertFalse(r.ok)
        assertTrue(r.message.contains("closed"))
        // CP must match the open assumption, IP needs a contradiction.
        val open = lines.take(3)
        assertFalse(Proofs.checkLine(open, Rule.CP, emptyList(), p("Q -> P")).ok)
        assertTrue(Proofs.checkLine(open, Rule.CP, emptyList(), p("P -> Q")).ok)
        assertFalse(Proofs.checkLine(open, Rule.IP, emptyList(), p("~P")).ok)
    }

    @Test fun everyLessonIsSolvableWithTheRulesItOffers() {
        for (course in Curriculum.allCourses) for (lesson in course.lessons) for (step in lesson.steps) {
            val (puzzle, rules) = when (step) {
                is LessonStep.Guided -> step.puzzle() to step.rules
                is LessonStep.Practice -> step.puzzle() to step.rules
                is LessonStep.Quiz -> { assertTrue("${lesson.id} quiz answer", step.answer in step.options.indices); continue }
                is LessonStep.Table -> { step.formulas.forEach { f(it) }; continue }
                is LessonStep.WorldStep -> { step.formulas.forEach { f(it) }; continue }
                else -> continue
            }
            val lines = puzzle.premises.map { ProofLine(it, null) }.toMutableList()
            for (n in Proofs.numbered(puzzle).drop(puzzle.premises.size)) {
                assertTrue("${lesson.id}: ${n.rule} isn't offered on the board", n.rule in rules)
                val r = Proofs.checkLine(lines, n.rule!!, if (n.rule!!.isStandard) n.cites else emptyList(), n.formula, puzzle.conclusion)
                assertTrue("${lesson.id} '${step.title}': ${n.formula} by ${n.rule}: ${r.message}", r.ok)
                lines += ProofLine(n.formula, n.rule, r.cites)
            }
            assertEquals("${lesson.id} reaches the conclusion", puzzle.conclusion, lines.last().formula)
            assertTrue(Proofs.scopes(lines).open.isEmpty())
            assertTrue("${lesson.id} hints work", solveWithHints(puzzle))
        }
    }

    @Test fun assumptionRulesFillInTheLikelyLine() {
        val puzzle = Puzzle(listOf(p("(P & Q) -> R"), p("~R")), p("P -> ~Q"), emptyList())
        var s = Session.newGame("t", 1, Mode.LESSON, puzzle, Rule.entries)
        s = Session.selectRule(s, Rule.ACP).state
        assertEquals("P", s.tokens.joinToString(""))
        s = Session.addLine(s).state
        s = Session.selectRule(s, Rule.AIP).state        // inside, the goal is ¬Q, so assume Q
        assertEquals("Q", s.tokens.joinToString(""))
        s = Session.selectRule(s, Rule.MP).state         // switching away clears the untouched fill
        assertTrue(s.tokens.isEmpty())
        s = Session.selectRule(s, Rule.AIP).state
        s = Session.addLine(s).state
        s = Session.selectRule(s, Rule.IP).state
        assertEquals("¬Q", s.tokens.joinToString(""))
        // A typed line is never overwritten.
        var t = Session.newGame("t", 1, Mode.LESSON, puzzle, Rule.entries)
        t = Session.key(t, 'R').state
        t = Session.selectRule(t, Rule.ACP).state
        assertEquals("R", t.tokens.joinToString(""))
    }

    @Test fun contradictionsAreFlagged() {
        val lines = listOf(ProofLine(p("Q"), null), ProofLine(p("P"), Rule.AIP), ProofLine(p("~Q"), Rule.MP, listOf(1, 2)))
        val c = Proofs.contradictions(lines)
        assertEquals(setOf(1, 3), c.keys)
        assertTrue(Proofs.contradictions(lines + ProofLine(p("Q & ~Q"), Rule.Conj, listOf(1, 3))).containsKey(4))
        // Once the subproof closes, its lines no longer count.
        val closed = lines + ProofLine(p("Q & ~Q"), Rule.Conj, listOf(1, 3)) + ProofLine(p("~P"), Rule.IP, listOf(2, 4))
        assertTrue(Proofs.contradictions(closed).isEmpty())
    }

    @Test fun sRankNeedsAPerfectDailyUnderPar() {
        val puzzle = Generator.generate(Curriculum.daily.spec, 3, 5L)
        fun solve(mode: Mode, hintsFirst: Boolean): String {
            var s = Session.newGame("daily", 3, mode, puzzle, Rule.entries)
            for (st in Proofs.numbered(puzzle).drop(puzzle.premises.size)) {
                if (hintsFirst) { s = Session.hint(s).state }
                s = s.copy(tokens = st.formula.show().filter { !it.isWhitespace() }.toList(), caret = 0, rule = st.rule, cites = if (st.rule!!.isStandard) st.cites else emptyList())
                s = Session.addLine(s).state
            }
            assertTrue(s.done)
            return s.result!!.grade
        }
        // Matching par exactly is an A; S needs fewer lines than the model proof.
        assertEquals("A", solve(Mode.DAILY, hintsFirst = false))
        assertEquals("A", solve(Mode.PRACTICE, hintsFirst = false))   // S is for the daily proof only
        assertTrue(solve(Mode.DAILY, hintsFirst = true) != "S")
        // Beating par: a model proof with a wasted line, solved without it.
        val padded = puzzle.copy(solution = puzzle.solution + puzzle.solution.last())
        var s = Session.newGame("daily", 3, Mode.DAILY, padded, Rule.entries)
        for (st in Proofs.numbered(puzzle).drop(puzzle.premises.size)) {
            s = s.copy(tokens = st.formula.show().filter { !it.isWhitespace() }.toList(), caret = 0, rule = st.rule, cites = if (st.rule!!.isStandard) st.cites else emptyList())
            s = Session.addLine(s).state
        }
        assertEquals("S", s.result!!.grade)
    }

    @Test fun predicateFormulasParseAndPrint() {
        assertEquals("∀x(Fx → Gx)", p("∀x(Fx → Gx)").show())
        assertEquals(p("∀x(Fx->Gx)"), p("∀x (Fx → Gx)"))
        assertEquals("∃x ¬Fx", p("∃x~Fx").show())
        assertEquals("Ms", p("Ms").show())
        assertTrue(Parser.parse("∀a Fa") is ParseResult.Error)   // quantifiers bind variables, not names
        assertEquals(p("Fa ∧ Gb"), p("Fx ∧ Gb").subst('x', 'a'))
        assertEquals(setOf('x'), p("Fx ∧ ∀y Gy").freeVariables())
    }

    @Test fun quantifierRulesAndTheirRestrictions() {
        fun ok(rule: Rule, from: String, to: String) = Checker.check(rule, listOf(p(from)), p(to)).ok
        assertTrue(ok(Rule.UI, "∀x(Fx -> Gx)", "Fa -> Ga"))
        assertTrue(ok(Rule.UI, "∀x(Fx -> Gx)", "Fx -> Gx"))
        assertFalse(ok(Rule.UI, "∀x(Fx -> Gx)", "Fa -> Gb"))
        assertTrue(ok(Rule.EG, "Fa & Gb", "∃x(Fx & Gb)"))
        assertTrue(ok(Rule.EI, "∃x Fx", "Fa"))
        assertFalse(ok(Rule.EI, "∃x Fx", "Fx"))
        assertTrue(ok(Rule.UG, "Fx -> Hx", "∀x(Fx -> Hx)"))
        assertEquals(Trap.UG_NAME, Checker.check(Rule.UG, listOf(p("Fa")), p("∀x Fx")).trap)
        assertTrue(ok(Rule.QN, "~∀x Fx", "∃x ~Fx"))
        assertTrue(ok(Rule.QN, "~∃x(Fx & ~Gx)", "∀x ~(Fx & ~Gx)"))
        assertTrue(ok(Rule.Impl, "∀x(Fx -> Gx)", "∀x(~Fx v Gx)"))   // replacement rules work inside quantifiers

        // EI needs a brand-new name.
        val lines = listOf(ProofLine(p("Ga"), null), ProofLine(p("∃x Fx"), null))
        val reused = Proofs.checkLine(lines, Rule.EI, listOf(2), p("Fa"))
        assertFalse(reused.ok); assertEquals(Trap.EI_NOT_NEW, reused.trap)
        assertTrue(Proofs.checkLine(lines, Rule.EI, listOf(2), p("Fb")).ok)
        assertFalse(Proofs.checkLine(lines, Rule.EI, listOf(2), p("Fb"), conclusion = p("Hb")).ok)
        // UG can't generalize a variable that an open assumption is about.
        val inside = listOf(ProofLine(p("Fx"), Rule.ACP))
        assertFalse(Proofs.checkLine(inside, Rule.UG, listOf(1), p("∀x Fx")).ok)
    }

    @Test fun predicatePuzzlesForEveryCourseAndLevel() {
        for (c in Curriculum.predicateCourses + Curriculum.dailyPredicate) for (level in 1..5) for (seed in 1..15) {
            val puzzle = Generator.generate(c.spec, level, seed * 131L + level)
            assertTrue("${c.id} uses quantifiers", (puzzle.premises + puzzle.conclusion).any { it.isPredicateLogic })
            val lines = ArrayList<ProofLine>()
            for (n in Proofs.numbered(puzzle)) {
                if (n.rule != null) {
                    assertTrue("${c.id}: ${n.rule} is on the board", n.rule in c.spec.rules)
                    val r = Proofs.checkLine(lines, n.rule!!, n.cites, n.formula, puzzle.conclusion)
                    assertTrue("${c.id} L$level ${n.formula} by ${n.rule}: ${r.message}", r.ok)
                }
                lines += n.line
            }
            assertEquals(puzzle.conclusion, lines.last().formula)
            assertTrue("${c.id} L$level hints solve it", solveWithHints(puzzle))
        }
        // Each course actually needs its own rules.
        val used = Curriculum.predicateCourses.associate { c -> c.id to (1..20).flatMap { Generator.generate(c.spec, 3, it.toLong()).solution.map { s -> s.rule } }.toSet() }
        assertTrue(Rule.UI in used.getValue("pred-quant"))
        assertTrue(Rule.UG in used.getValue("pred-allsome") && Rule.EI in used.getValue("pred-allsome") && Rule.EG in used.getValue("pred-allsome"))
        assertTrue(Rule.QN in used.getValue("pred-qn"))
        assertTrue(Rule.CP in used.getValue("pred-assume") && Rule.UG in used.getValue("pred-assume"))
        assertTrue(Rule.UI in used.getValue("pred-rel"))
        assertTrue(Rule.Id in used.getValue("pred-id") && Rule.IdR in used.getValue("pred-id"))
    }

    @Test fun worldsEvaluateQuantifiers() {
        val w = World(3, mapOf('F' to setOf(0, 1), 'G' to setOf(0)), mapOf('a' to 0, 'b' to 1, 'c' to 2))
        assertFalse(p("∀x Fx").eval(w))
        assertTrue(p("∃x Fx").eval(w))
        assertFalse(p("∀x(Fx -> Gx)").eval(w))    // b is F but not G
        assertTrue(p("Fa & Ga").eval(w))
        assertTrue(satisfiable(listOf(p("∃x Fx"), p("∃x ~Fx"))))
        assertFalse(satisfiable(listOf(p("∀x Fx"), p("∃x ~Fx"))))
    }

    @Test fun relationsAndQuantifierOrder() {
        assertEquals("∀x∃y Lxy", p("∀x∃y Lxy").show())
        assertEquals(p("Lab"), p("Lxy").subst('x', 'a').subst('y', 'b'))
        val w = World(3, emptyMap(), mapOf('a' to 0, 'b' to 1, 'c' to 2), rels = mapOf('L' to setOf(0 to 1, 1 to 2, 2 to 0)))
        assertTrue(p("Lab").eval(w)); assertFalse(p("Lba").eval(w))
        assertTrue(p("∀x∃y Lxy").eval(w)); assertFalse(p("∃y∀x Lxy").eval(w))
        // ∀x∃y Lxy does not imply ∃y∀x Lxy: there's a world with the premise true and the conclusion false.
        assertTrue(satisfiable(listOf(p("∀x∃y Lxy"), p("¬∃y∀x Lxy"))))
        // ...and the fake proof is blocked at UG.
        val lines = listOf(ProofLine(p("∀x∃y Lxy"), null), ProofLine(p("∃y Lxy"), Rule.UI, listOf(1)), ProofLine(p("Lxa"), Rule.EI, listOf(2)))
        assertTrue(Proofs.checkLine(lines.take(2), Rule.EI, listOf(2), p("Lxa")).ok)
        val ug = Proofs.checkLine(lines, Rule.UG, listOf(3), p("∀x Lxa"))
        assertFalse(ug.ok)
        assertTrue(ug.message.contains("EI"))
    }

    @Test fun identity() {
        assertEquals("¬(a = b)", p("¬(a = b)").show())
        assertEquals(p("Fb → b = a"), p("Fb -> b=a"))
        assertTrue(Checker.check(Rule.Id, listOf(p("Fa"), p("a = b")), p("Fb")).ok)
        assertTrue(Checker.check(Rule.Id, listOf(p("Gb"), p("a = b")), p("Ga")).ok)      // either direction
        assertTrue(Checker.check(Rule.Id, listOf(p("a = b"), p("b = c")), p("a = c")).ok)
        assertFalse(Checker.check(Rule.Id, listOf(p("Fa"), p("a = b")), p("Gb")).ok)
        assertTrue(Checker.check(Rule.IdS, listOf(p("a = b")), p("b = a")).ok)
        assertTrue(Checker.check(Rule.IdR, emptyList(), p("c = c")).ok)
        assertFalse(Checker.check(Rule.IdR, emptyList(), p("a = b")).ok)
        val w = World(3, mapOf('F' to setOf(0, 1)), mapOf('a' to 0, 'b' to 1, 'c' to 2))
        assertTrue(p("∃x∃y((Fx ∧ Fy) ∧ ¬(x = y))").eval(w))
        assertFalse(p("∃x(Fx ∧ ∀y(Fy → y = x))").eval(w))
    }

    @Test fun statsSummariseHistory() {
        fun a(grade: String, mistakes: Int, rules: String, bad: String) = com.therefore.logic.data.Attempt(
            courseId = "ifthen", level = 1, date = "2026-11-20", grade = grade, stars = 3, score = 90, lines = 3, modelLines = 3,
            mistakes = mistakes, hints = 0, seconds = 40, revealed = false, mode = "PRACTICE", rulesUsed = rules, mistakeRules = bad,
            finishedAt = java.time.LocalDate.of(2026, 11, 20).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        val attempts = listOf(a("A", 0, "MP,MP,MT", ""), a("B", 2, "MP,DS", "DS,DS"), a("S", 0, "HS", ""))
        val st = com.therefore.logic.data.Stats.compute(attempts, com.therefore.logic.data.ProgressRepository.compute(attempts, java.time.LocalDate.of(2026, 11, 22)), java.time.LocalDate.of(2026, 11, 22))
        assertEquals(3, st.totalSolved)
        assertEquals(30, st.perDay.size)
        assertEquals(3, st.perDay.first { it.first == java.time.LocalDate.of(2026, 11, 20) }.second)
        assertEquals(listOf("S" to 1, "A" to 1, "B" to 1, "C" to 0, "D" to 0), st.grades)
        assertEquals("DS" to 2, st.trickyRules.first())
        assertEquals("MP" to 3, st.favouriteRules.first())
        assertEquals(8, st.mistakesByWeek.size)
    }

    @Test fun streakCountsEitherDailyProof() {
        fun at(date: String) = java.time.LocalDate.parse(date).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun attempt(course: String, date: String, finished: String = date) = com.therefore.logic.data.Attempt(
            courseId = course, level = 3, date = date, grade = "A", stars = 3, score = 95, lines = 4, modelLines = 4,
            mistakes = 0, hints = 0, seconds = 60, revealed = false, finishedAt = at(finished), mode = "DAILY",
        )
        val today = java.time.LocalDate.of(2026, 11, 21)
        val prog = com.therefore.logic.data.ProgressRepository.compute(
            listOf(attempt("daily", "2026-11-19"), attempt("daily-pred", "2026-11-20"), attempt("daily", "2026-11-21"), attempt("daily-pred", "2026-11-21")), today,
        )
        assertEquals(3, prog.streak)
        assertEquals(setOf("2026-11-20", "2026-11-21"), prog.predicateDays.keys)
        assertTrue(prog.courses.isEmpty())   // daily proofs don't count as course practice
        // Catching up on a missed day earns a grade but doesn't count toward the streak.
        val caughtUp = com.therefore.logic.data.ProgressRepository.compute(
            listOf(attempt("daily", "2026-11-19", finished = "2026-11-21"), attempt("daily", "2026-11-21")), today,
        )
        assertEquals(1, caughtUp.streak)
        assertFalse(caughtUp.days.getValue("2026-11-19").onTime)
        assertTrue(caughtUp.days.getValue("2026-11-21").onTime)
    }

    @Test fun dailyPuzzleIsTheSameForTheSameSeed() {
        val a = Generator.generate(Curriculum.daily.spec, 3, 12345L)
        val b = Generator.generate(Curriculum.daily.spec, 3, 12345L)
        assertEquals(a, b)
    }

    @Test fun puzzlesSurviveSaveAndLoad() {
        val puzzle = Generator.generate(Curriculum.courses.last().spec, 4, 99L)
        val json = Json.encodeToString(puzzle)
        assertEquals(puzzle, Json.decodeFromString<Puzzle>(json))
        assertNotNull(Proofs.hint(puzzle, puzzle.premises.map { ProofLine(it, null) }))
    }
}
