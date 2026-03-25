import { useState, useCallback, useRef, useEffect } from "react";

const FIRE_THRESHOLD = 10;
const LASER_THRESHOLD = 20;
const NUKE_THRESHOLD = 35;
const EGG_HATCH_HITS = 8;
const NUKE_TO_EGG_DELAY = 2000;

type HitEmoji = { id: number; x: number; y: number; emoji: string };
type Flame = { id: number; x: number; delay: number; size: number };
type Laser = { id: number; angle: number };
type Phase = "chicken" | "nuking" | "egg" | "hatching";

const hitEmojis = ["💥", "💫", "⭐", "🔥", "😤", "❗"];
const eggHitEmojis = ["💥", "🔨", "💫", "⭐", "🥊"];

const CHICKEN_NORMAL = "/chicken-pixel.png";
const CHICKEN_SPRITES = [
  "/chicken-fire1.png",
  "/chicken-fire2.png",
  "/chicken-fire3.png",
];
const EGG_SPRITES = [
  "/egg.png",
  "/egg-crack1.png",
  "/egg-crack2.png",
  "/egg-crack3.png",
];

function getChickenSprite(hits: number): string {
  if (hits < FIRE_THRESHOLD) return CHICKEN_NORMAL;
  if (hits < FIRE_THRESHOLD + 4) return CHICKEN_SPRITES[0];
  if (hits < LASER_THRESHOLD) return CHICKEN_SPRITES[1];
  return CHICKEN_SPRITES[2];
}

function getEggSprite(eggHits: number): string {
  if (eggHits < 3) return EGG_SPRITES[0];
  if (eggHits < 5) return EGG_SPRITES[1];
  if (eggHits < 7) return EGG_SPRITES[2];
  return EGG_SPRITES[3];
}

