const {
  CARD_SERVICE_FEE, HOUR_MS, ReservationError, parseRequest, pickupCode, planReservations, releaseStock,
} = require("../../reservations/plan");

const NOW = Date.UTC(2026, 9, 1, 12);

const products = {
  milk: {
    name: "Milk",
    prices: [
      { storeId: "s1", storeName: "FreshHub", price: 1.19, inStock: true, quantity: 5 },
      { storeId: "s2", storeName: "Corner", price: 1.39, inStock: true, quantity: 1 },
    ],
  },
  bread: { name: "Bread", prices: [{ storeId: "s1", price: 2.5, inStock: true, quantity: 3 }] },
  soldOut: { name: "Gone", prices: [{ storeId: "s1", price: 1, inStock: false, quantity: 4 }] },
};
const stores = {
  s1: { name: "FreshHub", address: { formattedAddress: "Rüttenscheider Str. 1, Essen" } },
  s2: { name: "Corner Market" },
};

function plan(lines, extra = {}) {
  return planReservations({
    uid: "u1", paymentMethod: "pickup", lines, products, stores, user: { noShowCount: 0 },
    codes: { s1: "QL·0001", s2: "QL·0002" }, ids: { s1: "RES-1", s2: "RES-2" }, now: NOW,
    ...extra,
  });
}

function expectError(fn, code, reason) {
  try {
    fn();
  } catch (err) {
    expect(err).toBeInstanceOf(ReservationError);
    expect(err.code).toBe(code);
    if (reason) expect(err.details.reason).toBe(reason);
    return err;
  }
  throw new Error("expected a ReservationError");
}

describe("parseRequest", () => {
  test("merges repeated product/store lines", () => {
    const lines = parseRequest({
      paymentMethod: "pickup",
      items: [
        { productId: "milk", storeId: "s1", quantity: 1 },
        { productId: "milk", storeId: "s1", quantity: 2 },
        { productId: "milk", storeId: "s2", quantity: 1 },
      ],
    });
    expect(lines).toEqual([
      { productId: "milk", storeId: "s1", quantity: 3 },
      { productId: "milk", storeId: "s2", quantity: 1 },
    ]);
  });

  test.each([
    [{}, "empty"],
    [{ paymentMethod: "pickup", items: [] }, "no items"],
    [{ paymentMethod: "cash", items: [{ productId: "a", storeId: "s", quantity: 1 }] }, "unknown method"],
    [{ paymentMethod: "pickup", items: [{ productId: "a", quantity: 1 }] }, "no store"],
    [{ paymentMethod: "pickup", items: [{ productId: "a/b", storeId: "s", quantity: 1 }] }, "bad id"],
    [{ paymentMethod: "pickup", items: [{ productId: "a", storeId: "s", quantity: 0 }] }, "zero"],
    [{ paymentMethod: "pickup", items: [{ productId: "a", storeId: "s", quantity: 1.5 }] }, "fraction"],
    [{ paymentMethod: "pickup", items: [
      { productId: "a", storeId: "s", quantity: 15 }, { productId: "a", storeId: "s", quantity: 15 },
    ] }, "merged too large"],
  ])("rejects %j (%s)", (data) => {
    expectError(() => parseRequest(data), "invalid-argument");
  });
});

describe("pickupCode", () => {
  test("is QL· plus four digits", () => {
    expect(pickupCode(() => 0)).toBe("QL·0000");
    expect(pickupCode(() => 0.4821)).toBe("QL·4821");
    expect(pickupCode(() => 0.99999)).toBe("QL·9999");
  });
});

