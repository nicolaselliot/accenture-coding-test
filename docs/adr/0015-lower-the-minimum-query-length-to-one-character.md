# ADR-0015: Lower the minimum query length to one character

- **Status:** Accepted
- **Date:** 2026-09-10

## Context

`MIN_QUERY_LENGTH` was pinned at **2** from PR6, and `isSearchable` refused anything that trimmed
shorter. The submit control was disabled and the IME search action silently ignored, so a user who
typed `k` could not search for it.

The justification recorded in three places — the constant's KDoc, `SearchRepositoriesUseCase`'s
KDoc, and the README's rate-limit section — was the request budget: unauthenticated
`search/repositories` allows **10 requests a minute**, and a one-character query was described as
"never what the user meant" and "certain to be wasted".

That justification does not survive this application's own design.

**The budget argument belongs to a search trigger this app does not have.** `CLAUDE.md` introduces a
minimum query length only conditionally: *"If incremental search is kept as an affordance, it needs
**all** of: a minimum query length, a debounce no shorter than the pinned value, and cancellation of
the in-flight request on every change."* This app deliberately took the other branch —
`SearchViewModel`: *"Search runs on explicit submit only"* — and under submit-only triggering the
user spends exactly one request per deliberate action, whatever the query's length. The minimum
protected nothing. The README nevertheless filed it beside the 800 ms debounce as a rate-limit
measure, which is what made a UX choice look like a budget control.

**The factual claim was also wrong.** `q=k` is a valid GitHub search that returns real repositories.
The only `q` the API refuses outright is an empty one, with a 422.

**And it was not a coherent budget policy even on its own terms.** The app freely spends a request on
a four-character typo, on a repeat submit past the 800 ms window, and — per `SearchViewModel`'s
`init` — on restored text the user never submitted, which the same codebase argues is affordable. It
singled out one shape of possibly-unhelpful request while permitting every other shape.

Set against that, the assignment's functional contract asks that 「何かしらのキーワードを入力できる」
and that 「入力したキーワードで GitHub のリポジトリを検索できる」. A single character is
何かしらのキーワード. Refusing it made the app narrower than the specification it is graded
against — the one guardrail in `CLAUDE.md` with an unambiguous answer: *"A `CLAUDE.md` rule
conflicts with the assignment spec → **The spec wins.** Follow it and flag the conflict."*

## Decision

`MIN_QUERY_LENGTH` is **1**.

`isSearchable` therefore reduces to "trims to something non-empty". It keeps the trimming, which is
the trap it was written for: `"   "` is refused, `" k "` is accepted and searched as `k`.

Both guards in `SearchRepositoriesUseCase` are re-described as what they now are — **backstops
against a 422**, one for an empty `q` and one for a page past the result cap — rather than as
judgements about how promising a query looks. No guard in this codebase refuses a request the API
would have answered.

The rejected alternative to this change is worth naming as part of the decision: an earlier attempt
kept the floor at 2 and added a `search_min_length` hint under the field explaining the refusal. That
work was completed and then deleted. It labelled the gap rather than closing it — a user typing `k`
still could not search `k`, they were merely told they may not — and it cost a string in two locales,
a field-level semantics state description, and six UI tests to defend a rule with no remaining
reason to exist. **The deeper fix is less code than the patch.**

## Consequences

The app now searches every keyword the assignment allows, and the only refused input is one GitHub
refuses too. The 高「エラー発生時の処理」and functional-contract rows stop depending on a reviewer
accepting a rationale that does not hold.

What becomes harder: a one-character search returns whatever GitHub's relevance ranking makes of a
single letter across millions of repositories, which is close to noise. That is now the user's
request to make and the API's answer to give, not something this client pre-empts. If it ever proves
worth shaping, the honest mechanism is result presentation, not a refusal to send.

The request budget is unchanged and undefended by this constant, as it always effectively was. What
actually protects it is unaffected and stays: submit-only triggering, the 800 ms repeat-submit
window, the in-flight guard on paging, and the detail cache.

Three tests encoded the old policy and were changed rather than the code — the exception
`CLAUDE.md` names, so each is stated explicitly:

| Test | Was | Now |
|---|---|---|
| `SearchRepositoriesUseCaseTest.isSearchable applies the same trimming…` | `isSearchable(" a ")` was `false` | `true`; `"   "` carries the false case |
| `SearchViewModelTest.a query below the minimum length is not submitted` | `" a "` not submitted | renamed to `a blank query is not submitted`, drives `"   "` |
| `SearchContentTest.the IME search action / submit affordance is refused below the minimum query length` | driven with `"k"` | renamed to `…for a blank query`, driven with `"   "` |

Two tests were added so the new floor is asserted rather than merely configured:
`a single character query reaches the port` (`:domain`) and `a single character query is submitted`
(`:feature:search`). Both fail if the floor is ever raised again without an ADR, which is the point.

## Alternatives considered

- **Keep the floor at 2 and explain it in the UI.** Built, reviewed, and rejected — see the
  *Decision* section. It made the constraint legible without making it correct, and left the spec
  gap open.
- **Keep the floor at 2 and re-file it as a query-quality decision** rather than a budget one,
  correcting the README and the KDocs. Honest, and much cheaper than either other option, but it
  preserves the one thing actually worth fixing: the app still refuses a keyword the assignment
  allows. Rejected for that reason alone.
- **Remove the guard entirely** and let GitHub answer an empty `q` with its 422. Rejected: the 422
  arrives as `AppError.Unknown`, so a user who pressed search on an empty field would get "something
  went wrong" for input the client can recognise as incomplete without a round trip. Refusing blank
  input locally is a backstop, not a policy.
- **Make the minimum configurable per flavour.** Rejected as configuration standing in for a
  decision. One value, one behaviour, recorded here.
