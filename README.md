# PouleParty

A cross-platform (iOS + Android) location-based mobile game. One player is the **Chicken** who must evade **Hunters** on a real map with a shrinking zone. Players' positions sync in real-time via Firebase Firestore.

## Game modes

| Mode | Firestore value | Description |
|---|---|---|
| **Follow the Chicken** | `followTheChicken` | Hunters see a circle following the Chicken; Chicken doesn't see Hunters (unless `chickenCanSeeHunters` enabled) |
| **Stay in the Zone** | `stayInTheZone` | Fixed zone that shrinks and drifts over time, no position sharing (except via Radar Ping power-up) |

---

# Design Architecture

PouleParty's visual identity is built around a **retro arcade** aesthetic — pixel fonts, neon-flashy colors, and 90s game energy. Every UI element should feel like it belongs in a vibrant, chaotic arcade cabinet.

## Fonts

Two custom fonts define the app's personality:

| Font | File | Role | Usage |
|---|---|---|---|
| **Bangers** | `Bangers-Regular.ttf` | Fun / Expressive | Titles, buttons, rules, labels — everything "playful" |
| **Early GameBoy** | `Early GameBoy.ttf` | Data / Technical | Timers, game codes, stats, distances — everything "data" |

### Typography scale

| Token | Font | Size | Example usage |
|---|---|---|---|
| `display` | Early GameBoy | 80 | Countdown number |
| `title-xl` | Bangers | 36 | Page title ("Welcome!") |
| `title-lg` | Bangers | 32 | Section title |
| `title-md` | Bangers | 24 | Subtitle, major button |
| `title-sm` | Bangers | 20 | Card title, label |
| `body-lg` | Bangers | 18 | Large body text |
| `body` | Bangers | 16 | Standard body |
| `body-sm` | Bangers | 14 | Small body |
| `mono-xl` | Early GameBoy | 48 | Main timer |
| `mono-lg` | Early GameBoy | 28 | Game code |
| `mono-md` | Early GameBoy | 20 | Stats |
| `mono-sm` | Early GameBoy | 14 | Technical labels |
| `mono-xs` | Early GameBoy | 10 | Metadata |
| `mono-xxs` | Early GameBoy | 8 | Micro labels |

## Color palette

### Core brand

