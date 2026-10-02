const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { getFirestore } = require("firebase-admin/firestore");
const Stripe = require("stripe");

const { ReservationError } = require("./plan");
const { createReservations, cancelPendingPayment } = require("./store");

const STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");

// invoker: "public" — see recs/callables.js; sign-in is enforced below.
const OPTIONS = { invoker: "public", secrets: [STRIPE_SECRET_KEY] };

function signedIn(handler) {
  return onCall(OPTIONS, async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to reserve items.");
    try {
      const stripe = new Stripe(STRIPE_SECRET_KEY.value());
      return await handler(getFirestore(), request.auth.uid, request.data, { stripe });
    } catch (err) {
      if (err instanceof ReservationError) throw new HttpsError(err.code, err.message, err.details);
      throw err;
    }
  });
}

exports.createReservation = signedIn(createReservations);
exports.cancelPendingPayment = signedIn(cancelPendingPayment);
