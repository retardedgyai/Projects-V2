import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';

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
  one(p.schema_version, [2], 'schema_version must be 2 (linked authority and candidate inspection)');
  const t = object(p.task) ? p.task : {};
  const d = object(p.decision) ? p.decision : {};
  requireText(t.id, 'task.id'); requireText(t.revision, 'task.revision');
  for (const k of ['request', 'source', 'intended_use', 'next_action']) requireText(t[k], `task.${k}`);
  one(t.phase, ['representative', 'expansion', 'isolated_game', 'handoff'], 'task.phase');
  if (typeof t.game_required !== 'boolean') fail('FORMAT', 'task.game_required');
  const scope = object(t.representative) ? t.representative : {};
  requireText(scope.scope, 'representative.scope'); requireText(scope.stop_if, 'representative.stop_if');
  for (const k of ['features', 'counterexample']) requireText(t.whole_quality?.[k], `whole_quality.${k}`);
  const grounding = list(t.whole_quality?.grounding, 'whole_quality.grounding');
  requireText(t.interpretation_id, 'task.interpretation_id');
  if (!Number.isInteger(t.review_epoch) || t.review_epoch < 1) fail('FORMAT', 'task.review_epoch');
  if (!object(t.authority)) fail('FORMAT', 'task.authority');
  for (const kind of ['approved', 'rejected', 'preserve']) {
    for (const rule of list(t.authority?.[kind], `authority.${kind}`)) {
      if (!object(rule)) { fail('FORMAT', `authority.${kind} entry`); continue; }
      for (const k of ['item', 'reason', 'source']) requireText(rule[k], `authority.${kind}.${k}`);
      if (!object(rule.target)) { fail('FORMAT', `authority.${kind}.target`); continue; }
      const kinds = { approved: ['reference', 'continuation'], rejected: ['candidate', 'interpretation', 'criterion', 'reference'], preserve: ['constraint'] };
      one(rule.target.kind, kinds[kind], `authority.${kind}.target.kind`);
      requireText(rule.target.id, `authority.${kind}.target.id`);
      for (const id of list(rule.criterion_ids, `authority.${kind}.criterion_ids`)) requireText(id, `authority.${kind}.criterion_id`);
    }
  }
  const criteria = list(t.criteria, 'task.criteria');
  const affected = list(t.affected_criteria, 'task.affected_criteria');
  const refs = list(p.references, 'references');
  const evidence = list(p.evidence, 'evidence');
  const comparisons = list(d.comparisons, 'decision.comparisons');
  const issues = list(d.issues, 'decision.issues');
  const waits = list(p.waits, 'waits');
  const constraintChecks = list(d.constraint_checks, 'decision.constraint_checks');
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
    one(r.source?.kind, ['external', 'library'], `reference ${r.id} source.kind`);
    if (r.source?.kind === 'library') {
      if (!/^libfile[_-][A-Za-z0-9_-]+$/.test(r.source.library_file_id ?? '')) fail('FORMAT', `reference ${r.id} library_file_id`);
      requireText(r.source.version, `reference ${r.id} library version`);
    }
    for (const role of ['author', 'poster']) {
      if (!object(r[role])) { fail('FORMAT', `reference ${r.id} ${role}`); continue; }
      one(r[role].status, ['known', 'unknown'], `reference ${r.id} ${role}.status`);
      if (r[role].status === 'known') requireText(r[role].name, `reference ${r.id} ${role}.name`);
      if (r[role].status === 'unknown') requireText(r[role].note, `reference ${r.id} ${role}.note`);
    }
    one(r.medium, ['image', 'video', 'gameplay', 'turntable', 'document'], `reference ${r.id} medium`);
    one(r.inspected, inspections, `reference ${r.id} inspected`);
    if (typeof r.selected !== 'boolean') fail('FORMAT', `reference ${r.id} selected`);
    one(r.role, ['quality_target', 'support', 'preservation'], `reference ${r.id} role`);
    if (typeof r.required !== 'boolean') fail('FORMAT', `reference ${r.id} required`);
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
  one(failure.status, ['active', 'resolved'], 'failure.status');
  if (failure.count > 0) {
    one(failure.cause, ['reference_gap', 'misunderstanding', 'implementation', 'comparison_gap', 'unknown'], 'failure.cause');
    for (const k of ['response', 'source', 'rejected_revision', 'rejected_interpretation_id']) requireText(failure[k], `failure.${k}`);
    if (!Number.isInteger(failure.invalidated_epoch) || failure.invalidated_epoch < 1) fail('FORMAT', 'failure.invalidated_epoch');
    list(failure.criterion_ids, 'failure.criterion_ids');
    if (failure.status === 'resolved') { requireText(failure.resolution_reason, 'failure.resolution_reason'); requireText(failure.resolution_evidence_id, 'failure.resolution_evidence_id'); }
  }
  const holistic = object(d.holistic) ? d.holistic : {};
  one(holistic.status, ['unverified', 'failed', 'passed'], 'holistic.status');
  if (holistic.counterexample_present !== null && typeof holistic.counterexample_present !== 'boolean') fail('FORMAT', 'holistic.counterexample_present');
  const integration = object(d.integration) ? d.integration : {};
  one(integration.status, ['unverified', 'failed', 'passed'], 'integration.status');
  for (const e of evidence) {
    one(e.kind, ['artifact', 'saved_diff', 'technical', 'candidate_view', 'reference_view', 'game', 'integration', 'review_record'], `evidence ${e.id} kind`);
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
  for (const c of constraintChecks) {
    if (!object(c)) { fail('FORMAT', 'constraint check must be an object'); continue; }
    for (const k of ['constraint_id', 'revision']) requireText(c[k], `constraint_check.${k}`);
    one(c.status, ['unverified', 'unchanged', 'changed'], 'constraint_check.status');
  }
  if (errors.some(e => e.code === 'FORMAT')) return result();
  const needsReview = mode !== 'record' || states.aesthetic?.status === 'passed';
  const ev = new Map(evidence.map(x => [x.id, x]));
  const ref = new Map(refs.map(x => [x.id, x]));
  for (const id of affected) if (!criteria.some(c => c.id === id)) fail('CRITERION_LINK', `unknown affected criterion ${id}`);
  for (const id of failure.criterion_ids ?? []) if (!criteria.some(c => c.id === id)) fail('CRITERION_LINK', `unknown failure criterion ${id}`);
  if (!grounding.length) fail('GROUNDING_LINK', 'whole intent needs linked real-reference / criterion grounds');
  for (const g of grounding) {
    if (!object(g) || !ref.has(g.reference_id) || !criteria.some(c => c.id === g.criterion_id)) fail('GROUNDING_LINK', 'unknown whole-intent reference or criterion');
    else if (!ref.get(g.reference_id).purposes.includes(criteria.find(c => c.id === g.criterion_id).purpose)) fail('GROUNDING_LINK', 'whole-intent reference does not support this criterion');
  }
  for (const kind of ['approved', 'rejected', 'preserve']) for (const rule of t.authority[kind]) {
    for (const id of rule.criterion_ids) if (!criteria.some(c => c.id === id)) fail('AUTHORITY_LINK', `unknown criterion ${id}`);
    if (rule.target.kind === 'reference' && !ref.has(rule.target.id)) fail('AUTHORITY_LINK', `unknown reference ${rule.target.id}`);
    if (rule.target.kind === 'criterion' && !criteria.some(c => c.id === rule.target.id)) fail('AUTHORITY_LINK', `unknown criterion ${rule.target.id}`);
    if (rule.target.kind === 'continuation' && rule.target.id !== t.id) fail('AUTHORITY_LINK', 'continuation must identify this task');
    if (kind === 'approved' && rule.target.kind === 'reference') {
      const r = ref.get(rule.target.id);
      if (r && (!r.required || r.role !== 'quality_target' || r.priority !== 1)) fail('APPROVED_REFERENCE', `${r.id}: approved reference must remain required, top priority quality target`);
      if (!rule.criterion_ids.length) fail('AUTHORITY_LINK', 'approved reference needs explicit criterion scope');
    }
  }
  const checked = new Map();
  const disk = (e) => {
    if (checked.has(e.id)) return checked.get(e.id);
    try {
      const base = fs.realpathSync(root);
      if (path.isAbsolute(e.path) || e.path.includes(':') || e.path.includes('\\')) throw new Error('use portable relative path with /');
      const resolved = fs.realpathSync(path.resolve(base, e.path));
      const rel = path.relative(base, resolved);
      if (rel === '' || rel === '..' || rel.startsWith(`..${path.sep}`) || path.isAbsolute(rel)) throw new Error('evidence outside record folder');
      const stat = fs.statSync(resolved);
      if (!stat.isFile() || stat.size === 0) throw new Error('evidence must be a nonempty file');
      const actual = crypto.createHash('sha256').update(fs.readFileSync(resolved)).digest('hex');
      if (actual !== e.sha256?.toLowerCase()) { fail('HASH_MISMATCH', `evidence ${e.id}`); checked.set(e.id, false); return false; }
      checked.set(e.id, true); return true;
    } catch (error) { fail('EVIDENCE_FILE', `evidence ${e.id}: ${error.message}`); checked.set(e.id, false); return false; }
  };
  const use = (id, kind, revision = t.revision, artifactHash = t.artifact?.sha256) => {
    const e = ev.get(id);
    if (!e) { fail('EVIDENCE_MISSING', `${kind}: ${id ?? '(unset)'}`); return null; }
    if (e.kind !== kind) fail('EVIDENCE_KIND', `${id}: expected ${kind}`);
    if (e.revision !== revision) fail('STALE_REVISION', `${id}: expected ${revision}`);
    if (!disk(e)) return null;
    if (!['reference_view', 'artifact', 'review_record'].includes(kind) && e.artifact_sha256 !== artifactHash) fail('ARTIFACT_LINK', `${id}: different saved artifact`);
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
    const sourceKey = r => r.source.kind === 'library' ? `library:${r.source.library_file_id}` : r.original_url;
    if (new Set(selected.map(sourceKey).filter(Boolean)).size < 2) fail('DISTINCT_REFERENCES', 'multiple IDs for the same source do not establish multiple real works');
    for (const r of selected) {
      if (r.source.kind === 'external' && !r.original_url) fail('REFERENCE_URL', `${r.id}: original URL missing`);
      if (r.source.kind === 'library' && r.source.version !== r.source_version) fail('REFERENCE_LINK', `${r.id}: Library version differs from inspected source`);
      if (r.inspected === 'unconfirmed') fail('UNSEEN_REFERENCE', `${r.id}: not actually inspected`);
      if (!r.observed_features?.length) fail('REFERENCE_OBSERVATION', `${r.id}: observed features missing`);
      if (!comparisons.some(x => x.reference_id === r.id)) fail('REFERENCE_UNUSED', `${r.id}: selected but not used in actual comparison`);
    }
    for (const r of refs.filter(x => x.required)) if (!r.selected) fail('REQUIRED_REFERENCE', `${r.id}: required reference excluded`);
    for (const g of grounding) if (object(g) && (!ref.get(g.reference_id)?.selected || !comparisons.some(c => c.reference_id === g.reference_id && c.criterion_id === g.criterion_id))) fail('GROUNDING_COMPARISON', 'whole intent grounds must be used in matching actual comparison');
    for (const rule of t.authority.approved.filter(x => x.target.kind === 'reference')) {
      const r = ref.get(rule.target.id);
      if (!r?.selected) fail('REQUIRED_REFERENCE', `${rule.target.id}: approved reference excluded`);
      for (const id of rule.criterion_ids) if (!comparisons.some(c => c.reference_id === rule.target.id && c.criterion_id === id)) fail('APPROVED_COMPARISON', `${rule.target.id}: missing approved comparison for ${id}`);
    }
    for (const c of comparisons) {
      const criterion = criteria.find(x => x.id === c.criterion_id);
      const r = ref.get(c.reference_id);
      if (!criterion || !r || !r.selected) { fail('COMPARISON_LINK', `invalid criterion/reference ${c.criterion_id}/${c.reference_id}`); continue; }
      if (!r.purposes.includes(criterion.purpose)) fail('REFERENCE_PURPOSE', `${r.id}: does not support ${criterion.purpose}`);
      const adequate = { image: ['image', 'video_excerpt', 'full_timeline'], video_excerpt: ['video_excerpt', 'full_timeline'], full_timeline: ['full_timeline'], document: ['document'] };
      if (!adequate[criterion.required_inspection]?.includes(r.inspected)) fail('INSPECTION_SCOPE', `${r.id}: expected ${criterion.required_inspection}`);
      if (criterion.purpose === 'motion' && !['video', 'gameplay'].includes(r.medium)) fail('MOTION_SOURCE', `${r.id}: not a combat motion source`);
      let candidateRevision = t.revision, candidateHash = t.artifact?.sha256;
      if (c.reuse) {
        if (affected.includes(c.criterion_id)) fail('REUSE_AFFECTED', `${c.criterion_id}: changed criterion cannot reuse old review`);
        if (!text(c.reuse.reason) || !text(c.reuse.from_revision) || c.reuse.from_revision === t.revision) fail('REUSE_PROOF', 'reuse needs earlier revision and unchanged reason');
        const prior = use(c.reuse.prior_review_evidence_id, 'review_record', c.reuse.from_revision);
        const unchanged = use(c.reuse.unchanged_evidence_id, 'saved_diff');
        if (!Array.isArray(unchanged?.unchanged_criteria) || !unchanged.unchanged_criteria.includes(c.criterion_id)) fail('REUSE_PROOF', `${c.criterion_id}: saved difference must attest unaffected criterion`);
        if (prior) {
          try {
            const old = JSON.parse(fs.readFileSync(path.resolve(root, prior.path), 'utf8').replace(/^\uFEFF/, ''));
            const match = old.decision?.comparisons?.find(x => x.criterion_id === c.criterion_id && x.reference_id === c.reference_id);
            const oldRef = old.references?.find(x => x.id === r.id);
            const oldCriterion = old.task?.criteria?.find(x => x.id === c.criterion_id);
            const oldWhole = old.decision?.holistic;
            const oldVisual = old.decision?.states?.aesthetic;
            const keys = ['candidate_evidence_id', 'reference_evidence_id', 'candidate_conditions', 'reference_conditions', 'difference', 'verdict', 'reviewer'];
            if (old.task?.id !== t.id || old.task?.revision !== c.reuse.from_revision || old.decision?.revision !== c.reuse.from_revision || old.decision?.states?.aesthetic?.status !== 'passed'
                || oldWhole?.status !== 'passed' || oldWhole?.counterexample_present !== false || oldWhole?.revision !== c.reuse.from_revision || oldWhole?.review_epoch !== old.task?.review_epoch
                || !text(oldWhole?.reason) || !Array.isArray(oldWhole?.evidence_ids) || !oldWhole.evidence_ids.length || !text(oldVisual?.reason) || !text(oldVisual?.reviewer) || oldVisual?.review_epoch !== old.task?.review_epoch || !match || match.verdict !== 'acceptable'
                || oldRef?.source_version !== r.source_version || !isDeepStrictEqual(oldRef?.source, r.source) || oldRef?.original_url !== r.original_url
                || !isDeepStrictEqual(oldCriterion, criterion) || keys.some(k => !isDeepStrictEqual(match[k], c[k]))) fail('REUSE_PROOF', `${c.criterion_id}: prior acceptable criterion, source and comparison must match exactly`);
            if (failure.count > 0 && (!failure.criterion_ids.length || failure.criterion_ids.includes(c.criterion_id)) && old.task?.review_epoch <= failure.invalidated_epoch) fail('REUSE_INVALIDATED', `${c.criterion_id}: rejected interpretation cannot be reused`);
            for (const rule of t.authority.rejected) {
              if (rule.criterion_ids.length && !rule.criterion_ids.includes(c.criterion_id)) continue;
              const { kind, id } = rule.target;
              if ((kind === 'candidate' && id === c.reuse.from_revision) || (kind === 'interpretation' && id === old.task?.interpretation_id) || (kind === 'criterion' && id === c.criterion_id) || (kind === 'reference' && id === c.reference_id)) fail('REUSE_REJECTED', `${c.criterion_id}: authoritative rejection also invalidates historical comparison`);
            }
            candidateRevision = c.reuse.from_revision; candidateHash = old.task?.artifact?.sha256;
            if (!/^[a-f0-9]{64}$/i.test(candidateHash ?? '')) fail('REUSE_PROOF', 'prior artifact hash missing');
            for (const id of oldWhole?.evidence_ids ?? []) {
              const e = ev.get(id), historical = old.evidence?.find(x => x.id === id);
              if (!e || !historical || !['candidate_view', 'integration'].includes(e.kind) || e.sha256 !== historical.sha256 || historical.revision !== c.reuse.from_revision || historical.artifact_sha256 !== candidateHash) fail('REUSE_PROOF', 'historical whole pass lacks linked observation evidence');
              else use(id, e.kind, c.reuse.from_revision, candidateHash);
            }
          } catch { fail('REUSE_PROOF', 'prior review record is missing or unreadable'); }
        }
      }
      const candidate = use(c.candidate_evidence_id, 'candidate_view', candidateRevision, candidateHash);
      if (candidate) {
        if (!adequate[criterion.required_inspection]?.includes(candidate.inspected) || !text(candidate.inspected_range)) fail('CANDIDATE_SCOPE', `${candidate.id}: candidate must cover ${criterion.required_inspection}`);
        if (criterion.purpose === 'motion' && !['video', 'gameplay'].includes(candidate.medium)) fail('CANDIDATE_MOTION', `${candidate.id}: still captures cannot prove full motion`);
      }
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
  if (holistic.status === 'passed') {
    if (holistic.counterexample_present !== false) fail('HOLISTIC_GAP', 'passed whole review cannot contain the counterexample');
    if (holistic.revision !== t.revision || holistic.review_epoch !== t.review_epoch) fail('STALE_REVIEW', 'passed whole review must use current revision / epoch');
    requireText(holistic.reason, 'holistic.reason');
    const ids = list(holistic.evidence_ids, 'holistic.evidence_ids');
    if (!ids.length) fail('HOLISTIC_EVIDENCE', 'passed whole review needs actual whole-observation evidence');
    const artifact = use(t.artifact?.evidence_id, 'artifact');
    if (!artifact || artifact.sha256 !== t.artifact?.sha256) fail('ARTIFACT_LINK', 'passed whole review must identify saved artifact');
    for (const id of ids) {
      const e = ev.get(id);
      if (!e || !['candidate_view', 'integration'].includes(e.kind)) fail('HOLISTIC_EVIDENCE', `invalid whole evidence ${id}`);
      else use(id, e.kind);
    }
  }
  const needsExpansion = ['expand', 'handoff'].includes(mode) || states.aesthetic?.status === 'passed';
  if (needsExpansion || holistic.status === 'passed' || integration.status === 'passed') {
    if (failure.count > 0 && failure.status === 'active' && failure.cause === 'unknown') fail('FAILURE_UNKNOWN', 'active failure cause is unresolved; epoch changes are not resolution');
    if (failure.count > 0 && failure.status === 'resolved') {
      const e = ev.get(failure.resolution_evidence_id);
      if (!e || !['candidate_view', 'technical', 'game', 'integration'].includes(e.kind)) fail('FAILURE_RESOLUTION', 'resolved failure needs current proof');
      else use(e.id, e.kind);
    }
    for (const rule of t.authority.rejected) {
      const { kind, id } = rule.target;
      const contradicted = (kind === 'candidate' && id === t.revision) || (kind === 'interpretation' && id === t.interpretation_id)
        || (kind === 'criterion' && comparisons.some(c => c.criterion_id === id && c.verdict === 'acceptable'))
        || (kind === 'reference' && ref.get(id)?.selected);
      if (contradicted) fail('AUTHORITY_REJECTION', `${kind} ${id}: explicit rejection contradicts completion`);
    }
    for (const rule of t.authority.preserve) {
      const checks = constraintChecks.filter(c => c.constraint_id === rule.target.id);
      if (checks.length !== 1 || checks[0].revision !== t.revision || checks[0].status !== 'unchanged') fail('PRESERVE_CONFLICT', `${rule.target.id}: current preservation evidence required; changed/unverified cannot complete`);
      else {
        const e = ev.get(checks[0].evidence_id);
        if (!e || !['artifact', 'candidate_view', 'technical', 'game', 'integration'].includes(e.kind)) fail('PRESERVE_EVIDENCE', `${rule.target.id}: missing preservation evidence`);
        else use(e.id, e.kind);
      }
    }
  }
  if (needsExpansion) {
    if (user.status === 'rejected') fail('USER_REJECTED', 'current user rejection cannot be overridden by local pass');
    if (states.aesthetic?.status !== 'passed') fail('AESTHETIC_UNVERIFIED', 'representative visual review not recorded as acceptable');
    if (!comparisons.length || comparisons.some(x => x.verdict !== 'acceptable')) fail('AESTHETIC_GAP', 'representative comparison still has a gap');
    if (states.technical?.status !== 'passed') fail('TECHNICAL_UNVERIFIED', 'technical check must separately pass');
    if (holistic.status !== 'passed' || holistic.counterexample_present !== false) fail('HOLISTIC_GAP', 'whole defining features / counterexample check is unresolved');
    requireText(holistic.reason, 'holistic.reason (whole observation, not one isolated criterion)');
    if (holistic.revision !== t.revision || holistic.review_epoch !== t.review_epoch || states.aesthetic?.review_epoch !== t.review_epoch) fail('STALE_REVIEW', 'whole / aesthetic review epoch differs');
    if (failure.count > 0 && (t.revision === failure.rejected_revision || t.interpretation_id === failure.rejected_interpretation_id || t.review_epoch <= failure.invalidated_epoch)) fail('REJECTION_REOPEN', 'explicit rejection invalidates the relevant interpretation and old pass');
    for (const r of refs.filter(x => x.selected && comparisons.some(c => c.reference_id === x.id && !c.reuse))) if (r.analysis_epoch !== t.review_epoch) fail('STALE_ANALYSIS', `${r.id}: reopen only affected interpretation; unchanged comparisons may reuse proof`);
    if (failure.count >= 2 || failure.severe) {
      requireText(failure.independent_counterargument, 'failure.independent_counterargument');
      requireText(failure.independent_reviewer, 'failure.independent_reviewer');
      if (failure.independent_reviewer === states.aesthetic?.reviewer) fail('INDEPENDENT_REVIEW', 'repeated/major failure needs another perspective seeking strongest counterexample');
    }
  }
  if (integration.status === 'passed' || mode === 'handoff') {
    const artifact = use(t.artifact?.evidence_id, 'artifact');
    if (!artifact || artifact.sha256 !== t.artifact?.sha256) fail('ARTIFACT_LINK', 'whole integration must identify saved artifact');
    if (integration.status !== 'passed' || integration.scope !== 'whole_in_use' || integration.normal_display !== true) fail('INTEGRATION_MISSING', 'whole / full motion in actual use at normal display required');
    if (integration.revision !== t.revision) fail('STALE_REVIEW', 'whole integration must use current revision');
    requireText(integration.reason, 'integration.reason'); requireText(integration.reviewer, 'integration.reviewer');
    const whole = use(integration.evidence_id, 'integration');
    if (whole) {
      const motionRequired = criteria.some(c => c.purpose === 'motion' || c.required_inspection === 'full_timeline');
      const coverage = motionRequired ? 'full_motion' : 'whole_result';
      if (whole.scope !== 'whole_in_use' || whole.coverage !== coverage || !text(whole.inspected_range)) fail('INTEGRATION_COVERAGE', 'integration evidence itself must cover the whole result / full motion in actual use');
      const inspectedByMedium = { image: ['image'], document: ['document'], video: ['video_excerpt', 'full_timeline'], gameplay: ['video_excerpt', 'full_timeline'] };
      if (!inspectedByMedium[whole.medium]?.includes(whole.inspected)) fail('INTEGRATION_MEDIA', 'integration evidence must declare its actual medium and inspected range');
      if (motionRequired && (!['video', 'gameplay'].includes(whole.medium) || whole.inspected !== 'full_timeline')) fail('INTEGRATION_MOTION', 'representative motion evidence cannot substitute for full-motion integration video');
    }
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
