// Offline evaluation: leave-last-out hit rate and NDCG.
//
// For each user, the last purchased product is the target. Everything from
// that shopping session on is hidden (cut = 2h before the purchase), the
// profile is rebuilt from what's left, co-purchase counts only use baskets
// from before the cut, and we check where the target lands in the ranking.

const { applyEvent, emptyProfile, EVENT_WEIGHTS } = require("./profile");
const { recommendForUser, cheapestPrice, isAvailable, popularity } = require("./scoring");

const SESSION_GAP_MS = 2 * 60 * 60 * 1000;
const MIN_HISTORY = 5;

// productId -> [{ at, others: [ids] }] for building co-purchase counts.
function basketIndex(users) {
  const baskets = {};
  for (const { events } of users) {
    for (const e of events) {
      if (e.eventType !== "purchase") continue;
      const key = e.basketId || `${e.userId}@${e.timestamp}`;
      (baskets[key] = baskets[key] || { at: e.timestamp, ids: new Set() }).ids.add(e.productId);
    }
  }
  const index = {};
  for (const { at, ids } of Object.values(baskets)) {
    const list = [...ids];
    for (const id of list) {
      (index[id] = index[id] || []).push({ at, others: list.filter((o) => o !== id) });
    }
  }
  return index;
}

function coCountsBefore(index, seedIds, cut) {
  const counts = {};
  for (const seed of new Set(seedIds)) {
    for (const { at, others } of index[seed] || []) {
      if (at >= cut) continue;
      for (const o of others) counts[o] = (counts[o] || 0) + 1;
    }
  }
  return counts;
}

function buildCases(dataset) {
  const index = basketIndex(dataset.users);
  const cases = [];
  for (const { userId, events } of dataset.users) {
    const purchases = events.filter((e) => e.eventType === "purchase");
    if (!purchases.length) continue;
    const target = purchases[purchases.length - 1];
    const cut = target.timestamp - SESSION_GAP_MS;
    const history = events.filter((e) => e.timestamp < cut);
    if (history.length < MIN_HISTORY) continue;

    const profile = history.reduce(applyEvent, emptyProfile());
    const seeds = [...profile.recentProductIds.slice(0, 10), ...Object.keys(profile.purchased)];
    cases.push({
      userId,
      target: target.productId,
      now: cut,
      history,
      profile,
      coCounts: coCountsBefore(index, seeds, cut),
    });
  }
  return cases;
}

function evaluate(products, cases, ranker, k = 10) {
  let hits = 0;
  let ndcg = 0;
  for (const c of cases) {
    const ranked = ranker(products, c, k);
    const rank = ranked.findIndex((p) => p.id === c.target);
    if (rank >= 0 && rank < k) {
      hits += 1;
      ndcg += 1 / Math.log2(rank + 2);
    }
  }
  const n = cases.length || 1;
  return { cases: cases.length, hitRate: hits / n, ndcg: ndcg / n };
}

// --- Rankers -----------------------------------------------------------

const rankers = {
  // What production actually served: every profile read was denied, so
  // everyone got the global popularity list.
  legacyAsDeployed: (products, c, k) =>
    [...products].sort((a, b) => popularity(b) - popularity(a)).slice(0, k),

  // The old client formula, as if its profile reads had worked: no decay,
  // no purchases (never logged), no stock or diversity handling.
  legacyFormula: (products, c, k) => {
    const prefs = { cat: {}, brand: {} };
    let total = 0;
    let avgPrice = 0;
    for (const e of c.history) {
      if (e.eventType === "purchase" || e.eventType === "search" || !e.category) continue;
      const w = EVENT_WEIGHTS[e.eventType];
      prefs.cat[e.category] = (prefs.cat[e.category] || 0) + w;
      if (e.brand) prefs.brand[e.brand] = (prefs.brand[e.brand] || 0) + w;
      if (e.price > 0) avgPrice = (avgPrice * total + e.price) / (total + 1);
      total += 1;
    }
    const maxCat = Math.max(1, ...Object.values(prefs.cat));
    const maxBrand = Math.max(1, ...Object.values(prefs.brand));
    const week = 7 * 24 * 60 * 60 * 1000;
    return products
      .map((p) => {
        const cheapest = Math.min(...(p.prices || []).map((o) => o.price));
        const priceAdv = avgPrice > 0 && cheapest > 0
          ? Math.max(0, Math.min(1, (avgPrice - cheapest) / avgPrice + 0.5)) : 0;
        const recency = p.createdAt ? Math.max(0, 1 - (c.now - p.createdAt) / week) : 0;
        const score = 0.30 * ((prefs.cat[p.category] || 0) / maxCat)
          + 0.20 * ((prefs.brand[p.brand] || 0) / maxBrand)
          + 0.20 * recency + 0.15 * priceAdv + 0.15 * Math.min(1, (p.reviews || 0) / 200);
        return { ...p, _score: score };
      })
      .sort((a, b) => b._score - a._score)
      .slice(0, k);
  },

  coldStart: (products, c, k) => recommendForUser(products, null, { now: c.now, limit: k }),

  personal: (products, c, k, weights) => recommendForUser(products, c.profile, {
    coCounts: c.coCounts, now: c.now, limit: k, ...(weights ? { weights } : {}),
  }),
};

module.exports = { buildCases, evaluate, rankers, basketIndex, coCountsBefore, cheapestPrice, isAvailable };
