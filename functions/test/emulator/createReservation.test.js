// Runs against the Functions + Firestore emulators (npm run test:emulator).
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const PROJECT = process.env.GCLOUD_PROJECT || "demo-qless";
if (!getApps().length) initializeApp({ projectId: PROJECT });
const db = getFirestore();
const { createReservations, cancelPendingPayment } = require("../../reservations/store");

const tag = Date.now();
const id = (name) => `${name}-${tag}`;

beforeAll(async () => {
  await db.doc(`stores/${id("s1")}`).set({ name: "FreshHub", address: { formattedAddress: "Main St 1" } });
  await db.doc(`stores/${id("s2")}`).set({ name: "Corner Market" });
  await db.doc(`products/${id("milk")}`).set({
    name: "Milk",
    prices: [
      { storeId: id("s1"), price: 1.19, inStock: true, quantity: 5 },
      { storeId: id("s2"), price: 1.39, inStock: true, quantity: 5 },
    ],
  });
  await db.doc(`products/${id("last")}`).set({
    name: "Last one", prices: [{ storeId: id("s1"), price: 9.99, inStock: true, quantity: 1 }],
  });
  await db.doc(`users/${id("striker")}`).set({ noShowCount: 3 });
});

const request = (items) => ({ paymentMethod: "pickup", items });

test("creates one reservation per store and holds stock", async () => {
  const result = await createReservations(db, id("buyer"), request([
    { productId: id("milk"), storeId: id("s1"), quantity: 2 },
    { productId: id("milk"), storeId: id("s2"), quantity: 1 },
  ]));

  expect(result.reservations).toHaveLength(2);
  for (const r of result.reservations) {
    expect(r.pickupCode).toMatch(/^QL·\d{4}$/);
    const doc = (await db.doc(`reservations/${r.reservationId}`).get()).data();
    expect(doc).toMatchObject({ userId: id("buyer"), status: "reserved", paymentMethod: "pickup", serviceFee: 0 });
    expect(doc.pickupDeadline - doc.createdAt).toBe(12 * 60 * 60 * 1000);
  }

  const milk = (await db.doc(`products/${id("milk")}`).get()).data();
  expect(milk.prices.map((p) => p.quantity)).toEqual([3, 4]);
});

test("two shoppers racing for the last unit: exactly one gets it", async () => {
  const attempt = (uid) => createReservations(db, uid, request([
    { productId: id("last"), storeId: id("s1"), quantity: 1 },
  ])).then(() => "ok", (err) => err.code);

  const results = await Promise.all([attempt(id("a")), attempt(id("b"))]);
  expect(results.sort()).toEqual(["failed-precondition", "ok"]);
  const last = (await db.doc(`products/${id("last")}`).get()).data();
  expect(last.prices[0].quantity).toBe(0);
});

test("users with 3 no-shows are refused and nothing is written", async () => {
  await expect(createReservations(db, id("striker"), request([
    { productId: id("milk"), storeId: id("s1"), quantity: 1 },
  ]))).rejects.toMatchObject({ code: "failed-precondition", details: { reason: "prepay-required" } });
  const mine = await db.collection("reservations").where("userId", "==", id("striker")).get();
  expect(mine.empty).toBe(true);
});

test("pickup codes don't repeat among a store's active reservations", async () => {
  let n = 0;
  const codes = [0.1111, 0.1111, 0.2222]; // second attempt clashes, third is free
  const random = () => codes[n++ % codes.length];
  const first = await createReservations(db, id("c1"), request([
    { productId: id("milk"), storeId: id("s2"), quantity: 1 },
  ]), { random });
  const second = await createReservations(db, id("c2"), request([
    { productId: id("milk"), storeId: id("s2"), quantity: 1 },
  ]), { random });
  expect(first.reservations[0].pickupCode).toBe("QL·1111");
  expect(second.reservations[0].pickupCode).toBe("QL·2222");
});

test("the callable requires sign-in", async () => {
  const host = process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1:5001";
  const res = await fetch(`http://${host}/${PROJECT}/europe-west1/createReservation`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data: request([{ productId: id("milk"), storeId: id("s1"), quantity: 1 }]) }),
  });
  expect((await res.json()).error.status).toBe("UNAUTHENTICATED");
});

// --- Card payments (Stripe faked) --------------------------------------

let intentCount = 0;

