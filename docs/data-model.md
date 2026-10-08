# Data model

Firestore holds games and everything that must survive; the Realtime Database holds positions and presence, which change every few seconds; Storage holds challenge proofs. Clients never write membership, scores, power-up spawns or game status transitions: Cloud Functions do, with the admin SDK.

## Firestore

### `/games/{gameId}`

| Field | Written by | Notes |
|---|---|---|
| `name`, `maxPlayers`, `gameMode`, `chickenCanSeeHunters`, `timing`, `zone`, `powerUps.enabled`, `powerUps.enabledTypes` | creator at creation | `maxPlayers <= 5` unless `isAdminCreation`, then `<= 500` (rules) |
| `isAdminCreation`, `isDebugGame`, `manualStartEnabled`, `registrationBatchId` | creator at creation | `registrationBatchId` links a paid event batch; `joinGame` then requires a claimed paid code |
| `gameCode` | creator at creation | First 6 characters of the id, upper case; also claimed in `/gameCodes` |
| `roles` | functions only | `{ uid: "chicken" \| "hunter" \| "gameMaster" }`; seeded to `{ creatorId: "chicken" }` at creation |
| `status` | functions; creator or chicken may set `done` | `waiting`, `readyToLaunch`, `inProgress`, `done` |
| `timing.actualStart` | `launchGame` | Manual launch only; every timer anchors on it |
| `winners` | `submitFoundCode` | Clients cannot write it |
| `powerUps.activeEffects.*` | `activatePowerUp` | Expiry timestamps per effect |
| `hasGameMasterPassword` | `setGameMasterPassword` | The password itself lives in `private/security` |

Subcollections:

| Path | Content | Access |
|---|---|---|
| `zone/schedule` | Ordered circles `{ order, radiusMeters, lat, lng }`, computed once by `onGameCreated` | Participants read; nobody writes |
| `private/security` | `foundCode`, `gameMasterPassword` | Functions only |
| `lifecycle/*` | Cloud Task manifest and sent-notification markers | Functions only |
| `players/{uid}` | `{ teamName, joinedAt }` per hunter | Participants read; functions write |
| `powerUps/{id}` | Spawned power-ups, collection and activation state | Participants read; functions write |
| `challenges/{id}` | Frozen copy of the challenge catalogue at creation | Participants read |
| `challengeSubmissions/{id}` | Proofs `{ challengeId, hunterId, mediaUrl, status }` | Author and validators read; the hunter creates while the game runs |
| `challengeCompletions/{hunterId}` | Validated challenges and points | The hunter reads their own |
| `aggregates/leaderboard` | `{ entries: { uid: { teamName, totalPoints } } }` | Participants read; functions write |

### Top-level collections

| Path | Content | Access |
|---|---|---|
| `/gameCodes/{code}` | `{ gameId }`, claimed by `onGameCreated` | Signed-in users `get` one code; no listing |
| `/users/{uid}` | `{ nickname, token, platform, updatedAt }`; `token` holds the push registration (installation id on current builds) | Owner only |
| `/users/{uid}/memberships/{gameId}` | `{ gameId, role }` reverse index | Owner reads; functions write |
| `/challenges/{id}` | Challenge catalogue template | Functions only |
| `/eventRegistrations/{id}` | Paid web registrations, single-use join code, refund state | Functions only |
| `/failedSideEffects/{id}` | Confirmation emails or sheet rows to replay after a paid registration | Functions only |
| `/accountDeletionRequests/{id}` | Requests from the web deletion form | Functions only |
| `/gmRateLimits/{uid_gameId}`, `/gmRateLimits/game_{gameId}` | Referee code attempts per player and per game | Functions only, `expiresAt` TTL |
| `/foundCodeRateLimits/{uid_gameId}` | Wrong found-code attempts and progressive locks | Functions only, `expiresAt` TTL |
| `/validationRateLimits/{uid}` | Paid-code attempts | Functions only, `expiresAt` TTL |
| `/reports/{id}` | Player reports from leaderboards | Participants create; functions read |

## Realtime Database

```
/games/{gameId}/chickenLocations/latest   { lat, lng, ts, invisible }
/games/{gameId}/hunterLocations/{uid}     { lat, lng, ts }
/games/{gameId}/presence/chicken          { online, ts }   (onDisconnect sets offline)
/games/{gameId}/meta                      { creatorId, gameMode, status, roles, chickenCanSeeHunters,
                                            shareHunterLocations, radarPingUntil }
```

`meta` is mirrored from Firestore by `mirrorGameMetaToRtdb` because RTDB rules cannot read Firestore. Hunters read the chicken only in `followTheChicken` or while `radarPingUntil` is in the future, and never while it is invisible. The whole subtree is removed when the game ends.

## Storage

`gameSubmissions/{gameId}/{submissionId}.{jpg|mp4}`: any participant reads; a hunter uploads while the game is in progress, under 25 MB, with the matching content type.

## Retention

- Finished games are deleted 30 days after their end by `purgeFinishedGames`; `onGameDeleted` then removes every subcollection, the memberships, the game code and the Storage proofs.
- Rate-limit documents expire through Firestore TTL on `expiresAt` (`firestore.indexes.json`).
- Backups and bucket lifecycle rules are declared in `infra/`.
