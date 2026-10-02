const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const { getFirestore } = require("firebase-admin/firestore");
const Stripe = require("stripe");

const { STRIPE_SECRET_KEY } = require("./secrets");
const { cancelAbandoned, expireDue, retryRefunds } = require("./lifecycleStore");

// Ends reservations on time: abandoned card checkouts, expired pickup
// windows (strikes / 90% refunds) and refunds that failed last run.
exports.expireReservations = onSchedule(
  { schedule: "every 15 minutes", timeZone: "Europe/Berlin", secrets: [STRIPE_SECRET_KEY] },
  async () => {
    const db = getFirestore();
    const stripe = new Stripe(STRIPE_SECRET_KEY.value());
    const abandoned = await cancelAbandoned(db, { stripe });
    const expired = await expireDue(db, { stripe });
    const retried = await retryRefunds(db, { stripe });
    logger.info("Reservation sweep", { abandoned, expired, retried });
  },
);
