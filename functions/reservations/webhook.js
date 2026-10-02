// Routes verified Stripe events to reservation state changes. Signature
// verification happens in the HTTP wrapper (reservations/http.js).

const { cancelPending, markPaid } = require("./store");

function reservationIdsOf(intent) {
  const raw = (intent.metadata && intent.metadata.reservationIds) || "";
  return raw.split(",").map((id) => id.trim()).filter((id) => /^RES-[\w-]+$/.test(id));
}

// Returns a short description of what happened, for logs and tests.
async function handleStripeEvent(db, event, now = Date.now()) {
  const intent = event.data && event.data.object;
  if (!intent || intent.object !== "payment_intent") return "ignored";
  const ids = reservationIdsOf(intent);

  switch (event.type) {
    case "payment_intent.succeeded":
      return `paid:${await markPaid(db, ids, intent.id, now)}`;
    case "payment_intent.canceled":
      return `cancelled:${await cancelPending(db, ids, now)}`;
    case "payment_intent.payment_failed":
      // The shopper can retry inside the payment sheet; the app cancels via
      // cancelPendingPayment if they give up.
      return "failed-attempt";
    default:
      return "ignored";
  }
}

module.exports = { handleStripeEvent };
