// Run: node tests/engine.test.js
const L = require('../engine.js');
const { CATEGORIES, DAILY } = require('../content.js');
let failures = 0;
const assert = (c, m) => { if (!c) { failures++; console.error('FAIL:', m); } };
const P = (s) => { const r = L.parse(s); if (!r.ok) throw new Error(s + ': ' + r.error); return r.f; };

// Parser accepts both notations
assert(L.eq(P('(P & Q) -> R'), P('(P ∧ Q) → R')), 'plain text = symbols');
assert(L.eq(P('~R v S'), P('¬R ∨ S')), 'v is or');
assert(!L.parse('P ∨ Q ∨ R').ok, 'ambiguous chain rejected');
assert(!L.parse('(P ∧ Q').ok, 'missing bracket');

// The user's example puzzle, solved line by line
const lines = ['(P & Q) -> R', 'P', '~R v S', '~S'].map(P);
const steps = [['S v ~R', 'Com', [3]], ['~R', 'DS', [5, 4]], ['P -> (Q -> R)', 'Exp', [1]], ['Q -> R', 'MP', [7, 2]], ['~Q', 'MT', [8, 6]]];
for (const [f, rule, cites] of steps) {
  const r = L.checkStep(rule, cites.map((n) => lines[n - 1]), P(f));
  assert(r.ok, `example step ${f} by ${rule}: ${r.msg}`);
  lines.push(P(f));
}
// Mistakes are caught with useful messages
const bad = (rule, cites, f, trap) => { const r = L.checkStep(rule, cites.map(P), P(f)); assert(!r.ok && (!trap || r.trap === trap), `${rule} should reject ${f} (${r.msg})`); };
bad('MP', ['P -> Q', 'Q'], 'P', 'affirm');
bad('MT', ['P -> Q', '~P'], '~Q', 'deny');
bad('DS', ['~R v S', '~S'], '~R', 'dsright');
bad('Simp', ['P & Q'], 'Q', 'simpright');
bad('Add', ['P'], 'Q v P', 'addright');
bad('MT', ['P -> ~Q', 'Q'], '~P', 'dn');
bad('Com', ['P -> Q'], 'Q -> P');
assert(L.checkStep('DN', [P('P -> Q')], P('~~P -> Q')).ok, 'DN on part of a line');
assert(L.checkStep('DeM', [P('~(P v Q)')], P('~P & ~Q')).ok, 'DeM');
assert(L.checkStep('Trans', [P('~Q -> ~P')], P('P -> Q')).ok, 'Trans backwards');
assert(L.checkStep('Impl', [P('~P v Q')], P('P -> Q')).ok, 'Impl backwards');
assert(L.checkStep('Dist', [P('(P & Q) v (P & R)')], P('P & (Q v R)')).ok, 'Dist backwards');
assert(L.checkStep('CD', [P('(P -> Q) & (R -> S)'), P('P v R')], P('Q v S')).ok, 'CD');
const alt = L.checkStep('MP', [P('P -> Q'), P('~Q')], P('~P'));
assert(!alt.ok && alt.alt === 'MT', 'suggests the right rule');

// Lesson examples must be correct proofs
for (const c of CATEGORIES) {
  const ex = c.lesson.example; const ls = [];
  ex.lines.forEach(([f, j], i) => {
    const F = P(f);
    if (j !== 'Premise') {
      const parts = j.split(',').map((x) => x.trim()); const rule = parts.pop();
      const r = L.checkStep(rule, parts.map((n) => ls[+n - 1]), F);
      assert(r.ok, `${c.id} lesson line ${i + 1}: ${r.msg}`);
    }
    ls.push(F);
  });
  assert(L.eq(ls[ls.length - 1], P(ex.concl)), `${c.id} lesson reaches conclusion`);
}

// Generator: every category and level makes valid, consistent puzzles quickly
const t0 = Date.now(); let n = 0; const stats = {};
for (const c of CATEGORIES.concat([DAILY])) {
  for (let level = 1; level <= 5; level++) {
    for (let seed = 1; seed <= 25; seed++) {
      const t = Date.now();
      const p = L.generate(c.spec, level, seed * 7919 + level);
      const dt = Date.now() - t;
      assert(p, `${c.id} L${level} seed ${seed} generated`);
      if (!p) continue;
      n++;
      assert(dt < 1500, `${c.id} generation took ${dt}ms`);
      assert(L.satisfiable(p.premises), 'premises consistent');
      const used = new Set(p.solution.map((s) => s.rule));
      for (const r of used) assert(c.spec.rules.includes(r), `${c.id} used disallowed rule ${r}`);
      if (c.spec.focus.length) assert(c.spec.focus.some((r) => used.has(r)), `${c.id} missing focus rule`);
      const num = L.numberSolution(p);
      const fs = [];
      for (const ln of num) {
        if (ln.rule !== 'Premise') assert(L.checkStep(ln.rule, ln.cites.map((k) => fs[k - 1]), ln.f).ok, `${c.id} model step valid`);
        fs.push(ln.f);
      }
      assert(L.eq(fs[fs.length - 1], p.conclusion), 'model reaches conclusion');
      // Hints walk all the way to the conclusion
      const hs = p.premises.slice(); let guard = 0, h;
      while ((h = L.hintFor(p, hs)) && guard++ < 30) hs.push(h.step.f);
      assert(hs.some((f) => L.eq(f, p.conclusion)), 'hints reach conclusion');
      const st = (stats[c.id] = stats[c.id] || { steps: 0, n: 0 }); st.steps += p.solution.length; st.n++;
    }
  }
}
console.log(`${n} puzzles in ${Date.now() - t0}ms`);
for (const [k, v] of Object.entries(stats)) console.log(k.padEnd(10), 'avg steps', (v.steps / v.n).toFixed(1));
// A sample from each
for (const c of CATEGORIES) {
  const p = L.generate(c.spec, 3, 42);
  console.log(`\n[${c.title}]`);
  L.numberSolution(p).forEach((l, i) => console.log(String(i + 1).padStart(2) + '. ' + L.show(l.f).padEnd(26) + (l.rule === 'Premise' ? 'Premise' : l.cites.join(', ') + ', ' + l.rule)));
  console.log('   ∴ ' + L.show(p.conclusion));
}
if (failures) { console.error(`\n${failures} failure(s)`); process.exit(1); } else console.log('\nAll tests passed.');
