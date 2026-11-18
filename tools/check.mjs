#!/usr/bin/env node
// Runs the right checks for what changed and prints a short summary.
//
//   node tools/check.mjs            picks the level from what changed
//   node tools/check.mjs quick      unit tests of the changed modules
//   node tools/check.mjs gate       every unit test and the benchmark gate
//   node tools/check.mjs release    the full suite (Haystack, Gauntlet, Hammer, Shield)
//
// "What changed" is uncommitted work plus commits not yet on the remote
// branch. Docs alone need nothing. The rule pack, Signals.kt, fixtures or the
// baseline need the gate; a change to core/model runs every test.
//
// The full Gradle log goes to build/check-<level>.log and only the result,
// failures and the gate verdict are printed, so a run costs a session a few
// lines rather than the whole log. The gate writes its report to
// build/benchmark-results.md; docs/benchmark-results.md is only regenerated
// on purpose, with the external datasets downloaded.

import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const win = process.platform === "win32";
const MAX_LINES = 12;

const MODULES = [
  "core/model", "core/flagging", "core/feeds", "engine",
  ...readdirSync(join(root, "tools"))
    .filter((name) => existsSync(join(root, "tools", name, "build.gradle.kts")))
    .map((name) => `tools/${name}`),
];
const GATE_PATHS = /^(fixtures\/|core\/flagging\/src\/main\/resources\/rulepack|core\/flagging\/src\/main\/kotlin\/com\/medialiteracy\/flagging\/Signals\.kt)/;
const GRADLE_PATHS = /^(build\.gradle\.kts|settings\.gradle\.kts|gradle\/|gradle\.properties)/;

function git(...args) {
  const r = spawnSync("git", args, { cwd: root, encoding: "utf8" });
  return r.status === 0 ? r.stdout.trimEnd() : "";
}

function changedFiles() {
  const files = new Set();
  for (const line of git("status", "--porcelain").split("\n")) {
    if (!line) continue;
    const path = line.slice(3).split(" -> ").pop();
    files.add(path.replace(/^"|"$/g, ""));
  }
  const branch = git("branch", "--show-current");
  const upstream = branch && git("rev-parse", "--verify", "--quiet", `origin/${branch}`) ? `origin/${branch}` : "origin/main";
  for (const path of git("diff", "--name-only", `${upstream}...HEAD`).split("\n")) if (path) files.add(path);
  return [...files];
}

/** The level and Gradle tasks for the changed files. */
function plan(level, files) {
  if (level === "release") return { level, tasks: ["suite"] };
  if (level === "gate") return { level, tasks: gateTasks() };
  const code = files.filter((f) => !/\.md$/.test(f) && !f.startsWith("docs/"));
  if (level === undefined && code.length === 0) return { level: "none", tasks: [], why: "only docs changed" };
  if (level === undefined && code.some((f) => GATE_PATHS.test(f))) return { level: "gate", tasks: gateTasks(), why: "rule pack, signals or fixtures changed" };
  if (code.some((f) => f.startsWith("core/model/") || GRADLE_PATHS.test(f))) return { level: "quick", tasks: ["test"], why: "core/model or the build changed, so every module" };
  const modules = MODULES.filter((m) => code.some((f) => f.startsWith(m + "/")));
  if (modules.length === 0) return { level: "none", tasks: [], why: "no module code changed" };
  return { level: "quick", tasks: modules.map((m) => `:${m.replace(/\//g, ":")}:test`), why: modules.join(", ") };
}

function gateTasks() {
  const args = "--offline --gate fixtures/benchmark-baseline.json --report build/benchmark-results.md";
  // The same three stages as the public CI: tests, the Gauntlet, the benchmark gate.
  return ["test", ":tools:adversarial-fuzz:run", ":tools:benchmark:run", win ? `"--args=${args}"` : `--args=${args}`];
}

