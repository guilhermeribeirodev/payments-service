# AI-assisted engineering

AI tools are welcome where they add clear value: exploring a design, drafting tests, documenting, investigating failures.
The engineer remains accountable for everything merged.

## Good uses
* Generate test cases and edge cases for a unit you wrote, then review each one.
* Explain unfamiliar code, summarise a stack trace, propose hypotheses for a failing test.
* Draft docs/ADRs from the code and discussion; draft migration scripts to review.

## Validation checklist (before using any generated output)
- [ ] I can explain every line; nothing is cargo-culted.
- [ ] **Correctness:** tests exist and fail without the change; edge cases and error paths covered; I ran it.
- [ ] **Security:** no secrets/PII pasted into prompts or committed; inputs validated; no new injection surface; dependencies are real, maintained and licensed (check for hallucinated packages/APIs).
- [ ] **Maintainability:** matches our architecture (ArchUnit passes), naming and style; no needless abstraction.
- [ ] **Contract:** OpenAPI file is the source of truth; generated code was not hand-edited.
- [ ] **Operability:** logging, metrics and tracing for new paths.

## Rules of thumb
* Never paste production data, credentials or customer information into a tool.
* Prefer small, reviewable diffs; the PR description says if AI was used for non-trivial parts.
* Tests are the arbiter: ask the tool for tests first, then for the implementation (test-first works well).
* When the tool and the documentation disagree, the documentation and a running test win.
