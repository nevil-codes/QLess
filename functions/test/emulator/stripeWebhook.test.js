// Runs against the Functions + Firestore emulators (npm run test:emulator).
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const Stripe = require("stripe");

const PROJECT = process.env.GCLOUD_PROJECT || "demo-qless";
if (!getApps().length) initializeApp({ projectId: PROJECT });
const db = getFirestore();
const { handleStripeEvent } = require("../../reservations/webhook");

const tag = Date.now();
const H = 60 * 60 * 1000;
// Must match functions/.secret.local written by `npm run test:emulator`.
const WEBHOOK_SECRET = "whsec_emulator";

async function seed(name, { piId, status = "pending_payment", quantity = 2 } = {}) {
  const productId = `p-${name}-${tag}`;
  const reservationId = `RES-${tag}-${name}`;
  await db.doc(`products/${productId}`).set({
    name, prices: [{ storeId: "s1", price: 2, inStock: true, quantity: 3 }],
  });
  await db.doc(`reservations/${reservationId}`).set({
    reservationId, userId: "u1", status, paymentStatus: "pending", paymentMethod: "card",
    paymentIntentId: piId, createdAt: 0, pickupDeadline: 48 * H,
    items: [{ productId, storeId: "s1", price: 2, quantity }],
  });
  return { productId, reservationId };
}

const intentEvent = (type, piId, reservationIds) => ({
  id: `evt_${tag}_${type}`,
  type,
  data: { object: { object: "payment_intent", id: piId, metadata: { reservationIds: reservationIds.join(",") } } },
});

test("succeeded: reserved and paid, 48h counted from payment", async () => {
  const piId = `pi_ok_${tag}`;
  const { reservationId } = await seed("ok", { piId });
  const now = Date.UTC(2026, 9, 2, 12);

  expect(await handleStripeEvent(db, intentEvent("payment_intent.succeeded", piId, [reservationId]), now))
    .toBe("paid:1");
  const doc = (await db.doc(`reservations/${reservationId}`).get()).data();
  expect(doc).toMatchObject({ status: "reserved", paymentStatus: "paid", paidAt: now, pickupDeadline: now + 48 * H });

  // Stripe retries deliveries; a replay must not change anything.
  expect(await handleStripeEvent(db, intentEvent("payment_intent.succeeded", piId, [reservationId]), now + 5000))
    .toBe("paid:0");
  expect((await db.doc(`reservations/${reservationId}`).get()).data().paidAt).toBe(now);
});

test("an event for a different PaymentIntent doesn't touch the reservation", async () => {
  const { reservationId } = await seed("mismatch", { piId: `pi_real_${tag}` });
  expect(await handleStripeEvent(db, intentEvent("payment_intent.succeeded", `pi_other_${tag}`, [reservationId])))
    .toBe("paid:0");
  expect((await db.doc(`reservations/${reservationId}`).get()).data().status).toBe("pending_payment");
});

test("canceled: reservation cancelled and stock released once", async () => {
  const piId = `pi_cancel_${tag}`;
  const { productId, reservationId } = await seed("cancel", { piId, quantity: 2 });

  expect(await handleStripeEvent(db, intentEvent("payment_intent.canceled", piId, [reservationId]))).toBe("cancelled:1");
  expect(await handleStripeEvent(db, intentEvent("payment_intent.canceled", piId, [reservationId]))).toBe("cancelled:0");
  expect((await db.doc(`reservations/${reservationId}`).get()).data().status).toBe("cancelled");
  expect((await db.doc(`products/${productId}`).get()).data().prices[0].quantity).toBe(5);
});

test("a failed attempt leaves the reservation pending for a retry", async () => {
  const piId = `pi_fail_${tag}`;
  const { reservationId } = await seed("fail", { piId });
  expect(await handleStripeEvent(db, intentEvent("payment_intent.payment_failed", piId, [reservationId])))
    .toBe("failed-attempt");
  expect((await db.doc(`reservations/${reservationId}`).get()).data().status).toBe("pending_payment");
});

test("the HTTP endpoint accepts signed events and rejects forged ones", async () => {
  const piId = `pi_http_${tag}`;
  const { reservationId } = await seed("http", { piId });
  const host = process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1:5001";
  const url = `http://${host}/${PROJECT}/europe-west1/stripeWebhook`;
  const payload = JSON.stringify(intentEvent("payment_intent.succeeded", piId, [reservationId]));
  const stripe = new Stripe("sk_test_unused");

  const forged = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Stripe-Signature": "t=1,v1=deadbeef" },
    body: payload,
  });
  expect(forged.status).toBe(400);
  expect((await db.doc(`reservations/${reservationId}`).get()).data().status).toBe("pending_payment");

  const signature = stripe.webhooks.generateTestHeaderString({ payload, secret: WEBHOOK_SECRET });
  const signed = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Stripe-Signature": signature },
    body: payload,
  });
  expect(signed.status).toBe(200);
  expect((await signed.json()).outcome).toBe("paid:1");
  expect((await db.doc(`reservations/${reservationId}`).get()).data().status).toBe("reserved");
});
