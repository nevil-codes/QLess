// Validation for client-written user_events before they reach a profile.

const { EVENT_WEIGHTS } = require("./profile");

const MAX_TEXT = 100;
const MAX_PRICE = 100000;
const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;

function text(value) {
  return typeof value === "string" ? value.trim().slice(0, MAX_TEXT) : "";
}

// Returns a clean event, or null if it can't be used. Timestamps outside
// [now - 1 day, now + 5 min] are replaced with `now` so a skewed or forged
// client clock can't make an event look fresher or older than it is.
function sanitizeEvent(raw, now = Date.now()) {
  if (!raw || typeof raw.userId !== "string" || !raw.userId) return null;
  if (!Object.prototype.hasOwnProperty.call(EVENT_WEIGHTS, raw.eventType)) return null;

  const price = Number(raw.price);
  const ts = Number(raw.timestamp);
  const plausibleTs = Number.isFinite(ts) && ts >= now - 24 * 60 * 60 * 1000 && ts <= now + MAX_CLOCK_SKEW_MS;

  return {
    userId: raw.userId,
    eventType: raw.eventType,
    productId: typeof raw.productId === "string" ? raw.productId.slice(0, 200) : "",
    category: text(raw.category),
    brand: text(raw.brand),
    price: Number.isFinite(price) && price > 0 && price <= MAX_PRICE ? price : 0,
    timestamp: plausibleTs ? ts : now,
  };
}

module.exports = { sanitizeEvent };
