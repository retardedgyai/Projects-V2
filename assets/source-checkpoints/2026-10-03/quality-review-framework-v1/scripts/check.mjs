import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

// Record checks only. No image scoring, rendering, network, process launch or writes.
export function check(p, { root = '.', mode = 'record', now = new Date() } = {}) {
  const errors = [];
  const fail = (code, message) => errors.push({ code, message });
  const object = (x) => x && typeof x === 'object' && !Array.isArray(x);
  const text = (x) => typeof x === 'string' && x.trim().length > 0;
  const one = (x, values, label) => { if (!values.includes(x)) fail('FORMAT', label); };
  const requireText = (x, label) => { if (!text(x)) fail('FORMAT', label); };
  const list = (x, label) => { if (!Array.isArray(x)) { fail('FORMAT', label); return []; } return x; };
  if (!['record', 'review', 'expand', 'handoff'].includes(mode)) throw new Error('Unknown mode');
  if (!object(p)) return { mode, record_checks: 'incomplete', aesthetic_certified: false, errors: [{ code: 'FORMAT', message: 'packet must be an object' }] };
  one(p.schema_version, [1], 'schema_version must be 1');
  const t = object(p.task) ? p.task : {};
  const d = object(p.decision) ? p.decision : {};
  requireText(t.id, 'task.id'); requireText(t.revision, 'task.revision');
  for (const k of ['request', 'source', 'intended_use', 'next_action']) requireText(t[k], `task.${k}`);
  one(t.phase, ['representative', 'expansion', 'isolated_game', 'handoff'], 'task.phase');
  if (typeof t.game_required !== 'boolean') fail('FORMAT', 'task.game_required');
  const scope = object(t.representative) ? t.representative : {};
  requireText(scope.scope, 'representative.scope'); requireText(scope.stop_if, 'representative.stop_if');
  for (const k of ['features', 'grounding', 'counterexample']) requireText(t.whole_quality?.[k], `whole_quality.${k}`);
  requireText(t.interpretation_id, 'task.interpretation_id');
  if (!Number.isInteger(t.review_epoch) || t.review_epoch < 1) fail('FORMAT', 'task.review_epoch');
  if (!object(t.authority)) fail('FORMAT', 'task.authority');
  for (const kind of ['approved', 'rejected', 'preserve']) {
    for (const rule of list(t.authority?.[kind], `authority.${kind}`)) {
      if (!object(rule)) { fail('FORMAT', `authority.${kind} entry`); continue; }
      for (const k of ['item', 'reason', 'source']) requireText(rule[k], `authority.${kind}.${k}`);
    }
  }
  const criteria = list(t.criteria, 'task.criteria');
  const refs = list(p.references, 'references');
  const evidence = list(p.evidence, 'evidence');
  const comparisons = list(d.comparisons, 'decision.comparisons');
  const issues = list(d.issues, 'decision.issues');
  const waits = list(p.waits, 'waits');
  const unique = (rows, label) => {
    const ids = new Set();
    for (const x of rows) {
      if (!object(x)) { fail('FORMAT', `${label} entry`); continue; }
      requireText(x.id, `${label}.id`);
      if (ids.has(x.id)) fail('DUPLICATE_ID', `${label}: ${x.id}`);
      ids.add(x.id);
    }
  };
  unique(criteria, 'criteria'); unique(refs, 'references'); unique(evidence, 'evidence'); unique(issues, 'issues'); unique(waits, 'waits');
  // Malformed rows must not crash or be skipped into an apparent success.
  if ([...criteria, ...refs, ...evidence, ...comparisons, ...issues, ...waits].some(x => !object(x))) {
    fail('FORMAT', 'all entries must be objects');
    return result();
  }
  const purposes = ['shape', 'material', 'motion', 'system'];
  const inspections = ['unconfirmed', 'image', 'video_excerpt', 'full_timeline', 'document'];
  for (const c of criteria) {
    one(c.purpose, purposes, `criterion ${c.id} purpose`);
    one(c.required_inspection, inspections.filter(x => x !== 'unconfirmed'), `criterion ${c.id} inspection`);
    for (const k of ['target', 'comparison', 'failure']) requireText(c[k], `criterion ${c.id} ${k}`);
    if (c.purpose === 'motion' && c.required_inspection !== 'full_timeline') fail('MOTION_SCOPE', `criterion ${c.id} needs full_timeline`);
  }
  for (const r of refs) {
    for (const k of ['title', 'source_locator', 'reason']) requireText(r[k], `reference ${r.id} ${k}`);
    if (r.original_url !== null && !(text(r.original_url) && /^https?:\/\/\S+$/i.test(r.original_url))) fail('FORMAT', `reference ${r.id} original_url`);
    for (const role of ['author', 'poster']) {
      if (!object(r[role])) { fail('FORMAT', `reference ${r.id} ${role}`); continue; }
      one(r[role].status, ['known', 'unknown'], `reference ${r.id} ${role}.status`);
      if (r[role].status === 'known') requireText(r[role].name, `reference ${r.id} ${role}.name`);
      if (r[role].status === 'unknown') requireText(r[role].note, `reference ${r.id} ${role}.note`);
    }
    one(r.medium, ['image', 'video', 'gameplay', 'turntable', 'document'], `reference ${r.id} medium`);
    one(r.inspected, inspections, `reference ${r.id} inspected`);
    if (typeof r.selected !== 'boolean') fail('FORMAT', `reference ${r.id} selected`);
    if (!Number.isInteger(r.priority) || r.priority < 1) fail('FORMAT', `reference ${r.id} priority`);
    for (const purpose of list(r.purposes, `reference ${r.id} purposes`)) one(purpose, purposes, `reference ${r.id} purpose`);
    list(r.observed_features, `reference ${r.id} observed_features`);
    if (r.inspected !== 'unconfirmed') {
      requireText(r.inspected_range, `reference ${r.id} inspected_range`);
      requireText(r.source_version, `reference ${r.id} source_version`);
    }
    if (r.purposes?.includes('motion') && (r.medium === 'turntable' || r.medium === 'image' || r.medium === 'document')) fail('MOTION_SOURCE', `reference ${r.id} is not a motion source`);
  }
  const states = object(d.states) ? d.states : {};
  for (const axis of ['technical', 'aesthetic', 'game']) {
    const s = object(states[axis]) ? states[axis] : {};
    one(s.status, ['unverified', 'failed', 'passed'], `states.${axis}.status`);
    if (s.status === 'passed') { requireText(s.reviewer, `states.${axis}.reviewer`); requireText(s.reason, `states.${axis}.reason`); }
  }
  const user = object(states.user) ? states.user : {};
  one(user.status, ['pending', 'continue', 'adopted', 'rejected'], 'states.user.status');
  if (user.status !== 'pending') requireText(user.source, 'states.user.source (explicit user instruction)');
  const failure = object(d.failure) ? d.failure : {};
  if (!Number.isInteger(failure.count) || failure.count < 0) fail('FORMAT', 'failure.count');
  if (typeof failure.severe !== 'boolean') fail('FORMAT', 'failure.severe');
  if (failure.count > 0) {
    one(failure.cause, ['reference_gap', 'misunderstanding', 'implementation', 'comparison_gap', 'unknown'], 'failure.cause');
    for (const k of ['response', 'source', 'rejected_revision', 'rejected_interpretation_id']) requireText(failure[k], `failure.${k}`);
    if (!Number.isInteger(failure.invalidated_epoch) || failure.invalidated_epoch < 1) fail('FORMAT', 'failure.invalidated_epoch');
  }
  const holistic = object(d.holistic) ? d.holistic : {};
  one(holistic.status, ['unverified', 'failed', 'passed'], 'holistic.status');
  if (holistic.counterexample_present !== null && typeof holistic.counterexample_present !== 'boolean') fail('FORMAT', 'holistic.counterexample_present');
  const integration = object(d.integration) ? d.integration : {};
  one(integration.status, ['unverified', 'failed', 'passed'], 'integration.status');
  for (const e of evidence) {
    one(e.kind, ['artifact', 'saved_diff', 'technical', 'candidate_view', 'reference_view', 'game', 'integration'], `evidence ${e.id} kind`);
    for (const k of ['path', 'revision', 'captured_at']) requireText(e[k], `evidence ${e.id} ${k}`);
    if (!/^[a-f0-9]{64}$/i.test(e.sha256 ?? '')) fail('FORMAT', `evidence ${e.id} sha256`);
    if (!Number.isFinite(Date.parse(e.captured_at))) fail('FORMAT', `evidence ${e.id} timestamp`);
  }
  for (const i of issues) {
    one(i.severity, ['blocking', 'major', 'minor'], `issue ${i.id} severity`);
    one(i.status, ['open', 'resolved'], `issue ${i.id} status`);
    requireText(i.description, `issue ${i.id} description`);
    requireText(i.next_action, `issue ${i.id} next_action`);
    if (i.status === 'resolved') requireText(i.resolution_evidence_id, `issue ${i.id} resolution_evidence_id`);
  }
  for (const w of waits) {
    one(w.status, ['pending', 'recovered', 'stopped', 'denied'], `wait ${w.id} status`);
    for (const k of ['operation', 'recovery', 'started_at', 'deadline_at']) requireText(w[k], `wait ${w.id} ${k}`);
    const start = Date.parse(w.started_at), deadline = Date.parse(w.deadline_at);
    if (!Number.isFinite(start) || !Number.isFinite(deadline) || deadline <= start) fail('WAIT_BOUND', `wait ${w.id} deadline must follow start`);
    if (!Number.isInteger(w.attempts) || w.attempts < 0 || !Number.isInteger(w.max_attempts) || w.max_attempts < 1) fail('WAIT_BOUND', `wait ${w.id} attempt bounds`);
    if (w.attempts > w.max_attempts) fail('WAIT_BOUND', `wait ${w.id} attempts exceeded`);
    if (typeof w.bypass_access_denial !== 'boolean' || w.bypass_access_denial) fail('ACCESS_DENIAL', `wait ${w.id} cannot bypass access denial`);
    if (w.status === 'pending' && (now.getTime() >= deadline || w.attempts >= w.max_attempts)) fail('WAIT_EXPIRED', `wait ${w.id}: execute recovery or stop`);
  }
  if (p.effort_minutes !== null) {
    if (!object(p.effort_minutes)) fail('FORMAT', 'effort_minutes');
    else for (const k of ['research', 'management', 'production', 'verification']) {
      if (!Number.isFinite(p.effort_minutes[k]) || p.effort_minutes[k] < 0) fail('FORMAT', `effort_minutes.${k}`);
    }
  }
  for (const c of comparisons) {
    for (const k of ['criterion_id', 'reference_id', 'candidate_evidence_id', 'reference_evidence_id', 'difference', 'reviewer']) requireText(c[k], `comparison.${k}`);
    one(c.verdict, ['gap', 'acceptable'], 'comparison.verdict');
  }
  if (errors.some(e => e.code === 'FORMAT')) return result();
  const needsReview = mode !== 'record' || states.aesthetic?.status === 'passed';
  const ev = new Map(evidence.map(x => [x.id, x]));
  const ref = new Map(refs.map(x => [x.id, x]));
  const checked = new Set();
  const disk = (e) => {
    if (checked.has(e.id)) return;
    checked.add(e.id);
    try {
      const base = fs.realpathSync(root);
      if (path.isAbsolute(e.path) || e.path.includes(':') || e.path.includes('\\')) throw new Error('use portable relative path with /');
      const resolved = fs.realpathSync(path.resolve(base, e.path));
      const rel = path.relative(base, resolved);
      if (rel === '' || rel === '..' || rel.startsWith(`..${path.sep}`) || path.isAbsolute(rel)) throw new Error('evidence outside record folder');
      const stat = fs.statSync(resolved);
      if (!stat.isFile() || stat.size === 0) throw new Error('evidence must be a nonempty file');
      const actual = crypto.createHash('sha256').update(fs.readFileSync(resolved)).digest('hex');
      if (actual !== e.sha256?.toLowerCase()) fail('HASH_MISMATCH', `evidence ${e.id}`);
    } catch (error) { fail('EVIDENCE_FILE', `evidence ${e.id}: ${error.message}`); }
  };
  const use = (id, kind, revision = t.revision) => {
    const e = ev.get(id);
    if (!e) { fail('EVIDENCE_MISSING', `${kind}: ${id ?? '(unset)'}`); return null; }
    if (e.kind !== kind) fail('EVIDENCE_KIND', `${id}: expected ${kind}`);
    if (e.revision !== revision) fail('STALE_REVISION', `${id}: expected ${revision}`);
    disk(e);
    if (kind !== 'reference_view' && kind !== 'artifact' && e.artifact_sha256 !== t.artifact?.sha256) fail('ARTIFACT_LINK', `${id}: different saved artifact`);
    return e;
  };
  // A claimed technical/game pass must always be backed, even in record mode.
  if (needsReview || states.technical?.status === 'passed' || states.game?.status === 'passed') {
    const artifact = use(t.artifact?.evidence_id, 'artifact');
    if (!artifact || artifact.sha256 !== t.artifact?.sha256) fail('ARTIFACT_LINK', 'task.artifact does not match saved artifact');
  }
  if (needsReview || states.technical?.status === 'passed') {
    const tech = use(t.latest_validation_id, 'technical');
    const saved = use(t.saved_diff_id, 'saved_diff');
    if (tech && saved && Date.parse(tech.captured_at) < Date.parse(saved.captured_at)) fail('STALE_VALIDATION', 'validation predates saved difference');
    if (states.technical?.status === 'passed' && states.technical.evidence_id !== t.latest_validation_id) fail('STALE_VALIDATION', 'technical status does not cite latest validation');
  }
  if (needsReview) {
    if (d.revision !== t.revision) fail('STALE_REVIEW', 'decision revision must match current task');
    requireText(d.biggest_gap, 'decision.biggest_gap'); requireText(d.next_action, 'decision.next_action');
    if (!criteria.length) fail('CRITERIA_MISSING', 'representative criteria required');
    const selected = refs.filter(x => x.selected);
    if (selected.length < 2) fail('REFERENCE_COUNT', 'select multiple real references, each for a suitable role');
    if (new Set(selected.map(x => x.original_url).filter(Boolean)).size < 2) fail('DISTINCT_REFERENCES', 'multiple IDs for the same source do not establish multiple real works');
    for (const r of selected) {
      if (!r.original_url) fail('REFERENCE_URL', `${r.id}: original URL missing`);
      if (r.inspected === 'unconfirmed') fail('UNSEEN_REFERENCE', `${r.id}: not actually inspected`);
      if (!r.observed_features?.length) fail('REFERENCE_OBSERVATION', `${r.id}: observed features missing`);
      if (!comparisons.some(x => x.reference_id === r.id)) fail('REFERENCE_UNUSED', `${r.id}: selected but not used in actual comparison`);
    }
    for (const c of comparisons) {
      const criterion = criteria.find(x => x.id === c.criterion_id);
      const r = ref.get(c.reference_id);
      if (!criterion || !r || !r.selected) { fail('COMPARISON_LINK', `invalid criterion/reference ${c.criterion_id}/${c.reference_id}`); continue; }
      if (!r.purposes.includes(criterion.purpose)) fail('REFERENCE_PURPOSE', `${r.id}: does not support ${criterion.purpose}`);
      const adequate = { image: ['image', 'video_excerpt', 'full_timeline'], video_excerpt: ['video_excerpt', 'full_timeline'], full_timeline: ['full_timeline'], document: ['document'] };
      if (!adequate[criterion.required_inspection]?.includes(r.inspected)) fail('INSPECTION_SCOPE', `${r.id}: expected ${criterion.required_inspection}`);
      if (criterion.purpose === 'motion' && !['video', 'gameplay'].includes(r.medium)) fail('MOTION_SOURCE', `${r.id}: not a combat motion source`);
      use(c.candidate_evidence_id, 'candidate_view');
      const capture = use(c.reference_evidence_id, 'reference_view', r.source_version);
      if (capture && capture.reference_id !== r.id) fail('REFERENCE_LINK', `${capture.id}: reference identity differs`);
      const conditions = ['camera', 'size', 'lighting', 'state', 'display'];
      if (criterion.purpose === 'system') conditions.push('game_version', 'level', 'budget', 'equipment', 'objective', 'encounter');
      for (const k of conditions) {
        const a = c.candidate_conditions?.[k], b = c.reference_conditions?.[k];
        if (!text(a) || !text(b) || a !== b) fail('COMPARISON_CONDITION', `${c.criterion_id}: ${k} must match`);
      }
      if (c.candidate_conditions?.display !== 'normal') fail('NORMAL_DISPLAY', `${c.criterion_id}: normal display required`);
    }
    for (const c of criteria) if (!comparisons.some(x => x.criterion_id === c.id)) fail('COMPARISON_MISSING', `criterion ${c.id}`);
    for (const i of issues.filter(x => x.status === 'resolved')) {
      const e = ev.get(i.resolution_evidence_id);
      if (!e || !['candidate_view', 'technical', 'game'].includes(e.kind)) fail('RESOLUTION_EVIDENCE', `issue ${i.id}`);
      else use(e.id, e.kind);
    }
    for (const i of issues.filter(x => ['blocking', 'major'].includes(x.severity) && x.status !== 'resolved')) fail('UNRESOLVED_MAJOR', `issue ${i.id}: ${i.description}`);
  }
  if (states.game?.status === 'passed') use(states.game.evidence_id, 'game');
  const needsExpansion = ['expand', 'handoff'].includes(mode) || states.aesthetic?.status === 'passed';
  if (needsExpansion) {
    if (user.status === 'rejected') fail('USER_REJECTED', 'current user rejection cannot be overridden by local pass');
    if (states.aesthetic?.status !== 'passed') fail('AESTHETIC_UNVERIFIED', 'representative visual review not recorded as acceptable');
    if (!comparisons.length || comparisons.some(x => x.verdict !== 'acceptable')) fail('AESTHETIC_GAP', 'representative comparison still has a gap');
    if (states.technical?.status !== 'passed') fail('TECHNICAL_UNVERIFIED', 'technical check must separately pass');
    if (holistic.status !== 'passed' || holistic.counterexample_present !== false) fail('HOLISTIC_GAP', 'whole defining features / counterexample check is unresolved');
    requireText(holistic.reason, 'holistic.reason (whole observation, not one isolated criterion)');
    if (holistic.revision !== t.revision || holistic.review_epoch !== t.review_epoch || states.aesthetic?.review_epoch !== t.review_epoch) fail('STALE_REVIEW', 'whole / aesthetic review epoch differs');
    if (failure.count > 0 && (t.revision === failure.rejected_revision || t.interpretation_id === failure.rejected_interpretation_id || t.review_epoch <= failure.invalidated_epoch)) fail('REJECTION_REOPEN', 'explicit rejection invalidates the relevant interpretation and old pass');
    for (const r of refs.filter(x => x.selected)) if (r.analysis_epoch !== t.review_epoch) fail('STALE_ANALYSIS', `${r.id}: reuse bytes but reopen invalidated interpretation`);
    if (failure.count >= 2 || failure.severe) {
      requireText(failure.independent_counterargument, 'failure.independent_counterargument');
      requireText(failure.independent_reviewer, 'failure.independent_reviewer');
      if (failure.independent_reviewer === states.aesthetic?.reviewer) fail('INDEPENDENT_REVIEW', 'repeated/major failure needs another perspective seeking strongest counterexample');
    }
  }
  if (integration.status === 'passed' || mode === 'handoff') {
    if (integration.status !== 'passed' || integration.scope !== 'whole_in_use' || integration.normal_display !== true) fail('INTEGRATION_MISSING', 'whole / full motion in actual use at normal display required');
    if (integration.revision !== t.revision) fail('STALE_REVIEW', 'whole integration must use current revision');
    requireText(integration.reason, 'integration.reason'); requireText(integration.reviewer, 'integration.reviewer');
    use(integration.evidence_id, 'integration');
  }
  if (mode === 'handoff' && t.game_required && states.game?.status !== 'passed') fail('GAME_UNVERIFIED', 'required isolated game test not recorded');
  return result();
  function result() {
    return { task_id: t.id, mode, record_checks: errors.length ? 'incomplete' : 'complete', aesthetic_certified: false, user_status: d.states?.user?.status ?? 'pending', errors };
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2);
    if (![1, 3].includes(args.length) || (args.length === 3 && args[1] !== '--mode')) throw new Error('Usage: node scripts/check.mjs packet.json [--mode record|review|expand|handoff]');
    const filename = path.resolve(args[0]);
    const report = check(JSON.parse(fs.readFileSync(filename, 'utf8').replace(/^\uFEFF/, '')), { root: path.dirname(filename), mode: args[2] ?? 'record' });
    console.log(JSON.stringify(report, null, 2));
    process.exitCode = report.errors.length ? 1 : 0;
  } catch (error) {
    console.error(JSON.stringify({ record_checks: 'error', aesthetic_certified: false, error: error.message }));
    process.exitCode = 2;
  }
}
