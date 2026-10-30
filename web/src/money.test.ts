import { describe, expect, it } from "vitest";
import { formatMoney, toMinorUnits } from "./money";

describe("formatMoney", () => {
  it("formats minor units as currency", () => {
    expect(formatMoney(1500, "GBP")).toBe("£15.00");
  });
  it("respects currencies without decimals", () => {
    expect(formatMoney(1500, "JPY")).toContain("1,500");
  });
});

describe("toMinorUnits", () => {
  it.each([
    ["15", 1500],
    ["15.5", 1550],
    ["0.01", 1],
  ])("parses %s", (input, expected) => expect(toMinorUnits(input)).toBe(expected));

  it.each(["", "abc", "-1", "0", "1.234", "1,5"])("rejects %j", (input) =>
    expect(toMinorUnits(input)).toBeUndefined(),
  );
});
