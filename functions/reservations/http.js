const { onRequest } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const { getFirestore } = require("firebase-admin/firestore");
const Stripe = require("stripe");

const { handleStripeEvent } = require("./webhook");

const { STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET } = require("./secrets");

// Stripe calls this directly, so it must be public; every request is
// authenticated by its Stripe-Signature header instead.
exports.stripeWebhook = onRequest(
  { invoker: "public", secrets: [STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method not allowed");
      return;
    }

    let event;
    try {
      const stripe = new Stripe(STRIPE_SECRET_KEY.value());
      event = stripe.webhooks.constructEvent(
        req.rawBody, req.get("stripe-signature"), STRIPE_WEBHOOK_SECRET.value());
    } catch (err) {
      logger.warn("Rejected Stripe webhook", { error: err.message });
      res.status(400).send("Invalid signature");
      return;
    }

    const outcome = await handleStripeEvent(getFirestore(), event);
    logger.info("Stripe webhook handled", { id: event.id, type: event.type, outcome });
    res.json({ received: true, outcome });
  },
);
