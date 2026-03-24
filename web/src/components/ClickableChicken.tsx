import { useState, useCallback } from "react";

const ANGRY_THRESHOLD = 10;

type HitEmoji = { id: number; x: number; y: number; emoji: string };

const hitEmojis = ["💥", "💫", "⭐", "🔥", "😤", "❗"];

export default function ClickableChicken({ className = "" }: { className?: string }) {
  const [hits, setHits] = useState(0);
  const [shaking, setShaking] = useState(false);
  const [particles, setParticles] = useState<HitEmoji[]>([]);

  const handleClick = useCallback(() => {
    setHits((h) => h + 1);

    // Shake
    setShaking(true);
    setTimeout(() => setShaking(false), 400);

    // Spawn hit emoji at random position around the chicken
    const id = Date.now() + Math.random();
    const x = 20 + Math.random() * 60; // 20-80% horizontal
    const y = Math.random() * 60; // 0-60% vertical
    const emoji = hitEmojis[Math.floor(Math.random() * hitEmojis.length)];
    setParticles((p) => [...p, { id, x, y, emoji }]);
    setTimeout(() => setParticles((p) => p.filter((e) => e.id !== id)), 700);
  }, []);

  const isAngry = hits >= ANGRY_THRESHOLD;

  return (
    <div
      className={`relative inline-block cursor-pointer select-none ${className}`}
      onClick={handleClick}
      aria-hidden="true"
    >
      {/* Chicken image */}
      <img
        src="/chicken-pixel.png"
        alt="Poule Party"
        className={`h-36 drop-shadow-lg transition-transform ${shaking ? "animate-shake" : "animate-float"}`}
        style={{ imageRendering: "pixelated" }}
        draggable={false}
      />

      {/* Red eye overlay when angry */}
      {isAngry && (
        <div
          className="absolute rounded-full bg-red-600 animate-pulse"
          style={{
            width: "8%",
            height: "8%",
            left: "18%",
            top: "16%",
            boxShadow: "0 0 6px 2px rgba(220, 38, 38, 0.6)",
          }}
        />
      )}

      {/* Hit emoji particles */}
      {particles.map((p) => (
        <span
          key={p.id}
          className="absolute text-2xl pointer-events-none animate-hit-particle"
          style={{ left: `${p.x}%`, top: `${p.y}%` }}
        >
          {p.emoji}
        </span>
      ))}
    </div>
  );
}
