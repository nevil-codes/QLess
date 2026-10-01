// Runs against the Functions + Firestore emulators (npm run test:emulator).
const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const PROJECT = process.env.GCLOUD_PROJECT || "demo-qless";
if (!getApps().length) initializeApp({ projectId: PROJECT });
const db = getFirestore();
const api = require("../../recs/api");

const tag = Date.now();
const id = (name) => `${name}-${tag}`;
const offer = (price) => [{ storeId: "s1", storeName: "FreshHub", price, inStock: true, quantity: 5 }];

beforeAll(async () => {
  const products = {
    [id("milk")]: { name: "Milk", category: "Dairy", brand: "Acme", rating: 4, reviews: 20, prices: offer(1.2) },
    [id("yogurt")]: { name: "Yogurt", category: "Dairy", brand: "Acme", rating: 4, reviews: 5, prices: offer(1.5) },
    [id("bread")]: { name: "Bread", category: "Bakery", brand: "Oven", rating: 3, reviews: 5, prices: offer(2.5) },
    [id("soap")]: { name: "Soap", category: "Household", brand: "Clean", rating: 5, reviews: 900, prices: offer(3) },
    [id("gone")]: { name: "Gone", category: "Dairy", brand: "Acme", rating: 5, reviews: 900,
      prices: [{ price: 1, inStock: false }] },
  };
  for (const [docId, data] of Object.entries(products)) await db.doc(`products/${docId}`).set(data);
  await db.doc(`co_purchases/${id("milk")}`).set({ counts: { [id("bread")]: 4 } });
  await db.doc(`user_profiles/fan-${tag}`).set({
    categoryPrefs: { Dairy: 10 }, brandPrefs: { Acme: 10 }, priceSum: 12, priceWeight: 10,
    avgPrice: 1.2, recentProductIds: [], purchased: {}, totalEvents: 5, lastUpdated: Date.now(),
  });
  api.clearCatalogCache();
});

test("personal recommendations follow the profile and skip out-of-stock items", async () => {
  const { products } = await api.getRecommendations(db, `fan-${tag}`, { limit: 5 });
  const ids = products.map((p) => p.id);
  expect(ids[0]).toMatch(/^(milk|yogurt)-/);
  expect(ids).not.toContain(id("gone"));
  expect(products[0]).toHaveProperty("score");
  expect(products[0]).not.toHaveProperty("_score");
});

test("cart items are excluded and pull in their co-purchases", async () => {
  const { products } = await api.getRecommendations(db, `fan-${tag}`, {
    cartProductIds: [id("milk")], limit: 5,
  });
  const ids = products.map((p) => p.id);
  expect(ids).not.toContain(id("milk"));
  expect(ids).toContain(id("bread"));
});

test("signed-out users get the popularity cold start", async () => {
  const { products } = await api.getRecommendations(db, null, { limit: 3 });
  expect(products[0].id).toBe(id("soap"));
});

test("people also buy uses real co-purchases", async () => {
  const { products } = await api.getPeopleAlsoBuy(db, { productId: id("milk"), limit: 2 });
  expect(products[0].id).toBe(id("bread"));
});

test("similar products share category or brand", async () => {
  const { products } = await api.getSimilarProducts(db, { productId: id("milk"), limit: 3 });
  expect(products[0].id).toBe(id("yogurt"));
});

test("bad arguments are rejected", async () => {
  await expect(api.getSimilarProducts(db, {})).rejects.toThrow(api.InvalidArgument);
  await expect(api.getRecommendations(db, null, { limit: 999 })).rejects.toThrow(api.InvalidArgument);
});

test("the deployed callable answers in europe-west1", async () => {
  const host = process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1:5001";
  const res = await fetch(`http://${host}/${PROJECT}/europe-west1/getRecommendations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data: { limit: 2 } }),
  });
  expect(res.status).toBe(200);
  const body = await res.json();
  expect(body.result.products.length).toBeGreaterThan(0);

  const bad = await fetch(`http://${host}/${PROJECT}/europe-west1/getSimilarProducts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data: {} }),
  });
  expect((await bad.json()).error.status).toBe("INVALID_ARGUMENT");
});
