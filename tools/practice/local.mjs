#!/usr/bin/env node
// Builds the practice page, with the deck inside it, so it works straight from disk.
//
//   ./gradlew -q :tools:benchmark:run --args="practice export"
//   node tools/practice/local.mjs
//
// Writes build/practice/practice.html. Open it in a browser. Answers are kept in that browser; the
// end-of-round screen has a "Download results.json" button.

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const casesFile = join(root, "build", "practice", "cases.json");
if (!existsSync(casesFile)) {
  console.error('build/practice/cases.json is missing. Run: ./gradlew -q :tools:benchmark:run --args="practice export"');
  process.exit(2);
}
const deck = JSON.parse(readFileSync(casesFile, "utf8"));
const page = readFileSync(join(root, "tools", "practice", "page.html"), "utf8");
// "<" is escaped so a headline can never close the script element.
const inline = `<script>window.__CASES__ = ${JSON.stringify(deck).replace(/</g, "\\u003c")};</script>\n<script>`;
const at = page.indexOf("<script>");
if (at < 0) throw new Error("page.html has no script element");
const out = join(root, "build", "practice", "practice.html");
writeFileSync(out, page.slice(0, at) + inline + page.slice(at + "<script>".length));
console.log(`${deck.cases.length} cases -> ${out}`);
