// Product scoring and ranking. Pure functions: callers fetch products,
// profiles and co-purchase counts and pass them in.

const { DAY_MS, normalisedPrefs } = require("./profile");

// Initial weights. Tuned with scripts/evalRecs.js, see docs/recommendations.md.
const DEFAULT_WEIGHTS = {
  category: 0.30,
  brand: 0.15,
  coPurchase: 0.20,
  priceFit: 0.10,
  deal: 0.10,
  popularity: 0.10,
  recency: 0.05,
};

const NEW_PRODUCT_DAYS = 30;
const REPURCHASE_COOLDOWN_DAYS = 7;
const DEFAULT_LIMIT = 10;
const DEFAULT_PER_CATEGORY = 2;

function availablePrices(product) {
  return (product.prices || []).filter(
    (p) => p && p.price > 0 && p.inStock !== false && (p.quantity == null || p.quantity > 0),
  );
}

function isAvailable(product) {
  return availablePrices(product).length > 0;
}

function cheapestPrice(product) {
  const prices = availablePrices(product).map((p) => p.price);
  return prices.length ? Math.min(...prices) : 0;
}

function popularity(product) {
  const rating = product.rating || 0;
  const reviews = product.reviews || 0;
  return rating * Math.log(reviews + 1);
}

// How good a deal the cheapest offer is: savings vs the priciest store, or
// vs the listed original price, whichever is larger. 0..1.
function dealScore(product) {
  const prices = availablePrices(product);
  if (!prices.length) return 0;
  const cheapest = prices.reduce((a, b) => (b.price < a.price ? b : a));
  const highest = Math.max(...prices.map((p) => p.price));
  const vsStores = highest > 0 ? (highest - cheapest.price) / highest : 0;
  const original = cheapest.originalPrice || 0;
  const vsOriginal = original > cheapest.price ? (original - cheapest.price) / original : 0;
  return Math.min(1, Math.max(vsStores, vsOriginal));
}

function priceFit(price, avgPrice) {
  if (!(price > 0) || !(avgPrice > 0)) return 0;
  return 1 - Math.min(1, Math.abs(price - avgPrice) / avgPrice);
}

function recency(product, now) {
  if (!product.createdAt) return 0;
  const age = now - product.createdAt;
  return Math.max(0, 1 - age / (NEW_PRODUCT_DAYS * DAY_MS));
}

function maxOf(values) {
  return values.reduce((m, v) => (v > m ? v : m), 0);
}

// Highest score first, at most `perCategory` per category until the list
// would otherwise run short, then fill with the best of the rest.
function diversify(scored, limit, perCategory) {
  const ranked = [...scored].sort((a, b) => b._score - a._score);
  const picked = [];
  const skipped = [];
  const perCat = {};
  for (const product of ranked) {
    if (picked.length === limit) break;
    const cat = product.category || "";
    if ((perCat[cat] || 0) < perCategory) {
      picked.push(product);
      perCat[cat] = (perCat[cat] || 0) + 1;
    } else {
      skipped.push(product);
    }
  }
  for (const product of skipped) {
    if (picked.length === limit) break;
    picked.push(product);
  }
  return picked;
}

// Personalised "Recommended for you".
//   profile:    user_profiles doc (or null for a new user)
//   coCounts:   { productId: count } co-purchase strength with the user's
//               recent and in-cart products
//   excludeIds: product ids never to return (e.g. already in the cart)
function recommendForUser(products, profile, options = {}) {
  const {
    coCounts = {},
    excludeIds = [],
    now = Date.now(),
    limit = DEFAULT_LIMIT,
    perCategory = DEFAULT_PER_CATEGORY,
    weights = DEFAULT_WEIGHTS,
  } = options;

  const excluded = new Set(excludeIds);
  const purchased = (profile && profile.purchased) || {};
  const cooldown = now - REPURCHASE_COOLDOWN_DAYS * DAY_MS;
  const candidates = products.filter(
    (p) => isAvailable(p) && !excluded.has(p.id) && !((purchased[p.id] || 0) >= cooldown),
  );

  const maxPop = maxOf(candidates.map(popularity));
  const maxCo = maxOf(Object.values(coCounts));
  const hasProfile = Boolean(profile && profile.totalEvents > 0);
  const cats = hasProfile ? normalisedPrefs(profile.categoryPrefs) : {};
  const brands = hasProfile ? normalisedPrefs(profile.brandPrefs) : {};
  const avgPrice = hasProfile ? profile.avgPrice : 0;

  const scored = candidates.map((product) => {
    const pop = maxPop > 0 ? popularity(product) / maxPop : 0;
    const deal = dealScore(product);
    let score;
    if (hasProfile) {
      score =
        weights.category * (cats[product.category] || 0) +
        weights.brand * (brands[product.brand] || 0) +
        weights.coPurchase * (maxCo > 0 ? (coCounts[product.id] || 0) / maxCo : 0) +
        weights.priceFit * priceFit(cheapestPrice(product), avgPrice) +
        weights.deal * deal +
        weights.popularity * pop +
        weights.recency * recency(product, now);
    } else {
      // Cold start: nothing known about the user yet.
      score = 0.6 * pop + 0.3 * deal + 0.1 * recency(product, now);
    }
    return { ...product, _score: score };
  });

  return diversify(scored, limit, perCategory);
}

// Content-based "Similar products": same category, same brand, similar price.
function similarTo(products, target, options = {}) {
  const { limit = DEFAULT_LIMIT } = options;
  const targetPrice = cheapestPrice(target);
  const maxPop = maxOf(products.map(popularity));

  const scored = [];
  for (const product of products) {
    if (product.id === target.id || !isAvailable(product)) continue;
    let score = 0;
    if (target.category && product.category === target.category) score += 0.5;
    if (target.brand && product.brand === target.brand) score += 0.3;
    const price = cheapestPrice(product);
    if (targetPrice > 0 && price > 0) {
      const diff = Math.abs(price - targetPrice) / targetPrice;
      if (diff < 0.3) score += 0.2 * (1 - diff / 0.3);
    }
    if (score === 0) continue;
    // Popularity only breaks ties between equally similar products.
    score += maxPop > 0 ? 0.01 * (popularity(product) / maxPop) : 0;
    scored.push({ ...product, _score: score });
  }
  return scored.sort((a, b) => b._score - a._score).slice(0, limit);
}

// "People also buy": products bought together with `productId`, topped up
// with the most popular products in the same category.
function peopleAlsoBuy(products, target, coCounts, options = {}) {
  const { limit = DEFAULT_LIMIT } = options;
  const available = products.filter((p) => p.id !== target.id && isAvailable(p));

  const bought = available
    .filter((p) => (coCounts[p.id] || 0) > 0)
    .map((p) => ({ ...p, _score: coCounts[p.id] }))
    .sort((a, b) => b._score - a._score || popularity(b) - popularity(a));

  if (bought.length >= limit) return bought.slice(0, limit);

  const taken = new Set(bought.map((p) => p.id));
  const popularInCategory = available
    .filter((p) => !taken.has(p.id) && p.category === target.category)
    .map((p) => ({ ...p, _score: 0 }))
    .sort((a, b) => popularity(b) - popularity(a));

  return [...bought, ...popularInCategory].slice(0, limit);
}

module.exports = {
  DEFAULT_WEIGHTS,
  availablePrices,
  isAvailable,
  cheapestPrice,
  popularity,
  dealScore,
  priceFit,
  recency,
  diversify,
  recommendForUser,
  similarTo,
  peopleAlsoBuy,
};
