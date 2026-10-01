# Recommendations

How QLess picks products for "Recommended for you", "Similar products" and
"People also buy", and how we measure whether it works.

## Data flow

```
app ── view / search / add_to_cart ──▶ user_events ──▶ onUserEventCreated ──▶ user_profiles/{uid}
admin marks reservation picked_up ──▶ onReservationUpdated ─┬─▶ purchase events ──▶ (same trigger)
                                                             └─▶ co_purchases/{productId}.counts
app ◀── getRecommendations / getSimilarProducts / getPeopleAlsoBuy (europe-west1)
```

- **Events** come from the app (`EventLogger`). Clients can only log `view`,
  `search` and `add_to_cart`; `purchase` is logged by Cloud Functions on pickup.
- **Profiles** (`functions/recs/profile.js`) hold category and brand affinity,
  weighted by event type (view 1, search 0.5, cart 3, purchase 5), with a
  30-day half-life so recent behaviour counts more. They also keep a
  weighted average price, recent products and recent purchases. Only Cloud
  Functions write them.
- **Co-purchases** count, per product, how often each other product was in
  the same picked-up basket.

## Ranking (`functions/recs/scoring.js`)

**Recommended for you** scores each available product:

| Signal | Weight |
|---|---|
| Category affinity | 0.30 |
| Co-purchase with cart, recent and bought items | 0.20 |
| Brand affinity | 0.15 |
| Price fit (close to the user's usual price) | 0.10 |
| Deal size (cross-store spread or discount) | 0.10 |
| Popularity (rating × log reviews) | 0.10 |
| Newness (added in the last 30 days) | 0.05 |

The ranking then applies these rules:
- Out-of-stock products, products in the cart, and anything bought in the
  last 7 days are filtered out.
- At most 3 products per category in the top 10.
- New and signed-out users get popularity plus deal size.

**Similar products** ranks by:
- Same category: 0.5
- Same brand: 0.3
- Price within 30%: up to 0.2
- Popularity: only breaks ties

**People also buy** lists real co-purchases first. If there aren't enough,
it fills the list with the most popular products in the same category.

## Evaluation

Run `node scripts/evalRecs.js` in `functions/`; add `--tune` for weight search,
and `--firestore` to use real data.

The method is leave-last-out:
- Each user's last purchase is the target.
- That whole shopping session is hidden, and the profile is rebuilt from
  earlier events.
- Co-purchase counts use only baskets from before the cutoff.

We report hit rate@10 (the target is in the top 10) and NDCG@10 (higher
when it ranks nearer the top).

The live catalog has one product, so results use reproducible synthetic
shoppers. They have hidden favourite categories, sometimes a favourite
brand, a price sensitivity, and complementary basket items. These numbers
show the ranking recovers those patterns. They are **not** a measure of
real-world accuracy; re-run with `--firestore` once real usage builds up.

Mean over 5 held-out populations (2,000 users):

| Ranker | HR@10 | NDCG@10 |
|---|---|---|
| What production served before (popularity fallback; profile reads were denied) | 4.0% | 0.018 |
| Old client formula, if it had worked | 13.4% | 0.063 |
| Cold start (no profile) | 4.0% | 0.020 |
| **New ranking** | **14.4%** | **0.071** |

Choices backed by the evaluation (seed 42, 400 users):

| Variant | HR@10 |
|---|---|
| Cap of 2 per category | 11.0% |
| No cap | 12.3% |
| **Cap of 3 (chosen)** | **14.0%** |
| Without the 7-day repurchase filter | 12.0% (with cap 2) |

- **Repurchase filter.** Keeping it costs about a point on this data, where only 8
  of 400 targets were bought in the prior week. We keep it on purpose:
  "recommended for you" shouldn't repeat what someone just bought.
- **Weight tuning.** Random search, averaged across three tuning
  populations, found weights that scored higher there but *lower* on
  held-out data (11.8% vs 14.0%). So the defaults stay. Retune when there is
  real data.

What the synthetic data can't show:
- The benefit of time decay, since the simulated preferences don't drift.
- The stock filter, since targets are never out of stock.
- How well co-purchases and real browsing match actual shopper behaviour.
