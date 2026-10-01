package com.example.qless;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Logs user behavior events (view, search, add_to_cart) to user_events.
 * Recommendation profiles are built from them by Cloud Functions;
 * purchases are logged server-side when a reservation is picked up.
 */
public class EventLogger {

    private static EventLogger instance;
    private final FirebaseFirestore db;

    public static final String VIEW = "view";
    public static final String SEARCH = "search";
    public static final String ADD_TO_CART = "add_to_cart";

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
}
