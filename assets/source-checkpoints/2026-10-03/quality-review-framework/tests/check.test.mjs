import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { check } from '../scripts/check.mjs';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const tempBase = fs.realpathSync(os.tmpdir());
const temp = fs.mkdtempSync(path.join(tempBase, 'projects-review-test-'));
let seq = 0;
const hash = (content) => crypto.createHash('sha256').update(content).digest('hex');
const clone = (x) => JSON.parse(JSON.stringify(x));
const template = JSON.parse(fs.readFileSync(path.join(project, 'templates/packet.json'), 'utf8'));
const now = new Date('2026-10-03T12:00:00Z');
after(() => {
  const absolute = fs.realpathSync(temp);
  assert.equal(path.dirname(absolute), tempBase);
  assert.ok(path.basename(absolute).startsWith('projects-review-test-'));
  fs.rmSync(absolute, { recursive: true, force: true });
});

// Synthetic byte fixtures validate record guards only; they are not aesthetic evidence.
function fixture() {
  const root = path.join(temp, String(seq++)); fs.mkdirSync(root);
  const p = clone(template), revision = p.task.revision;
  function add(id, kind, content, time = '2026-10-03T10:00:00Z', rev = revision) {
    const filename = `${id}.txt`; fs.writeFileSync(path.join(root, filename), content);
    const e = { id, kind, path: filename, sha256: hash(content), revision: rev, captured_at: time };
    if (kind === 'candidate_view') Object.assign(e, { medium: 'image', inspected: 'image', inspected_range: 'whole representative at normal display' });
    if (kind !== 'artifact' && kind !== 'reference_view') e.artifact_sha256 = p.task.artifact.sha256;
    p.evidence.push(e); return e;
  }
  const artifact = add('artifact', 'artifact', 'synthetic saved representative');
  p.task.artifact = { evidence_id: artifact.id, sha256: artifact.sha256 };
  p.task.saved_diff_id = add('diff', 'saved_diff', 'synthetic saved difference').id;
  p.task.latest_validation_id = add('tech', 'technical', 'synthetic technical report', '2026-10-03T10:01:00Z').id;
  add('candidate', 'candidate_view', 'synthetic normal-display capture', '2026-10-03T10:02:00Z');
  const r = p.references[0];
  Object.assign(r, { selected: true, original_url: 'https://example.org/original/1', inspected: 'image', inspected_range: 'full image', source_version: 'ref-1', observed_features: ['observed defining shape'], analysis_epoch: 1 });
  const r2 = clone(r); Object.assign(r2, { id: 'reference-2', title: 'second work', original_url: 'https://example.org/original/2', source_version: 'ref-2' });
  p.references.push(r2);
  for (const ref of p.references) {
    const capture = add(`${ref.id}-view`, 'reference_view', `synthetic ${ref.id}`, '2026-10-02T10:00:00Z', ref.source_version);
    capture.reference_id = ref.id;
  }
  const conditions = { camera: 'front', size: 'same framing', lighting: 'same studio', state: 'idle', display: 'normal' };
  p.decision.revision = revision;
  p.decision.states.technical = { status: 'passed', reviewer: 'technical runner', reason: 'separate technical check', evidence_id: 'tech' };
  p.decision.comparisons = p.references.map(ref => ({ criterion_id: 'main-shape', reference_id: ref.id, candidate_evidence_id: 'candidate', reference_evidence_id: `${ref.id}-view`, candidate_conditions: clone(conditions), reference_conditions: clone(conditions), difference: 'whole defining shape observed in both fixtures', verdict: 'acceptable', reviewer: 'visual reviewer' }));
  return { p, root, add };
}
function passing() {
  const f = fixture();
  Object.assign(f.p.decision.states.aesthetic, { status: 'passed', reviewer: 'visual reviewer', reason: 'whole defining features compared', review_epoch: 1 });
  Object.assign(f.p.decision.holistic, { status: 'passed', reason: 'all whole defining features observed; counterexample absent', revision: f.p.task.revision, review_epoch: 1, counterexample_present: false, evidence_ids: ['candidate'] });
  return f;
}
const run = (f, mode = 'review') => check(f.p, { root: f.root, mode, now });
const rejects = (f, code, mode = 'expand') => assert.ok(run(f, mode).errors.some(e => e.code === code), JSON.stringify(run(f, mode)));