/** The lines worth reading from a Gradle log. */
function summarise(log) {
  const lines = log.split(/\r?\n/);
  const out = [];
  lines.forEach((line, i) => {
    if (/^e: /.test(line)) out.push(line);
    else if (/ > .* FAILED$/.test(line)) {
      const cause = lines.slice(i + 1).find((l) => l.trim());
      out.push(line.trim() + (cause ? `: ${cause.trim()}` : ""));
    } else if (/QUALITY GATE|DEFENSE SCORE|^\s*Headline: "/.test(line) || /^    - /.test(line)) out.push(line.trim());
    else if (/^\* What went wrong:/.test(line)) {
      out.push(...lines.slice(i + 1).filter((l) => l.trim()).slice(0, 2).map((l) => l.trim()));
    }
  });
  if (/status code 429/.test(log)) out.push("Maven Central is rate-limiting this machine (429); wait and run it again, or use a mirror.");
  if (/Cannot find a Java installation .*languageVersion=17/.test(log)) out.push("No JDK 17 on this machine; install one and run it again.");
  const unique = [...new Set(out)];
  return unique.length > MAX_LINES ? [...unique.slice(0, MAX_LINES), `… ${unique.length - MAX_LINES} more in the log`] : unique;
}

/**
 * The changelog has to tell the truth about this tree. The newest numbered
 * entry names the rule pack and codebook it ships; when the tree has moved on
 * since (a new rule pack, a codebook change), the Unreleased section must say
 * so, so the next release cannot go out without saying what changed.
 */
function versionProblem() {
  const changelog = readFileSync(join(root, "CHANGELOG.md"), "utf8");
  const released = changelog.match(/^## (\d+\.\d+\.\d+) \(.*\)\s*\n+Rule pack (\d+), codebook (\d+)\./m);
  if (!released) return "CHANGELOG.md: the newest release has no 'Rule pack N, codebook M.' line.";
  const [, version, pack, codebook] = released;
  const unreleased = (changelog.match(/^## Unreleased\s*\n([\s\S]*?)^## /m) || [null, ""])[1].trim();
  const packNow = JSON.parse(readFileSync(join(root, "core/flagging/src/main/resources/rulepack-v1.json"), "utf8")).version;
  const codebookNow = Number((readFileSync(join(root, "docs/benchmark/codebook.md"), "utf8").match(/\*\*Version (\d+)\*\*/) || [])[1]);
  const moved = [];
  if (packNow !== Number(pack)) moved.push(`rule pack is ${packNow}, ${version} shipped ${pack}`);
  if (codebookNow !== Number(codebook)) moved.push(`codebook is ${codebookNow}, ${version} shipped ${codebook}`);
  if (moved.length && (unreleased === "" || /^Nothing yet\.?$/.test(unreleased))) {
    return `CHANGELOG.md: ${moved.join("; ")}. Say what changed under Unreleased.`;
  }
  return null;
}

const versionIssue = versionProblem();
if (versionIssue) console.log(`check version: FAIL. ${versionIssue}`);

const requested = process.argv[2];
if (requested && !["quick", "gate", "release"].includes(requested)) {
  console.error(`Unknown level '${requested}'. Use quick, gate or release, or nothing to pick from what changed.`);
  process.exit(2);
}

const p = plan(requested, changedFiles());
if (p.tasks.length === 0) {
  console.log(`check: nothing to run (${p.why}).`);
  process.exit(versionIssue ? 1 : 0);
}

const started = Date.now();
const run = spawnSync(win ? ".\\gradlew.bat" : "./gradlew", ["--continue", ...p.tasks], {
  cwd: root,
  shell: win,
  encoding: "utf8",
  maxBuffer: 256 * 1024 * 1024,
});
const log = `${run.stdout || ""}\n${run.stderr || ""}`;
const logPath = join(root, "build", `check-${p.level}.log`);
mkdirSync(dirname(logPath), { recursive: true });
writeFileSync(logPath, log);

const seconds = Math.round((Date.now() - started) / 1000);
const result = run.status === 0 ? "PASS" : `FAIL (exit ${run.status})`;
console.log(`check ${p.level}${p.why ? ` (${p.why})` : ""}: ${result} in ${seconds}s. Log: build/check-${p.level}.log`);
for (const line of summarise(log)) console.log(`  ${line}`);
process.exit(run.status === 0 && !versionIssue ? 0 : 1);
