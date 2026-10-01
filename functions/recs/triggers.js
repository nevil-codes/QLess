const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const { getFirestore } = require("firebase-admin/firestore");

const { applyEvent, emptyProfile } = require("./profile");
const { sanitizeEvent } = require("./events");

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

exports.onUserEventCreated = onDocumentCreated("user_events/{eventId}", async (change) => {
  const event = sanitizeEvent(change.data && change.data.data());
  if (!event) {
    logger.warn("Ignoring invalid user event", { eventId: change.params.eventId });
    return;
  }
  await foldEventIntoProfile(getFirestore(), change.params.eventId, event);
});

exports.foldEventIntoProfile = foldEventIntoProfile;
