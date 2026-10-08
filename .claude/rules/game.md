# Game mechanics

Read this before touching gameplay, timers, zones, power-ups, roles or registrations.

## Roles

- Membership is one server-owned map on the game, `roles: { uid: "chicken" | "hunter" | "gameMaster" }`. A uid has exactly one role. Clients read it through derived accessors (`chickenId`, `hunterIds`, `gameMasterIds`, `isChicken(uid)`, `role(of:)`) and never write it.
- The create payload may only seed `{ creatorId: "chicken" }`. Every later change goes through a callable: `joinGame`, `leaveGame`, `designateChicken` (creator or referee, while `waiting`, atomic swap) and `joinAsGameMaster`.
- `creatorId` is ownership, separate from the play role. The creator can reconfigure or cancel while `waiting`.
- GameMasters (referees) join with the 4-digit code stored in `private/security`, see every position and power-up read-only, validate challenge proofs, can launch manual games and use the QA panel on debug games. Hunters always share their position when at least one referee joined.
- When `roles` changes while `waiting`, both apps re-route the affected players (chicken map vs hunter map) and show a one-time alert.

## Modes and zone

- `followTheChicken`: the zone is centred on the chicken's live position; hunters see the chicken inside it.
- `stayInTheZone`: the zone shrinks and drifts towards a final point; positions stay hidden except during a radar ping.
- The full list of circles is computed once by `onGameCreated` into `zone/schedule`. Apps download it and render `circles[activeIndex]`; the index comes from timing only (`selectActiveCircle`, freeze aware). In `followTheChicken` only the radius comes from the schedule.
- The last circle is the 50 m final zone and the zone stays there. A game ends on time, when every hunter found the chicken, or when cancelled, never because the zone collapsed.
- Creation wizard: start pin, then final pin (stay in the zone only, at least 100 m apart), then a recap that previews the drift with the client mirrors of the server maths.

## Power-ups

- Hunter: zone preview, radar ping. Chicken: invisibility, zone freeze, decoy, jammer. Only zone freeze and zone preview are enabled by default; invisibility, decoy and jammer are hidden and disabled server-side in `stayInTheZone`.
- `spawnPowerUpBatch` spawns them deterministically from `zone.driftSeed` and snaps them to roads: 5 at start, 2 at each shrink.
- `collectPowerUp(gameId, powerUpId, lat, lng)` checks role, live game and distance (30 m plus GPS tolerance) against the client position and the last stored one. `activatePowerUp` checks the collector and sets the effect expiry on the game in one transaction.

## Lifecycle

1. The creator writes the game; `onGameCreated` claims `/gameCodes/{code}`, stores the found code and schedule, copies the challenge catalogue and plans Cloud Tasks.
2. At `timing.start` the status becomes `inProgress`, or `readyToLaunch` with manual launch. `launchGame` then stamps `timing.actualStart`, shifts the end and plans the runtime tasks.
3. Hunters start after `timing.headStartMinutes`. `evaluateOutOfZone` applies the out-of-zone penalty server-side on top of client reports.
4. Hunters submit the chicken's 4-digit found code through `submitFoundCode`, with progressive locks after wrong attempts.
5. The game ends on time, when all hunters won, or on cancel; RTDB data is removed and the game is purged 30 days later.

## Paid events

- Players pay on the web form (`createPendingRegistration`, Stripe Checkout, `confirmRegistrationPayment` webhook) and receive a unique code by email. A full refund invalidates the code.
- A game with `registrationBatchId` asks hunters for that code in the join flow; `validateRegistrationCode` claims it once per player and `joinGame` refuses players without a claimed paid code.
- No deep link, no link to the paid form and no associated domains in the apps: Apple rejected the build twice for it (guideline 3.1.1).

## Admin and QA modes

- Long-press "Create party" and type the admin code (Remote Config `admin_code`) to lift the player cap from 5 to 500 (`isAdminCreation`, enforced by rules).
- The QA code (`qa_debug_code`, empty disables it) creates a debug game: start in a minute, 5 minutes long, 1-minute shrinks, manual launch. Chicken and referees get a panel whose Next and End buttons call `debugAdvanceGame`, which refuses non-debug games.

## Remote Config

Only client-side, non-deterministic values: `admin_code`, `qa_debug_code`, `found_code_max_wrong_attempts`, `found_code_cooldown_seconds`, `default_initial_radius_meters`. Anything the server decides or that both apps must compute identically stays out of Remote Config.
