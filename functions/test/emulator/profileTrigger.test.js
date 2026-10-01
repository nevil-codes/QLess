// Runs against the Functions + Firestore emulators (npm run test:emulator).
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

initializeApp({ projectId: process.env.GCLOUD_PROJECT || "demo-qless" });
const db = getFirestore();

async function waitFor(fn, timeoutMs = 15000) {
  const start = Date.now();
  for (;;) {
    const value = await fn();
    if (value) return value;
    if (Date.now() - start > timeoutMs) throw new Error("timed out");
    await new Promise((r) => setTimeout(r, 250));
  }
}

test("a logged event builds the user's profile", async () => {
  const uid = `u-${Date.now()}`;
  const now = Date.now();
  await db.collection("user_events").add({
    userId: uid, eventType: "add_to_cart", productId: "p1",
    category: "Dairy", brand: "Acme", price: 2, timestamp: now,
  });
  await db.collection("user_events").add({
    userId: uid, eventType: "view", productId: "p2",
    category: "Bakery", brand: "Oven", price: 0, timestamp: now,
  });

  const profile = await waitFor(async () => {
    const snap = await db.doc(`user_profiles/${uid}`).get();
    return snap.exists && snap.data().totalEvents === 2 ? snap.data() : null;
  });

  expect(profile.categoryPrefs.Dairy).toBeCloseTo(3);
  expect(profile.categoryPrefs.Bakery).toBeCloseTo(1, 1);
  expect(profile.avgPrice).toBeCloseTo(2);
  expect(profile.recentProductIds).toHaveLength(2);
  expect(profile.processedEventIds).toHaveLength(2);
});

test("invalid events are ignored", async () => {
  const uid = `bad-${Date.now()}`;
  await db.collection("user_events").add({ userId: uid, eventType: "hack", category: "X" });
  await new Promise((r) => setTimeout(r, 3000));
  const snap = await db.doc(`user_profiles/${uid}`).get();
  expect(snap.exists).toBe(false);
});
