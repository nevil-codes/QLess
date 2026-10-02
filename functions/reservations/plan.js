// Pure planning for reservations: validate the request, price it from the
// catalog (never from the client), hold stock, and build one reservation per
// store. The caller loads the data and writes the result in a transaction.

const HOUR_MS = 60 * 60 * 1000;

const PICKUP_HOLD_HOURS = 12;
const CARD_HOLD_HOURS = 48;
const CARD_SERVICE_FEE = 0.99;
const MAX_NO_SHOWS = 3;
const PAYMENT_METHODS = ["pickup", "card"];
const MAX_LINES = 50;
const MAX_QUANTITY = 20;

class ReservationError extends Error {
  // code is an HttpsError code: invalid-argument, failed-precondition, ...
  constructor(code, message, details) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

const ID_PATTERN = /^[^/]{1,200}$/;

// { items: [{ productId, storeId, quantity }], paymentMethod } -> merged lines
function parseRequest(data) {
  if (!data || !Array.isArray(data.items) || !data.items.length) {
    throw new ReservationError("invalid-argument", "items must be a non-empty array");
  }
  if (data.items.length > MAX_LINES) {
    throw new ReservationError("invalid-argument", `at most ${MAX_LINES} items per reservation`);
  }
  if (!PAYMENT_METHODS.includes(data.paymentMethod)) {
    throw new ReservationError("invalid-argument", "paymentMethod must be \"pickup\" or \"card\"");
  }

  const merged = new Map();
  for (const item of data.items) {
    const { productId, storeId, quantity } = item || {};
    if (!ID_PATTERN.test(productId || "") || !ID_PATTERN.test(storeId || "")) {
      throw new ReservationError("invalid-argument", "each item needs a productId and storeId");
    }
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) {
      throw new ReservationError("invalid-argument", `quantity must be 1-${MAX_QUANTITY}`);
    }
    const key = `${productId}|${storeId}`;
    const line = merged.get(key) || { productId, storeId, quantity: 0 };
    line.quantity += quantity;
    if (line.quantity > MAX_QUANTITY) {
      throw new ReservationError("invalid-argument", `quantity must be 1-${MAX_QUANTITY}`);
    }
    merged.set(key, line);
  }
  return [...merged.values()];
}

function round2(n) {
  return Math.round(n * 100) / 100;
}

// "QL·0427" style code; `random` returns [0, 1).
function pickupCode(random) {
  return `QL·${String(Math.floor(random() * 10000)).padStart(4, "0")}`;
}

// Builds the reservation docs and the new `prices` arrays for every product
// whose stock changes.
//   paymentMethod: "pickup" (reserved now, pay in store, 12h) or "card"
//     (pending until Stripe confirms payment, 48h, one service fee per
//     checkout charged on the first reservation)
//   products: { id: productData }, stores: { id: storeData }
//   user: users/{uid} data (or null); codes: { storeId: pickupCode }
//   ids: { storeId: reservationId }
function planReservations({ uid, paymentMethod, lines, products, stores, user, codes, ids, now }) {
  const card = paymentMethod === "card";
  if (!card && ((user && user.noShowCount) || 0) >= MAX_NO_SHOWS) {
    throw new ReservationError("failed-precondition",
      "Too many missed pickups; please pay in advance.", { reason: "prepay-required" });
  }

  const newPrices = {};
  const byStore = {};
  const unavailable = [];

  for (const line of lines) {
    const product = products[line.productId];
    const prices = newPrices[line.productId] || (product && (product.prices || []).map((p) => ({ ...p })));
    const offer = prices && prices.find((p) => p && p.storeId === line.storeId);
    const enoughStock = offer && offer.price > 0 && offer.inStock !== false
      && (offer.quantity == null || offer.quantity >= line.quantity);
    if (!enoughStock) {
      unavailable.push(line.productId);
      continue;
    }
    if (offer.quantity != null) offer.quantity -= line.quantity;
    newPrices[line.productId] = prices;

    const store = stores[line.storeId] || {};
    const group = byStore[line.storeId] || (byStore[line.storeId] = {
      storeId: line.storeId,
      storeName: store.name || offer.storeName || "",
      storeAddress: (store.address && store.address.formattedAddress) || "",
      items: [],
    });
    group.items.push({
      productId: line.productId,
      productName: product.name || "",
      storeId: line.storeId,
      storeName: group.storeName,
      price: offer.price,
      quantity: line.quantity,
    });
  }

  if (unavailable.length) {
    throw new ReservationError("failed-precondition",
      "Some items are no longer available in the requested quantity.",
      { reason: "unavailable", productIds: unavailable });
  }

  const reservations = Object.values(byStore).map((group, index) => {
    const subtotal = round2(group.items.reduce((sum, i) => sum + i.price * i.quantity, 0));
    const serviceFee = card && index === 0 ? CARD_SERVICE_FEE : 0;
    const reservationId = ids[group.storeId];
    return {
      reservationId,
      userId: uid,
      storeId: group.storeId,
      storeName: group.storeName,
      storeAddress: group.storeAddress,
      items: group.items,
      subtotal,
      serviceFee,
      total: round2(subtotal + serviceFee),
      paymentMethod,
      paymentStatus: card ? "pending" : "none",
      status: card ? "pending_payment" : "reserved",
      pickupCode: codes[group.storeId],
      createdAt: now,
      pickupDeadline: now + (card ? CARD_HOLD_HOURS : PICKUP_HOLD_HOURS) * HOUR_MS,
    };
  });

  return { reservations, newPrices };
}

// New `prices` arrays that put a reservation's held stock back.
//   products: { id: productData } for every product in the reservation
function releaseStock(reservation, products) {
  const newPrices = {};
  for (const item of reservation.items || []) {
    const product = products[item.productId];
    if (!product) continue;
    const prices = newPrices[item.productId] || (product.prices || []).map((p) => ({ ...p }));
    const offer = prices.find((p) => p && p.storeId === item.storeId);
    if (offer && offer.quantity != null) offer.quantity += item.quantity;
    newPrices[item.productId] = prices;
  }
  return newPrices;
}

module.exports = {
  CARD_HOLD_HOURS,
  CARD_SERVICE_FEE,
  HOUR_MS,
  MAX_NO_SHOWS,
  PICKUP_HOLD_HOURS,
  ReservationError,
  parseRequest,
  pickupCode,
  planReservations,
  releaseStock,
};
