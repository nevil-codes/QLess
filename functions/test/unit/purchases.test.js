const {
  isNewPickup, basketProductIds, coPurchaseUpdates, purchaseEvents,
} = require("../../recs/purchases");

describe("isNewPickup", () => {
  test("true only on the transition into picked_up", () => {
    expect(isNewPickup({ status: "reserved" }, { status: "picked_up" })).toBe(true);
    expect(isNewPickup({ status: "picked_up" }, { status: "picked_up" })).toBe(false);
    expect(isNewPickup({ status: "reserved" }, { status: "expired" })).toBe(false);
    expect(isNewPickup({ status: "reserved" }, null)).toBe(false);
  });
});

describe("basketProductIds", () => {
  test("dedupes and drops items without a product id", () => {
    const res = { items: [{ productId: "a" }, { productId: "b" }, { productId: "a" }, {}, null] };
    expect(basketProductIds(res)).toEqual(["a", "b"]);
  });

  test("drops ids that can't be used as document ids", () => {
    const res = { items: [{ productId: "ok" }, { productId: "../users/x" }, { productId: "a.b" }] };
    expect(basketProductIds(res)).toEqual(["ok"]);
  });
});

describe("coPurchaseUpdates", () => {
  test("links every product to the others in the basket", () => {
    expect(coPurchaseUpdates(["a", "b", "c"])).toEqual({
      a: ["b", "c"], b: ["a", "c"], c: ["a", "b"],
    });
  });

  test("a single-item basket says nothing about co-purchases", () => {
    expect(coPurchaseUpdates(["a"])).toEqual({});
  });
});

describe("purchaseEvents", () => {
  test("one event per product with catalog category and brand", () => {
    const reservation = {
      userId: "u1",
      storeId: "s1",
      items: [
        { productId: "milk", price: 1.2, quantity: 2 },
        { productId: "bread", price: 3, quantity: 1 },
        { productId: "milk", price: 1.2, quantity: 1 },
      ],
    };
    const products = { milk: { category: "Dairy", brand: "Acme" } };
    const events = purchaseEvents(reservation, products, 1000);

    expect(events).toHaveLength(2);
    expect(events[0]).toEqual({
      userId: "u1", eventType: "purchase", productId: "milk",
      category: "Dairy", brand: "Acme", price: 1.2, storeId: "s1",
      timestamp: 1000, source: "pickup",
    });
    expect(events[1]).toMatchObject({ productId: "bread", category: "", brand: "" });
  });
});