| Token | Hex | Swatch | Role |
|---|---|---|---|
| `beige` | `#FDF9D5` | ![#FDF9D5](https://via.placeholder.com/16/FDF9D5/FDF9D5) | Light background base |
| `beige-warm` | `#FFE8C8` | ![#FFE8C8](https://via.placeholder.com/16/FFE8C8/FFE8C8) | Warm gradient edge for beige backgrounds |
| `orange` | `#FE6A00` | ![#FE6A00](https://via.placeholder.com/16/FE6A00/FE6A00) | Primary action, CTA, timers |
| `pink` | `#EF0778` | ![#EF0778](https://via.placeholder.com/16/EF0778/EF0778) | Secondary accent, gradients |

### Game roles

| Token | Hex | Swatch | Text color | Role |
|---|---|---|---|---|
| `chicken-yellow` | `#FFD23F` | ![#FFD23F](https://via.placeholder.com/16/FFD23F/FFD23F) | Black (14.5:1) | Chicken marker, halo, chicken-side UI |
| `hunter-red` | `#C41E45` | ![#C41E45](https://via.placeholder.com/16/C41E45/C41E45) | White (5.8:1) | Hunter marker, hunter-side UI, alerts |

### Zone & map

| Token | Hex | Swatch | Role |
|---|---|---|---|
| `zone-green` | `#39FF14` | ![#39FF14](https://via.placeholder.com/16/39FF14/39FF14) | Safe zone border, success |
| `zone-danger` | `#CC0530` | ![#CC0530](https://via.placeholder.com/16/CC0530/CC0530) | Outside zone, shrink alert (5.4:1 on beige) |

### Power-ups

All power-up colors share the same saturation/brightness range to form a cohesive neon family.

| Power-Up | Token | Hex | Swatch |
|---|---|---|---|
| Invisibility | `powerup-stealth` | `#BF40BF` | ![#BF40BF](https://via.placeholder.com/16/BF40BF/BF40BF) |
| Zone Freeze | `powerup-freeze` | `#00F5FF` | ![#00F5FF](https://via.placeholder.com/16/00F5FF/00F5FF) |
| Radar Ping | `powerup-radar` | `#FF6F3C` | ![#FF6F3C](https://via.placeholder.com/16/FF6F3C/FF6F3C) |
| Zone Preview | `powerup-vision` | `#4D96FF` | ![#4D96FF](https://via.placeholder.com/16/4D96FF/4D96FF) |
| Speed Boost | `powerup-speed` | `#DFFF00` | ![#DFFF00](https://via.placeholder.com/16/DFFF00/DFFF00) |
| Shield | `powerup-shield` | `#FFD700` | ![#FFD700](https://via.placeholder.com/16/FFD700/FFD700) |

### Semantic

| Token | Light | Dark | Text color | Usage |
|---|---|---|---|---|
| `success` | `#39FF14` | `#39FF14` | Black | Permissions OK, copy confirmed, victory |
| `danger` | `#CC0530` | `#FF2E63` | White | Out of zone, errors, delete |
| `warning` | `#FFD23F` | `#FFD23F` | Black | Time running low, alerts |
| `info` | `#4D96FF` | `#4D96FF` | White | Neutral info, help |
| `disabled` | `#9E9E9E` | `#616161` | White | Inactive elements |

### Dark mode

| Token | Light | Dark |
|---|---|---|
| `background` | `#FDF9D5` | `#1A1A2E` (deep navy, not pure black) |
| `surface` | `#FFFFFF` | `#16213E` |
| `on-background` | `#000000` | `#FFFFFF` |
| `on-surface` | `#000000` | `#FFFFFF` |
| `primary` | `#FE6A00` | `#FF8C33` |
| `secondary` | `#EF0778` | `#F54D9E` |

> Dark mode uses deep navy blue (`#1A1A2E`) instead of gray — evoking an arcade room in the dark, not a corporate dark mode.

### Contrast rules (WCAG AA)

These rules are **mandatory** — never violate them:

| Rule | Rationale |
|---|---|
| Never use `orange` as text on `beige` | 2.7:1 — FAIL. Use black text instead |
| Gradient-fire buttons (`orange` → `pink`) use **black** text | Black on orange = 7.3:1, black on pink = 5.0:1 — both PASS |
| `hunter-red` uses **white** text | `#C41E45` on white = 5.8:1 PASS |
| `chicken-yellow` uses **black** text | 14.5:1 PASS |
| `powerup-speed` and `powerup-shield` use **black** text | Light colors on light backgrounds fail; black gives 18.4:1 / 16.3:1 |
| `zone-danger` text on beige uses `#CC0530` (darkened) | Original `#FF073A` = 3.7:1 FAIL; `#CC0530` = 5.4:1 PASS |
| All other power-ups use **white** text | stealth 4.5:1, radar 4.6:1, vision 5.2:1 — all PASS |
| `zone-green` is for map/glow only, not text on light bg | 1.5:1 on white — use only on dark backgrounds or as fills |

## Neon glow effects

Glow effects reinforce the arcade identity. They use layered `box-shadow` / `text-shadow` with the element's own color.

### Glow levels

| Level | Shadow definition | Usage |
|---|---|---|
| **Subtle** | `0 0 4px {color}40` | Idle state, soft accent (zone border, inactive badge) |
| **Medium** | `0 0 7px {color}60, 0 0 14px {color}30` | Hover/active state, power-up badge, timer |
| **Intense** | `0 0 7px {color}, 0 0 14px {color}80, 0 0 28px {color}40` | Countdown, active power-up, victory |

> `{color}` is the element's semantic color (e.g., `#BF40BF` for stealth power-up). Hex suffixes are alpha.

### Text glow (timers, countdown)

```
/* Subtle */
text-shadow: 0 0 4px {color}60;

/* Medium — active timer */
text-shadow: 0 0 7px {color}80, 0 0 14px {color}40;

/* Intense — countdown number */
text-shadow: 0 0 7px {color}, 0 0 14px {color}80, 0 0 28px {color}40;
```

### SwiftUI equivalent

```swift
.shadow(color: .CROrange.opacity(0.4), radius: 4)     // subtle
.shadow(color: .CROrange.opacity(0.6), radius: 7)      // medium
.shadow(color: .CROrange.opacity(0.6), radius: 7)      // intense (layered)
.shadow(color: .CROrange.opacity(0.3), radius: 14)
```

### Compose equivalent

```kotlin
Modifier.shadow(elevation = 0.dp, shape = shape,
    ambientColor = CROrange.copy(alpha = 0.4f),
    spotColor = CROrange.copy(alpha = 0.6f))
```

### Where to apply glow

| Element | Glow level | Color source |
|---|---|---|
| Power-up badge (idle) | Subtle | Power-up's own color |
| Power-up badge (active) | Intense | Power-up's own color |
| Countdown number | Intense | `orange` |
| Timer display | Medium | `orange` |
| Zone border on map | Subtle | `zone-green` |
| Zone shrink warning | Medium | `zone-danger` |
| CTA button hover/press | Medium | `orange` or `pink` |
| Chicken marker halo | Medium | `chicken-yellow` |
| Hunter alert | Medium | `hunter-red` |

## Gradients

Gradients bring the neon-arcade energy that flat colors alone can't deliver.

| Name | Colors | Usage |
|---|---|---|
| **Fire** | `orange` → `pink` | Primary CTA buttons, "Start Game" |
| **Chicken** | `chicken-yellow` → `orange` | Chicken-side elements, chicken marker halo |
| **Hunter** | `hunter-red` → `pink` | Hunter-side elements, hunter alerts |
| **Power-up glow** | power-up color → transparent | Radial glow behind active power-up badge |
| **Zone edge** | `zone-green` → transparent | Neon fade-out on zone border |
| **Background warmth** | `beige` → `beige-warm` | Radial vignette on menu/lobby screens |

## Elevation

| Level | Shadow | Usage |
|---|---|---|
| 0 | None | Backgrounds, flat text |
| 1 | `0 2dp 4dp black/15%` | Cards, input fields |
| 2 | `0 4dp 8dp black/20%` | Buttons, power-up badges |
| 3 | `0 8dp 16dp black/25%` | Modals, overlays, countdown |

## Borders & corners

### Stroke widths

| Element | Width | Color |
|---|---|---|
| Primary button | 3dp | black |
| Secondary button | 2dp | black |
| Card | 2dp | `orange` |
| Input field | 1.5dp | `black/30%` |
| Power-up badge | 0 | — (solid fill + shadow) |

### Corner radius

| Token | Value | Usage |
|---|---|---|
| `radius-sm` | 8dp | Small elements, badges |
| `radius-md` | 12dp | Cards, secondary buttons |
| `radius-lg` | 16dp | Large containers, banners |
| `radius-pill` | 50dp | Primary buttons (capsule) |

## Role-based UI

The game has two distinct roles. The UI subtly shifts to immerse the player:

| Aspect | Chicken | Hunter |
|---|---|---|
| Dominant color | `chicken-yellow` `#FFD23F` | `hunter-red` `#C41E45` |
| Text on dominant | Black | White |
| Gradient | yellow → orange | red → pink |
| Map marker | Yellow chicken with neon halo (medium glow) | Red crosshair with neon glow |
| Countdown tint | Warm yellow-orange | Hot red-pink |
| Info card border | `chicken-yellow` | `hunter-red` |
| Timer color | `orange` (neutral) | `orange` (neutral) |

## Opacity scale

| Value | Usage |
|---|---|
| 0.1 | Subtle dividers, card tints |
| 0.2 | Inactive dots, soft borders |
| 0.3 | Zone fill overlay |
| 0.4 | Danger zone overlay, secondary text |
| 0.6 | Subtitles, tertiary text |
| 0.7 | Overlay backgrounds |
| 0.8 | Active elements, zone borders |
| 0.85 | Button backgrounds |
| 0.9 | Notification overlays |

## Animation principles

| Type | Values | Usage |
|---|---|---|
| Spring | response 0.3, damping 0.5–0.7 | Bouncy interactions (buttons, badges) |
| Ease | easeInOut 0.3s | Smooth transitions (screens, overlays) |
| Fade | 200ms in, 300ms out | Countdown, notifications |
| Pulse | 500ms tween | Blinking elements (waiting states) |

---

## Firestore data model

```
/games/{gameId}
├── id, name, maxPlayers, gameMode, chickenCanSeeHunters
├── foundCode, hunterIds, status, winners, creatorId
├── timing: { start, end, headStartMinutes }
├── zone: { center, finalCenter, radius, shrinkIntervalMinutes, shrinkMetersPerUpdate, driftSeed }
├── pricing: { model, pricePerPlayer, deposit, commission }
├── registration: { required, closesMinutesBefore }
├── powerUps: { enabled, enabledTypes, activeEffects: { invisibility, zoneFreeze, radarPing, decoy, jammer } }
├── /chickenLocations/latest
├── /hunterLocations/{hunterId}
├── /powerUps/{powerUpId}
└── /registrations/{userId}

/fcmTokens/{userId}
/registrations/{docId}              (event registrations, admin-only)
```

## Tech stack

| Concern | iOS | Android |
|---|---|---|
| UI | SwiftUI | Jetpack Compose + Material 3 |
| Architecture | TCA (Composable Architecture) | MVVM + Hilt DI |
| Maps | Mapbox Maps SDK | Mapbox Maps Compose SDK |
| Location | CoreLocation | FusedLocationProvider (Play Services) |
| Async | AsyncStream / async-await | Kotlin Coroutines + Flow |
| Backend | Firebase Firestore, Auth, Analytics, Messaging | Firebase Firestore, Auth, Analytics, Messaging |
