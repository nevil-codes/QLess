package com.example.qless;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ML-powered recommendation engine.
 *
 * Scoring formula per product:
 *   score = 0.30 * categoryAffinity
 *         + 0.20 * brandAffinity
 *         + 0.20 * recencyBoost
 *         + 0.15 * priceAdvantage
 *         + 0.15 * popularityScore
 *
 * Results are sorted descending by score.
 */
public class RecommendationEngine {

    public interface OnRecommendationsReady {
        void onReady(List<Map<String, Object>> products);
    }

    private final FirebaseFirestore db;

    public RecommendationEngine() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Get personalized "Recommended For You" products.
     * Falls back to popular products if no user profile exists.
     */
    public void getRecommendations(OnRecommendationsReady callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            getPopularProducts(callback);
            return;
        }

        db.collection("user_profiles").document(user.getUid()).get()
                .addOnSuccessListener(profileDoc -> {
                    if (!profileDoc.exists()) {
                        getPopularProducts(callback);
                        return;
                    }

                    Map<String, Object> catPrefs = profileDoc.get("categoryPrefs") != null ?
                            (Map<String, Object>) profileDoc.get("categoryPrefs") : new HashMap<>();
                    Map<String, Object> brandPrefs = profileDoc.get("brandPrefs") != null ?
                            (Map<String, Object>) profileDoc.get("brandPrefs") : new HashMap<>();
                    double avgPrice = profileDoc.get("avgPrice") != null ?
                            ((Number) profileDoc.get("avgPrice")).doubleValue() : 0;

                    // Get all products and score them
                    db.collection("products").get().addOnSuccessListener(snap -> {
                        List<Map<String, Object>> scored = new ArrayList<>();

                        // Find max values for normalization
                        double maxCatScore = 1, maxBrandScore = 1;
                        for (Object v : catPrefs.values())
                            maxCatScore = Math.max(maxCatScore, ((Number) v).doubleValue());
                        for (Object v : brandPrefs.values())
                            maxBrandScore = Math.max(maxBrandScore, ((Number) v).doubleValue());

                        for (QueryDocumentSnapshot doc : snap) {
                            Map<String, Object> product = new HashMap<>(doc.getData());
                            product.put("id", doc.getId());

                            String category = (String) product.get("category");
                            String brand = (String) product.get("brand");

                            // 1. Category affinity (0-1)
                            double categoryAffinity = 0;
                            if (category != null && catPrefs.containsKey(category)) {
                                categoryAffinity = ((Number) catPrefs.get(category)).doubleValue() / maxCatScore;
                            }

                            // 2. Brand affinity (0-1)
                            double brandAffinity = 0;
                            if (brand != null && brandPrefs.containsKey(brand)) {
                                brandAffinity = ((Number) brandPrefs.get(brand)).doubleValue() / maxBrandScore;
                            }

                            // 3. Recency boost — newer products score higher
                            double recencyBoost = 0;
                            Object createdAt = product.get("createdAt");
                            if (createdAt != null) {
                                long age = System.currentTimeMillis() - ((Number) createdAt).longValue();
                                long sevenDays = 7L * 24 * 60 * 60 * 1000;
                                recencyBoost = Math.max(0, 1.0 - (double) age / sevenDays);
                            }

                            // 4. Price advantage — products cheaper than user's avg price preference
                            double priceAdvantage = 0;
                            List<Map<String, Object>> prices = (List<Map<String, Object>>) product.get("prices");
                            double cheapest = getCheapestPrice(prices);
                            if (avgPrice > 0 && cheapest > 0) {
                                priceAdvantage = Math.max(0, Math.min(1, (avgPrice - cheapest) / avgPrice + 0.5));
                            }

                            // 5. Popularity — based on review count
                            double popularityScore = 0;
                            Object reviews = product.get("reviews");
                            if (reviews != null) {
                                int reviewCount = ((Number) reviews).intValue();
                                popularityScore = Math.min(1.0, reviewCount / 200.0);
                            }

                            // Final weighted score
                            double score = 0.30 * categoryAffinity
                                    + 0.20 * brandAffinity
                                    + 0.20 * recencyBoost
                                    + 0.15 * priceAdvantage
                                    + 0.15 * popularityScore;

                            product.put("_score", score);
                            scored.add(product);
                        }

                        // Sort by score descending
                        scored.sort((a, b) -> Double.compare(
                                ((Number) b.get("_score")).doubleValue(),
                                ((Number) a.get("_score")).doubleValue()));

                        callback.onReady(scored);
                    });
                })
                .addOnFailureListener(e -> getPopularProducts(callback));
    }

    /**
     * Get "Similar Products" for a given product.
     * Matches by same category, same brand, or similar price range.
     */
    public void getSimilarProducts(String productId, String category, String brand, double price, OnRecommendationsReady callback) {
        db.collection("products").get().addOnSuccessListener(snap -> {
            List<Map<String, Object>> scored = new ArrayList<>();

            for (QueryDocumentSnapshot doc : snap) {
                if (doc.getId().equals(productId)) continue; // Skip current product

                Map<String, Object> product = new HashMap<>(doc.getData());
                product.put("id", doc.getId());

                String pCat = (String) product.get("category");
                String pBrand = (String) product.get("brand");
                double pPrice = getCheapestPrice((List<Map<String, Object>>) product.get("prices"));

                double score = 0;

                // Same category = strong match
                if (category != null && category.equals(pCat)) score += 0.5;

                // Same brand = medium match
                if (brand != null && brand.equals(pBrand)) score += 0.3;

                // Price similarity (within 30% range)
                if (price > 0 && pPrice > 0) {
                    double priceDiff = Math.abs(price - pPrice) / price;
                    if (priceDiff < 0.3) score += 0.2 * (1 - priceDiff / 0.3);
                }

                if (score > 0) {
                    product.put("_score", score);
                    scored.add(product);
                }
            }

            scored.sort((a, b) -> Double.compare(
                    ((Number) b.get("_score")).doubleValue(),
                    ((Number) a.get("_score")).doubleValue()));

            // Return top 10
            callback.onReady(scored.size() > 10 ? scored.subList(0, 10) : scored);
        });
    }

    /**
     * Get "Most Popular" products in a category.
     */
    public void getPopularInCategory(String category, String excludeProductId, OnRecommendationsReady callback) {
        db.collection("products")
                .whereEqualTo("category", category)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Map<String, Object>> products = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        if (doc.getId().equals(excludeProductId)) continue;
                        Map<String, Object> p = new HashMap<>(doc.getData());
                        p.put("id", doc.getId());
                        products.add(p);
                    }

                    // Sort by rating * reviews (popularity score)
                    products.sort((a, b) -> {
                        double scoreA = getPopScore(a);
                        double scoreB = getPopScore(b);
                        return Double.compare(scoreB, scoreA);
                    });

                    callback.onReady(products.size() > 10 ? products.subList(0, 10) : products);
                })
                .addOnFailureListener(e -> callback.onReady(new ArrayList<>()));
    }

    /**
     * Fallback: get popular products across all categories.
     */
    private void getPopularProducts(OnRecommendationsReady callback) {
        db.collection("products").get().addOnSuccessListener(snap -> {
            List<Map<String, Object>> products = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snap) {
                Map<String, Object> p = new HashMap<>(doc.getData());
                p.put("id", doc.getId());
                p.put("_score", getPopScore(p));
                products.add(p);
            }

            products.sort((a, b) -> Double.compare(
                    ((Number) b.get("_score")).doubleValue(),
                    ((Number) a.get("_score")).doubleValue()));

            callback.onReady(products);
        }).addOnFailureListener(e -> callback.onReady(new ArrayList<>()));
    }

    // --- Helpers ---

    private double getCheapestPrice(List<Map<String, Object>> prices) {
        if (prices == null || prices.isEmpty()) return 0;
        double min = Double.MAX_VALUE;
        for (Map<String, Object> p : prices) {
            Object priceObj = p.get("price");
            if (priceObj != null) min = Math.min(min, ((Number) priceObj).doubleValue());
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    private double getPopScore(Map<String, Object> product) {
        double rating = product.get("rating") != null ? ((Number) product.get("rating")).doubleValue() : 0;
        int reviews = product.get("reviews") != null ? ((Number) product.get("reviews")).intValue() : 0;
        return rating * Math.log(reviews + 1);
    }
}

