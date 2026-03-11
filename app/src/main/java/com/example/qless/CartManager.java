package com.example.qless;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Singleton cart manager to persist cart state across activities.
 */
public class CartManager {
    private static CartManager instance;
    private final List<CartItem> items = new ArrayList<>();

    private CartManager() {}

    public static synchronized CartManager getInstance() {
        if (instance == null) {
            instance = new CartManager();
        }
        return instance;
    }

    public void addItem(String productId, String name, String storeName, String storeId, double price) {
        // Check if already in cart from same store
        for (CartItem item : items) {
            if (item.productId.equals(productId) && item.storeId.equals(storeId)) {
                item.quantity++;
                return;
            }
        }
        items.add(new CartItem(productId, name, storeName, storeId, price));
    }

    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public void updateQuantity(int index, int quantity) {
        if (index >= 0 && index < items.size()) {
            if (quantity <= 0) {
                items.remove(index);
            } else {
                items.get(index).quantity = quantity;
            }
        }
    }

    public List<CartItem> getItems() {
        return items;
    }

    public int getItemCount() {
        int count = 0;
        for (CartItem item : items) {
            count += item.quantity;
        }
        return count;
    }

    public double getTotal() {
        double total = 0;
        for (CartItem item : items) {
            total += item.price * item.quantity;
        }
        return total;
    }

    public void clear() {
        items.clear();
    }

    public static class CartItem {
        public String productId;
        public String name;
        public String storeName;
        public String storeId;
        public double price;
        public int quantity;

        public CartItem(String productId, String name, String storeName, String storeId, double price) {
            this.productId = productId;
            this.name = name;
            this.storeName = storeName;
            this.storeId = storeId;
            this.price = price;
            this.quantity = 1;
        }
    }
}

