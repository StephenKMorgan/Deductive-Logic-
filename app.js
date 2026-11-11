/* Therefore — app UI. Depends on engine.js (Logic) and content.js (Content). */
(function () {
  'use strict';
  const L = window.Logic;
  const { CATEGORIES, DAILY } = window.Content;
  const $app = document.getElementById('app');
  const STORE = 'therefore.v1';

  // ---------- Storage ----------
  function load() { try { return JSON.parse(localStorage.getItem(STORE)) || {}; } catch (e) { return {}; } }
  function save() { try { localStorage.setItem(STORE, JSON.stringify(store)); } catch (e) { /* storage unavailable */ } }
  const store = load();
  store.progress = store.progress || {};
  store.daily = store.daily || {};
  store.notation = store.notation || 'sym';
  let game = store.game || null;
  let screen = { name: 'home' };
  let confirmGiveUp = false, confirmReset = false;

  // ---------- Helpers ----------
  const PLAIN = { '¬': '~', '∧': '&', '∨': 'v', '→': '->', '↔': '<->' };
  const disp = (str) => (store.notation === 'plain' ? str.replace(/[¬∧∨→↔]/g, (c) => PLAIN[c]) : str);
  const esc = (str) => String(str).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const T = (str) => esc(disp(str));
  const F = (f) => T(L.show(f));
  const catById = (id) => (id === 'daily' ? DAILY : CATEGORIES.find((c) => c.id === id));
  const prog = (id) => (store.progress[id] = store.progress[id] || { solved: 0, best: null, grades: [] });
  const levelOf = (id) => Math.min(5, 1 + Math.floor(prog(id).solved / 2));
  const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
  const GRADE_RANK = { 'A': 4, 'B': 3, 'C': 2, 'D': 1 };
  const better = (a, b) => (!b || (GRADE_RANK[a] || 0) > (GRADE_RANK[b] || 0) ? a : b);
  const ruleName = (r) => (L.RULES[r] ? L.RULES[r].name : r);
  const justText = (line) => (line.rule === 'Premise' ? 'Premise' : `${line.cites.join(', ')}, ${line.rule}`);

  function streak() {
    let n = 0;
    const d = new Date();
    if (!store.daily[today()]) d.setDate(d.getDate() - 1);
    for (;;) {
      const k = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      if (!store.daily[k] || store.daily[k].grade === '–') break;
      n++; d.setDate(d.getDate() - 1);
    }
    return n;
  }

  // ---------- Game ----------
  function newGame(catId) {
    const cat = catById(catId);
    const daily = catId === 'daily';
    const level = daily ? 3 : levelOf(catId);
    const base = daily ? L.hashStr('therefore-daily-' + today()) : (Math.random() * 4294967296) >>> 0;
    let puzzle = null;
    for (let i = 0; !puzzle && i < 8; i++) puzzle = L.generate(cat.spec, level, base + i * 7919);
    game = {
      catId, level, date: daily ? today() : null, puzzle,
      lines: puzzle.premises.map((f) => ({ f, rule: 'Premise', cites: [] })),
      tokens: [], caret: 0, rule: null, cites: [], mistakes: [],
      hints: 0, hintPenalty: 0, hintStage: 0, hintKey: null,
      revealed: false, done: false, started: Date.now(), feedback: null,
    };
    confirmGiveUp = false;
    store.game = game; save();
    go('play', { scrollEnd: true });
  }

  function go(name, opts = {}) {
    screen = Object.assign({ name }, opts);
    confirmReset = false;
    render();
    if (!opts.keepScroll) {
      const m = document.querySelector('.scroll');
      if (m) m.scrollTop = opts.scrollEnd ? m.scrollHeight : 0;
    }
  }

  function setFeedback(type, html, extra) { game.feedback = Object.assign({ type, html }, extra || {}); }

  function addLine() {
    const src = game.tokens.join('');
    const parsed = L.parse(src);
    if (!parsed.ok) { setFeedback('bad', T(parsed.error)); return persist(); }
    if (!game.rule) { setFeedback('bad', 'Pick the rule that justifies this line.'); return persist(); }
    const f = parsed.f;
    const dup = game.lines.findIndex((l) => L.eq(l.f, f));
    if (dup >= 0) { setFeedback('bad', `That formula is already line ${dup + 1}. Try a different step.`); return persist(); }
    const cites = game.cites.slice().sort((a, b) => a - b);
    const res = L.checkStep(game.rule, cites.map((n) => game.lines[n - 1].f), f);
    if (!res.ok) {
      game.mistakes.push({ f: L.show(f), rule: game.rule, cites, msg: res.msg, trap: res.trap || (res.alt ? 'alt' : 'other') });
      setFeedback('bad', T(res.msg));
      return persist();
    }
    game.lines.push({ f, rule: game.rule, cites });
    game.tokens = []; game.caret = 0; game.rule = null; game.cites = []; game.hintStage = 0; game.hintKey = null;
    confirmGiveUp = false;
    if (L.eq(f, game.puzzle.conclusion)) return finish();
    setFeedback('good', `Line ${game.lines.length} checks out: ${esc(ruleName(game.lines[game.lines.length - 1].rule))}.`);
    persist(true);
  }

  function persist(scrollEnd) {
    save();
    render();
    if (scrollEnd) { const m = document.querySelector('.scroll'); if (m) m.scrollTop = m.scrollHeight; }
  }

  function undoLine() {
    if (game.lines.length <= game.puzzle.premises.length) return;
    const n = game.lines.length;
    game.lines.pop();
    game.cites = game.cites.filter((c) => c < n);
    setFeedback('info', `Removed line ${n}.`);
    persist();
  }

  const HINT_COST = [0, 4, 8, 15];
  function hint() {
    const h = L.hintFor(game.puzzle, game.lines.map((l) => l.f));
    if (!h) return;
    const k = L.key(h.step.f);
    if (game.hintKey !== k) { game.hintKey = k; game.hintStage = 0; }
    if (game.hintStage < 3) {
      game.hintStage++;
      game.hints++;
      game.hintPenalty += HINT_COST[game.hintStage] - HINT_COST[game.hintStage - 1];
    }
    const lines = h.cites.length === 1 ? `line ${h.cites[0]}` : `lines ${h.cites.join(' and ')}`;
    const r = h.step.rule;
    let html;
    if (game.hintStage === 1) html = `Look at ${lines}. What can they give you together?`;
    else if (game.hintStage === 2) html = `Use <b>${esc(ruleName(r))} (${r})</b> on ${lines}. <span class="rulepat">${T(L.RULES[r].from)} ⟹ ${T(L.RULES[r].to)}</span>`;
    else html = `Write <span class="fx">${F(h.step.f)}</span>, justified by ${h.cites.join(', ')}, ${r}.`;
    setFeedback('hint', html, game.hintStage === 3 ? { fill: { tokens: [...L.show(h.step.f).replace(/\s/g, '')], rule: r, cites: h.cites } } : null);
    persist();
  }

  function finish() {
    game.done = true;
    const p = game.puzzle;
    const model = p.solution.length;
    const yours = game.lines.length - p.premises.length;
    let score = 100 - 8 * game.mistakes.length - game.hintPenalty - 4 * Math.max(0, yours - model);
    score = Math.max(50, Math.min(100, score));
    let grade = score >= 90 ? 'A' : score >= 80 ? 'B' : score >= 70 ? 'C' : 'D';
    let stars = grade === 'A' ? 3 : grade === 'B' ? 2 : 1;
    if (game.revealed) { grade = '–'; stars = 0; score = 0; }
    game.result = { grade, stars, score, model, yours, secs: Math.round((Date.now() - game.started) / 1000) };
    if (!game.revealed) {
      if (game.catId === 'daily') {
        const prev = store.daily[game.date];
        store.daily[game.date] = { grade: prev ? better(grade, prev.grade) : grade, stars: Math.max(stars, prev ? prev.stars : 0) };
      } else {
        const pr = prog(game.catId);
        pr.solved++;
        pr.best = better(grade, pr.best);
        pr.grades = (pr.grades || []).concat(grade).slice(-10);
      }
    } else if (game.catId === 'daily' && !store.daily[game.date]) {
      store.daily[game.date] = { grade: '–', stars: 0 };
    }
    save();
    go('result');
  }

  // ---------- Views ----------
  function nav(active) {
    const item = (id, label, icon) =>
      `<button class="nav-item${active === id ? ' on' : ''}" data-act="nav" data-to="${id}" aria-current="${active === id ? 'page' : 'false'}"><span class="nav-ico" aria-hidden="true">${icon}</span>${label}</button>`;
    return `<nav class="nav">${item('home', 'Learn', '∴')}${item('daily', 'Daily', '◷')}${item('rules', 'Rules', '§')}</nav>`;
  }

  function viewHome() {
    const resume = game && !game.done
      ? `<button class="resume" data-act="resume"><span><span class="eyebrow">Puzzle in progress</span><b>${esc(catById(game.catId).title)}</b> · line ${game.lines.length + 1}</span><span class="go">Resume →</span></button>` : '';
    const dToday = store.daily[today()];
    const rows = CATEGORIES.map((c, i) => {
      const pr = prog(c.id), lv = levelOf(c.id);
      const dots = [1, 2, 3, 4, 5].map((k) => `<i class="${k <= lv ? 'on' : ''}"></i>`).join('');
      return `<li><button class="course" data-act="lesson" data-id="${c.id}">
        <span class="step">${i + 1}</span>
        <span class="course-main"><b>${esc(c.title)}</b><span class="muted">${esc(c.blurb)}</span>
          <span class="course-meta"><span class="tag">${T(c.short)}</span><span class="dots" aria-label="Level ${lv} of 5">${dots}</span>${pr.solved ? `<span class="muted small">${pr.solved} solved</span>` : ''}</span></span>
        ${pr.best ? `<span class="best g-${pr.best}" aria-label="Best grade ${pr.best}">${pr.best}</span>` : '<span class="best none">·</span>'}
      </button></li>`;
    }).join('');
    return `<header class="top"><div class="brand"><span class="mark" aria-hidden="true">∴</span><div><h1>Therefore</h1><p class="muted">Deductive logic proofs, one line at a time</p></div></div></header>
      <main class="scroll"><div class="wrap">
        ${resume}
        <button class="daily-card" data-act="nav" data-to="daily">
          <span><span class="eyebrow">Daily proof · ${esc(today())}</span><b>${dToday ? (dToday.grade === '–' ? 'Studied today' : `Solved · grade ${dToday.grade}`) : 'A new proof is ready'}</b><span class="muted small">${streak()} day streak</span></span>
          <span class="go">${dToday ? 'View' : 'Start'} →</span>
        </button>
        <h2 class="section-h">Courses</h2>
        <p class="muted lead">Each course adds new rules. Puzzles get longer as you solve more: the dots show your level.</p>
        <ol class="courses">${rows}</ol>
      </div></main>${nav('home')}`;
  }

  function proofTable(lines, concl, opts = {}) {
    const nPrem = opts.nPrem != null ? opts.nPrem : lines.filter((l) => l.rule === 'Premise').length;
    return `<ol class="ledger${opts.small ? ' small' : ''}">${lines.map((l, i) => {
      const f = typeof l.f === 'string' ? T(l.f) : F(l.f);
      const j = typeof l.j === 'string' ? T(l.j) : esc(justText(l));
      return `<li><span class="ln">${i + 1}.</span><span class="lf">${f}</span><span class="lj">${j}${i === nPrem - 1 && concl ? ` <span class="therefore">∴ ${typeof concl === 'string' ? T(concl) : F(concl)}</span>` : ''}</span>${opts.explain && l.rule !== 'Premise' ? `<span class="lx">${T(L.explain(l))}</span>` : ''}</li>`;
    }).join('')}</ol>`;
  }

  function ruleCard(r) {
    const R = L.RULES[r];
    return `<div class="rule-card"><div class="rc-head"><b>${esc(R.name)}</b><span class="tag">${r}</span></div>
      <div class="rc-pat"><span class="fx">${T(R.from)}</span><span class="arrow" aria-label="gives">${R.kind === 'rep' ? '⟺' : '⟹'}</span><span class="fx">${T(R.to)}</span></div>
      <p class="muted small">${esc(R.plain)}</p></div>`;
  }

  function viewLesson() {
    const c = catById(screen.id);
    const ls = c.lesson, pr = prog(c.id), lv = levelOf(c.id);
    const idx = CATEGORIES.indexOf(c);
    const ex = ls.example;
    const exLines = ex.lines.map(([f, j]) => ({ f, j, rule: j === 'Premise' ? 'Premise' : 'x' }));
    const allowed = c.spec.rules;
    return `<header class="bar"><button class="icon-btn" data-act="nav" data-to="home" aria-label="Back to courses">←</button><span class="bar-title">Course ${idx + 1}</span><span></span></header>
      <main class="scroll"><div class="wrap">
        <p class="eyebrow">${T(c.short)} · Level ${lv} of 5${pr.solved ? ` · ${pr.solved} solved` : ''}</p>
        <h2 class="page-h">${esc(c.title)}</h2>
        <button class="primary wide" data-act="start" data-id="${c.id}">${pr.solved ? 'Next puzzle' : 'Start first puzzle'}</button>
        ${ls.intro.map((p) => `<p class="body">${T(p)}</p>`).join('')}
        ${ls.rules.length ? `<h3 class="sub-h">New rules</h3><div class="rule-grid">${ls.rules.map(ruleCard).join('')}</div>` : ''}
        <h3 class="sub-h">Worked example</h3>
        <div class="card">${proofTable(exLines, ex.concl)}</div>
        <div class="tipbox"><b>Tip</b> ${T(ls.tip)}</div>
        <h3 class="sub-h">Rules you can use here</h3>
        <div class="chips static">${allowed.map((r) => `<span class="chip">${r}</span>`).join('')}</div>
        <button class="primary wide" data-act="start" data-id="${c.id}">${pr.solved ? 'Next puzzle' : 'Start first puzzle'}</button>
      </div></main>`;
  }

  function viewDaily() {
    const d = store.daily[today()];
    const inProgress = game && !game.done && game.catId === 'daily' && game.date === today();
    const days = [];
    for (let i = 6; i >= 0; i--) {
      const dt = new Date(); dt.setDate(dt.getDate() - i);
      const k = `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
      const r = store.daily[k];
      days.push(`<li class="${r ? (r.grade === '–' ? 'studied' : 'done') : ''}${i === 0 ? ' today' : ''}"><span class="small muted">${dt.toLocaleDateString(undefined, { weekday: 'narrow' })}</span><span class="day-dot">${r ? esc(r.grade) : ''}</span></li>`);
    }
    return `<header class="top"><div class="brand"><span class="mark" aria-hidden="true">◷</span><div><h1>Daily Proof</h1><p class="muted">${esc(new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }))}</p></div></div></header>
      <main class="scroll"><div class="wrap">
        <div class="card daily-big">
          <p class="eyebrow">Everyone gets the same proof today</p>
          <p class="streak"><b>${streak()}</b> day streak</p>
          <ol class="week">${days.join('')}</ol>
          ${d ? `<p class="body">${d.grade === '–' ? 'You studied today’s solution. A new proof arrives tomorrow.' : `You solved today’s proof with grade <b>${esc(d.grade)}</b>. You can replay it, but your best grade is kept.`}</p>` : '<p class="body">A mixed puzzle that can use any of the 18 rules. Medium length.</p>'}
          <button class="primary wide" data-act="${inProgress ? 'resume' : 'start'}" data-id="daily">${inProgress ? 'Resume' : d ? 'Replay today’s proof' : 'Start today’s proof'}</button>
        </div>
      </div></main>${nav('daily')}`;
  }

  function viewRules() {
    const sym = [['¬', 'Negation', '"not P"', '~P'], ['∧', 'Conjunction', '"P and Q"', 'P & Q'], ['∨', 'Disjunction', '"P or Q" (or both)', 'P v Q'],
      ['→', 'Conditional', '"if P, then Q"', 'P -> Q'], ['↔', 'Biconditional', '"P if and only if Q"', 'P <-> Q'], ['∴', 'Therefore', 'the conclusion', ':.']];
    const tbl = (rules) => `<div class="tbl-wrap"><table class="tbl"><thead><tr><th>Rule</th><th>From</th><th>You may write</th></tr></thead><tbody>${rules.map((r) => {
      const R = L.RULES[r];
      return `<tr><td><b>${esc(R.name)}</b> <span class="tag">${r}</span><div class="muted small">${esc(R.plain)}</div></td><td class="fx">${T(R.from)}</td><td class="fx">${T(R.to)}</td></tr>`;
    }).join('')}</tbody></table></div>`;
    const inf = L.RULE_ORDER.filter((r) => L.RULES[r].kind === 'inf'), rep = L.RULE_ORDER.filter((r) => L.RULES[r].kind === 'rep');
    return `<header class="top"><div class="brand"><span class="mark" aria-hidden="true">§</span><div><h1>Rules</h1><p class="muted">The cheat sheet, always at hand</p></div></div></header>
      <main class="scroll"><div class="wrap">
        <div class="setting"><span><b>Notation</b><span class="muted small">How formulas are shown</span></span>
          <div class="seg" role="group" aria-label="Notation"><button data-act="notation" data-v="sym" class="${store.notation === 'sym' ? 'on' : ''}">¬ ∧ ∨ →</button><button data-act="notation" data-v="plain" class="${store.notation === 'plain' ? 'on' : ''}">~ &amp; v -&gt;</button></div></div>
        <h2 class="section-h">Symbols</h2>
        <div class="tbl-wrap"><table class="tbl"><thead><tr><th>Symbol</th><th>Name</th><th>Read it as</th><th>Plain text</th></tr></thead><tbody>
          ${sym.map(([a, b, c, d]) => `<tr><td class="fx big">${a}</td><td>${b}</td><td>${esc(c)}</td><td class="fx">${esc(d)}</td></tr>`).join('')}</tbody></table></div>
        <p class="muted small">On a hardware keyboard you can type the plain-text versions straight into a puzzle.</p>
        <h2 class="section-h">Rules of inference</h2>
        <p class="body">These apply to whole lines only: from the lines on the left, you may write the line on the right.</p>
        ${tbl(inf)}
        <div class="tipbox warn"><b>Not valid</b> From P → Q and Q, concluding P (affirming the consequent). From P → Q and ¬P, concluding ¬Q (denying the antecedent).</div>
        <h2 class="section-h">Replacement rules</h2>
        <p class="body">Each side means the same thing, so you can swap one for the other in either direction, even inside part of a line.</p>
        ${tbl(rep)}
        <h2 class="section-h">How to write a proof</h2>
        <p class="body">Number every line. The premises come first. Each new line names the rule you used and the line numbers it came from. You're done when the conclusion appears on its own line.</p>
        <div class="card">${proofTable([{ f: 'P ∨ Q', j: 'Premise', rule: 'Premise' }, { f: 'Q ∧ S', j: 'Premise', rule: 'Premise' }, { f: 'S ∧ Q', j: '2, Com' }, { f: 'S', j: '3, Simp' }, { f: 'S ∨ P', j: '4, Add' }, { f: 'P ∨ S', j: '5, Com' }], 'P ∨ S')}</div>
        <ul class="tips"><li>Work backward from the conclusion: ask what line would let you write it.</li><li>Simplification only takes the left side of an "and", so use Commutation first if you need the right side.</li><li>Not every premise has to be used.</li></ul>
        <h2 class="section-h">Your progress</h2>
        <div class="setting"><span><b>Reset progress</b><span class="muted small">Clears levels, grades and the daily streak</span></span>
          <button class="ghost danger" data-act="reset">${confirmReset ? 'Tap again to confirm' : 'Reset'}</button></div>
      </div></main>${nav('rules')}`;
  }

  function viewPlay() {
    const g = game, p = g.puzzle, cat = catById(g.catId);
    const nPrem = p.premises.length;
    const lines = g.lines.map((l, i) => {
      const n = i + 1, sel = g.cites.includes(n);
      return `<li class="${sel ? 'sel' : ''}${i === g.lines.length - 1 && i >= nPrem ? ' fresh' : ''}${i === nPrem - 1 ? ' last-prem' : ''}">
        <button class="line-btn" data-act="cite" data-n="${n}" aria-pressed="${sel}" aria-label="Line ${n}: ${T(L.show(l.f))}. Tap to cite.">
          <span class="ln">${n}.</span><span class="lf">${F(l.f)}</span><span class="lj">${esc(justText(l))}</span>
        </button>
        ${sel ? `<button class="copy-btn" data-act="copy" data-n="${n}" aria-label="Copy line ${n} into the input">Copy</button>` : ''}
      </li>`;
    }).join('');
    const toks = g.tokens.map((t, i) => (i === g.caret ? '<span class="caret"></span>' : '') + `<span class="${/[∧∨→↔]/.test(t) ? 'op' : 't'}">${T(t)}</span>`).join('') + (g.caret === g.tokens.length ? '<span class="caret"></span>' : '');
    const fb = g.feedback;
    const fbHtml = fb ? `<div class="fb fb-${fb.type}" role="status"><span>${fb.html}</span>${fb.fill ? '<button class="mini" data-act="fill">Fill it in</button>' : ''}</div>`
      : g.tokens.length || g.cites.length || g.rule || g.lines.length > nPrem ? ''
      : `<div class="fb fb-info" role="status"><span>Tap lines to cite them, choose a rule, then write the new line.</span></div>`;
    const rules = cat.spec.rules.map((r) => `<button class="chip${g.rule === r ? ' on' : ''}" data-act="rule" data-r="${r}" aria-pressed="${g.rule === r}" title="${esc(ruleName(r))}">${r}</button>`).join('');
    const letters = 'PQRSTU'.slice(0, Math.max(cat.spec.letters, ...[...p.premises.concat([p.conclusion]).reduce((s, f) => L.atomsOf(f, s), new Set())].map((l) => 'PQRSTU'.indexOf(l) + 1))).split('');
    const key = (t, label, cls = '') => `<button class="key ${cls}" data-act="key" data-t="${esc(t)}" aria-label="${esc(label || t)}">${T(t)}</button>`;
    const selR = g.rule ? L.RULES[g.rule] : null;
    return `<header class="bar">
        <button class="icon-btn" data-act="nav" data-to="${g.catId === 'daily' ? 'daily' : 'home'}" aria-label="Leave puzzle">←</button>
        <span class="bar-title">${esc(cat.title)}<span class="muted small"> · ${g.catId === 'daily' ? esc(g.date) : 'Level ' + g.level}</span></span>
        <span class="bar-actions"><button class="ghost sm" data-act="hint">Hint</button><button class="ghost sm${confirmGiveUp ? ' danger' : ''}" data-act="giveup">${confirmGiveUp ? 'Show it?' : 'Solution'}</button></span>
      </header>
      <main class="scroll play-scroll"><div class="wrap">
        <div class="goal"><span class="eyebrow">Prove</span><span class="fx goal-f">∴ ${F(p.conclusion)}</span></div>
        <ol class="ledger interactive">${lines}
          <li class="ghost-line"><span class="ln">${g.lines.length + 1}.</span><span class="lf">${g.tokens.length ? T(L.parse(g.tokens.join('')).ok ? L.show(L.parse(g.tokens.join('')).f) : g.tokens.join(' ')) : '<span class="muted">…</span>'}</span><span class="lj">${g.cites.length || g.rule ? esc(`${g.cites.slice().sort((a, b) => a - b).join(', ')}${g.cites.length && g.rule ? ', ' : ''}${g.rule || ''}`) : ''}</span></li>
        </ol>
      </div></main>
      <section class="composer" aria-label="Write the next line">
        ${fbHtml}
        <div class="input-row">
          <div class="input" aria-label="New line formula" aria-live="polite">${g.tokens.length ? toks : '<span class="caret"></span><span class="ph">Write line ' + (g.lines.length + 1) + '</span>'}</div>
        </div>
        <div class="rule-row">${cat.spec.rules.length <= 5 ? `<div class="chips" id="rule-chips">${rules}</div>` : rulePicker(cat.spec.rules, g.rule)}</div>
        <div class="rule-help">${selR ? `<span><b>${esc(selR.name)}</b> · cite ${selR.n === 1 ? 'one line' : 'two lines'}</span><span class="fx">${T(selR.from)} ${selR.kind === 'rep' ? '⟺' : '⟹'} ${T(selR.to)}</span>` : `Citing: ${g.cites.length ? g.cites.slice().sort((a, b) => a - b).join(', ') : 'none yet'}`}</div>
        <div class="keypad">
          ${letters.map((l) => key(l)).join('')}
          ${key('¬', 'not', 'op')}${key('∧', 'and', 'op')}${key('∨', 'or', 'op')}${key('→', 'if then', 'op')}${key('↔', 'if and only if', 'op')}${key('(', 'open bracket')}${key(')', 'close bracket')}
          <button class="key fn" data-act="left" aria-label="Move cursor left">◀</button><button class="key fn" data-act="right" aria-label="Move cursor right">▶</button><button class="key fn" data-act="back" aria-label="Delete">⌫</button>
          <button class="key fn" data-act="undo" aria-label="Remove last line"${g.lines.length > nPrem ? '' : ' disabled'}>Undo</button>
          <button class="key add" data-act="add" style="grid-column: span ${7 - ((letters.length + 11) % 7)}">Add line</button>
        </div>
      </section>`;
  }

  function rulePicker(list, current) {
    const group = (kind, label) => {
      const rs = list.filter((r) => L.RULES[r].kind === kind);
      return rs.length ? `<optgroup label="${label}">${rs.map((r) => `<option value="${r}"${current === r ? ' selected' : ''}>${r} · ${esc(ruleName(r))}</option>`).join('')}</optgroup>` : '';
    };
    return `<label class="picker${current ? ' on' : ''}"><span class="sr">Rule</span>
      <select id="rule-select" data-act="pick">
        <option value=""${current ? '' : ' selected'}>Choose a rule…</option>
        ${group('inf', 'Rules of inference')}${group('rep', 'Replacement rules')}
      </select></label>`;
  }

  const TRAP_LESSONS = {
    affirm: ['Affirming the consequent', 'From P → Q and Q you cannot conclude P. Q might be true for some other reason. Modus Ponens only runs forward: you need P to get Q.'],
    deny: ['Denying the antecedent', 'From P → Q and ¬P you cannot conclude ¬Q. The conditional says nothing about what happens when P is false. Modus Tollens needs ¬Q to get ¬P.'],
    dsright: ['DS cancels the left side', 'Disjunctive Syllogism removes the left part of an "or". If your "not" line denies the right part, use Commutation to swap the sides first.'],
    simpright: ['Simplification takes the left side', 'From P ∧ Q, Simplification gives P. To reach Q, use Commutation to write Q ∧ P first.'],
    addright: ['Addition keeps your line on the left', 'From P, Addition gives P ∨ (anything). Use Commutation afterwards if you need the new part on the left.'],
    dn: ['Exact negations', 'Rules like Modus Tollens and Disjunctive Syllogism need the exact negation. The negation of ¬Q is ¬¬Q, not Q. Double Negation converts between them.'],
    alt: ['Naming the right rule', 'Some of your lines were correct but cited the wrong rule. Check the shape of the cited lines against the rule card before you pick a rule.'],
    other: ['Matching the pattern', 'Every rule has an exact pattern. Compare the main connective of each cited line with the rule card, and check that the matching parts are identical.'],
  };

  function viewResult() {
    const g = game, r = g.result, p = g.puzzle, cat = catById(g.catId);
    const model = L.numberSolution(p);
    const verdict = g.revealed ? 'Solution shown'
      : r.grade === 'A' ? (g.mistakes.length || g.hints ? 'Strong proof' : 'Flawless proof')
      : r.grade === 'B' ? 'Good proof' : r.grade === 'C' ? 'Proof complete' : 'You got there';
    const advice = [];
    if (g.revealed) advice.push('Read through the model proof below, then try a fresh puzzle in this course. Cover the explanation and see if you can justify each line yourself.');
    else {
      if (r.yours < r.model) advice.push(`Your proof was ${r.model - r.yours} line${r.model - r.yours > 1 ? 's' : ''} shorter than the model proof.`);
      else if (r.yours > r.model) advice.push(`Your proof took ${r.yours - r.model} more line${r.yours - r.model > 1 ? 's' : ''} than the model proof. Compare the routes below to spot the shortcut.`);
      else advice.push('Your proof was the same length as the model proof.');
      if (!g.mistakes.length) advice.push('Every line you submitted was correct.');
      if (g.hints) advice.push(`You used ${g.hints} hint${g.hints > 1 ? 's' : ''}. Next time, try working backward from the conclusion before asking.`);
    }
    const trapCounts = {};
    g.mistakes.forEach((m) => (trapCounts[m.trap] = (trapCounts[m.trap] || 0) + 1));
    const lessons = Object.keys(trapCounts).map((t) => TRAP_LESSONS[t] || TRAP_LESSONS.other);
    const used = [...new Set(p.solution.map((s) => s.rule).concat(g.lines.filter((l) => l.rule !== 'Premise').map((l) => l.rule)))];
    const stars = [1, 2, 3].map((k) => `<span class="${k <= r.stars ? 'on' : ''}" aria-hidden="true">★</span>`).join('');
    const fmtTime = (s) => (s >= 60 ? `${Math.floor(s / 60)}m ${s % 60}s` : `${s}s`);
    return `<header class="bar"><button class="icon-btn" data-act="nav" data-to="${g.catId === 'daily' ? 'daily' : 'home'}" aria-label="Done">←</button><span class="bar-title">${esc(cat.title)}</span><span></span></header>
      <main class="scroll"><div class="wrap">
        <div class="grade-card g-${r.grade === '–' ? 'X' : r.grade}">
          <div class="grade-letter" aria-label="Grade ${r.grade}">${r.grade}</div>
          <div><p class="eyebrow">${g.revealed ? 'Not graded' : `Score ${r.score}`}</p><h2 class="page-h">${verdict}</h2><div class="stars" aria-label="${r.stars} of 3 stars">${stars}</div></div>
        </div>
        <div class="stats">
          <div><b>${g.revealed ? '–' : r.yours}</b><span>your lines</span></div>
          <div><b>${r.model}</b><span>model lines</span></div>
          <div><b>${g.mistakes.length}</b><span>mistakes</span></div>
          <div><b>${g.hints}</b><span>hints</span></div>
          <div><b>${fmtTime(r.secs)}</b><span>time</span></div>
        </div>
        <ul class="advice">${advice.map((a) => `<li>${esc(a)}</li>`).join('')}</ul>
        ${lessons.length ? `<h3 class="sub-h">What to work on</h3>${lessons.map(([h, b]) => `<div class="tipbox"><b>${esc(h)}</b> ${T(b)}</div>`).join('')}` : ''}
        ${g.mistakes.length ? `<details class="card fold"><summary>Your ${g.mistakes.length} rejected line${g.mistakes.length > 1 ? 's' : ''}</summary><ul class="mistakes">${g.mistakes.map((m) => `<li><span class="fx">${T(m.f)}</span> <span class="tag">${m.cites.join(', ')}${m.cites.length ? ', ' : ''}${m.rule}</span><p class="small">${T(m.msg)}</p></li>`).join('')}</ul></details>` : ''}
        <h3 class="sub-h">Model proof, explained</h3>
        <div class="card">${proofTable(model, p.conclusion, { explain: true, nPrem: p.premises.length })}</div>
        ${!g.revealed ? `<details class="card fold"><summary>Your proof</summary>${proofTable(g.lines, p.conclusion, { nPrem: p.premises.length })}</details>` : ''}
        <h3 class="sub-h">Rules in this puzzle</h3>
        <div class="rule-grid">${used.map(ruleCard).join('')}</div>
        <div class="end-actions">
          ${g.catId === 'daily' ? `<button class="primary wide" data-act="share">${screen.copied ? 'Copied' : 'Copy result'}</button><button class="ghost wide" data-act="nav" data-to="home">Back to courses</button>`
            : `<button class="primary wide" data-act="start" data-id="${g.catId}">Next puzzle${levelOf(g.catId) > g.level ? ' · level ' + levelOf(g.catId) : ''}</button><button class="ghost wide" data-act="lesson" data-id="${g.catId}">Review the lesson</button>`}
        </div>
      </div></main>`;
  }

  // ---------- Render & events ----------
  function render() {
    const prev = document.querySelector('.scroll');
    const st = prev ? prev.scrollTop : 0;
    const chips = document.getElementById('rule-chips');
    const cx = chips ? chips.scrollLeft : 0;
    const views = { home: viewHome, lesson: viewLesson, play: viewPlay, result: viewResult, daily: viewDaily, rules: viewRules };
    if ((screen.name === 'play' || screen.name === 'result') && !game) screen = { name: 'home' };
    $app.className = 'screen-' + screen.name;
    $app.innerHTML = views[screen.name]();
    const m = document.querySelector('.scroll');
    if (m) m.scrollTop = st;
    const c2 = document.getElementById('rule-chips');
    if (c2) c2.scrollLeft = cx;
  }

  function act(a, el) {
    const g = game;
    if (g && g.feedback && (g.feedback.type === 'good' || g.feedback.type === 'info') && ['key', 'back', 'left', 'right', 'rule', 'pick', 'cite', 'copy'].includes(a)) g.feedback = null;
    switch (a) {
      case 'nav': return go(el.dataset.to);
      case 'lesson': return go('lesson', { id: el.dataset.id });
      case 'start': return newGame(el.dataset.id);
      case 'resume': return go(game.done ? 'result' : 'play', { scrollEnd: true });
      case 'notation': store.notation = el.dataset.v; save(); return render();
      case 'reset':
        if (!confirmReset) { confirmReset = true; return render(); }
        store.progress = {}; store.daily = {}; store.game = null; game = null; save(); confirmReset = false; return render();
      case 'key': g.tokens.splice(g.caret, 0, el.dataset.t); g.caret++; return persist();
      case 'back': if (g.caret > 0) { g.tokens.splice(g.caret - 1, 1); g.caret--; } return persist();
      case 'left': g.caret = Math.max(0, g.caret - 1); return persist();
      case 'right': g.caret = Math.min(g.tokens.length, g.caret + 1); return persist();
      case 'rule': g.rule = g.rule === el.dataset.r ? null : el.dataset.r; return persist();
      case 'pick': g.rule = el.value || null; return persist();
      case 'cite': {
        const n = +el.dataset.n;
        g.cites = g.cites.includes(n) ? g.cites.filter((x) => x !== n) : g.cites.concat(n);
        return persist();
      }
      case 'copy': {
        const n = +el.dataset.n;
        g.tokens = [...L.show(g.lines[n - 1].f).replace(/\s/g, '')]; g.caret = g.tokens.length;
        return persist();
      }
      case 'fill': {
        const f = g.feedback && g.feedback.fill;
        if (f) { g.tokens = f.tokens.slice(); g.caret = g.tokens.length; g.rule = f.rule; g.cites = f.cites.slice(); }
        return persist();
      }
      case 'add': return addLine();
      case 'undo': return undoLine();
      case 'hint': return hint();
      case 'giveup':
        if (!confirmGiveUp) { confirmGiveUp = true; return render(); }
        confirmGiveUp = false; g.revealed = true; return finish();
      case 'share': {
        const r = g.result;
        const text = `Therefore · Daily proof ${g.date}\nGrade ${r.grade} ${'★'.repeat(r.stars)}${'☆'.repeat(3 - r.stars)} · ${r.yours} lines · ${g.mistakes.length} mistakes · ${g.hints} hints`;
        const done = () => { screen.copied = true; render(); };
        try { navigator.clipboard.writeText(text).then(done, done); } catch (e) { done(); }
        return;
      }
    }
  }

  $app.addEventListener('change', (e) => {
    if (e.target.id === 'rule-select') act('pick', e.target);
  });
  $app.addEventListener('click', (e) => {
    const el = e.target.closest('[data-act]');
    if (!el || el.disabled) return;
    act(el.dataset.act, el);
  });

  // Hardware keyboard support while solving
  document.addEventListener('keydown', (e) => {
    if (screen.name !== 'play' || !game || game.done || e.metaKey || e.ctrlKey || e.altKey) return;
    const g = game;
    const k = e.key;
    let t = null;
    if (/^[pqrstu]$/i.test(k)) t = k.toUpperCase();
    else if (k === '~' || k === '!' || k === '-') t = '¬';
    else if (k === '&' || k === '^') t = '∧';
    else if (k === 'v' || k === 'V' || k === '|') t = '∨';
    else if (k === '>') t = '→';
    else if (k === '=' || k === '<') t = '↔';
    else if (k === '(' || k === ')') t = k;
    if (t) {
      e.preventDefault();
      // "->" and "<->" arrive as '-' then '>': fold them into one arrow
      if (t === '→' && g.tokens[g.caret - 1] === '¬') { g.tokens.splice(g.caret - 1, 1, '→'); return persist(); }
      if (t === '→' && g.tokens[g.caret - 1] === '↔') return;
      if (t === '¬' && g.tokens[g.caret - 1] === '↔') return;
      g.tokens.splice(g.caret, 0, t); g.caret++; return persist();
    }
    if (k === 'Backspace') { e.preventDefault(); return act('back'); }
    if (k === 'ArrowLeft') { e.preventDefault(); return act('left'); }
    if (k === 'ArrowRight') { e.preventDefault(); return act('right'); }
    if (k === 'Enter') { e.preventDefault(); return addLine(); }
  });

  if (game && !game.done) screen = { name: 'home' };
  render();

  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    try { navigator.serviceWorker.register('sw.js').catch(() => {}); } catch (e) { /* not available */ }
  }
})();
