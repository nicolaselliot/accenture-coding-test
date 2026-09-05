## What this changes

<!-- One paragraph. What behaviour exists after this PR that did not before? -->

## Why

<!-- The problem being solved. Link the issue or ADR if there is one. -->

---

## The four checks

Every change answers all four before it is called done. Answer them here, not just in your head.

- **Security** — could this leak a secret, log sensitive data, trust unvalidated input, or ship a
  debug affordance to production?
- **Correctness** — what is the failure mode? What happens on null, empty, timeout, cancellation,
  rotation, process death, and a hostile API response?
- **Performance** — what does this cost per frame, per request, per recomposition? Is it on the main
  thread? Does it allocate in a hot loop?
- **Effectiveness** — is this the simplest thing that solves the actual problem, or am I building for
  a requirement nobody has?

## TDD scenario list

<!--
The list this PR worked from — normal path, failure path, boundary (empty, null, zero, max,
cancelled). Tick what landed. Scenarios discovered while working get added here, never removed.
-->

- [ ]

## What a reviewer should look at first

<!-- Point at the one file or decision that most deserves scrutiny. -->

## Checklist

- [ ] A failing test existed first, and it now passes
- [ ] Normal, failure, and boundary cases covered
- [ ] `./gradlew ktlintCheck detekt` clean (the gate accepted in docs/adr/0003)
- [ ] Android and Desktop build; iOS builds or the gap is recorded
- [ ] Dark mode and rotation verified on any touched screen
- [ ] No hardcoded user-facing strings
- [ ] No secret, token, or sensitive value logged or committed
- [ ] Every new value is in *Fixed parameters*, or newly added there with a reason
