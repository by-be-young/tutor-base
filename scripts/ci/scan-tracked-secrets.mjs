import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const candidates = execFileSync(
  'git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'], { encoding: 'utf8' }
)
  .split('\0')
  .filter(Boolean)

const forbiddenNames = candidates.filter(path =>
  /(^|\/)\.env($|\.)/.test(path) && !/\.env\.example$/.test(path)
)

const signatures = [
  ['private key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],
  ['GitHub token', /\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\b/],
  ['GitHub fine-grained token', /\bgithub_pat_[A-Za-z0-9_]{60,}\b/],
  ['AWS access key', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
  ['Supabase service-role assignment', /SUPABASE_SERVICE_ROLE_KEY\s*=\s*(?!<|replace|example)[^\s#]+/i]
]

const findings = forbiddenNames.map(path => `${path}: tracked environment file`)

for (const path of candidates) {
  let text
  try {
    text = readFileSync(path, 'utf8')
  } catch {
    continue
  }
  for (const [label, pattern] of signatures) {
    if (pattern.test(text)) findings.push(`${path}: ${label}`)
  }
}

if (findings.length) {
  console.error('Repository secret scan failed:\n' + findings.map(item => `- ${item}`).join('\n'))
  process.exit(1)
}

console.log(`Repository secret scan passed (${candidates.length} files checked).`)
