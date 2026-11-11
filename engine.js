/* Therefore — logic engine: formulas, parser, rule checker, puzzle generator.
   Works in the browser (window.Logic) and in Node (module.exports). */
(function (root) {
  'use strict';

  // ---------- Formulas ----------
  const SYM = { and: '∧', or: '∨', imp: '→', iff: '↔' };
  const atom = (v) => ({ t: 'atom', v });
  const not = (a) => ({ t: 'not', a });
  const bin = (t, a, b) => ({ t, a, b });
  const and = (a, b) => bin('and', a, b);
  const or = (a, b) => bin('or', a, b);
  const imp = (a, b) => bin('imp', a, b);
  const iff = (a, b) => bin('iff', a, b);
  const isBin = (f) => f.t === 'and' || f.t === 'or' || f.t === 'imp' || f.t === 'iff';

  function show(f, top = true) {
    if (f.t === 'atom') return f.v;
    if (f.t === 'not') return '¬' + show(f.a, false);
    const s = show(f.a, false) + ' ' + SYM[f.t] + ' ' + show(f.b, false);
    return top ? s : '(' + s + ')';
  }
  const key = (f) => show(f);
  const eq = (a, b) => key(a) === key(b);
  const size = (f) => (f.t === 'atom' ? 1 : f.t === 'not' ? 1 + size(f.a) : 1 + size(f.a) + size(f.b));
  function atomsOf(f, s = new Set()) {
    if (f.t === 'atom') s.add(f.v);
    else if (f.t === 'not') atomsOf(f.a, s);
    else { atomsOf(f.a, s); atomsOf(f.b, s); }
    return s;
  }
  function evaluate(f, v) {
    switch (f.t) {
      case 'atom': return v[f.v];
      case 'not': return !evaluate(f.a, v);
      case 'and': return evaluate(f.a, v) && evaluate(f.b, v);
      case 'or': return evaluate(f.a, v) || evaluate(f.b, v);
      case 'imp': return !evaluate(f.a, v) || evaluate(f.b, v);
      case 'iff': return evaluate(f.a, v) === evaluate(f.b, v);
    }
  }
  function satisfiable(fs) {
    const letters = [...fs.reduce((s, f) => atomsOf(f, s), new Set())];
    for (let m = 0; m < 1 << letters.length; m++) {
      const v = {};
      letters.forEach((l, i) => (v[l] = !!(m & (1 << i))));
      if (fs.every((f) => evaluate(f, v))) return true;
    }
    return false;
  }

  // ---------- Parser ----------
  // Accepts symbols (¬ ∧ ∨ → ↔) and plain text (~ & v -> <->). Atoms are capital letters.
  function tokenize(s) {
    const toks = [];
    let i = 0;
    while (i < s.length) {
      const c = s[i];
      if (/\s/.test(c)) { i++; continue; }
      if (s.startsWith('<->', i) || s.startsWith('<=>', i)) { toks.push('↔'); i += 3; continue; }
      if (s.startsWith('->', i) || s.startsWith('=>', i)) { toks.push('→'); i += 2; continue; }
      if ('↔≡'.includes(c)) { toks.push('↔'); i++; continue; }
      if ('→⊃>'.includes(c)) { toks.push('→'); i++; continue; }
      if ('¬~!-'.includes(c)) { toks.push('¬'); i++; continue; }
      if ('∧&^·*'.includes(c)) { toks.push('∧'); i++; continue; }
      if ('∨v|+'.includes(c)) { toks.push('∨'); i++; continue; }
      if ('([{'.includes(c)) { toks.push('('); i++; continue; }
      if (')]}'.includes(c)) { toks.push(')'); i++; continue; }
      if (/[A-Z]/.test(c)) { toks.push(c); i++; continue; }
      throw new Error(`"${c}" isn't a symbol I know. Use capital letters for statements and the connective keys.`);
    }
    return toks;
  }
  const BINTOK = { '∧': 'and', '∨': 'or', '→': 'imp', '↔': 'iff' };
  function parse(s) {
    let toks;
    try { toks = tokenize(s); } catch (e) { return { ok: false, error: e.message }; }
    if (!toks.length) return { ok: false, error: 'Write a formula first.' };
    let p = 0;
    function formula() {
      const left = unary();
      if (p < toks.length && BINTOK[toks[p]]) {
        const op = BINTOK[toks[p++]];
        const right = unary();
        if (p < toks.length && BINTOK[toks[p]])
          throw new Error('Add brackets to show the grouping, like (P ∧ Q) ∨ R. Two connectives side by side need brackets.');
        return bin(op, left, right);
      }
      return left;
    }
    function unary() {
      const t = toks[p];
      if (t === undefined) throw new Error('The formula stops too early. Something is missing at the end.');
      if (t === '¬') { p++; return not(unary()); }
      if (t === '(') {
        p++;
        const f = formula();
        if (toks[p] !== ')') throw new Error('A bracket is missing: every "(" needs a matching ")".');
        p++;
        return f;
      }
      if (/[A-Z]/.test(t)) { p++; return atom(t); }
      if (t === ')') throw new Error('There is a ")" with nothing inside or before it.');
      throw new Error(`"${t}" needs a statement on its left side.`);
    }
    try {
      const f = formula();
      if (p < toks.length)
        throw new Error(toks[p] === ')' ? 'There is an extra ")".' : `"${toks[p]}" is out of place. Is a connective or bracket missing?`);
      return { ok: true, f };
    } catch (e) {
      return { ok: false, error: e.message };
    }
  }

  // ---------- Rules ----------
  const RULES = {
    MP: { name: 'Modus Ponens', kind: 'inf', n: 2, from: 'P → Q, P', to: 'Q', plain: 'If P then Q; P is true; so Q.' },
    MT: { name: 'Modus Tollens', kind: 'inf', n: 2, from: 'P → Q, ¬Q', to: '¬P', plain: 'If P then Q; Q is false; so P is false.' },
    HS: { name: 'Hypothetical Syllogism', kind: 'inf', n: 2, from: 'P → Q, Q → R', to: 'P → R', plain: 'Chain two ifs together.' },
    DS: { name: 'Disjunctive Syllogism', kind: 'inf', n: 2, from: 'P ∨ Q, ¬P', to: 'Q', plain: "One of them is true; it isn't P; so Q." },
    Simp: { name: 'Simplification', kind: 'inf', n: 1, from: 'P ∧ Q', to: 'P', plain: 'Both are true, so the left one is.' },
    Conj: { name: 'Conjunction', kind: 'inf', n: 2, from: 'P, Q', to: 'P ∧ Q', plain: 'Two true lines can be joined with "and".' },
    Add: { name: 'Addition', kind: 'inf', n: 1, from: 'P', to: 'P ∨ Q', plain: 'If P is true, "P or anything" is true.' },
    CD: { name: 'Constructive Dilemma', kind: 'inf', n: 2, from: '(P → Q) ∧ (R → S), P ∨ R', to: 'Q ∨ S', plain: 'Either cause happens, so one of the results does.' },
    DN: { name: 'Double Negation', kind: 'rep', n: 1, from: 'P', to: '¬¬P', plain: 'Two negations cancel out.' },
    Com: { name: 'Commutation', kind: 'rep', n: 1, from: 'P ∨ Q', to: 'Q ∨ P (same for ∧)', plain: 'Swap the two sides of ∨ or ∧.' },
    Assoc: { name: 'Association', kind: 'rep', n: 1, from: '(P ∨ Q) ∨ R', to: 'P ∨ (Q ∨ R) (same for ∧)', plain: 'Regroup the brackets in a chain of ∨ or of ∧.' },
    DeM: { name: "De Morgan's", kind: 'rep', n: 1, from: '¬(P ∧ Q) / ¬(P ∨ Q)', to: '¬P ∨ ¬Q / ¬P ∧ ¬Q', plain: 'Push a negation inside the brackets and flip ∧ and ∨.' },
    Dist: { name: 'Distribution', kind: 'rep', n: 1, from: 'P ∧ (Q ∨ R) / P ∨ (Q ∧ R)', to: '(P ∧ Q) ∨ (P ∧ R) / (P ∨ Q) ∧ (P ∨ R)', plain: 'Share the outside letter with each part inside the brackets.' },
    Trans: { name: 'Transposition', kind: 'rep', n: 1, from: 'P → Q', to: '¬Q → ¬P', plain: 'Swap the sides of a conditional and negate both.' },
    Impl: { name: 'Material Implication', kind: 'rep', n: 1, from: 'P → Q', to: '¬P ∨ Q', plain: '"If P then Q" means "not P, or Q".' },
    Equiv: { name: 'Material Equivalence', kind: 'rep', n: 1, from: 'P ↔ Q', to: '(P → Q) ∧ (Q → P), or (P ∧ Q) ∨ (¬P ∧ ¬Q)', plain: '"P if and only if Q" means each implies the other.' },
    Exp: { name: 'Exportation', kind: 'rep', n: 1, from: '(P ∧ Q) → R', to: 'P → (Q → R)', plain: '"If P and Q, then R" means "if P, then if Q then R".' },
    Taut: { name: 'Tautology', kind: 'rep', n: 1, from: 'P', to: 'P ∨ P (same for ∧)', plain: 'Saying the same thing twice adds nothing.' },
  };
  const RULE_ORDER = Object.keys(RULES);

  function rootRewrites(rule, f) {
    const out = [];
    const T = f.t;
    switch (rule) {
      case 'DN':
        out.push(not(not(f)));
        if (T === 'not' && f.a.t === 'not') out.push(f.a.a);
        break;
      case 'Com':
        if (T === 'and' || T === 'or') out.push(bin(T, f.b, f.a));
        break;
      case 'Assoc':
        if (T === 'and' || T === 'or') {
          if (f.a.t === T) out.push(bin(T, f.a.a, bin(T, f.a.b, f.b)));
          if (f.b.t === T) out.push(bin(T, bin(T, f.a, f.b.a), f.b.b));
        }
        break;
      case 'DeM':
        if (T === 'not' && (f.a.t === 'and' || f.a.t === 'or'))
          out.push(bin(f.a.t === 'and' ? 'or' : 'and', not(f.a.a), not(f.a.b)));
        if ((T === 'and' || T === 'or') && f.a.t === 'not' && f.b.t === 'not')
          out.push(not(bin(T === 'and' ? 'or' : 'and', f.a.a, f.b.a)));
        break;
      case 'Dist':
        for (const [o, i] of [['and', 'or'], ['or', 'and']]) {
          if (T === o && f.b.t === i) out.push(bin(i, bin(o, f.a, f.b.a), bin(o, f.a, f.b.b)));
          if (T === i && f.a.t === o && f.b.t === o && eq(f.a.a, f.b.a)) out.push(bin(o, f.a.a, bin(i, f.a.b, f.b.b)));
        }
        break;
      case 'Trans':
        if (T === 'imp') {
          out.push(imp(not(f.b), not(f.a)));
          if (f.a.t === 'not' && f.b.t === 'not') out.push(imp(f.b.a, f.a.a));
        }
        break;
      case 'Impl':
        if (T === 'imp') out.push(or(not(f.a), f.b));
        if (T === 'or' && f.a.t === 'not') out.push(imp(f.a.a, f.b));
        break;
      case 'Equiv':
        if (T === 'iff') {
          out.push(and(imp(f.a, f.b), imp(f.b, f.a)));
          out.push(or(and(f.a, f.b), and(not(f.a), not(f.b))));
        }
        if (T === 'and' && f.a.t === 'imp' && f.b.t === 'imp' && eq(f.a.a, f.b.b) && eq(f.a.b, f.b.a)) out.push(iff(f.a.a, f.a.b));
        if (T === 'or' && f.a.t === 'and' && f.b.t === 'and' && f.b.a.t === 'not' && f.b.b.t === 'not' &&
            eq(f.a.a, f.b.a.a) && eq(f.a.b, f.b.b.a)) out.push(iff(f.a.a, f.a.b));
        break;
      case 'Exp':
        if (T === 'imp' && f.a.t === 'and') out.push(imp(f.a.a, imp(f.a.b, f.b)));
        if (T === 'imp' && f.b.t === 'imp') out.push(imp(and(f.a, f.b.a), f.b.b));
        break;
      case 'Taut':
        out.push(or(f, f), and(f, f));
        if ((T === 'or' || T === 'and') && eq(f.a, f.b)) out.push(f.a);
        break;
    }
    return out;
  }

  // B follows from A by applying `rule` (in either direction) at one or more separate places.
  function rewritesTo(rule, A, B) {
    if (eq(A, B)) return true;
    if (rootRewrites(rule, A).some((C) => eq(C, B))) return true;
    if (A.t !== B.t || A.t === 'atom') return false;
    if (A.t === 'not') return rewritesTo(rule, A.a, B.a);
    return rewritesTo(rule, A.a, B.a) && rewritesTo(rule, A.b, B.b);
  }

  const OK = { ok: true };
  const fail = (msg, trap) => ({ ok: false, msg, trap });
  const s = show;

  function checkInference(rule, c, r) {
    const perms = c.length === 2 ? [[c[0], c[1]], [c[1], c[0]]] : [[c[0]]];
    switch (rule) {
      case 'MP': {
        for (const [x, y] of perms)
          if (x.t === 'imp' && eq(x.a, y))
            return eq(r, x.b) ? OK : fail(`From ${s(x)} and ${s(y)}, Modus Ponens gives the right side of the conditional: ${s(x.b)}.`);
        for (const [x, y] of perms)
          if (x.t === 'imp' && eq(x.b, y))
            return fail(`This is the trap called affirming the consequent. Knowing the "then" part (${s(y)}) does not tell you the "if" part. MP needs the "if" part, ${s(x.a)}.`, 'affirm');
        if (!c.some((x) => x.t === 'imp'))
          return fail('Modus Ponens needs a conditional line, P → Q, as one of the lines you cite. Neither line is an if-then as a whole.');
        return fail('Modus Ponens needs a conditional P → Q and a second line that is exactly its left side, P.');
      }
      case 'MT': {
        for (const [x, y] of perms)
          if (x.t === 'imp' && y.t === 'not' && eq(y.a, x.b))
            return eq(r, not(x.a)) ? OK : fail(`From ${s(x)} and ${s(y)}, Modus Tollens gives the negation of the left side: ${s(not(x.a))}.`);
        for (const [x, y] of perms)
          if (x.t === 'imp' && y.t === 'not' && eq(y.a, x.a))
            return fail(`This is the trap called denying the antecedent. ${s(y)} denies the "if" part, which tells you nothing about ${s(x.b)}. MT needs the negation of the "then" part: ${s(not(x.b))}.`, 'deny');
        for (const [x, y] of perms)
          if (x.t === 'imp' && x.b.t === 'not' && eq(y, x.b.a))
            return fail(`MT needs the exact negation of ${s(x.b)}, which is ${s(not(x.b))}. Use Double Negation to turn ${s(y)} into ${s(not(not(y)))} first.`, 'dn');
        if (!c.some((x) => x.t === 'imp'))
          return fail('Modus Tollens needs a conditional line, P → Q, as one of the lines you cite.');
        return fail('Modus Tollens needs a conditional P → Q and a line that is exactly ¬Q, the negation of its right side.');
      }
      case 'HS': {
        for (const [x, y] of perms)
          if (x.t === 'imp' && y.t === 'imp' && eq(x.b, y.a))
            return eq(r, imp(x.a, y.b)) ? OK : fail(`Chaining ${s(x)} and ${s(y)} gives ${s(imp(x.a, y.b))}.`);
        return fail('Hypothetical Syllogism needs two conditionals where the right side of one is exactly the left side of the other: P → Q and Q → R.');
      }
      case 'DS': {
        for (const [x, y] of perms)
          if (x.t === 'or' && y.t === 'not' && eq(y.a, x.a))
            return eq(r, x.b) ? OK : fail(`From ${s(x)} and ${s(y)}, Disjunctive Syllogism gives the right side: ${s(x.b)}.`);
        for (const [x, y] of perms)
          if (x.t === 'or' && y.t === 'not' && eq(y.a, x.b))
            return fail(`DS only cancels the left side of an "or". ${s(y)} denies the right side of ${s(x)}. Use Commutation first to get ${s(or(x.b, x.a))}, then DS.`, 'dsright');
        for (const [x, y] of perms)
          if (x.t === 'or' && x.a.t === 'not' && eq(y, x.a.a))
            return fail(`DS needs the exact negation of ${s(x.a)}, which is ${s(not(x.a))}. Use Double Negation on ${s(y)} first.`, 'dn');
        if (!c.some((x) => x.t === 'or'))
          return fail('Disjunctive Syllogism needs an "or" line, P ∨ Q, as one of the lines you cite.');
        return fail('Disjunctive Syllogism needs P ∨ Q and a line that is exactly ¬P, the negation of the left side.');
      }
      case 'Simp': {
        const x = c[0];
        if (x.t !== 'and') return fail(`Simplification works only when the whole line is an "and". The main connective of ${s(x)} isn't ∧. Rules of inference apply to whole lines only.`);
        if (eq(r, x.a)) return OK;
        if (eq(r, x.b)) return fail(`Simplification takes only the left side, ${s(x.a)}. To get ${s(x.b)}, use Commutation first to write ${s(and(x.b, x.a))}.`, 'simpright');
        return fail(`Simplification on ${s(x)} gives its left side: ${s(x.a)}.`);
      }
      case 'Conj': {
        if (r.t === 'and' && perms.some(([x, y]) => eq(r.a, x) && eq(r.b, y))) return OK;
        return fail(`Conjunction joins the two cited lines with ∧: ${s(and(c[0], c[1]))} or ${s(and(c[1], c[0]))}.`);
      }
      case 'Add': {
        const x = c[0];
        if (r.t === 'or' && eq(r.a, x)) return OK;
        if (r.t === 'or' && eq(r.b, x)) return fail(`Addition keeps the original line on the left: write ${s(or(x, r.a))}, then use Commutation if you need it the other way round.`, 'addright');
        return fail(`Addition turns ${s(x)} into "${s(x)} or something": ${s(x, false)} ∨ …`);
      }
      case 'CD': {
        for (const [x, y] of perms)
          if (x.t === 'and' && x.a.t === 'imp' && x.b.t === 'imp' && y.t === 'or' && eq(y.a, x.a.a) && eq(y.b, x.b.a))
            return eq(r, or(x.a.b, x.b.b)) ? OK : fail(`From ${s(x)} and ${s(y)}, Constructive Dilemma gives ${s(or(x.a.b, x.b.b))}.`);
        return fail('Constructive Dilemma needs (P → Q) ∧ (R → S) and P ∨ R, with the "or" line matching the two "if" parts in order.');
      }
    }
    return fail('Unknown rule.');
  }

  function checkReplacement(rule, A, B) {
    const R = RULES[rule];
    if (eq(A, B)) return fail('This line is identical to the line it comes from. A replacement rule has to change something.');
    if (rewritesTo(rule, A, B)) return OK;
    return fail(`${R.name} doesn't turn ${s(A)} into ${s(B)}. It swaps ${R.from} with ${R.to}.`);
  }

  function ruleWorks(rule, cited, result) {
    const R = RULES[rule];
    if (cited.length !== R.n) return false;
    return (R.kind === 'inf' ? checkInference(rule, cited, result) : checkReplacement(rule, cited[0], result)).ok;
  }

  function checkStep(rule, cited, result) {
    const R = RULES[rule];
    if (!R) return fail('Pick a rule.');
    if (cited.length !== R.n)
      return fail(`${R.name} uses ${R.n === 1 ? 'one line' : 'two lines'}, and you cited ${cited.length}. Tap lines in the proof to cite them.`);
    const res = R.kind === 'inf' ? checkInference(rule, cited, result) : checkReplacement(rule, cited[0], result);
    if (!res.ok) {
      const alt = RULE_ORDER.find((o) => o !== rule && ruleWorks(o, cited, result));
      if (alt) { res.alt = alt; res.msg += ` Your line is still correct, though: it follows by ${RULES[alt].name} (${alt}).`; }
    }
    return res;
  }

  // ---------- Generator ----------
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function hashStr(str) {
    let h = 2166136261;
    for (let i = 0; i < str.length; i++) h = Math.imul(h ^ str.charCodeAt(i), 16777619);
    return h >>> 0;
  }
  const pick = (rng, arr) => arr[Math.floor(rng() * arr.length)];
  function shuffle(rng, arr) {
    const a = arr.slice();
    for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rng() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
    return a;
  }

  function positions(f, path = [], out = []) {
    out.push([path, f]);
    if (f.t === 'not') positions(f.a, path.concat('a'), out);
    else if (isBin(f)) { positions(f.a, path.concat('a'), out); positions(f.b, path.concat('b'), out); }
    return out;
  }
  function setAt(f, path, g) {
    if (!path.length) return g;
    const [h, ...rest] = path;
    return Object.assign({}, f, { [h]: setAt(f[h], rest, g) });
  }
  // Silly shapes: P ∧ P, P → ¬P, ¬¬¬P …
  function degenerate(f, allowSame) {
    if (f.t === 'atom') return false;
    if (f.t === 'not') return (f.a.t === 'not' && f.a.a.t === 'not') || degenerate(f.a, allowSame);
    if (!allowSame && eq(f.a, f.b)) return true;
    if (eq(f.a, not(f.b)) || eq(not(f.a), f.b)) return true;
    return degenerate(f.a, allowSame) || degenerate(f.b, allowSame);
  }

  function freshAtom(ctx) {
    const unused = ctx.letters.filter((l) => !ctx.used.has(l));
    const l = unused.length && ctx.rng() < 0.8 ? pick(ctx.rng, unused) : pick(ctx.rng, ctx.letters);
    ctx.used.add(l);
    return atom(l);
  }
  function fresh(ctx) {
    const a = freshAtom(ctx);
    return ctx.rng() < ctx.spec.negP ? not(a) : a;
  }

  function options(ctx, g) {
    const opts = [];
    const allow = new Set(ctx.spec.rules);
    const add = (rule, subs) => opts.push({ rule, subs });
    if (allow.has('MP')) { const A = fresh(ctx); add('MP', [imp(A, g), A]); }
    if (allow.has('MT') && g.t === 'not') { const B = fresh(ctx); add('MT', [imp(g.a, B), not(B)]); }
    if (allow.has('HS') && g.t === 'imp') { const B = fresh(ctx); add('HS', [imp(g.a, B), imp(B, g.b)]); }
    if (allow.has('DS')) { const A = freshAtom(ctx); add('DS', [or(A, g), not(A)]); }
    if (allow.has('Simp')) { const B = fresh(ctx); add('Simp', [and(g, B)]); }
    if (allow.has('Conj') && g.t === 'and') add('Conj', [g.a, g.b]);
    if (allow.has('Add') && g.t === 'or') add('Add', [g.a]);
    if (allow.has('CD') && g.t === 'or') { const C = freshAtom(ctx), D = freshAtom(ctx); add('CD', [and(imp(C, g.a), imp(D, g.b)), or(C, D)]); }
    for (const rule of ctx.spec.rules) {
      if (RULES[rule].kind !== 'rep') continue;
      const cands = [];
      for (const [path, sub] of positions(g))
        for (const w of rootRewrites(rule, sub)) {
          // Backward Taut/DN only rarely grow a formula (keeps premises tidy)
          if (rule === 'Taut' && size(w) > size(sub) && ctx.rng() < 0.5) continue;
          cands.push(setAt(g, path, w));
        }
      // DN: prefer stripping a ¬¬ the proof will need; only sometimes put ¬¬ into a premise
      let pool = cands;
      if (rule === 'DN') {
        const shrink = cands.filter((c) => size(c) < size(g));
        pool = shrink.length ? shrink : ctx.rng() < 0.2 ? cands : [];
      }
      if (pool.length) add(rule, [pick(ctx.rng, pool)]);
    }
    return opts.filter((o) =>
      o.subs.every((x) => size(x) <= ctx.spec.maxSize && !degenerate(x, o.rule === 'Taut') && !ctx.inProgress.has(key(x)) && !eq(x, g))
    );
  }

  function weightOf(ctx, rule) {
    if (ctx.spec.focus.includes(rule)) return 5;
    if (rule === 'Taut') return 0.15;
    return RULES[rule].kind === 'rep' ? 0.6 : 1.5;
  }

  function derive(ctx, g, d, top) {
    const k = key(g);
    if (ctx.known.has(k)) return;
    let opts = d > 0 && (top || ctx.rng() > ctx.spec.stop) ? options(ctx, g) : [];
    if (!opts.length) { ctx.known.add(k); ctx.premises.push(g); return; }
    const total = opts.reduce((t, o) => t + weightOf(ctx, o.rule), 0);
    let x = ctx.rng() * total, choice = opts[0];
    for (const o of opts) { x -= weightOf(ctx, o.rule); if (x <= 0) { choice = o; break; } }
    ctx.inProgress.add(k);
    for (const sub of choice.subs) derive(ctx, sub, d - 1 - (ctx.rng() < 0.25 ? 1 : 0), false);
    ctx.inProgress.delete(k);
    ctx.steps.push({ f: g, rule: choice.rule, from: choice.subs });
    ctx.known.add(k);
  }

  function pickGoal(ctx) {
    const shapes = ctx.spec.goals;
    const total = Object.values(shapes).reduce((a, b) => a + b, 0);
    let x = ctx.rng() * total, shape = 'atom';
    for (const [k, w] of Object.entries(shapes)) { x -= w; if (x <= 0) { shape = k; break; } }
    const A = freshAtom(ctx);
    if (shape === 'atom') return A;
    if (shape === 'neg') return not(A);
    const B = freshAtom(ctx);
    return bin(shape, ctx.rng() < 0.2 ? not(A) : A, B);
  }

  function tryGen(spec, rng, level) {
    const ctx = {
      rng, spec, letters: 'PQRSTU'.slice(0, spec.letters).split(''),
      used: new Set(), known: new Set(), inProgress: new Set(), premises: [], steps: [],
    };
    const goal = pickGoal(ctx);
    const depth = spec.depth[0] + Math.floor(((spec.depth[1] - spec.depth[0] + 1) * (level - 1)) / 5);
    derive(ctx, goal, Math.max(1, depth), true);
    const minSteps = spec.steps[0] + Math.floor((level - 1) / 2);
    const steps = ctx.steps;
    if (steps.length < Math.min(minSteps, spec.steps[1]) || steps.length > spec.steps[1]) return null;
    if (ctx.premises.length < 2 || ctx.premises.length > 6) return null;
    if (spec.focus.length && !steps.some((st) => spec.focus.includes(st.rule))) return null;
    if (!satisfiable(ctx.premises)) return null;
    if (ctx.premises.some((p) => satisfiable([not(p)]) === false)) return null; // no tautological premises
    // Every model step must pass the checker (guards against generator bugs)
    for (const st of steps) if (!checkStep(st.rule, st.from, st.f).ok) return null;
    return { premises: shuffle(rng, ctx.premises), conclusion: goal, solution: steps };
  }

  function generate(spec, level, seed) {
    const rng = mulberry32(seed >>> 0);
    for (let i = 0; i < 3000; i++) {
      const p = tryGen(spec, rng, i < 400 ? level : 1);
      if (p) return p;
    }
    return null;
  }

  // Next useful step from the model proof, given the formulas already on the page.
  function hintFor(puzzle, lineFormulas) {
    const have = new Map();
    lineFormulas.forEach((f, i) => { if (!have.has(key(f))) have.set(key(f), i + 1); });
    for (const st of puzzle.solution) {
      if (have.has(key(st.f))) continue;
      if (st.from.every((f) => have.has(key(f)))) return { step: st, cites: st.from.map((f) => have.get(key(f))) };
    }
    return null;
  }

  // Number the model proof: premises 1..n, then steps.
  function numberSolution(puzzle) {
    const lines = puzzle.premises.map((f) => ({ f, rule: 'Premise', cites: [] }));
    const at = new Map(lines.map((l, i) => [key(l.f), i + 1]));
    for (const st of puzzle.solution) {
      lines.push({ f: st.f, rule: st.rule, cites: st.from.map((f) => at.get(key(f))), from: st.from });
      at.set(key(st.f), lines.length);
    }
    return lines;
  }

  function explain(line) {
    const { rule, from, f, cites } = line;
    const L = cites;
    const [a, b] = from || [];
    switch (rule) {
      case 'MP': return `Line ${L[0]} says "if ${s(a.a)}, then ${s(a.b)}", and line ${L[1]} gives exactly the "if" part. So the "then" part follows.`;
      case 'MT': return `Line ${L[0]} says "if ${s(a.a)}, then ${s(a.b)}". Line ${L[1]} says the "then" part is false, so the "if" part must be false too.`;
      case 'HS': return `Line ${L[0]} ends where line ${L[1]} begins (${s(a.b)}), so the two conditionals chain into one.`;
      case 'DS': return `Line ${L[0]} says at least one of ${s(a.a)} and ${s(a.b)} is true. Line ${L[1]} rules out the left one, so the right one is true.`;
      case 'Simp': return `Line ${L[0]} is an "and", so its left side is true on its own.`;
      case 'Conj': return `Lines ${L[0]} and ${L[1]} are both true, so they can be joined with ∧.`;
      case 'Add': return `Line ${L[0]} is true, so "${s(a)} or anything" is true. Addition lets you attach whatever you need on the right.`;
      case 'CD': return `Line ${L[0]} gives two conditionals, and line ${L[1]} says one of their "if" parts holds. So one of the two results holds.`;
      default: {
        const R = RULES[rule];
        const how = {
          DN: 'adds or removes a double negation',
          Com: 'swaps the two sides',
          Assoc: 'moves the brackets',
          DeM: 'moves the negation through the brackets and flips ∧ and ∨',
          Dist: 'distributes one part over the brackets',
          Trans: 'swaps the sides of the conditional and negates both',
          Impl: 'rewrites a conditional as an "or" (or back again)',
          Equiv: 'unpacks or repacks the biconditional',
          Exp: 'moves between (P ∧ Q) → R and P → (Q → R)',
          Taut: 'removes or adds a repeated part',
        }[rule];
        return `${R.name} ${how}, turning line ${L[0]} into this equivalent form.`;
      }
    }
  }

  const api = {
    atom, not, and, or, imp, iff, show, key, eq, size, atomsOf, evaluate, satisfiable,
    parse, tokenize, RULES, RULE_ORDER, checkStep, ruleWorks, rewritesTo, rootRewrites,
    generate, hintFor, numberSolution, explain, hashStr, mulberry32,
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Logic = api;
})(typeof window !== 'undefined' ? window : globalThis);
