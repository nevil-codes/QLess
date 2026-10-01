package com.example.qless;

import android.util.Log;

import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the recommendation Cloud Functions (functions/recs).
 * Ranking happens server-side; see docs/recommendations.md.
 */
public class RecommendationEngine {

    private static final String TAG = "RecommendationEngine";
    private static final String REGION = "europe-west1";

    public interface OnRecommendationsReady {
        void onReady(List<Map<String, Object>> products);
    }

    private final FirebaseFunctions functions;

    public RecommendationEngine() {
        functions = FirebaseFunctions.getInstance(REGION);
    }

    /** Personalised "Recommended for you"; excludes what's already in the cart. */
    public void getRecommendations(OnRecommendationsReady callback) {
        List<String> cartIds = new ArrayList<>();
        for (CartManager.CartItem item : CartManager.getInstance().getItems()) {
            if (item.productId != null && !cartIds.contains(item.productId)) {
                cartIds.add(item.productId);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("cartProductIds", cartIds);
        call("getRecommendations", data, callback);
    }

    /** Products similar in category, brand and price. */
    public void getSimilarProducts(String productId, OnRecommendationsReady callback) {
        call("getSimilarProducts", productData(productId), callback);
    }

    /** Products other shoppers bought together with this one. */
    public void getPeopleAlsoBuy(String productId, OnRecommendationsReady callback) {
        call("getPeopleAlsoBuy", productData(productId), callback);
    }

    private static Map<String, Object> productData(String productId) {
        Map<String, Object> data = new HashMap<>();
        data.put("productId", productId);
        return data;
    }

    @SuppressWarnings("unchecked")
    private void call(String name, Map<String, Object> data, OnRecommendationsReady callback) {
        functions.getHttpsCallable(name)
                .call(data)
                .addOnSuccessListener(result -> {
                    List<Map<String, Object>> products = new ArrayList<>();
                    Object body = result.getData();
                    if (body instanceof Map) {
                        Object list = ((Map<String, Object>) body).get("products");
                        if (list instanceof List) {
                            for (Object item : (List<Object>) list) {
                                if (item instanceof Map) products.add((Map<String, Object>) item);
                            }
                        }
                    }
                    callback.onReady(products);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, name + " failed", e);
                    callback.onReady(new ArrayList<>());
                });
    }
}
