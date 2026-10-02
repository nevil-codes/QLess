// Runs against the Functions + Firestore emulators (npm run test:emulator).
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const PROJECT = process.env.GCLOUD_PROJECT || "demo-qless";
if (!getApps().length) initializeApp({ projectId: PROJECT });
const db = getFirestore();
const { createPickupReservations } = require("../../reservations/store");

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
  const result = await createPickupReservations(db, id("buyer"), request([
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
  const attempt = (uid) => createPickupReservations(db, uid, request([
    { productId: id("last"), storeId: id("s1"), quantity: 1 },
  ])).then(() => "ok", (err) => err.code);

  const results = await Promise.all([attempt(id("a")), attempt(id("b"))]);
  expect(results.sort()).toEqual(["failed-precondition", "ok"]);
  const last = (await db.doc(`products/${id("last")}`).get()).data();
  expect(last.prices[0].quantity).toBe(0);
});

test("users with 3 no-shows are refused and nothing is written", async () => {
  await expect(createPickupReservations(db, id("striker"), request([
    { productId: id("milk"), storeId: id("s1"), quantity: 1 },
  ]))).rejects.toMatchObject({ code: "failed-precondition", details: { reason: "prepay-required" } });
  const mine = await db.collection("reservations").where("userId", "==", id("striker")).get();
  expect(mine.empty).toBe(true);
});

test("pickup codes don't repeat among a store's active reservations", async () => {
  let n = 0;
  const codes = [0.1111, 0.1111, 0.2222]; // second attempt clashes, third is free
  const random = () => codes[n++ % codes.length];
  const first = await createPickupReservations(db, id("c1"), request([
    { productId: id("milk"), storeId: id("s2"), quantity: 1 },
  ]), { random });
  const second = await createPickupReservations(db, id("c2"), request([
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
