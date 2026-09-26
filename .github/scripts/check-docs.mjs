// Checks the developer docs (README.md, CHANGELOG.md and docs/*.md). Run from the repository root:
//   node .github/scripts/check-docs.mjs
// 1. Every `<!-- snippet: path#region -->` is followed by a code block identical to that region of the source file
//    (`// region name` … `// endregion`, or `<!-- region name -->` … `<!-- endregion -->`), so docs can't drift
//    from code that compiles (docs-snippets is compiled in CI).
// 2. Relative links point at files that exist.
// 3. Every doc except the CHANGELOG ends with a "Last verified against:" line.
// 4. No doc claims to guarantee policy compliance.
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const docs = ["README.md", "CHANGELOG.md", ...fs.readdirSync("docs").filter((f) => f.endsWith(".md")).map((f) => `docs/${f}`)];
const problems = [];
let snippetCount = 0;

function region(file, name) {
  const lines = fs.readFileSync(path.join(root, file), "utf8").split(/\r?\n/);
  const start = lines.findIndex((l) => new RegExp(`^\\s*(//|<!--)\\s*region\\s+${name}\\b`).test(l));
  if (start < 0) return null;
  const end = lines.findIndex((l, i) => i > start && /^\s*(\/\/|<!--)\s*endregion\b/.test(l));
  if (end < 0) return null;
  const body = lines.slice(start + 1, end);
  const indent = Math.min(...body.filter((l) => l.trim()).map((l) => l.match(/^ */)[0].length));
  return body.map((l) => l.slice(indent).trimEnd()).join("\n").trim();
}

for (const doc of docs) {
  const text = fs.readFileSync(doc, "utf8").replace(/\r\n/g, "\n");

  // 1. Snippets.
  const snippet = /<!-- snippet: ([^#\s]+)#([\w-]+) -->\n```[\w-]*\n([\s\S]*?)\n```/g;
  for (const match of text.matchAll(snippet)) {
    snippetCount++;
    const [, file, name, quoted] = match;
    if (!fs.existsSync(path.join(root, file))) {
      problems.push(`${doc}: snippet source ${file} doesn't exist`);
      continue;
    }
    const source = region(file, name);
    if (source === null) problems.push(`${doc}: no region '${name}' in ${file}`);
    else if (source !== quoted.split("\n").map((l) => l.trimEnd()).join("\n").trim()) {
      problems.push(`${doc}: the code quoted from ${file}#${name} differs from the source`);
    }
  }
  const markers = (text.match(/<!-- snippet:/g) || []).length;
  const checked = [...text.matchAll(snippet)].length;
  if (markers !== checked) problems.push(`${doc}: ${markers - checked} snippet marker(s) not followed by a code block`);

  // 2. Relative links (outside code blocks).
  const prose = text.replace(/```[\s\S]*?```/g, "");
  for (const [, target] of prose.matchAll(/\]\(([^)\s]+)\)/g)) {
    if (/^(https?:|mailto:|#)/.test(target)) continue;
    const file = target.split("#")[0];
    if (!fs.existsSync(path.join(path.dirname(doc), file))) problems.push(`${doc}: broken link ${target}`);
  }

  // 3. Footer.
  if (doc !== "CHANGELOG.md") {
    const last = text.trimEnd().split("\n").pop();
    if (!last.includes("Last verified against:")) problems.push(`${doc}: must end with "Last verified against: …"`);
  }

  // 4. Policy wording.
  for (const line of text.split("\n")) {
    if (/guarantee/i.test(line) && /(policy|polic(ies)|complian)/i.test(line)) {
      problems.push(`${doc}: policy guarantee wording: ${line.trim()}`);
    }
  }
}

if (problems.length) {
  console.error(problems.join("\n"));
  process.exit(1);
}
console.log(`Docs OK: ${docs.length} files, ${snippetCount} snippets match their compiled sources.`);