export default function ClickableChicken({ className = "" }: { className?: string }) {
  const [hits, setHits] = useState(0);
  const [eggHits, setEggHits] = useState(0);
  const [phase, setPhase] = useState<Phase>("chicken");
  const [shaking, setShaking] = useState(false);
  const [particles, setParticles] = useState<HitEmoji[]>([]);
  const [flames, setFlames] = useState<Flame[]>([]);
  const [lasers, setLasers] = useState<Laser[]>([]);
  const [screenFlash, setScreenFlash] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Preload all sprites
  useEffect(() => {
    [...CHICKEN_SPRITES, ...EGG_SPRITES].forEach((src) => {
      const img = new Image();
      img.src = src;
    });
  }, []);

  // Spawn persistent flames when on fire
  const isBurning = phase === "chicken" && hits >= FIRE_THRESHOLD;
  useEffect(() => {
    if (!isBurning) return;
    const interval = setInterval(() => {
      const id = Date.now() + Math.random();
      const flame: Flame = {
        id,
        x: 10 + Math.random() * 80,
        delay: Math.random() * 0.3,
        size: 16 + Math.random() * 20,
      };
      setFlames((f) => [...f.slice(-12), flame]);
      setTimeout(() => setFlames((f) => f.filter((fl) => fl.id !== id)), 1200);
    }, 120);
    return () => clearInterval(interval);
  }, [isBurning]);

  const fireLaser = useCallback(() => {
    const id = Date.now() + Math.random();
    const angle = -30 + Math.random() * 60;
    setLasers((l) => [...l, { id, angle }]);
    setTimeout(() => setLasers((l) => l.filter((la) => la.id !== id)), 800);
  }, []);

  const spawnParticle = useCallback((emoji: string) => {
    const id = Date.now() + Math.random();
    const x = 20 + Math.random() * 60;
    const y = Math.random() * 60;
    setParticles((p) => [...p, { id, x, y, emoji }]);
    setTimeout(() => setParticles((p) => p.filter((e) => e.id !== id)), 700);
  }, []);

  // Chicken click handler
  const handleChickenClick = useCallback(() => {
    const newHits = hits + 1;
    setHits(newHits);

    setShaking(true);
    setTimeout(() => setShaking(false), newHits >= FIRE_THRESHOLD ? 600 : 400);

    const emoji = newHits >= FIRE_THRESHOLD
      ? ["🔥", "🔥", "🔥", "💀", "☠️", "😈"][Math.floor(Math.random() * 6)]
      : hitEmojis[Math.floor(Math.random() * hitEmojis.length)];
    spawnParticle(emoji);

    if (newHits >= LASER_THRESHOLD && newHits < NUKE_THRESHOLD) {
      fireLaser();
      if (Math.random() > 0.5) setTimeout(fireLaser, 150);
    }

    if (newHits === FIRE_THRESHOLD || newHits === LASER_THRESHOLD) {
      setScreenFlash(true);
      setTimeout(() => setScreenFlash(false), 300);
    }

    // NUKE → transition to egg
    if (newHits === NUKE_THRESHOLD) {
      setScreenFlash(true);
      setPhase("nuking");
      for (let i = 0; i < 8; i++) {
        setTimeout(fireLaser, i * 80);
      }
      setTimeout(() => setScreenFlash(false), 1500);
      setTimeout(() => {
        setPhase("egg");
        setEggHits(0);
        setFlames([]);
        setLasers([]);
      }, NUKE_TO_EGG_DELAY);
    }
  }, [hits, fireLaser, spawnParticle]);

  // Egg click handler
  const handleEggClick = useCallback(() => {
    const newEggHits = eggHits + 1;
    setEggHits(newEggHits);

    setShaking(true);
    setTimeout(() => setShaking(false), 300);

    const emoji = eggHitEmojis[Math.floor(Math.random() * eggHitEmojis.length)];
    spawnParticle(emoji);

    // Hatch!
    if (newEggHits >= EGG_HATCH_HITS) {
      setPhase("hatching");
      setScreenFlash(true);
      setTimeout(() => setScreenFlash(false), 400);

      // Spawn celebration particles
      const celebEmojis = ["🐣", "🐥", "✨", "🎉", "🐔", "💛"];
      for (let i = 0; i < 6; i++) {
        setTimeout(() => spawnParticle(celebEmojis[i]), i * 100);
      }

      // Reset to chicken after hatch animation
      setTimeout(() => {
        setPhase("chicken");
        setHits(0);
        setEggHits(0);
        setParticles([]);
      }, 1500);
    }
  }, [eggHits, spawnParticle]);

  const handleClick = phase === "chicken" ? handleChickenClick
    : phase === "egg" ? handleEggClick
    : undefined;

  const hasLasers = hits >= LASER_THRESHOLD;
  const isNuking = phase === "nuking";

  return (
    <>
      {/* Full-screen flash overlay */}
      {screenFlash && (
        <div
          className="fixed inset-0 pointer-events-none z-50"
          style={{
            background: isNuking
              ? "radial-gradient(circle, rgba(255,255,255,0.9) 0%, rgba(255,100,0,0.6) 50%, transparent 80%)"
              : phase === "hatching"
                ? "radial-gradient(circle, rgba(255,220,50,0.5) 0%, transparent 70%)"
                : "radial-gradient(circle, rgba(255,106,0,0.3) 0%, transparent 70%)",
            animation: isNuking ? "nuke-flash 1.5s ease-out forwards" : "screen-flash 0.4s ease-out forwards",
          }}
        />
      )}

      {/* Full-viewport laser beams */}
      {lasers.map((l) => (
        <div
          key={l.id}
          className="fixed pointer-events-none z-40"
          style={{
            left: "50%",
            top: "30%",
            width: "200vw",
            height: isNuking ? "6px" : "3px",
            marginLeft: "-100vw",
            background: isNuking
              ? "linear-gradient(90deg, transparent 0%, #ff0000 20%, #ff4400 40%, #ffffff 50%, #ff4400 60%, #ff0000 80%, transparent 100%)"
              : "linear-gradient(90deg, transparent 0%, #ff0000 30%, #ff4400 50%, #ff0000 70%, transparent 100%)",
            transform: `rotate(${l.angle}deg)`,
            boxShadow: isNuking
              ? "0 0 30px 10px rgba(255,0,0,0.8), 0 0 60px 20px rgba(255,68,0,0.4)"
              : "0 0 15px 5px rgba(255,0,0,0.6), 0 0 30px 10px rgba(255,68,0,0.3)",
            animation: "laser-fire 0.8s ease-out forwards",
          }}
        />
      ))}

      <div
        ref={containerRef}
        className={`relative inline-block select-none ${handleClick ? "cursor-pointer" : ""} ${className}`}
        onClick={handleClick}
        aria-hidden="true"
      >
        {/* Fire glow behind chicken */}
        {isBurning && !isNuking && (
          <div
            className="absolute inset-0 -m-4 rounded-full"
            style={{
              background: "radial-gradient(circle, rgba(255,106,0,0.4) 0%, rgba(239,7,120,0.2) 50%, transparent 70%)",
              animation: "fire-glow 0.8s ease-in-out infinite alternate",
              filter: "blur(8px)",
            }}
          />
        )}

        {/* === CHICKEN === */}
        {(phase === "chicken" || phase === "nuking") && (
          <img
            src={getChickenSprite(hits)}
            alt="Poule Party"
            className={`h-36 drop-shadow-lg transition-transform ${
              isNuking
                ? "animate-nuke-spin"
                : shaking
                  ? (hasLasers ? "animate-shake-hard" : "animate-shake")
                  : "animate-float"
            }`}
            style={{
              imageRendering: "pixelated",
              filter: isNuking
                ? "brightness(3) saturate(0)"
                : isBurning
                  ? `brightness(${1 + Math.min(hits - FIRE_THRESHOLD, 10) * 0.05}) drop-shadow(0 0 ${8 + hits}px rgba(255,68,0,0.8))`
                  : undefined,
            }}
            draggable={false}
          />
        )}

        {/* === EGG === */}
        {phase === "egg" && (
          <img
            src={getEggSprite(eggHits)}
            alt="Egg"
            className={`h-36 drop-shadow-lg ${eggHits === 0 && !shaking ? "animate-egg-appear" : shaking ? "animate-egg-wobble" : "animate-egg-idle"}`}
            style={{ imageRendering: "pixelated" }}
            draggable={false}
          />
        )}

        {/* === HATCHING === */}
        {phase === "hatching" && (
          <>
            {/* Egg shells flying apart */}
            <div className="absolute inset-0 pointer-events-none">
              <span className="absolute text-3xl animate-shell-left" style={{ left: "20%", top: "30%" }}>🥚</span>
              <span className="absolute text-3xl animate-shell-right" style={{ left: "60%", top: "30%" }}>🥚</span>
            </div>
            {/* Baby chicken appearing */}
            <img
              src={CHICKEN_NORMAL}
              alt="Poule Party"
              className="h-36 drop-shadow-lg animate-hatch-appear"
              style={{ imageRendering: "pixelated" }}
              draggable={false}
            />
          </>
        )}

        {/* Rising flames */}
        {flames.map((f) => (
          <span
            key={f.id}
            className="absolute pointer-events-none"
            style={{
              left: `${f.x}%`,
              bottom: "10%",
              fontSize: `${f.size}px`,
              animationDelay: `${f.delay}s`,
              animation: "flame-rise 1.2s ease-out forwards",
            }}
          >
            🔥
          </span>
        ))}

        {/* Hit/crack particles */}
        {particles.map((p) => (
          <span
            key={p.id}
            className="absolute text-2xl pointer-events-none animate-hit-particle"
            style={{ left: `${p.x}%`, top: `${p.y}%` }}
          >
            {p.emoji}
          </span>
        ))}

        {/* Nuke mushroom cloud */}
        {isNuking && (
          <div className="absolute -top-16 left-1/2 -translate-x-1/2 text-6xl animate-nuke-mushroom pointer-events-none">
            ☁️
          </div>
        )}

        {/* Hit counter badge */}
        {phase === "chicken" && hits >= 5 && (
          <div
            className="absolute -top-2 -right-2 rounded-full text-white text-xs font-bold min-w-[24px] h-6 flex items-center justify-center px-1.5"
            style={{
              background: hasLasers
                ? "linear-gradient(135deg, #ff0000, #ff4400)"
                : isBurning
                  ? "linear-gradient(135deg, #FE6A00, #EF0778)"
                  : "#FE6A00",
              boxShadow: hasLasers ? "0 0 8px rgba(255,0,0,0.6)" : undefined,
            }}
          >
            {`${hits}x`}
          </div>
        )}

        {/* Egg crack progress */}
        {phase === "egg" && eggHits > 0 && (
          <div
            className="absolute -top-2 -right-2 rounded-full text-white text-xs font-bold min-w-[24px] h-6 flex items-center justify-center px-1.5"
            style={{ background: "linear-gradient(135deg, #FE6A00, #EF0778)" }}
          >
            {`${eggHits}/${EGG_HATCH_HITS}`}
          </div>
        )}
      </div>
    </>
  );
}
