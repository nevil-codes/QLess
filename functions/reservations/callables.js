const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { getFirestore } = require("firebase-admin/firestore");

const { ReservationError } = require("./plan");
const { createPickupReservations } = require("./store");

// invoker: "public" — see recs/callables.js; sign-in is enforced below.
exports.createReservation = onCall({ invoker: "public" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to reserve items.");
  try {
    return await createPickupReservations(getFirestore(), request.auth.uid, request.data);
  } catch (err) {
    if (err instanceof ReservationError) throw new HttpsError(err.code, err.message, err.details);
    throw err;
  }
});
