const {
  DAY_MS, HALF_LIFE_DAYS, emptyProfile, applyEvent, decayFactor, normalisedPrefs,
} = require("../../recs/profile");

const T0 = Date.UTC(2026, 0, 1);

describe("decayFactor", () => {
  test("halves after one half-life", () => {
    expect(decayFactor(HALF_LIFE_DAYS * DAY_MS)).toBeCloseTo(0.5);
  });

  test("is 1 for no or negative elapsed time", () => {
    expect(decayFactor(0)).toBe(1);
    expect(decayFactor(-1000)).toBe(1);
  });
});

describe("applyEvent", () => {
  test("weights event types differently", () => {
    let p = emptyProfile();
    p = applyEvent(p, { eventType: "view", category: "Dairy", timestamp: T0 });
    p = applyEvent(p, { eventType: "add_to_cart", category: "Bakery", timestamp: T0 });
    p = applyEvent(p, { eventType: "purchase", category: "Produce", timestamp: T0 });
    expect(p.categoryPrefs).toEqual({ Dairy: 1, Bakery: 3, Produce: 5 });
    expect(p.totalEvents).toBe(3);
  });

  test("ignores unknown event types", () => {
    const p = applyEvent(emptyProfile(), { eventType: "click", category: "Dairy", timestamp: T0 });
    expect(p.categoryPrefs).toEqual({});
    expect(p.totalEvents).toBe(0);
  });

  test("older preferences decay relative to newer ones", () => {
    let p = applyEvent(emptyProfile(), { eventType: "purchase", category: "Old", timestamp: T0 });
    p = applyEvent(p, {
      eventType: "purchase", category: "New", timestamp: T0 + HALF_LIFE_DAYS * DAY_MS,
    });
    expect(p.categoryPrefs.Old).toBeCloseTo(2.5);
    expect(p.categoryPrefs.New).toBe(5);
  });

  test("prunes preferences that decayed to almost nothing", () => {
    let p = applyEvent(emptyProfile(), { eventType: "view", category: "Gone", timestamp: T0 });
    p = applyEvent(p, { eventType: "view", category: "Here", timestamp: T0 + 365 * DAY_MS });
    expect(p.categoryPrefs).toEqual({ Here: 1 });
  });

  test("averages only priced events, weighted by event type", () => {
    let p = applyEvent(emptyProfile(), { eventType: "view", price: 0, category: "A", timestamp: T0 });
    p = applyEvent(p, { eventType: "add_to_cart", price: 2, category: "A", timestamp: T0 });
    p = applyEvent(p, { eventType: "view", price: 6, category: "A", timestamp: T0 });
    expect(p.avgPrice).toBeCloseTo((2 * 3 + 6 * 1) / 4);
  });

  test("keeps recent products most-recent first without duplicates", () => {
    let p = emptyProfile();
    for (const id of ["a", "b", "a", "c"]) {
      p = applyEvent(p, { eventType: "view", productId: id, timestamp: T0 });
    }
    expect(p.recentProductIds).toEqual(["c", "a", "b"]);
  });

  test("remembers purchases and forgets them after 60 days", () => {
    let p = applyEvent(emptyProfile(), { eventType: "purchase", productId: "milk", timestamp: T0 });
    expect(p.purchased).toEqual({ milk: T0 });
    p = applyEvent(p, { eventType: "view", productId: "x", timestamp: T0 + 61 * DAY_MS });
    expect(p.purchased).toEqual({});
  });

  test("does not mutate the input profile", () => {
    const before = applyEvent(emptyProfile(), { eventType: "view", category: "A", timestamp: T0 });
    const snapshot = JSON.parse(JSON.stringify(before));
    applyEvent(before, { eventType: "purchase", category: "A", timestamp: T0 + DAY_MS });
    expect(before).toEqual(snapshot);
  });
});

describe("normalisedPrefs", () => {
  test("scales the strongest preference to 1", () => {
    expect(normalisedPrefs({ a: 4, b: 2 })).toEqual({ a: 1, b: 0.5 });
  });

  test("handles empty input", () => {
    expect(normalisedPrefs({})).toEqual({});
    expect(normalisedPrefs(undefined)).toEqual({});
  });
});
