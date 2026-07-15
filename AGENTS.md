# NoteBucket Agent Rules

## Prime directive
Follow the user's plan and instructions exactly. Do not deviate.
Do not change the plan, scope, or architecture without explicit user approval.

## Locked artifacts
- `PRD.md` is the contract. Do not edit, amend, or "improve" it without
  explicit user sign-off. Treat it as read-only unless told otherwise.

## Anti-scope-creep
- Do not add features, modules, dependencies, tools, or abstractions the
  user did not ask for.
- Do not refactor, rename, or restructure existing working code unless the
  user requests it or it is blocking a requested change.
- When in doubt about a decision with one-way-door consequences (data loss,
  history rewrite, dependency add, public API change, schema change), stop
  and ask. Do not guess.

## Plan stability
- We have a plan. We stick to it. If a new idea surfaces, write it down as
  a question for later — do not inject it into the current work.
- If the user changes direction, they will say so explicitly. Do not
  preemptively pivot.

## Communication
- Terse. Technical substance exact. No filler, no hedging, no preamble.
- Code/commits/PRs: normal prose.

## Verification
- After code changes, run lint/typecheck/test commands if present.
  Do not claim done without verification.
- Never commit unless the user explicitly asks.
