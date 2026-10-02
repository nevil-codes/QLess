const { defineSecret } = require("firebase-functions/params");

// Values live in Secret Manager: firebase functions:secrets:set <NAME>
exports.STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");
exports.STRIPE_WEBHOOK_SECRET = defineSecret("STRIPE_WEBHOOK_SECRET");
