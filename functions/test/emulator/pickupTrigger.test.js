// Runs against the Functions + Firestore emulators (npm run test:emulator).
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

if (!getApps().length) initializeApp({ projectId: process.env.GCLOUD_PROJECT || "demo-qless" });
const db = getFirestore();
const { recordPickup } = require("../../recs/store");

async function waitFor(fn, timeoutMs = 15000) {
  const start = Date.now();
  for (;;) {
    const value = await fn();
    if (value) return value;
    if (Date.now() - start > timeoutMs) throw new Error("timed out");
    await new Promise((r) => setTimeout(r, 250));
  }
}

test("picking up a reservation logs purchases and co-purchases once", async () => {
  const tag = Date.now();
  const [milk, bread, jam] = [`milk${tag}`, `bread${tag}`, `jam${tag}`];
  await db.doc(`products/${milk}`).set({ name: "Milk", category: "Dairy", brand: "Acme" });
  await db.doc(`products/${bread}`).set({ name: "Bread", category: "Bakery", brand: "Oven" });
  await db.doc(`products/${jam}`).set({ name: "Jam", category: "Spreads", brand: "Fruity" });

  const uid = `buyer${tag}`;
  const resRef = db.doc(`reservations/RES-${tag}`);
  await resRef.set({
    userId: uid, status: "reserved", storeId: "s1",
    items: [{ productId: milk, price: 1.2 }, { productId: bread, price: 2.5 }, { productId: jam, price: 3 }],
  });
  await resRef.update({ status: "picked_up" });

  const co = await waitFor(async () => {
    const snap = await db.doc(`co_purchases/${milk}`).get();
    return snap.exists ? snap.data() : null;
  });
  expect(co.counts).toEqual({ [bread]: 1, [jam]: 1 });

  const profile = await waitFor(async () => {
    const snap = await db.doc(`user_profiles/${uid}`).get();
    return snap.exists && snap.data().totalEvents === 3 ? snap.data() : null;
  });
  expect(Object.keys(profile.purchased).sort()).toEqual([bread, jam, milk].sort());
  expect(profile.categoryPrefs.Dairy).toBeCloseTo(5);

  // A retried trigger or a later edit must not count the pickup again.
  expect(await recordPickup(db, `RES-${tag}`, (await resRef.get()).data())).toBe(false);
  await resRef.update({ note: "edited after pickup" });
  await new Promise((r) => setTimeout(r, 2000));
  expect((await db.doc(`co_purchases/${milk}`).get()).data().counts[bread]).toBe(1);
});