function motionFixture() {
  const f = passing();
  Object.assign(f.p.task.criteria[0], { purpose: 'motion', required_inspection: 'full_timeline' });
  for (const r of f.p.references) Object.assign(r, { purposes: ['motion'], medium: 'video', inspected: 'full_timeline', inspected_range: 'start through recovery' });
  Object.assign(f.p.evidence.find(e => e.id === 'candidate'), { medium: 'video', inspected: 'full_timeline', inspected_range: 'start through recovery' });
  return f;
}
function approvedReference(f) {
  f.p.references[0].required = true;
  f.p.task.authority.approved.push({ item: 'approved coating', reason: 'top quality reference for scoped criterion', source: 'explicit prior approval', target: { kind: 'reference', id: 'reference-1' }, criterion_ids: ['main-shape'] });
}
function protect(f) {
  f.p.task.authority.preserve.push({ item: 'central five starts', reason: 'do not change', source: 'explicit instruction', target: { kind: 'constraint', id: 'central-five' }, criterion_ids: ['main-shape'] });
  f.p.decision.constraint_checks = [{ constraint_id: 'central-five', revision: f.p.task.revision, status: 'unchanged', evidence_id: 'candidate' }];
}
function localReuse() {
  const f = passing(), p = f.p;
  p.task.criteria.push({ ...clone(p.task.criteria[0]), id: 'detail-shape' });
  p.task.affected_criteria = ['main-shape', 'detail-shape'];
  p.decision.comparisons[1].criterion_id = 'detail-shape';
  p.task.whole_quality.grounding.push({ reference_id: 'reference-2', criterion_id: 'detail-shape' });
  assert.deepEqual(run(f, 'expand').errors, []);
  const previous = JSON.stringify(p);
  f.add('prior-review', 'review_record', previous);
  p.task.revision = 'prototype-2'; p.task.review_epoch = 2; p.task.affected_criteria = ['detail-shape'];
  const current = p.task.revision;
  const artifact = f.add('artifact-2', 'artifact', 'synthetic locally modified artifact', '2026-10-03T11:00:00Z', current);
  p.task.artifact = { evidence_id: artifact.id, sha256: artifact.sha256 };
  const diff = f.add('diff-2', 'saved_diff', 'synthetic detail changed; main shape unchanged', '2026-10-03T11:00:00Z', current);
  diff.unchanged_criteria = ['main-shape']; p.task.saved_diff_id = diff.id;
  p.task.latest_validation_id = f.add('tech-2', 'technical', 'synthetic current check', '2026-10-03T11:01:00Z', current).id;
  f.add('candidate-2', 'candidate_view', 'synthetic current whole representative', '2026-10-03T11:02:00Z', current);
  p.decision.revision = current; p.decision.states.technical.evidence_id = 'tech-2';
  p.decision.states.aesthetic.review_epoch = 2;
  Object.assign(p.decision.holistic, { revision: current, review_epoch: 2, evidence_ids: ['candidate-2'] });
  p.references[1].analysis_epoch = 2;
  p.decision.comparisons[1].candidate_evidence_id = 'candidate-2';
  p.decision.comparisons[0].reuse = { from_revision: 'prototype-1', reason: 'only the independent detail changed; main defining shape unchanged', prior_review_evidence_id: 'prior-review', unchanged_evidence_id: 'diff-2' };
  return f;
}

