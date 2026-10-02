// Runs against the Functions + Firestore emulators (npm run test:emulator).
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

if (!getApps().length) initializeApp({ projectId: process.env.GCLOUD_PROJECT || "demo-qless" });
const db = getFirestore();
const {
  cancelAbandoned, cancelReservation, expireDue, retryRefunds,
} = require("../../reservations/lifecycleStore");

const tag = Date.now();
const H = 60 * 60 * 1000;
const NOW = Date.UTC(2030, 0, 1, 12); // far future so other suites' docs aren't "due" with a later NOW

function fakeStripe({ refundFails = false, intentStatus = "requires_payment_method" } = {}) {
  const calls = { refunds: [], cancels: [] };
  return {
    calls,
    refunds: {
      create: async (params, opts) => {
        calls.refunds.push({ ...params, key: opts.idempotencyKey });
        if (refundFails) throw new Error("stripe down");
        return { id: `re_${calls.refunds.length}` };
      },
    },
    paymentIntents: {
      retrieve: async (id) => ({ id, status: intentStatus }),
      cancel: async (id) => { calls.cancels.push(id); return { id, status: "canceled" }; },
    },
  };
}

async function seed(name, reservation) {
  const productId = `p-${name}-${tag}`;
  const reservationId = `RES-${tag}-${name}`;
  await db.doc(`products/${productId}`).set({
    name, prices: [{ storeId: "s1", price: 2, inStock: true, quantity: 3 }],
  });
  await db.doc(`reservations/${reservationId}`).set({
    reservationId, userId: `u-${name}-${tag}`, storeId: "s1",
    items: [{ productId, storeId: "s1", price: 2, quantity: 2 }],
    createdAt: NOW - 50 * H,
    ...reservation,
  });
  return { productId, reservationId, userId: `u-${name}-${tag}` };
}

const stock = async (productId) => (await db.doc(`products/${productId}`).get()).data().prices[0].quantity;
const res = async (id) => (await db.doc(`reservations/${id}`).get()).data();

describe("expireDue", () => {
  test("pickup no-show: expired, strike recorded, stock returned", async () => {
    const s = await seed("noshow", {
      status: "reserved", paymentMethod: "pickup", paymentStatus: "none", total: 4, pickupDeadline: NOW - 1,
    });
    const notDue = await seed("notdue", {
      status: "reserved", paymentMethod: "pickup", paymentStatus: "none", total: 4, pickupDeadline: NOW + H,
    });

    const result = await expireDue(db, { stripe: fakeStripe(), now: NOW });
    expect(result.expired).toBeGreaterThanOrEqual(1);
    expect(await res(s.reservationId)).toMatchObject({ status: "expired", noShow: true, expiredAt: NOW });
    expect(await stock(s.productId)).toBe(5);
    expect((await db.doc(`users/${s.userId}`).get()).data().noShowCount).toBe(1);
    expect((await res(notDue.reservationId)).status).toBe("reserved");
  });

  test("prepaid not collected: expired and refunded 90%, no strike", async () => {
    const s = await seed("prepaid", {
      status: "reserved", paymentMethod: "card", paymentStatus: "paid", paymentIntentId: `pi_${tag}_a`,
      total: 10.98, pickupDeadline: NOW - 1,
    });
    const stripe = fakeStripe();
    await expireDue(db, { stripe, now: NOW });

    const refundCall = stripe.calls.refunds.find((r) => r.payment_intent === `pi_${tag}_a`);
    expect(refundCall).toMatchObject({ amount: 988, key: `refund-${s.reservationId}` });
    expect(await res(s.reservationId)).toMatchObject({
      status: "expired", paymentStatus: "partially_refunded", refundCents: 988,
    });
    expect(await stock(s.productId)).toBe(5);
    expect((await db.doc(`users/${s.userId}`).get()).exists).toBe(false);
  });

  test("running twice doesn't double-count strikes or stock", async () => {
    const s = await seed("twice", {
      status: "reserved", paymentMethod: "pickup", paymentStatus: "none", total: 4, pickupDeadline: NOW - 1,
    });
    await expireDue(db, { stripe: fakeStripe(), now: NOW });
    await expireDue(db, { stripe: fakeStripe(), now: NOW + 1000 });
    expect(await stock(s.productId)).toBe(5);
    expect((await db.doc(`users/${s.userId}`).get()).data().noShowCount).toBe(1);
  });
});

