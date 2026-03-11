package com.example.qless;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks user behavior events for ML recommendations.
 * Events: view, search, add_to_cart, purchase
 */
public class EventLogger {

    private static EventLogger instance;
    private final FirebaseFirestore db;

    public static final String VIEW = "view";
    public static final String SEARCH = "search";
    public static final String ADD_TO_CART = "add_to_cart";
    public static final String PURCHASE = "purchase";

    private EventLogger() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized EventLogger getInstance() {
        if (instance == null) {
            instance = new EventLogger();
        }
        return instance;
    }

    /**
     * Log a user behavior event.
     */
    public void logEvent(String eventType, String productId, String category, String brand, double price, String storeId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();

        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("eventType", eventType);
        event.put("productId", productId);
        event.put("category", category != null ? category : "");
        event.put("brand", brand != null ? brand : "");
        event.put("price", price);
        event.put("storeId", storeId != null ? storeId : "");
        event.put("timestamp", System.currentTimeMillis());

        // Write event
        db.collection("user_events").add(event);

        // Update user preference profile
        updateUserProfile(userId, eventType, category, brand, price);
    }

    /**
     * Simplified log for views (no store/price context).
     */
    public void logView(String productId, String category, String brand) {
        logEvent(VIEW, productId, category, brand, 0, null);
    }

    /**
     * Log a search query.
     */
    public void logSearch(String query) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("userId", user.getUid());
        event.put("eventType", SEARCH);
        event.put("query", query);
        event.put("timestamp", System.currentTimeMillis());

        db.collection("user_events").add(event);
    }

    /**
     * Update user preference profile based on behavior.
     * Each event type has a different weight:
     * - view: +1
     * - add_to_cart: +3
     * - purchase: +5
     */
    private void updateUserProfile(String userId, String eventType, String category, String brand, double price) {
        if (category == null || category.isEmpty()) return;

        double weight;
        switch (eventType) {
            case ADD_TO_CART: weight = 3.0; break;
            case PURCHASE:    weight = 5.0; break;
            default:          weight = 1.0; break;
        }

        // Update category preference
        db.collection("user_profiles").document(userId).get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> profile;
                    Map<String, Object> catPrefs;
                    Map<String, Object> brandPrefs;

                    if (doc.exists()) {
                        profile = new HashMap<>(doc.getData());
                        catPrefs = doc.get("categoryPrefs") != null ?
                                new HashMap<>((Map<String, Object>) doc.get("categoryPrefs")) : new HashMap<>();
                        brandPrefs = doc.get("brandPrefs") != null ?
                                new HashMap<>((Map<String, Object>) doc.get("brandPrefs")) : new HashMap<>();
                    } else {
                        profile = new HashMap<>();
                        catPrefs = new HashMap<>();
                        brandPrefs = new HashMap<>();
                    }

                    // Increment category score
                    double currentCatScore = catPrefs.containsKey(category) ?
                            ((Number) catPrefs.get(category)).doubleValue() : 0;
                    catPrefs.put(category, currentCatScore + weight);

                    // Increment brand score
                    if (brand != null && !brand.isEmpty()) {
                        double currentBrandScore = brandPrefs.containsKey(brand) ?
                                ((Number) brandPrefs.get(brand)).doubleValue() : 0;
                        brandPrefs.put(brand, currentBrandScore + weight);
                    }

                    // Track total interactions and average price
                    long totalEvents = profile.containsKey("totalEvents") ?
                            ((Number) profile.get("totalEvents")).longValue() : 0;
                    double avgPrice = profile.containsKey("avgPrice") ?
                            ((Number) profile.get("avgPrice")).doubleValue() : 0;

                    if (price > 0) {
                        avgPrice = (avgPrice * totalEvents + price) / (totalEvents + 1);
                    }

                    profile.put("categoryPrefs", catPrefs);
                    profile.put("brandPrefs", brandPrefs);
                    profile.put("totalEvents", totalEvents + 1);
                    profile.put("avgPrice", avgPrice);
                    profile.put("lastUpdated", System.currentTimeMillis());

                    db.collection("user_profiles").document(userId).set(profile);
                });
    }
}

