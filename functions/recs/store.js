// Firestore reads and writes for recommendations. Kept free of
// firebase-functions so it can be exercised directly in emulator tests.

const { FieldValue } = require("firebase-admin/firestore");

const { applyEvent, emptyProfile } = require("./profile");
const { basketProductIds, coPurchaseUpdates, purchaseEvents } = require("./purchases");

// Firestore triggers can fire more than once for the same event; remember
// the last few processed ids so a retry doesn't count an event twice.
const PROCESSED_IDS_LIMIT = 50;

async function foldEventIntoProfile(db, eventId, event) {
  const ref = db.doc(`user_profiles/${event.userId}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const current = snap.exists ? snap.data() : emptyProfile();
    const processed = current.processedEventIds || [];
    if (processed.includes(eventId)) return;

    const next = applyEvent(current, event);
    next.processedEventIds = [eventId, ...processed].slice(0, PROCESSED_IDS_LIMIT);
    tx.set(ref, next);
  });
}

// When a reservation is picked up: log a purchase event per product (which
// feeds the buyer's profile) and count which products were bought together.
// The pickup_log marker is created in the same batch, so a retry fails on it
// and nothing is counted twice. Returns false if already recorded.
async function recordPickup(db, reservationId, reservation, now = Date.now()) {
  const productIds = basketProductIds(reservation);
  if (!productIds.length || !reservation.userId) return false;

  const productSnaps = await db.getAll(...productIds.map((id) => db.doc(`products/${id}`)));
  const productsById = {};
  for (const snap of productSnaps) if (snap.exists) productsById[snap.id] = snap.data();

  const batch = db.batch();
  batch.create(db.doc(`pickup_log/${reservationId}`), { userId: reservation.userId, at: now });
  for (const event of purchaseEvents(reservation, productsById, now)) {
    batch.create(db.doc(`user_events/${reservationId}_${event.productId}`), event);
  }
  for (const [productId, others] of Object.entries(coPurchaseUpdates(productIds))) {
    const counts = Object.fromEntries(others.map((id) => [id, FieldValue.increment(1)]));
    batch.set(db.doc(`co_purchases/${productId}`), { counts }, { merge: true });
  }

  try {
    await batch.commit();
    return true;
  } catch (err) {
    if (err.code === 6) return false; // ALREADY_EXISTS
    throw err;
  }
}

module.exports = { foldEventIntoProfile, recordPickup };
