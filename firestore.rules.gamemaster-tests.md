# Firestore rules — GameMaster role test checklist (PP-66)

This repository does **not** yet have a `@firebase/rules-unit-testing`
harness wired into `functions/` or a dedicated rules-test package — no
`firestore.rules.test.ts`, no `firebase emulators:exec` runner, no
`mocha` / `vitest` config that targets the rules file. Until one is
added, the GameMaster-side rules contract introduced by PP-23 / PP-24 /
PP-66 is verified manually against the local emulator (or against a
short-lived test game on staging).

This file is the checklist. Every assertion below has a corresponding
location in `firestore.rules` that should fail closed if the rules
ever regress.

## Setup

```
firebase emulators:start --only firestore
```

Seed two synthetic UIDs in the emulator UI:
- `chicken-uid` — creator + chicken of the game under test
- `gm-uid` — joins via `joinAsGameMaster` (admin SDK path, bypasses
  rules; required because the public rule denies adding to
  `gameMasterIds`)
- `hunter-uid` — registered via the public client path
- `outsider-uid` — authenticated but not in the game

Seed a game doc at `/games/test-game` with:
- `creatorId: "chicken-uid"`, `chickenId: "chicken-uid"`,
  `hunterIds: ["hunter-uid"]`, `gameMasterIds: ["gm-uid"]`
- `hasGameMasterPassword: true`, `status: "waiting"`
- `maxPlayers: 5`, valid `timing`, `zone`, etc.

Authenticate the emulator client as the UID under test before each
section.

## 1. `gameMasterIds` is admin-SDK only (PP-23)

| # | Actor | Operation | Expected |
|---|-------|-----------|----------|
| 1.1 | `hunter-uid` | `update /games/test-game` with `gameMasterIds: arrayUnion("hunter-uid")` | **DENIED** — `hasOnly(['hunterIds','winners'])` clause excludes `gameMasterIds`; the dedicated chicken-id / GM-promotion rules do not allow this field either. |
| 1.2 | `chicken-uid` | `update /games/test-game` with `gameMasterIds: arrayUnion("chicken-uid")` | **DENIED** — the creator update rule explicitly excludes `gameMasterIds` (`hasAny(['gameMasterIds', 'chickenId'])` short-circuits). |
| 1.3 | `outsider-uid` | `update /games/test-game` adding their UID to `gameMasterIds` | **DENIED** — no matching `allow update` clause (not in `hunterIds`, not creator, not GM). |
| 1.4 | `gm-uid` (already a GM) | `update /games/test-game` setting `gameMasterIds: ["gm-uid", "outsider-uid"]` | **DENIED** — no clause grants a GM the right to edit `gameMasterIds`. |
| 1.5 | Admin SDK (Cloud Function `joinAsGameMaster`) | Same operation as 1.4 | **ALLOWED** — admin SDK bypasses rules; this is the only legit path. |

## 2. `gameMasterPassword` lives in the admin-SDK-only private subcollection

| # | Actor | Operation | Expected |
|---|-------|-----------|----------|
| 2.1 | `hunter-uid` | `get /games/test-game/private/security` | **DENIED** — `match /private/{docId} { allow read, write: if false; }`. |
| 2.2 | `chicken-uid` | Same | **DENIED** — same blanket-deny rule (the creator cannot probe the password either). |
| 2.3 | `gm-uid` | Same | **DENIED** — even the joined GM cannot read it. |
| 2.4 | Anyone | `update /games/test-game/private/security` setting a new password | **DENIED**. |
| 2.5 | Admin SDK (`setGameMasterPassword` CF) | Same | **ALLOWED** — only path that writes the password. |
| 2.6 | Any client | Read `Game.hasGameMasterPassword` field on the public doc | **ALLOWED** — public boolean by design (PP-70: drives the "Join as GameMaster" CTA visibility without leaking the password). |

## 3. Subcollection reads scoped to the GameMaster role

The PP-24 spec requires GMs to read chicken + hunter locations + every
registration in real time, even in `stayInTheZone` where hunters
cannot see the chicken.

| # | Actor | Operation | Expected |
|---|-------|-----------|----------|
| 3.1 | `gm-uid` | `get /games/test-game/chickenLocations/latest` | **ALLOWED** — read clause OR-s `gameMasterIds`. |
| 3.2 | `gm-uid` | `get /games/test-game/hunterLocations/hunter-uid` | **ALLOWED** — read clause OR-s `gameMasterIds`. |
| 3.3 | `gm-uid` | `list /games/test-game/registrations` | **ALLOWED** — `read` clause grants GMs every doc so the drawer can label markers (PP-86). |
| 3.4 | `gm-uid` | `create /games/test-game/chickenLocations/latest` (impersonate the chicken) | **DENIED** — write clause requires `request.auth.uid == chickenId`. |
| 3.5 | `gm-uid` | `update /games/test-game/hunterLocations/hunter-uid` | **DENIED** — write clause requires `visitorId == request.auth.uid` AND `hunterId == request.auth.uid`. |
| 3.6 | `outsider-uid` | `get /games/test-game/chickenLocations/latest` | **DENIED**. |
| 3.7 | `outsider-uid` | `list /games/test-game/registrations` | **DENIED** — not creator, not GM, and `visitorId != uid`. |

## 4. `chickenId` re-designation (PP-26)

