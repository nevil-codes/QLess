const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const { getFirestore } = require("firebase-admin/firestore");

const { sanitizeEvent } = require("./events");
const { isNewPickup } = require("./purchases");
const { foldEventIntoProfile, recordPickup } = require("./store");

exports.onUserEventCreated = onDocumentCreated("user_events/{eventId}", async (change) => {
  const event = sanitizeEvent(change.data && change.data.data());
  if (!event) {
    logger.warn("Ignoring invalid user event", { eventId: change.params.eventId });
    return;
  }
  await foldEventIntoProfile(getFirestore(), change.params.eventId, event);
});

exports.onReservationUpdated = onDocumentUpdated("reservations/{reservationId}", async (change) => {
  const before = change.data.before.data();
  const after = change.data.after.data();
  if (!isNewPickup(before, after)) return;
  await recordPickup(getFirestore(), change.params.reservationId, after);
});