describe("planReservations (pay at pickup)", () => {
  test("one reservation per store, priced from the catalog with no fee", () => {
    const { reservations } = plan([
      { productId: "milk", storeId: "s1", quantity: 2 },
      { productId: "bread", storeId: "s1", quantity: 1 },
      { productId: "milk", storeId: "s2", quantity: 1 },
    ]);
    expect(reservations).toHaveLength(2);
    const [s1, s2] = reservations;
    expect(s1).toMatchObject({
      reservationId: "RES-1", userId: "u1", storeId: "s1", storeName: "FreshHub",
      storeAddress: "Rüttenscheider Str. 1, Essen", subtotal: 4.88, serviceFee: 0, total: 4.88,
      paymentMethod: "pickup", paymentStatus: "none", status: "reserved", pickupCode: "QL·0001",
      createdAt: NOW, pickupDeadline: NOW + 12 * HOUR_MS,
    });
    expect(s1.items.map((i) => [i.productId, i.price, i.quantity])).toEqual([
      ["milk", 1.19, 2], ["bread", 2.5, 1],
    ]);
    expect(s2).toMatchObject({ storeId: "s2", storeName: "Corner Market", total: 1.39 });
  });

  test("holds stock in the store that was chosen only", () => {
    const { newPrices } = plan([{ productId: "milk", storeId: "s1", quantity: 2 }]);
    expect(newPrices.milk.map((p) => p.quantity)).toEqual([3, 1]);
    expect(products.milk.prices[0].quantity).toBe(5);
  });

  test("rejects when a store doesn't have enough left", () => {
    const err = expectError(
      () => plan([{ productId: "milk", storeId: "s2", quantity: 2 }]),
      "failed-precondition", "unavailable");
    expect(err.details.productIds).toEqual(["milk"]);
  });

  test("rejects out-of-stock, unknown products and stores without the product", () => {
    for (const line of [
      { productId: "soldOut", storeId: "s1", quantity: 1 },
      { productId: "nope", storeId: "s1", quantity: 1 },
      { productId: "bread", storeId: "s2", quantity: 1 },
    ]) {
      expectError(() => plan([line]), "failed-precondition", "unavailable");
    }
  });

  test("users with 3 no-shows must prepay", () => {
    expectError(
      () => plan([{ productId: "milk", storeId: "s1", quantity: 1 }], { user: { noShowCount: 3 } }),
      "failed-precondition", "prepay-required");
    expect(() => plan([{ productId: "milk", storeId: "s1", quantity: 1 }], { user: null }))
      .not.toThrow();
  });

  test("ignores a client-claimed price; only the catalog price counts", () => {
    const { reservations } = plan([{ productId: "milk", storeId: "s1", quantity: 1, price: 0.01 }]);
    expect(reservations[0].total).toBe(1.19);
  });
});

describe("planReservations (card)", () => {
  const lines = [
    { productId: "milk", storeId: "s1", quantity: 2 },
    { productId: "milk", storeId: "s2", quantity: 1 },
  ];

  test("pending until paid, 48h hold, one service fee per checkout", () => {
    const { reservations } = plan(lines, { paymentMethod: "card" });
    expect(reservations.map((r) => [r.status, r.paymentStatus, r.paymentMethod])).toEqual([
      ["pending_payment", "pending", "card"], ["pending_payment", "pending", "card"],
    ]);
    expect(reservations.map((r) => r.serviceFee)).toEqual([CARD_SERVICE_FEE, 0]);
    expect(reservations[0].total).toBe(3.37);
    expect(reservations[1].total).toBe(1.39);
    expect(reservations[0].pickupDeadline - NOW).toBe(48 * HOUR_MS);
  });

  test("prepaying is allowed after 3 missed pickups", () => {
    expect(() => plan(lines, { paymentMethod: "card", user: { noShowCount: 5 } })).not.toThrow();
  });

  test("still holds stock", () => {
    const { newPrices } = plan(lines, { paymentMethod: "card" });
    expect(newPrices.milk.map((p) => p.quantity)).toEqual([3, 0]);
  });
});

describe("releaseStock", () => {
  test("puts each item's quantity back in its store", () => {
    const reservation = {
      items: [
        { productId: "milk", storeId: "s1", quantity: 2 },
        { productId: "bread", storeId: "s1", quantity: 1 },
        { productId: "deleted", storeId: "s1", quantity: 1 },
      ],
    };
    const newPrices = releaseStock(reservation, products);
    expect(newPrices.milk.map((p) => p.quantity)).toEqual([7, 1]);
    expect(newPrices.bread[0].quantity).toBe(4);
    expect(newPrices).not.toHaveProperty("deleted");
    expect(products.milk.prices[0].quantity).toBe(5);
  });

  test("hold then release restores the original stock", () => {
    const line = [{ productId: "milk", storeId: "s1", quantity: 3 }];
    const { reservations, newPrices } = plan(line);
    const held = { ...products, milk: { ...products.milk, prices: newPrices.milk } };
    expect(releaseStock(reservations[0], held).milk[0].quantity).toBe(5);
  });
});
