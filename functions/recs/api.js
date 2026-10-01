// Handlers behind the recommendation callables. Free of firebase-functions
// so emulator tests can call them directly.

const { recommendForUser, similarTo, peopleAlsoBuy } = require("./scoring");

const CATALOG_TTL_MS = 60 * 1000;
const MAX_LIMIT = 50;
const MAX_IDS = 100;
const SEED_RECENT = 10;

class InvalidArgument extends Error {}

let catalogCache = { at: 0, products: null };

async function loadCatalog(db, now = Date.now()) {
  if (catalogCache.products && now - catalogCache.at < CATALOG_TTL_MS) {
    return catalogCache.products;
  }
  const snap = await db.collection("products").get();
  const products = snap.docs.map((doc) => ({ ...doc.data(), id: doc.id }));
  catalogCache = { at: now, products };
  return products;
}

function clearCatalogCache() {
  catalogCache = { at: 0, products: null };
}

function parseLimit(value, fallback = 10) {
  if (value == null) return fallback;
  const n = Number(value);
  if (!Number.isInteger(n) || n < 1 || n > MAX_LIMIT) {
    throw new InvalidArgument(`limit must be an integer from 1 to ${MAX_LIMIT}`);
  }
  return n;
}

function parseProductId(value) {
  if (typeof value !== "string" || !/^[^/]{1,200}$/.test(value)) {
    throw new InvalidArgument("productId is required");
  }
  return value;
}

function parseIdList(value) {
  if (value == null) return [];
  if (!Array.isArray(value) || value.length > MAX_IDS) {
    throw new InvalidArgument(`cartProductIds must be an array of at most ${MAX_IDS} ids`);
  }
  return value.filter((id) => typeof id === "string" && /^[^/]{1,200}$/.test(id));
}

// Strips internal fields and keeps the score for debugging/analytics.
function present(products) {
  return products.map(({ _score, ...product }) => ({ ...product, score: _score }));
}

// Sums co-purchase counts across the seed products' co_purchases docs.
async function coPurchaseCounts(db, seedIds) {
  const unique = [...new Set(seedIds)];
  if (!unique.length) return {};
  const snaps = await db.getAll(...unique.map((id) => db.doc(`co_purchases/${id}`)));
  const totals = {};
  for (const snap of snaps) {
    if (!snap.exists) continue;
    for (const [id, count] of Object.entries(snap.data().counts || {})) {
      totals[id] = (totals[id] || 0) + count;
    }
  }
  return totals;
}

async function getRecommendations(db, uid, data = {}, now = Date.now()) {
  const limit = parseLimit(data.limit);
  const cartIds = parseIdList(data.cartProductIds);

  const [products, profileSnap] = await Promise.all([
    loadCatalog(db, now),
    uid ? db.doc(`user_profiles/${uid}`).get() : Promise.resolve(null),
  ]);
  const profile = profileSnap && profileSnap.exists ? profileSnap.data() : null;

  const seeds = [
    ...cartIds,
    ...((profile && profile.recentProductIds) || []).slice(0, SEED_RECENT),
    ...Object.keys((profile && profile.purchased) || {}),
  ];
  const coCounts = await coPurchaseCounts(db, seeds);

  return {
    products: present(recommendForUser(products, profile, {
      coCounts, excludeIds: cartIds, now, limit,
    })),
  };
}

async function getSimilarProducts(db, data = {}, now = Date.now()) {
  const productId = parseProductId(data.productId);
  const limit = parseLimit(data.limit);
  const products = await loadCatalog(db, now);
  const target = products.find((p) => p.id === productId);
  if (!target) return { products: [] };
  return { products: present(similarTo(products, target, { limit })) };
}

async function getPeopleAlsoBuy(db, data = {}, now = Date.now()) {
  const productId = parseProductId(data.productId);
  const limit = parseLimit(data.limit);
  const [products, coSnap] = await Promise.all([
    loadCatalog(db, now),
    db.doc(`co_purchases/${productId}`).get(),
  ]);
  const target = products.find((p) => p.id === productId);
  if (!target) return { products: [] };
  const counts = coSnap.exists ? coSnap.data().counts || {} : {};
  return { products: present(peopleAlsoBuy(products, target, counts, { limit })) };
}

module.exports = {
  InvalidArgument,
  getRecommendations,
  getSimilarProducts,
  getPeopleAlsoBuy,
  clearCatalogCache,
};