function fakeStripe({ failCreate = false, status = "requires_payment_method" } = {}) {
  const calls = { create: [], cancel: [] };
  return {
    calls,
    paymentIntents: {
      create: async (params, opts) => {
        calls.create.push({ params, opts });
        if (failCreate) throw new Error("stripe down");
        intentCount += 1;
        const piId = `pi_test${tag}${intentCount}`;
        return { id: piId, client_secret: `${piId}_secret_x` };
      },
      retrieve: async (id) => ({ id, status }),
      cancel: async (id) => { calls.cancel.push(id); return { id, status: "canceled" }; },
    },
  };
}

const cardRequest = (items) => ({ paymentMethod: "card", items });

test("card checkout: pending reservations, one PaymentIntent for the total", async () => {
  const stripe = fakeStripe();
  const result = await createReservations(db, id("payer"), cardRequest([
    { productId: id("milk"), storeId: id("s1"), quantity: 1 },
    { productId: id("milk"), storeId: id("s2"), quantity: 1 },
  ]), { stripe });

  expect(result.clientSecret).toMatch(/_secret_/);
  const [{ params, opts }] = stripe.calls.create;
  expect(params).toMatchObject({ amount: 119 + 99 + 139, currency: "eur" });
  expect(params.metadata.reservationIds.split(",")).toHaveLength(2);
  expect(opts.idempotencyKey).toMatch(/^checkout-RES-/);

  for (const r of result.reservations) {
    const doc = (await db.doc(`reservations/${r.reservationId}`).get()).data();
    expect(doc).toMatchObject({
      status: "pending_payment", paymentStatus: "pending", paymentIntentId: result.paymentIntentId,
    });
  }
});

test("if Stripe fails, the reservations are cancelled and stock returned", async () => {
  const before = (await db.doc(`products/${id("milk")}`).get()).data().prices[0].quantity;
  await expect(createReservations(db, id("unlucky"), cardRequest([
    { productId: id("milk"), storeId: id("s1"), quantity: 1 },
  ]), { stripe: fakeStripe({ failCreate: true }) }))
    .rejects.toMatchObject({ code: "unavailable", details: { reason: "payment-setup-failed" } });

  const mine = await db.collection("reservations").where("userId", "==", id("unlucky")).get();
  expect(mine.docs.map((d) => d.data().status)).toEqual(["cancelled"]);
  expect((await db.doc(`products/${id("milk")}`).get()).data().prices[0].quantity).toBe(before);
});

test("closing the payment sheet cancels the intent and releases stock", async () => {
  const stripe = fakeStripe();
  const before = (await db.doc(`products/${id("milk")}`).get()).data().prices[0].quantity;
  const { paymentIntentId, reservations } = await createReservations(db, id("quitter"), cardRequest([
    { productId: id("milk"), storeId: id("s1"), quantity: 2 },
  ]), { stripe });
  expect((await db.doc(`products/${id("milk")}`).get()).data().prices[0].quantity).toBe(before - 2);

  await expect(cancelPendingPayment(db, id("someone-else"), { paymentIntentId }, { stripe }))
    .rejects.toMatchObject({ code: "not-found" });

  const result = await cancelPendingPayment(db, id("quitter"), { paymentIntentId }, { stripe });
  expect(result).toEqual({ cancelled: 1, paid: false });
  expect(stripe.calls.cancel).toEqual([paymentIntentId]);
  const doc = (await db.doc(`reservations/${reservations[0].reservationId}`).get()).data();
  expect(doc).toMatchObject({ status: "cancelled", paymentStatus: "canceled" });
  expect((await db.doc(`products/${id("milk")}`).get()).data().prices[0].quantity).toBe(before);

  // Calling again (or the webhook arriving later) changes nothing.
  expect((await cancelPendingPayment(db, id("quitter"), { paymentIntentId }, { stripe })).cancelled).toBe(0);
  expect((await db.doc(`products/${id("milk")}`).get()).data().prices[0].quantity).toBe(before);
});

test("a payment that already went through is not cancelled", async () => {
  const stripe = fakeStripe({ status: "succeeded" });
  const { paymentIntentId } = await createReservations(db, id("fast"), cardRequest([
    { productId: id("milk"), storeId: id("s1"), quantity: 1 },
  ]), { stripe });
  expect(await cancelPendingPayment(db, id("fast"), { paymentIntentId }, { stripe }))
    .toEqual({ cancelled: 0, paid: true });
  expect(stripe.calls.cancel).toEqual([]);
});
