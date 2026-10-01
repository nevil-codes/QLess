// Reproducible synthetic shoppers for offline evaluation.
//
// Each shopper has hidden favourite categories, sometimes a favourite brand
// in them, and a price sensitivity. Baskets sometimes add an item from a
// complementary category (bread -> spreads). This lets the evaluation check
// that the ranking recovers those patterns; it is not a substitute for real
// data (see scripts/evalRecs.js --firestore).

const { DAY_MS } = require("./profile");

const CATEGORIES = [
  "Dairy", "Bakery", "Spreads", "Produce", "Meat", "Pasta",
  "Sauces", "Coffee", "Snacks", "Beverages", "Household", "Personal Care",
];
const COMPLEMENTS = {
  Bakery: "Spreads", Spreads: "Bakery", Pasta: "Sauces", Sauces: "Pasta",
  Coffee: "Dairy", Dairy: "Coffee", Snacks: "Beverages", Beverages: "Snacks",
  Meat: "Produce", Produce: "Meat", Household: "Personal Care", "Personal Care": "Household",
};
const BRANDS_PER_CATEGORY = 4;
const PRODUCTS_PER_CATEGORY = 25;

// Mulberry32: tiny seeded PRNG so runs are reproducible.
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function pick(rand, list) {
  return list[Math.floor(rand() * list.length)];
}

function weightedPick(rand, items, weightOf) {
  const weights = items.map(weightOf);
  const total = weights.reduce((a, b) => a + b, 0);
  let r = rand() * total;
  for (let i = 0; i < items.length; i++) {
    r -= weights[i];
    if (r <= 0) return items[i];
  }
  return items[items.length - 1];
}

function makeCatalog(rand, now) {
  const products = [];
  for (const category of CATEGORIES) {
    const basePrice = 1 + rand() * 6;
    for (let i = 0; i < PRODUCTS_PER_CATEGORY; i++) {
      const price = Math.round(basePrice * (0.5 + rand() * 1.5) * 100) / 100;
      const spread = 1 + rand() * 0.4;
      products.push({
        id: `${category.replace(/\s/g, "")}-${i}`,
        name: `${category} item ${i}`,
        category,
        brand: `${category}-brand-${Math.floor(rand() * BRANDS_PER_CATEGORY)}`,
        rating: Math.round((3 + rand() * 2) * 10) / 10,
        reviews: Math.floor(Math.pow(rand(), 3) * 800),
        createdAt: now - Math.floor(rand() * 90) * DAY_MS,
        prices: [
          { storeId: "s1", price, inStock: rand() > 0.05, quantity: 10 },
          { storeId: "s2", price: Math.round(price * spread * 100) / 100, inStock: true, quantity: 10 },
        ],
      });
    }
  }
  return products;
}

function makeShopper(rand) {
  const favourites = [];
  while (favourites.length < 2 + Math.floor(rand() * 2)) {
    const c = pick(rand, CATEGORIES);
    if (!favourites.includes(c)) favourites.push(c);
  }
  const favouriteBrands = {};
  for (const c of favourites) {
    if (rand() < 0.6) favouriteBrands[c] = `${c}-brand-${Math.floor(rand() * BRANDS_PER_CATEGORY)}`;
  }
  return { favourites, favouriteBrands, priceSensitivity: rand() };
}

function choose(rand, shopper, byCategory, category) {
  return weightedPick(rand, byCategory[category], (p) => {
    let w = 1 + Math.log(p.reviews + 1) * 0.3;
    if (shopper.favouriteBrands[category] === p.brand) w *= 6;
    const cheapest = Math.min(...p.prices.map((o) => o.price));
    w *= Math.pow(1 / cheapest, shopper.priceSensitivity * 2);
    return w;
  });
}

// Returns { products, users: [{ userId, events }] } with events sorted by time.
function generate({ seed = 42, users = 400, days = 60, now = Date.UTC(2026, 5, 1) } = {}) {
  const rand = rng(seed);
  const products = makeCatalog(rand, now);
  const byCategory = {};
  for (const p of products) (byCategory[p.category] = byCategory[p.category] || []).push(p);

  const out = [];
  for (let u = 0; u < users; u++) {
    const shopper = makeShopper(rand);
    const userId = `user-${u}`;
    const events = [];
    const sessions = 5 + Math.floor(rand() * 10);
    for (let s = 0; s < sessions; s++) {
      const ts = now - days * DAY_MS + Math.floor(((s + rand()) / sessions) * days * DAY_MS);
      const viewed = [];
      const views = 3 + Math.floor(rand() * 6);
      for (let v = 0; v < views; v++) {
        const category = rand() < 0.75 ? pick(rand, shopper.favourites) : pick(rand, CATEGORIES);
        const p = choose(rand, shopper, byCategory, category);
        viewed.push(p);
        events.push(event(userId, "view", p, ts + v * 60000, 0));
      }
      const basket = [];
      for (const p of viewed) if (rand() < 0.35 && !basket.includes(p)) basket.push(p);
      if (!basket.length) continue;
      const anchor = basket[0];
      if (rand() < 0.5) {
        const extra = choose(rand, shopper, byCategory, COMPLEMENTS[anchor.category]);
        if (!basket.includes(extra)) basket.push(extra);
      }
      basket.forEach((p, i) => events.push(event(userId, "add_to_cart", p, ts + 600000 + i, cheapestOf(p))));
      basket.forEach((p, i) => events.push(event(userId, "purchase", p, ts + 3600000 + i, cheapestOf(p), `${userId}-s${s}`)));
    }
    out.push({ userId, events: events.sort((a, b) => a.timestamp - b.timestamp) });
  }
  return { products, users: out, now };
}

function cheapestOf(p) {
  return Math.min(...p.prices.map((o) => o.price));
}

function event(userId, eventType, p, timestamp, price, basketId) {
  return {
    userId, eventType, productId: p.id, category: p.category, brand: p.brand,
    price, timestamp, ...(basketId ? { basketId } : {}),
  };
}

module.exports = { generate, rng, CATEGORIES, COMPLEMENTS };