| # | Actor | Operation | Expected |
|---|-------|-----------|----------|
| 4.1 | `gm-uid` | `update /games/test-game` with `chickenId: "hunter-uid"` and `hunterIds: []` (status == waiting) | **ALLOWED** — dedicated rule on `chickenId` admits creator OR a member of `gameMasterIds`. |
| 4.2 | `gm-uid` | Same write while status == `inProgress` | **DENIED** — rule requires `status == 'waiting'`. |
| 4.3 | `hunter-uid` | `update /games/test-game` with `chickenId: "hunter-uid"` | **DENIED** — not creator, not GM. |
| 4.4 | `gm-uid` | `update /games/test-game` with `chickenId: "hunter-uid"` while `hunterIds` still contains "hunter-uid" | **DENIED** — rule asserts `!hasAny([request.resource.data.chickenId])` on the new `hunterIds`. |

## 5. `challengeCompletions` — TODAY's rules vs. PP-25 contract

### 5a. Today (pre-PP-25)

| # | Actor | Operation | Expected |
|---|-------|-----------|----------|
| 5a.1 | `hunter-uid` | `set /games/test-game/challengeCompletions/hunter-uid` (own doc) | **ALLOWED** — rule allows `request.auth.uid == hunterId` AND `auth.uid in hunterIds`. |
| 5a.2 | `gm-uid` | `set /games/test-game/challengeCompletions/hunter-uid` (someone else's doc) | **DENIED today** — the rule grants `create/update` only when `auth.uid == hunterId`. This is the gap PP-25 will fix when it formalises GM-side validation. |
| 5a.3 | `chicken-uid` | `set /games/test-game/challengeCompletions/hunter-uid` | **DENIED today** — same reason. |
| 5a.4 | `hunter-uid` | `set /games/test-game/challengeCompletions/other-hunter-uid` | **DENIED** — `auth.uid != hunterId`. |
| 5a.5 | `gm-uid` | `get /games/test-game/challengeCompletions/hunter-uid` | **DENIED today** — the read rule requires `auth.uid in hunterIds`. PP-25 will broaden the read to GMs + the chicken so the validation queue + leaderboard work on the GM map. |

### 5b. PP-25 contract (forward-looking — when implemented, the rule
diff lands in the same commit as PP-25's app code)

When PP-25 ships, `challengeCompletions/{hunterId}` shifts to a
field-level diff:
- `validatedChallengeIds: Set<String>` (oneShot)
- `repeatableCounts: Map<String, Int>` (repeatable)
- `totalPoints: Int`

The rule must then:
- Allow read for `auth.uid in hunterIds || auth.uid == creatorId || auth.uid in gameMasterIds`.
- Allow create/update by the hunter themselves (write their own row,
  no `points` field — credited by a CF / validator path).
- Allow create/update by the chicken or any GM **on the hunter's
  row** with `affectedKeys().hasOnly(['validatedChallengeIds', 'repeatableCounts', 'totalPoints', 'teamName'])`
  — the validator may not edit `hunterId` or invent new top-level
  fields.

Anti-doublon (server-side enforcement, since the rule cannot read a
`challengeSubmissions` subcollection without a `get(...)`):
- `oneShot` validators must verify on the call site that the
  `challengeId` isn't already in `validatedChallengeIds` and not in
  any `submissions` doc with `status == pending`.
- `repeatable` validators must verify there is no `submissions` doc
  for the same `challengeId` with `status == pending` (else they'd
  double-count when the queued one transitions to validated).

These two anti-doublon rules are tested today as unit tests in
`PoulePartyTests/GameMasterValidationTests.swift` (iOS) and
`GameMasterValidationTest.kt` (Android) using an in-memory model so
the contract is pinned even though the production rule for PP-25 is
not yet authored.

## 6. PP-25 `challengeSubmissions` subcollection (forward-looking)

When PP-25 introduces `/games/{gameId}/challengeSubmissions/{submissionId}`:
- Read: `auth.uid in hunterIds || auth.uid == creatorId || auth.uid in gameMasterIds`
- Create: `auth.uid == submissionData.hunterId && hunterId in hunterIds && status == 'pending'`
- Update: only the chicken or a GM can flip `status` to
  `validated` / `rejected`. Must keep `hunterId`, `challengeId`, and
  `createdAt` immutable.

No matching rule exists today. The test harness will need to cover all
three operations once PP-25 lands.

---

### Why no real harness today

We deliberately keep the rules file lean and rely on:
1. Unit tests on each platform that mirror the rule expectations in
   pure Swift / Kotlin (`Game.isChicken`, `markChallengeCompleted`
   transaction shape).
2. This manual checklist + the emulator UI for end-to-end coverage
   before each release that touches `firestore.rules`.
3. Server-side enforcement in Cloud Functions (`joinAsGameMaster`,
   `setGameMasterPassword`, future PP-25 validation handler) — those
   already have unit tests under `functions/test/`.

Adding `@firebase/rules-unit-testing` would mean a `functions/test/`
or new `firestore-rules-tests/` package, a `mocha` + `firebase-tools`
dev dependency, and CI wiring (none of which exist yet — no CI runs
on this repo per `CLAUDE.md`). When PP-25 lands, the dedicated
rules-test harness is on the same ticket so the formal coverage
catches up.
