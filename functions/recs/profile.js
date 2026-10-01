// User preference profile, built incrementally from behaviour events.
//
// Scores decay exponentially so recent behaviour outweighs old habits:
// after HALF_LIFE_DAYS an event counts half as much as a fresh one.

const DAY_MS = 24 * 60 * 60 * 1000;

const HALF_LIFE_DAYS = 30;
const EVENT_WEIGHTS = { view: 1, search: 0.5, add_to_cart: 3, purchase: 5 };
const PRUNE_BELOW = 0.01;
const RECENT_LIMIT = 20;
const PURCHASE_MEMORY_DAYS = 60;

function emptyProfile() {
  return {
    categoryPrefs: {},
    brandPrefs: {},
    priceSum: 0,
    priceWeight: 0,
    avgPrice: 0,
    recentProductIds: [],
    purchased: {},
    totalEvents: 0,
    lastUpdated: 0,
  };
}

function decayFactor(elapsedMs) {
  if (elapsedMs <= 0) return 1;
  return Math.pow(0.5, elapsedMs / (HALF_LIFE_DAYS * DAY_MS));
}

function decayMap(map, factor) {
  const out = {};
  for (const [key, value] of Object.entries(map)) {
    const decayed = value * factor;
    if (decayed >= PRUNE_BELOW) out[key] = decayed;
  }
  return out;
}

// Returns a new profile with `event` folded in. `event` needs eventType and
// timestamp (ms); productId, category, brand and price are optional.
function applyEvent(profile, event) {
  const base = { ...emptyProfile(), ...profile };
  const weight = EVENT_WEIGHTS[event.eventType];
  if (!weight) return base;

  const ts = event.timestamp || Date.now();
  const factor = base.lastUpdated ? decayFactor(ts - base.lastUpdated) : 1;

  const next = {
    ...base,
    categoryPrefs: decayMap(base.categoryPrefs, factor),
    brandPrefs: decayMap(base.brandPrefs, factor),
    priceSum: base.priceSum * factor,
    priceWeight: base.priceWeight * factor,
    lastUpdated: Math.max(base.lastUpdated, ts),
    totalEvents: base.totalEvents + 1,
  };

  if (event.category) {
    next.categoryPrefs[event.category] = (next.categoryPrefs[event.category] || 0) + weight;
  }
  if (event.brand) {
    next.brandPrefs[event.brand] = (next.brandPrefs[event.brand] || 0) + weight;
  }
  if (event.price > 0) {
    next.priceSum += event.price * weight;
    next.priceWeight += weight;
  }
  next.avgPrice = next.priceWeight > 0 ? next.priceSum / next.priceWeight : 0;

  if (event.productId) {
    next.recentProductIds = [event.productId, ...base.recentProductIds.filter((id) => id !== event.productId)]
      .slice(0, RECENT_LIMIT);
  }

  const cutoff = ts - PURCHASE_MEMORY_DAYS * DAY_MS;
  next.purchased = Object.fromEntries(
    Object.entries(base.purchased).filter(([, at]) => at >= cutoff),
  );
  if (event.eventType === "purchase" && event.productId) {
    next.purchased[event.productId] = ts;
  }

  return next;
}

// Preference strengths normalised to 0..1 by the strongest one. Decay since
// lastUpdated scales every entry equally, so it cancels out here.
function normalisedPrefs(map) {
  const max = Math.max(0, ...Object.values(map || {}));
  if (max === 0) return {};
  const out = {};
  for (const [key, value] of Object.entries(map)) out[key] = value / max;
  return out;
}

module.exports = {
  DAY_MS,
  EVENT_WEIGHTS,
  HALF_LIFE_DAYS,
  emptyProfile,
  applyEvent,
  decayFactor,
  normalisedPrefs,
};
