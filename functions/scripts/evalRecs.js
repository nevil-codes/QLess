#!/usr/bin/env node
// Offline evaluation of the recommendation ranking.
//
//   node scripts/evalRecs.js                  # synthetic shoppers (default)
//   node scripts/evalRecs.js --tune           # also random-search weights
//   node scripts/evalRecs.js --firestore      # real products + user_events
//
// --firestore needs Admin credentials (GOOGLE_APPLICATION_CREDENTIALS or
// gcloud application-default login). Results are written up in
// docs/recommendations.md.

const { generate, rng } = require("../recs/synthetic");
const { buildCases, evaluate, rankers } = require("../recs/eval");
const { DEFAULT_WEIGHTS } = require("../recs/scoring");

const K = 10;
const TUNE_SEEDS = [7, 8, 9];
const TEST_SEEDS = [42, 43, 44, 45, 46];
const TUNE_SAMPLES = 300;

function fmt(r) {
  return `HR@${K} ${(r.hitRate * 100).toFixed(1).padStart(5)}%   NDCG@${K} ${r.ndcg.toFixed(3)}`;
}

// sets: [{ products, cases }]; prints the mean over all sets.
function report(title, sets, extra = {}) {
  const users = sets.reduce((n, s) => n + s.cases.length, 0);
  console.log(`\n${title} (${sets.length} population(s), ${users} users)`);
  const all = {
    "legacy (as deployed)": rankers.legacyAsDeployed,
    "legacy formula": rankers.legacyFormula,
    "cold start": rankers.coldStart,
    "personal (default)": rankers.personal,
    ...extra,
  };
  for (const [name, ranker] of Object.entries(all)) {
    const runs = sets.map(({ products, cases }) => evaluate(products, cases, ranker, K));
    const mean = (key) => runs.reduce((sum, r) => sum + r[key], 0) / runs.length;
    console.log(`  ${name.padEnd(22)} ${fmt({ hitRate: mean("hitRate"), ndcg: mean("ndcg") })}`);
  }
}

function randomWeights(rand) {
  const keys = Object.keys(DEFAULT_WEIGHTS);
  const raw = keys.map(() => -Math.log(rand()));
  const sum = raw.reduce((a, b) => a + b, 0);
  return Object.fromEntries(keys.map((key, i) => [key, Math.round((raw[i] / sum) * 100) / 100]));
}

// Random search, scored by mean NDCG across several independent synthetic
// populations; a single population of ~400 users is too noisy and overfits.
function tune(sets) {
  const rand = rng(1);
  const score = (weights) => {
    const total = sets.reduce((sum, { products, cases }) =>
      sum + evaluate(products, cases, (p, c, k) => rankers.personal(p, c, k, weights), K).ndcg, 0);
    return total / sets.length;
  };
  let best = { weights: DEFAULT_WEIGHTS, ndcg: score(DEFAULT_WEIGHTS) };
  const defaultNdcg = best.ndcg;
  for (let i = 0; i < TUNE_SAMPLES; i++) {
    const weights = randomWeights(rand);
    const ndcg = score(weights);
    if (ndcg > best.ndcg) best = { weights, ndcg };
  }
  return { ...best, defaultNdcg };
}

async function loadFirestore() {
  const { initializeApp, applicationDefault } = require("firebase-admin/app");
  const { getFirestore } = require("firebase-admin/firestore");
  initializeApp({ credential: applicationDefault(), projectId: "qless-1ccb9" });
  const db = getFirestore();
  const [productSnap, eventSnap] = await Promise.all([
    db.collection("products").get(),
    db.collection("user_events").get(),
  ]);
  const products = productSnap.docs.map((d) => ({ ...d.data(), id: d.id }));
  const byUser = {};
  for (const d of eventSnap.docs) {
    const e = d.data();
    if (!e.userId) continue;
    (byUser[e.userId] = byUser[e.userId] || []).push(e);
  }
  const users = Object.entries(byUser).map(([userId, events]) => ({
    userId, events: events.sort((a, b) => a.timestamp - b.timestamp),
  }));
  return { products, users };
}

async function main() {
  const args = new Set(process.argv.slice(2));

  if (args.has("--firestore")) {
    const data = await loadFirestore();
    console.log(`Loaded ${data.products.length} products, ${data.users.length} users with events`);
    report("Firestore", [{ products: data.products, cases: buildCases(data) }]);
    return;
  }

  const testSets = TEST_SEEDS.map((seed) => {
    const data = generate({ seed });
    return { products: data.products, cases: buildCases(data) };
  });
  report(`Synthetic, held-out seeds ${TEST_SEEDS.join(", ")}`, testSets);

  if (args.has("--tune")) {
    const best = tune(TUNE_SEEDS.map((seed) => {
      const data = generate({ seed });
      return { products: data.products, cases: buildCases(data) };
    }));
    console.log(`\nTuning on seeds ${TUNE_SEEDS.join(", ")} (${TUNE_SAMPLES} samples):`);
    console.log(`  default mean NDCG ${best.defaultNdcg.toFixed(3)}, best ${best.ndcg.toFixed(3)}`);
    console.log(" ", JSON.stringify(best.weights));
    report("Held-out seeds with tuned weights", testSets, {
      "personal (tuned)": (p, c, k) => rankers.personal(p, c, k, best.weights),
    });
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
