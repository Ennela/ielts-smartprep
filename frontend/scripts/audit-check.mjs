// `npm audit --audit-level=high`, except for the advisories listed in audit-allowlist.json.
//
// npm audit has no way to accept one advisory, so an advisory with no fix used to leave the
// whole check red, and a red check stops meaning anything. This fails exactly as npm audit
// would on every high or critical advisory, dev dependencies included, apart from the ones
// listed -- each with the reason it is accepted and when to take it out.
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const FAILING = new Set(['high', 'critical']);

const allowlist = JSON.parse(readFileSync(new URL('../audit-allowlist.json', import.meta.url), 'utf8'));
const allowed = new Set(allowlist.map(entry => entry.id));

// npm audit exits non-zero whenever it finds anything, so read its report, not its status.
// One command string through the shell, so it runs as-is on Windows (npm is npm.cmd) too.
const audit = spawnSync('npm audit --json', { encoding: 'utf8', shell: true, maxBuffer: 64 * 1024 * 1024 });
let report;
try {
  report = JSON.parse(audit.stdout);
} catch {
  console.error('Could not read the npm audit report:\n' + (audit.stderr || audit.stdout));
  process.exit(1);
}
if (report.error) {
  console.error('npm audit failed: ' + (report.error.summary || JSON.stringify(report.error)));
  process.exit(1);
}

// Every advisory appears as an object in the `via` list of the package it is reported
// against; packages that are only vulnerable through a dependency list its name instead,
// and are covered when that dependency is reached.
const advisories = new Map();
for (const [name, vulnerability] of Object.entries(report.vulnerabilities || {})) {
  for (const via of vulnerability.via) {
    if (typeof via !== 'object' || !FAILING.has(via.severity)) continue;
    const id = (via.url || '').split('/').pop();
    advisories.set(id, { id, name, severity: via.severity, title: via.title, url: via.url });
  }
}

const accepted = [...advisories.values()].filter(a => allowed.has(a.id));
const failing = [...advisories.values()].filter(a => !allowed.has(a.id));

for (const a of accepted) {
  console.log(`accepted  ${a.severity}  ${a.name}  ${a.id}  (see audit-allowlist.json)`);
}
const unused = [...allowed].filter(id => !advisories.has(id));
for (const id of unused) {
  console.log(`note      ${id} is allowlisted but no longer reported; remove it from audit-allowlist.json`);
}

if (failing.length > 0) {
  for (const a of failing) console.error(`FAIL      ${a.severity}  ${a.name}  ${a.title}  ${a.url}`);
  console.error(`\n${failing.length} high or critical advisor${failing.length === 1 ? 'y' : 'ies'} not in audit-allowlist.json.`);
  process.exit(1);
}
console.log(`No high or critical advisories outside audit-allowlist.json (${accepted.length} accepted).`);
