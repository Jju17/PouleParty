import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const HEAVY_MODULES = ["googleapis"];

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return path.endsWith(".ts") ? [path] : [];
  });
}

describe("startup imports", () => {
  it.each(HEAVY_MODULES)("loads %s lazily so every function instance stays under its memory limit", (module) => {
    const offenders = sourceFiles(join(__dirname, "..", "src")).filter((file) => {
      const eagerImport = new RegExp(`^import (?!type )[^;]*from "${module}"`, "m");
      return eagerImport.test(readFileSync(file, "utf8"));
    });
    expect(offenders).toEqual([]);
  });

  it("detects an eager import", () => {
    const eagerImport = /^import (?!type )[^;]*from "googleapis"/m;
    expect(eagerImport.test('import { google } from "googleapis";')).toBe(true);
    expect(eagerImport.test('import type { sheets_v4 } from "googleapis";')).toBe(false);
  });
});