describe("refund retries", () => {
  test("a failed refund stays pending and is retried with the same idempotency key", async () => {
    const s = await seed("retry", {
      status: "reserved", paymentMethod: "card", paymentStatus: "paid", paymentIntentId: `pi_${tag}_r`,
      total: 5, pickupDeadline: NOW - 1,
    });
    const down = fakeStripe({ refundFails: true });
    await expireDue(db, { stripe: down, now: NOW });
    expect(await res(s.reservationId)).toMatchObject({
      status: "expired", paymentStatus: "refund_pending", refundCents: 450,
    });

    const up = fakeStripe();
    await retryRefunds(db, { stripe: up });
    const retried = up.calls.refunds.find((r) => r.payment_intent === `pi_${tag}_r`);
    expect(retried).toMatchObject({ amount: 450, key: `refund-${s.reservationId}` });
    expect((await res(s.reservationId)).paymentStatus).toBe("partially_refunded");
  });
});

describe("cancelReservation", () => {
  test("pickup: cancelled, no strike, stock returned", async () => {
    const s = await seed("cancelpickup", {
      status: "reserved", paymentMethod: "pickup", paymentStatus: "none", total: 4, pickupDeadline: NOW + H,
    });
    const out = await cancelReservation(db, s.userId, { reservationId: s.reservationId }, { stripe: fakeStripe(), now: NOW });
    expect(out).toEqual({ status: "cancelled", refundCents: 0, refundStatus: "none" });
    expect(await stock(s.productId)).toBe(5);
    expect((await db.doc(`users/${s.userId}`).get()).exists).toBe(false);
  });

  test("card: full refund including the fee", async () => {
    const s = await seed("cancelcard", {
      status: "reserved", paymentMethod: "card", paymentStatus: "paid", paymentIntentId: `pi_${tag}_c`,
      total: 10.98, pickupDeadline: NOW + H,
    });
    const stripe = fakeStripe();
    const out = await cancelReservation(db, s.userId, { reservationId: s.reservationId }, { stripe, now: NOW });
    expect(out).toEqual({ status: "cancelled", refundCents: 1098, refundStatus: "refunded" });
    expect(stripe.calls.refunds).toEqual([
      { payment_intent: `pi_${tag}_c`, amount: 1098, key: `refund-${s.reservationId}` },
    ]);
    expect((await res(s.reservationId)).paymentStatus).toBe("refunded");
  });

  test("only the owner, only while active", async () => {
    const s = await seed("guard", {
      status: "reserved", paymentMethod: "pickup", paymentStatus: "none", total: 4, pickupDeadline: NOW + H,
    });
    const stripe = fakeStripe();
    await expect(cancelReservation(db, "intruder", { reservationId: s.reservationId }, { stripe, now: NOW }))
      .rejects.toMatchObject({ code: "not-found" });
    await cancelReservation(db, s.userId, { reservationId: s.reservationId }, { stripe, now: NOW });
    await expect(cancelReservation(db, s.userId, { reservationId: s.reservationId }, { stripe, now: NOW }))
      .rejects.toMatchObject({ code: "failed-precondition" });
    expect(await stock(s.productId)).toBe(5);
  });
});

describe("cancelAbandoned", () => {
  test("cancels stale pending checkouts and returns stock", async () => {
    const stale = await seed("stale", {
      status: "pending_payment", paymentMethod: "card", paymentStatus: "pending",
      paymentIntentId: `pi_${tag}_stale`, total: 4, pickupDeadline: NOW + 47 * H, createdAt: NOW - 31 * 60000,
    });
    const fresh = await seed("fresh", {
      status: "pending_payment", paymentMethod: "card", paymentStatus: "pending",
      paymentIntentId: `pi_${tag}_fresh`, total: 4, pickupDeadline: NOW + 47 * H, createdAt: NOW - 60000,
    });
    const stripe = fakeStripe();
    await cancelAbandoned(db, { stripe, now: NOW });

    expect(stripe.calls.cancels).toContain(`pi_${tag}_stale`);
    expect(stripe.calls.cancels).not.toContain(`pi_${tag}_fresh`);
    expect((await res(stale.reservationId)).status).toBe("cancelled");
    expect(await stock(stale.productId)).toBe(5);
    expect((await res(fresh.reservationId)).status).toBe("pending_payment");
  });

  test("a payment that went through is left for the webhook", async () => {
    const s = await seed("latepaid", {
      status: "pending_payment", paymentMethod: "card", paymentStatus: "pending",
      paymentIntentId: `pi_${tag}_late`, total: 4, pickupDeadline: NOW + 47 * H, createdAt: NOW - 31 * 60000,
    });
    const stripe = fakeStripe({ intentStatus: "succeeded" });
    await cancelAbandoned(db, { stripe, now: NOW });
    expect(stripe.calls.cancels).not.toContain(`pi_${tag}_late`);
    expect((await res(s.reservationId)).status).toBe("pending_payment");
  });
});

test("the cancelReservation callable requires sign-in", async () => {
  const host = process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1:5001";
  const project = process.env.GCLOUD_PROJECT || "demo-qless";
  const res = await fetch(`http://${host}/${project}/europe-west1/cancelReservation`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data: { reservationId: "RES-1-x" } }),
  });
  expect((await res.json()).error.status).toBe("UNAUTHENTICATED");
});
