import { describe, expect, test } from "vitest";
import { registrationErrorMessage } from "./registrationErrors";

const copy = { defaultError: "default", verificationError: "verify", invalidRequest: "invalid" };

describe("registrationErrorMessage", () => {
  test("explains a failed browser verification", () => {
    expect(registrationErrorMessage(401, copy)).toBe("verify");
  });
  test("asks to check the form on a rejected payload", () => {
    expect(registrationErrorMessage(400, copy)).toBe("invalid");
  });
  test("falls back to the generic message for anything else", () => {
    expect(registrationErrorMessage(500, copy)).toBe("default");
    expect(registrationErrorMessage(429, copy)).toBe("default");
  });
});
