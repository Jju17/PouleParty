import { writeFileSync } from "fs";
import { resolve } from "path";
import { buildParityVectors } from "../test/support/parityVectors";

const target = resolve(__dirname, "../../parity/golden.json");
writeFileSync(target, JSON.stringify(buildParityVectors(), null, 2) + "\n");
console.log(`Wrote ${target}`);
