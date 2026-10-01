const {
  isAvailable, cheapestPrice, dealScore, priceFit, diversify,
  recommendForUser, similarTo, peopleAlsoBuy,
} = require("../../recs/scoring");
const { DAY_MS, emptyProfile, applyEvent } = require("../../recs/profile");

const NOW = Date.UTC(2026, 5, 1);

function product(id, overrides = {}) {
  return {
    id,
    category: "Dairy",
    brand: "Acme",
    rating: 4,
    reviews: 10,
    prices: [{ storeId: "s1", price: 2, inStock: true, quantity: 5 }],
    ...overrides,
  };
}

function profileFrom(events) {
  return events.reduce((p, e) => applyEvent(p, { timestamp: NOW - DAY_MS, ...e }), emptyProfile());
}

describe("availability and price helpers", () => {
  test("a product is unavailable when every store is out of stock", () => {
    expect(isAvailable(product("a", { prices: [{ price: 2, inStock: false }] }))).toBe(false);
    expect(isAvailable(product("a", { prices: [{ price: 2, quantity: 0 }] }))).toBe(false);
    expect(isAvailable(product("a", { prices: [] }))).toBe(false);
    expect(isAvailable(product("a", { prices: [{ price: 2 }] }))).toBe(true);
  });

  test("cheapest price ignores out-of-stock stores", () => {
    const p = product("a", {
      prices: [{ price: 1, inStock: false }, { price: 3 }, { price: 2.5 }],
    });
    expect(cheapestPrice(p)).toBe(2.5);
  });

  test("deal score uses the bigger of store spread and original-price discount", () => {
    const spread = product("a", { prices: [{ price: 1 }, { price: 2 }] });
    expect(dealScore(spread)).toBeCloseTo(0.5);
    const discounted = product("b", { prices: [{ price: 3, originalPrice: 4 }] });
    expect(dealScore(discounted)).toBeCloseTo(0.25);
  });

  test("price fit peaks at the user's average price", () => {
    expect(priceFit(5, 5)).toBe(1);
    expect(priceFit(7.5, 5)).toBeCloseTo(0.5);
    expect(priceFit(20, 5)).toBe(0);
    expect(priceFit(5, 0)).toBe(0);
  });
});

describe("diversify", () => {
  test("caps items per category, then fills with the rest", () => {
    const scored = [
      { id: "d1", category: "Dairy", _score: 0.9 },
      { id: "d2", category: "Dairy", _score: 0.8 },
      { id: "d3", category: "Dairy", _score: 0.7 },
      { id: "b1", category: "Bakery", _score: 0.1 },
    ];
    expect(diversify(scored, 3, 2).map((p) => p.id)).toEqual(["d1", "d2", "b1"]);
    expect(diversify(scored, 4, 2).map((p) => p.id)).toEqual(["d1", "d2", "b1", "d3"]);
  });
});

describe("recommendForUser", () => {
  const catalog = [
    product("milk", { category: "Dairy", brand: "Acme" }),
    product("bread", { category: "Bakery", brand: "Oven" }),
    product("soap", { category: "Household", brand: "Clean", rating: 5, reviews: 500 }),
    product("gone", { category: "Dairy", prices: [{ price: 2, inStock: false }] }),
  ];

  test("ranks the user's preferred category first", () => {
    const profile = profileFrom([
      { eventType: "purchase", category: "Bakery", brand: "Oven" },
      { eventType: "add_to_cart", category: "Bakery", brand: "Oven" },
    ]);
    const ids = recommendForUser(catalog, profile, { now: NOW }).map((p) => p.id);
    expect(ids[0]).toBe("bread");
  });

  test("falls back to popularity for a new user", () => {
    const ids = recommendForUser(catalog, null, { now: NOW }).map((p) => p.id);
    expect(ids[0]).toBe("soap");
  });

  test("never returns out-of-stock, excluded or just-bought products", () => {
    const profile = profileFrom([{ eventType: "purchase", productId: "milk", category: "Dairy" }]);
    const ids = recommendForUser(catalog, profile, { now: NOW, excludeIds: ["bread"] })
      .map((p) => p.id);
    expect(ids).not.toContain("gone");
    expect(ids).not.toContain("bread");
    expect(ids).not.toContain("milk");
  });

  test("offers a product again once the repurchase cooldown has passed", () => {
    const profile = applyEvent(emptyProfile(), {
      eventType: "purchase", productId: "milk", category: "Dairy", timestamp: NOW - 8 * DAY_MS,
    });
    const ids = recommendForUser(catalog, profile, { now: NOW }).map((p) => p.id);
    expect(ids).toContain("milk");
  });

  test("co-purchase signal lifts products bought with the user's items", () => {
    const profile = profileFrom([{ eventType: "view", category: "Household", brand: "Clean" }]);
    const without = recommendForUser(catalog, profile, { now: NOW }).map((p) => p.id);
    const withCo = recommendForUser(catalog, profile, { now: NOW, coCounts: { bread: 10 } })
      .map((p) => p.id);
    expect(withCo.indexOf("bread")).toBeLessThan(without.indexOf("bread"));
  });
});

describe("similarTo", () => {
  test("prefers same category and brand, excludes the product itself", () => {
    const target = product("t", { category: "Dairy", brand: "Acme" });
    const catalog = [
      target,
      product("sameBoth", { category: "Dairy", brand: "Acme" }),
      product("sameCat", { category: "Dairy", brand: "Other" }),
      product("unrelated", { category: "Tools", brand: "Other", prices: [{ price: 99 }] }),
    ];
    const ids = similarTo(catalog, target).map((p) => p.id);
    expect(ids).toEqual(["sameBoth", "sameCat"]);
  });
});

describe("peopleAlsoBuy", () => {
  const target = product("milk", { category: "Dairy" });
  const catalog = [
    target,
    product("cereal", { category: "Breakfast" }),
    product("butter", { category: "Dairy" }),
    product("cheese", { category: "Dairy", rating: 5, reviews: 100 }),
  ];

  test("orders by co-purchase count", () => {
    const ids = peopleAlsoBuy(catalog, target, { butter: 2, cereal: 7 }, { limit: 2 })
      .map((p) => p.id);
    expect(ids).toEqual(["cereal", "butter"]);
  });

  test("tops up with popular products in the same category", () => {
    const ids = peopleAlsoBuy(catalog, target, { cereal: 1 }, { limit: 3 }).map((p) => p.id);
    expect(ids).toEqual(["cereal", "cheese", "butter"]);
  });
});
