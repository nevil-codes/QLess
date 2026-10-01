const { buildCases, evaluate, basketIndex, coCountsBefore } = require("../../recs/eval");
const { generate } = require("../../recs/synthetic");

const H = 60 * 60 * 1000;

function ev(eventType, productId, timestamp, extra = {}) {
  return { userId: "u", eventType, productId, category: "C", brand: "B", price: 1, timestamp, ...extra };
}

test("the target's own session is hidden from the profile", () => {
  const t0 = Date.UTC(2026, 0, 1);
  const events = [
    ...[1, 2, 3, 4, 5].map((i) => ev("view", `old${i}`, t0 + i)),
    ev("purchase", "old1", t0 + 10, { basketId: "b1" }),
    ev("view", "target", t0 + 100 * H),
    ev("add_to_cart", "target", t0 + 100 * H + 1),
    ev("purchase", "target", t0 + 101 * H, { basketId: "b2" }),
  ];
  const [c] = buildCases({ users: [{ userId: "u", events }] });
  expect(c.target).toBe("target");
  expect(c.profile.recentProductIds).not.toContain("target");
  expect(c.history.every((e) => e.productId !== "target")).toBe(true);
});

test("co-purchase counts ignore baskets after the cut", () => {
  const index = basketIndex([{ userId: "u", events: [
    ev("purchase", "a", 10, { basketId: "early" }), ev("purchase", "b", 10, { basketId: "early" }),
    ev("purchase", "a", 50, { basketId: "late" }), ev("purchase", "c", 50, { basketId: "late" }),
  ] }]);
  expect(coCountsBefore(index, ["a"], 20)).toEqual({ b: 1 });
  expect(coCountsBefore(index, ["a"], 100)).toEqual({ b: 1, c: 1 });
});

test("hit rate and NDCG reward ranking the target higher", () => {
  const products = [{ id: "x" }, { id: "y" }];
  const cases = [{ target: "y" }];
  const first = evaluate(products, cases, () => [{ id: "y" }, { id: "x" }], 10);
  const second = evaluate(products, cases, () => [{ id: "x" }, { id: "y" }], 10);
  const miss = evaluate(products, cases, () => [{ id: "x" }], 10);
  expect(first).toEqual({ cases: 1, hitRate: 1, ndcg: 1 });
  expect(second.hitRate).toBe(1);
  expect(second.ndcg).toBeCloseTo(1 / Math.log2(3));
  expect(miss.hitRate).toBe(0);
});

test("synthetic data is reproducible for a given seed", () => {
  const a = generate({ seed: 3, users: 5 });
  const b = generate({ seed: 3, users: 5 });
  expect(a.users).toEqual(b.users);
  expect(generate({ seed: 4, users: 5 }).users).not.toEqual(a.users);
});