test('P1: static candidate cannot pass motion even when references cover full timeline', () => {
  const f = motionFixture(); Object.assign(f.p.evidence.find(e => e.id === 'candidate'), { medium: 'image', inspected: 'image' });
  rejects(f, 'CANDIDATE_SCOPE'); rejects(f, 'CANDIDATE_MOTION');
});
test('P1: candidate excerpt and unrecorded viewing range cannot pass full motion', () => {
  const f = motionFixture(); const e = f.p.evidence.find(e => e.id === 'candidate'); e.inspected = 'video_excerpt'; rejects(f, 'CANDIDATE_SCOPE');
  e.inspected = 'full_timeline'; e.inspected_range = null; rejects(f, 'CANDIDATE_SCOPE');
});
test('P1: recorded full candidate and reference timeline can satisfy motion record guard', () => assert.deepEqual(run(motionFixture(), 'expand').errors, []));
test('P1: two auxiliary references cannot exclude required approved reference', () => {
  const f = passing(); approvedReference(f);
  const third = clone(f.p.references[1]); third.id = 'reference-3'; third.original_url = 'https://example.org/original/3'; third.source_version = 'ref-3'; f.p.references.push(third);
  const capture = f.add('reference-3-view', 'reference_view', 'synthetic third', '2026-10-02T10:00:00Z', 'ref-3'); capture.reference_id = third.id;
  f.p.decision.comparisons[0] = { ...clone(f.p.decision.comparisons[1]), reference_id: third.id, reference_evidence_id: capture.id };
  f.p.references[0].selected = false;
  rejects(f, 'REQUIRED_REFERENCE'); rejects(f, 'APPROVED_COMPARISON');
});
test('P1: demoting approved coating to optional support cannot erase priority', () => {
  const f = passing(); approvedReference(f); Object.assign(f.p.references[0], { role: 'support', required: false, priority: 2 }); rejects(f, 'APPROVED_REFERENCE');
});
test('P1: authority-only rejection of current candidate or interpretation blocks completion', () => {
  for (const kind of ['candidate', 'interpretation', 'criterion', 'reference']) {
    const f = passing(); const ids = { candidate: f.p.task.revision, interpretation: f.p.task.interpretation_id, criterion: 'main-shape', reference: 'reference-1' };
    f.p.task.authority.rejected.push({ item: 'explicitly rejected', reason: 'not accepted', source: 'user rejection', target: { kind, id: ids[kind] }, criterion_ids: ['main-shape'] });
    rejects(f, 'AUTHORITY_REJECTION');
  }
});
test('P1: historical candidate rejection is not current-candidate rejection', () => {
  const f = passing(); f.p.task.authority.rejected.push({ item: 'old', reason: 'old version failed', source: 'user', target: { kind: 'candidate', id: 'old' }, criterion_ids: ['main-shape'] }); assert.deepEqual(run(f, 'expand').errors, []);
});
test('P1: protected constraint changed, unverified or stale cannot complete', () => {
  for (const status of ['changed', 'unverified']) { const f = passing(); protect(f); f.p.decision.constraint_checks[0].status = status; rejects(f, 'PRESERVE_CONFLICT'); }
  const f = passing(); protect(f); f.p.decision.constraint_checks[0].revision = 'old'; rejects(f, 'PRESERVE_CONFLICT');
});
test('P1: preservation requires actual evidence rather than unchanged declaration alone', () => {
  const f = passing(); protect(f); f.p.decision.constraint_checks[0].evidence_id = 'missing'; rejects(f, 'PRESERVE_EVIDENCE');
  f.p.decision.constraint_checks[0].evidence_id = 'candidate'; assert.deepEqual(run(f, 'expand').errors, []);
});
test('P2: active unknown failure cannot be resolved by updating epoch and interpretation', () => {
  const f = passing(); Object.assign(f.p.decision.failure, { count: 1, status: 'active', cause: 'unknown', response: 'still investigating', source: 'user rejected', rejected_revision: 'old', rejected_interpretation_id: 'old', invalidated_epoch: 1 });
  f.p.task.review_epoch = 2; f.p.task.interpretation_id = 'new'; f.p.decision.states.aesthetic.review_epoch = 2; f.p.decision.holistic.review_epoch = 2; f.p.references.forEach(r => r.analysis_epoch = 2);
  rejects(f, 'FAILURE_UNKNOWN');
  Object.assign(f.p.decision.failure, { status: 'resolved', resolution_reason: 'underlying cause and remedy now confirmed', resolution_evidence_id: 'candidate' }); assert.deepEqual(run(f, 'expand').errors, []);
});
test('P2: record mode independently verifies every claimed holistic pass', () => {
  for (const mutation of [p=>p.decision.holistic.counterexample_present=true,p=>p.decision.holistic.revision='old',p=>p.decision.holistic.reason=null,p=>p.decision.holistic.evidence_ids=[],p=>p.decision.holistic.evidence_ids=['absent']]) {
    const f = passing(); f.p.decision.states.aesthetic.status = 'unverified'; f.p.decision.states.technical.status = 'unverified'; mutation(f.p); assert.ok(run(f, 'record').errors.length);
  }
});
test('P2: record mode integration pass requires current saved artifact and whole evidence', () => {
  const f = fixture(); f.p.decision.states.technical.status = 'unverified';
  Object.assign(f.p.decision.integration, { status: 'passed', revision: f.p.task.revision, reviewer: 'reviewer', reason: 'whole reviewed', evidence_id: 'absent' });
  f.p.task.artifact = { evidence_id: null, sha256: null }; rejects(f, 'ARTIFACT_LINK', 'record'); rejects(f, 'EVIDENCE_MISSING', 'record');
});
test('Library reference ID + version + inspection is accepted without an HTTP URL', () => {
  const f = passing(); Object.assign(f.p.references[0], { source: { kind: 'library', library_file_id: 'libfile_approved', version: 'ref-1' }, original_url: null });
  approvedReference(f); assert.deepEqual(run(f, 'expand').errors, []);
  f.p.references[0].inspected = 'unconfirmed'; rejects(f, 'UNSEEN_REFERENCE');
});
test('Library reference version mismatch is rejected', () => {
  const f = passing(); Object.assign(f.p.references[0], { source: { kind: 'library', library_file_id: 'libfile_approved', version: 'different' }, original_url: null }); rejects(f, 'REFERENCE_LINK');
});
test('whole intent cannot cite missing reference/criterion or unused grounds', () => {
  const f = passing(); f.p.task.whole_quality.grounding = [{ reference_id: 'absent', criterion_id: 'absent' }]; rejects(f, 'GROUNDING_LINK');
  const unused = passing(); unused.p.task.whole_quality.grounding = [{ reference_id: 'reference-2', criterion_id: 'main-shape' }]; unused.p.decision.comparisons.pop(); rejects(unused, 'GROUNDING_COMPARISON');
});
test('local change can reuse unrelated comparison and reference analysis with exact prior review and saved difference', () => assert.deepEqual(run(localReuse(), 'expand').errors, []));
test('reuse cannot hide affected criteria or claim unchanged without saved evidence', () => {
  const f = localReuse(); f.p.task.affected_criteria.push('main-shape'); rejects(f, 'REUSE_AFFECTED');
  const missing = localReuse(); delete missing.p.evidence.find(e => e.id === 'diff-2').unchanged_criteria; rejects(missing, 'REUSE_PROOF');
});
test('reuse cannot alter earlier criterion, observation or reference identity', () => {
  const f = localReuse(); f.p.task.criteria[0].target = 'different standard'; rejects(f, 'REUSE_PROOF');
  const observation = localReuse(); observation.p.decision.comparisons[0].difference = 'self-affirming replacement'; rejects(observation, 'REUSE_PROOF');
  const source = localReuse(); source.p.references[0].original_url = 'https://example.org/changed-source'; rejects(source, 'REUSE_PROOF');
});
test('reuse is invalidated only for the scope of rejected interpretation', () => {
  const f = localReuse(); Object.assign(f.p.decision.failure, { count: 1, status: 'active', cause: 'implementation', response: 'short detail prototype', source: 'user rejection', rejected_revision: 'old', rejected_interpretation_id: 'old', invalidated_epoch: 1, criterion_ids: ['detail-shape'] });
  assert.deepEqual(run(f, 'expand').errors, []);
  f.p.decision.failure.criterion_ids = ['main-shape']; rejects(f, 'REUSE_INVALIDATED');
});
test('historical reuse cannot revive a candidate rejected only in authority', () => {
  const f = localReuse(); f.p.task.authority.rejected.push({ item: 'old rejected candidate', reason: 'explicit rejection', source: 'user', target: { kind: 'candidate', id: 'prototype-1' }, criterion_ids: ['main-shape'] }); rejects(f, 'REUSE_REJECTED');
});
test('historical reuse cannot revive a prior false holistic pass with missing evidence', () => {
  const f = localReuse(), e = f.p.evidence.find(e => e.id === 'prior-review');
  const old = JSON.parse(fs.readFileSync(path.join(f.root, e.path), 'utf8')); old.decision.holistic.evidence_ids = [];
  const content = JSON.stringify(old); fs.writeFileSync(path.join(f.root, e.path), content); e.sha256 = hash(content); rejects(f, 'REUSE_PROOF');
});
test('schema 1 cannot silently bypass new linked checks', () => { const f = passing(); f.p.schema_version = 1; rejects(f, 'FORMAT', 'record'); });

