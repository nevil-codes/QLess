// Firestore (and Stripe) side of reservations. Every state change that
// touches stock runs in a transaction so holds and releases can't
// interleave with another checkout.

const {
  ReservationError, parseRequest, pickupCode, planReservations, releaseStock,
} = require("./plan");

const CODE_ATTEMPTS = 10;

async function uniquePickupCode(tx, db, storeId, random) {
  for (let i = 0; i < CODE_ATTEMPTS; i++) {
    const code = pickupCode(random);
    const clash = await tx.get(db.collection("reservations")
      .where("storeId", "==", storeId)
      .where("pickupCode", "==", code)
      .where("status", "==", "reserved")
      .limit(1));
    if (clash.empty) return code;
  }
  throw new Error(`Could not find a free pickup code for store ${storeId}`);
}

function summary(r) {
  return {
    reservationId: r.reservationId,
    storeId: r.storeId,
    storeName: r.storeName,
    pickupCode: r.pickupCode,
    pickupDeadline: r.pickupDeadline,
    total: r.total,
  };
}

async function writeReservations(db, uid, paymentMethod, lines, now, random) {
  const productIds = [...new Set(lines.map((l) => l.productId))];
  const storeIds = [...new Set(lines.map((l) => l.storeId))];

  return db.runTransaction(async (tx) => {
    const [userSnap, ...rest] = await tx.getAll(
      db.doc(`users/${uid}`),
      ...productIds.map((id) => db.doc(`products/${id}`)),
      ...storeIds.map((id) => db.doc(`stores/${id}`)),
    );
    const productSnaps = rest.slice(0, productIds.length);
    const storeSnaps = rest.slice(productIds.length);
    const byId = (snaps) => Object.fromEntries(snaps.filter((s) => s.exists).map((s) => [s.id, s.data()]));

    const codes = {};
    const ids = {};
    for (const storeId of storeIds) {
      codes[storeId] = await uniquePickupCode(tx, db, storeId, random);
      ids[storeId] = `RES-${now}-${db.collection("reservations").doc().id.slice(0, 6)}`;
    }

    const { reservations, newPrices } = planReservations({
      uid,
      paymentMethod,
      lines,
      products: byId(productSnaps),
      stores: byId(storeSnaps),
      user: userSnap.exists ? userSnap.data() : null,
      codes,
      ids,
      now,
    });

    for (const [productId, prices] of Object.entries(newPrices)) {
      tx.update(db.doc(`products/${productId}`), { prices });
    }
    for (const reservation of reservations) {
      tx.create(db.doc(`reservations/${reservation.reservationId}`), reservation);
    }
    return reservations;
  });
}

// Cancels reservations that are still waiting for payment and returns their
// held stock. Reservations in any other state are left alone, so this is
// safe to call more than once (e.g. from the app and from the webhook).
async function cancelPending(db, reservationIds, now = Date.now()) {
  if (!reservationIds.length) return 0;
  return db.runTransaction(async (tx) => {
    const snaps = await tx.getAll(...reservationIds.map((id) => db.doc(`reservations/${id}`)));
    const pending = snaps.filter((s) => s.exists && s.data().status === "pending_payment");
    if (!pending.length) return 0;

    const productIds = [...new Set(pending.flatMap((s) => (s.data().items || []).map((i) => i.productId)))];
    const productSnaps = await tx.getAll(...productIds.map((id) => db.doc(`products/${id}`)));
    const products = Object.fromEntries(productSnaps.filter((s) => s.exists).map((s) => [s.id, s.data()]));

    for (const snap of pending) {
      for (const [productId, prices] of Object.entries(releaseStock(snap.data(), products))) {
        products[productId] = { ...products[productId], prices };
      }
      tx.update(snap.ref, { status: "cancelled", paymentStatus: "canceled", cancelledAt: now });
    }
    for (const [productId, product] of Object.entries(products)) {
      tx.update(db.doc(`products/${productId}`), { prices: product.prices });
    }
    return pending.length;
  });
}

function toCents(amount) {
  return Math.round(amount * 100);
}

// { items, paymentMethod } -> { reservations, paymentIntentId?, clientSecret? }
// `stripe` is required for card payments.
async function createReservations(db, uid, data, { now = Date.now(), random = Math.random, stripe } = {}) {
  const lines = parseRequest(data);
  const reservations = await writeReservations(db, uid, data.paymentMethod, lines, now, random);
  if (data.paymentMethod !== "card") return { reservations: reservations.map(summary) };

  const reservationIds = reservations.map((r) => r.reservationId);
  const amount = reservations.reduce((sum, r) => sum + toCents(r.total), 0);
  let intent;
  try {
    intent = await stripe.paymentIntents.create({
      amount,
      currency: "eur",
      automatic_payment_methods: { enabled: true },
      description: `QLess reservation ${reservationIds.join(", ")}`,
      metadata: { userId: uid, reservationIds: reservationIds.join(",") },
    }, { idempotencyKey: `checkout-${reservationIds[0]}` });
  } catch (err) {
    // console.error shows up as an error in Cloud Logging; firebase-functions'
    // logger is avoided here so this module stays loadable in emulator tests.
    console.error("Creating PaymentIntent failed", { reservationIds, error: err.message });
    await cancelPending(db, reservationIds, now);
    throw new ReservationError("unavailable", "Payment could not be started. Please try again.",
      { reason: "payment-setup-failed" });
  }

  const batch = db.batch();
  for (const id of reservationIds) {
    batch.update(db.doc(`reservations/${id}`), { paymentIntentId: intent.id });
  }
  await batch.commit();

  return {
    reservations: reservations.map(summary),
    paymentIntentId: intent.id,
    clientSecret: intent.client_secret,
  };
}

// The shopper closed the payment sheet or the payment failed: cancel the
// PaymentIntent (unless it already succeeded) and release the stock.
async function cancelPendingPayment(db, uid, data, { stripe, now = Date.now() } = {}) {
  const paymentIntentId = data && data.paymentIntentId;
  if (typeof paymentIntentId !== "string" || !/^pi_[A-Za-z0-9_]+$/.test(paymentIntentId)) {
    throw new ReservationError("invalid-argument", "paymentIntentId is required");
  }

  const snap = await db.collection("reservations").where("paymentIntentId", "==", paymentIntentId).get();
  if (snap.empty || snap.docs.some((d) => d.data().userId !== uid)) {
    throw new ReservationError("not-found", "No such pending payment.");
  }

  const intent = await stripe.paymentIntents.retrieve(paymentIntentId);
  if (intent.status === "succeeded") return { cancelled: 0, paid: true };
  if (intent.status !== "canceled") await stripe.paymentIntents.cancel(paymentIntentId);

  const cancelled = await cancelPending(db, snap.docs.map((d) => d.id), now);
  return { cancelled, paid: false };
}

module.exports = { createReservations, cancelPending, cancelPendingPayment };
