import { readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { buildParityVectors } from "./support/parityVectors";

describe("shared parity vectors", () => {
  it("match the server implementation; regenerate with npm run parity:golden", () => {
    const stored = JSON.parse(readFileSync(resolve(__dirname, "../../parity/golden.json"), "utf8"));
    expect(stored).toEqual(JSON.parse(JSON.stringify(buildParityVectors())));
  });
});