test('all five handoff examples and blank template are valid pending records', () => {
  for (const file of ['templates/packet.json', ...fs.readdirSync(path.join(project, 'examples')).map(x => `examples/${x}`)]) {
    const p = JSON.parse(fs.readFileSync(path.join(project, file), 'utf8'));
    assert.deepEqual(check(p, { mode: 'record', now }).errors, [], file);
    assert.equal(p.decision.states.aesthetic.status, 'unverified');
    assert.ok(check(p, { mode: 'review', now }).errors.length > 0, file);
  }
});
test('review checks are complete without a claimed aesthetic pass', () => {
  const f = fixture(), out = run(f); assert.deepEqual(out.errors, []);
  assert.equal(out.aesthetic_certified, false); assert.equal(out.user_status, 'pending');
});
test('expansion guard can accept complete synthetic records without certifying beauty', () => {
  const out = run(passing(), 'expand'); assert.deepEqual(out.errors, []); assert.equal(out.aesthetic_certified, false);
});
test('technical success alone cannot pass aesthetic expansion', () => rejects(fixture(), 'AESTHETIC_UNVERIFIED'));
test('missing local evidence is detected', () => { const f = passing(); fs.unlinkSync(path.join(f.root, 'candidate.txt')); rejects(f, 'EVIDENCE_FILE'); });
test('changed saved artifact is detected by bytes', () => { const f = passing(); fs.writeFileSync(path.join(f.root, 'artifact.txt'), 'changed'); rejects(f, 'HASH_MISMATCH'); });
test('candidate from a previous revision cannot support current pass', () => { const f = passing(); f.p.evidence.find(x => x.id === 'candidate').revision = 'old'; rejects(f, 'STALE_REVISION'); });
test('validation linked to another artifact cannot support pass', () => { const f = passing(); f.p.evidence.find(x => x.id === 'tech').artifact_sha256 = 'old'; rejects(f, 'ARTIFACT_LINK'); });
test('validation predating saved difference cannot support pass', () => { const f = passing(); f.p.evidence.find(x => x.id === 'tech').captured_at = '2026-10-03T09:00:00Z'; rejects(f, 'STALE_VALIDATION'); });
test('technical state must cite the latest validation', () => { const f = passing(); f.p.decision.states.technical.evidence_id = 'old-report'; rejects(f, 'STALE_VALIDATION'); });
test('reference identity and version must match capture', () => {
  const f = passing(), e = f.p.evidence.find(x => x.kind === 'reference_view'); e.reference_id = 'another'; e.revision = 'old';
  rejects(f, 'REFERENCE_LINK'); rejects(f, 'STALE_REVISION');
});
test('unseen sources and missing original URL block review', () => {
  const f = passing(); Object.assign(f.p.references[0], { original_url: null, inspected: 'unconfirmed' });
  rejects(f, 'REFERENCE_URL'); rejects(f, 'UNSEEN_REFERENCE');
});
test('one source is insufficient to establish a multiple-work reference selection', () => {
  const f = passing(); f.p.references[1].selected = false; rejects(f, 'REFERENCE_COUNT');
});
test('source count does not pass without actual use of each selected reference', () => {
  const f = passing(); f.p.decision.comparisons.pop(); rejects(f, 'REFERENCE_UNUSED');
});
test('multiple IDs cannot disguise one original source as several works', () => {
  const f = passing(); f.p.references[1].original_url = f.p.references[0].original_url; rejects(f, 'DISTINCT_REFERENCES');
});
test('empty files are not evidence', () => {
  const f = passing(); const e = f.p.evidence.find(x => x.id === 'candidate'); fs.writeFileSync(path.join(f.root, e.path), ''); e.sha256 = hash(''); rejects(f, 'EVIDENCE_FILE');
});
test('turntable cannot support combat motion even as a pending record', () => {
  const f = fixture(); Object.assign(f.p.references[0], { medium: 'turntable', purposes: ['motion'] }); rejects(f, 'MOTION_SOURCE', 'record');
});
test('Noctveil false pass: claws alone do not satisfy whole wing arc', () => {
  const f = passing(); f.p.task.whole_quality.counterexample = '39 ordinary foreleg small sweep despite correct claws';
  f.p.decision.holistic.counterexample_present = true; rejects(f, 'HOLISTIC_GAP');
});
test('armor false pass: more panels cannot overcome remaining panel-like structure', () => {
  const f = passing(); f.p.decision.issues.push({ id: 'panel-like-29', severity: 'major', status: 'open', description: '29 panel-like structure still present', next_action: 'remake head collar chest' });
  rejects(f, 'UNRESOLVED_MAJOR');
});
test('ice false pass: attractive static crystal / excerpt does not prove full formation contact', () => {
  const f = passing(); Object.assign(f.p.task.criteria[0], { purpose: 'motion', required_inspection: 'full_timeline' });
  Object.assign(f.p.references[0], { medium: 'video', purposes: ['motion'], inspected: 'video_excerpt' }); rejects(f, 'INSPECTION_SCOPE');
});
test('tree false pass: unequal budget or mandatory dead node cannot pass', () => {
  const f = passing(); Object.assign(f.p.task.criteria[0], { purpose: 'system', required_inspection: 'document' });
  Object.assign(f.p.references[0], { medium: 'document', purposes: ['system'], inspected: 'document' });
  const c = f.p.decision.comparisons[0];
  for (const side of ['candidate_conditions', 'reference_conditions']) Object.assign(c[side], { game_version: 'same', level: 'same', budget: 'same', equipment: 'same', objective: 'same', encounter: 'same' });
  c.candidate_conditions.budget = 'larger';
  f.p.decision.issues.push({ id: 'dead-node', severity: 'major', status: 'open', description: 'mandatory dead node', next_action: 'repair route' });
  rejects(f, 'COMPARISON_CONDITION'); rejects(f, 'UNRESOLVED_MAJOR');
});
test('normal display and matching lighting are required', () => {
  const f = passing(); f.p.decision.comparisons[0].candidate_conditions.display = 'zoom';
  f.p.decision.comparisons[0].reference_conditions.lighting = 'different'; rejects(f, 'NORMAL_DISPLAY'); rejects(f, 'COMPARISON_CONDITION');
});
test('missing criteria comparison cannot hide behind partial acceptable observations', () => {
  const f = passing(); const c = clone(f.p.task.criteria[0]); c.id = 'whole-arc'; f.p.task.criteria.push(c); rejects(f, 'COMPARISON_MISSING');
});
test('previous decision cannot certify latest revision', () => { const f = passing(); f.p.decision.revision = 'old'; rejects(f, 'STALE_REVIEW'); });
test('explicit rejection reopens previous interpretation and cached analysis', () => {
  const f = passing(); Object.assign(f.p.decision.failure, { count: 1, cause: 'misunderstanding', response: 'return to full source', source: 'explicit rejection', rejected_revision: f.p.task.revision, rejected_interpretation_id: f.p.task.interpretation_id, invalidated_epoch: 1 });
  rejects(f, 'REJECTION_REOPEN');
});
test('invalidated analysis cannot be reused as an unchanged pass', () => { const f = passing(); f.p.references[0].analysis_epoch = 0; rejects(f, 'STALE_ANALYSIS'); });
test('repeated failures require independent strongest counterargument', () => {
  const f = passing(); Object.assign(f.p.decision.failure, { count: 2, cause: 'reference_gap', response: 'add appropriate work', source: 'two explicit rejections', rejected_revision: 'old', rejected_interpretation_id: 'old', invalidated_epoch: 1 });
  f.p.task.review_epoch = 2; f.p.task.interpretation_id = 'new'; f.p.decision.states.aesthetic.review_epoch = 2; f.p.decision.holistic.review_epoch = 2;
  f.p.references.forEach(r => r.analysis_epoch = 2);
  rejects(f, 'FORMAT');
  f.p.decision.failure.independent_counterargument = 'test greatest remaining counterexample';
  f.p.decision.failure.independent_reviewer = 'visual reviewer'; rejects(f, 'INDEPENDENT_REVIEW');
  f.p.decision.failure.independent_reviewer = 'another perspective'; assert.deepEqual(run(f, 'expand').errors, []);
});
test('current user rejection cannot be overwritten by internal pass', () => {
  const f = passing(); f.p.decision.states.user = { status: 'rejected', source: 'explicit current rejection' }; rejects(f, 'USER_REJECTED');
});
test('whole integration and required game proof are separate handoff requirements', () => { const f = passing(); rejects(f, 'INTEGRATION_MISSING', 'handoff'); rejects(f, 'GAME_UNVERIFIED', 'handoff'); });
test('complete synthetic whole integration and game records do not imply user adoption', () => {
  const f = passing(); const whole = f.add('whole', 'integration', 'synthetic whole-use report'); f.add('game', 'game', 'synthetic isolated game report');
  Object.assign(whole, { scope: 'whole_in_use', medium: 'image', inspected: 'image', coverage: 'whole_result', inspected_range: 'whole static result in actual use at normal display' });
  Object.assign(f.p.decision.integration, { status: 'passed', revision: f.p.task.revision, reviewer: 'whole reviewer', reason: 'whole and full motion reviewed in use', evidence_id: 'whole' });
  f.p.decision.states.game = { status: 'passed', reviewer: 'game runner', reason: 'isolated game check', evidence_id: 'game' };
  const out = run(f, 'handoff'); assert.deepEqual(out.errors, []); assert.equal(out.user_status, 'pending'); assert.equal(out.aesthetic_certified, false);
});
test('P1: full representative motion cannot substitute for integration evidence scope and full-motion coverage', () => {
  const f = motionFixture(); const whole = f.add('whole-motion', 'integration', 'synthetic whole-motion observation'); f.add('game-motion', 'game', 'synthetic isolated game check');
  Object.assign(f.p.decision.integration, { status: 'passed', revision: f.p.task.revision, reviewer: 'whole reviewer', reason: 'whole motion reviewed in use', evidence_id: whole.id });
  f.p.decision.states.game = { status: 'passed', reviewer: 'game runner', reason: 'isolated check', evidence_id: 'game-motion' };
  Object.assign(whole, { scope: 'whole_in_use', medium: 'image', inspected: 'image', coverage: 'whole_result', inspected_range: 'one representative frame, not full result' });
  for (const mode of ['record', 'handoff']) { rejects(f, 'INTEGRATION_MOTION', mode); rejects(f, 'INTEGRATION_COVERAGE', mode); }
  Object.assign(whole, { medium: 'video', inspected: 'full_timeline', coverage: 'full_motion', inspected_range: 'whole result in use, start through recovery', scope: 'representative' });
  rejects(f, 'INTEGRATION_COVERAGE', 'handoff');
  whole.scope = 'whole_in_use'; whole.inspected_range = null; rejects(f, 'INTEGRATION_COVERAGE', 'handoff');
  whole.inspected_range = 'whole result in use, start through recovery';
  assert.deepEqual(run(f, 'handoff').errors, []); assert.deepEqual(run(f, 'record').errors, []);
});
test('resolved major issues need current proof', () => {
  const f = passing(); f.p.decision.issues.push({ id: 'resolved', severity: 'major', status: 'resolved', description: 'claimed resolved', next_action: 'verify', resolution_evidence_id: 'absent' }); rejects(f, 'RESOLUTION_EVIDENCE');
});
test('bounded acquisition waits expire instead of becoming pass', () => {
  const f = fixture(); f.p.waits = [{ id: 'fetch', operation: 'fetch original', status: 'pending', started_at: '2026-10-03T10:00:00Z', deadline_at: '2026-10-03T11:00:00Z', attempts: 1, max_attempts: 2, recovery: 'reuse permitted cache or stop and ask required access', bypass_access_denial: false }]; rejects(f, 'WAIT_EXPIRED', 'record');
  f.p.waits[0].status = 'stopped'; assert.deepEqual(run(f, 'record').errors, []);
});
test('access denial cannot be bypassed or hidden by retrying beyond bound', () => {
  const f = fixture(); f.p.waits = [{ id: 'denied', operation: 'denied download', status: 'denied', started_at: '2026-10-03T10:00:00Z', deadline_at: '2026-10-03T11:00:00Z', attempts: 3, max_attempts: 2, recovery: 'stop and request necessary access', bypass_access_denial: true }]; rejects(f, 'ACCESS_DENIAL', 'record'); rejects(f, 'WAIT_BOUND', 'record');
});
test('evidence paths cannot escape record folder', () => { const f = passing(); f.p.evidence.find(e => e.id === 'candidate').path = '../0/artifact.txt'; rejects(f, 'EVIDENCE_FILE'); });
test('malformed records fail without uncaught exceptions', () => {
  for (const p of [null, [], {}, { ...clone(template), references: [null] }, { ...clone(template), references: [{ id: 'broken' }] }]) {
    const out = check(p, { mode: 'review', now }); assert.ok(out.errors.length > 0); assert.equal(out.aesthetic_certified, false);
  }
});
test('duplicate evidence IDs cannot shadow old evidence', () => { const f = passing(); f.p.evidence.push(clone(f.p.evidence[0])); rejects(f, 'DUPLICATE_ID'); });
test('time tracking is optional and imposes no invented upper bound', () => { const f = fixture(); f.p.effort_minutes = { research: 9000, management: 0, production: 30, verification: 3 }; assert.deepEqual(run(f).errors, []); });
test('CLI emits distinct exit codes for pending review and malformed JSON', () => {
  const cli = path.join(project, 'scripts/check.mjs');
  const pending = spawnSync(process.execPath, [cli, path.join(project, 'examples/armor.json'), '--mode', 'review'], { encoding: 'utf8' });
  assert.equal(pending.status, 1); assert.equal(JSON.parse(pending.stdout).aesthetic_certified, false);
  const malformed = path.join(temp, 'bad.json'); fs.writeFileSync(malformed, '{');
  assert.equal(spawnSync(process.execPath, [cli, malformed], { encoding: 'utf8' }).status, 2);
});
