package com.example.qless;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductDetailActivity extends AppCompatActivity {

    private TextView txtCategory, txtProductName, txtBrand, txtRating, txtReviews, txtDescription, txtBestPrice;
    private RecyclerView rvPrices;
    private FirebaseFirestore db;

    private String productId;
    private String productName = "";
    private double cheapestPrice = 0;
    private String cheapestStore = "";
    private String cheapestStoreId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        db = FirebaseFirestore.getInstance();
        productId = getIntent().getStringExtra("productId");

        txtCategory = findViewById(R.id.txtCategory);
        txtProductName = findViewById(R.id.txtProductName);
        txtBrand = findViewById(R.id.txtBrand);
        txtRating = findViewById(R.id.txtRating);
        txtReviews = findViewById(R.id.txtReviews);
        txtDescription = findViewById(R.id.txtDescription);
        txtBestPrice = findViewById(R.id.txtBestPrice);
        rvPrices = findViewById(R.id.rvPrices);

        rvPrices.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnAddToCart).setOnClickListener(v -> {
            if (!productName.isEmpty()) {
                CartManager.getInstance().addItem(productId, productName, cheapestStore, cheapestStoreId, cheapestPrice);
                Toast.makeText(this, getString(R.string.added_to_cart), Toast.LENGTH_SHORT).show();
            }
        });

        if (productId != null) loadProduct();
    }

    private void loadProduct() {
        db.collection("products").document(productId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    productName = doc.getString("name");
                    txtProductName.setText(productName);
                    txtCategory.setText(doc.getString("category"));
                    txtBrand.setText(doc.getString("brand"));

                    Object desc = doc.get("description");
                    txtDescription.setText(desc != null ? (String) desc : "");

                    Object rating = doc.get("rating");
                    if (rating != null) txtRating.setText(String.format(Locale.getDefault(), "%.1f", ((Number) rating).doubleValue()));

                    Object reviews = doc.get("reviews");
                    if (reviews != null) txtReviews.setText("(" + ((Number) reviews).intValue() + " reviews)");

                    // Prices
                    List<Map<String, Object>> prices = (List<Map<String, Object>>) doc.get("prices");
                    if (prices != null && !prices.isEmpty()) {
                        // Find cheapest
                        cheapestPrice = Double.MAX_VALUE;
                        int cheapestIndex = 0;
                        for (int i = 0; i < prices.size(); i++) {
                            double p = ((Number) prices.get(i).get("price")).doubleValue();
                            if (p < cheapestPrice) {
                                cheapestPrice = p;
                                cheapestIndex = i;
                                cheapestStore = (String) prices.get(i).get("storeName");
                                cheapestStoreId = prices.get(i).get("storeId") != null ? (String) prices.get(i).get("storeId") : "";
                            }
                        }
                        txtBestPrice.setText(String.format(Locale.getDefault(), "€%.2f", cheapestPrice));
                        rvPrices.setAdapter(new PriceAdapter(prices, cheapestIndex));
                    }
                });
    }

    // Adapter showing real-time prices from all stores
    private class PriceAdapter extends RecyclerView.Adapter<PriceAdapter.VH> {
        private final List<Map<String, Object>> prices;
        private final int cheapestIndex;

        PriceAdapter(List<Map<String, Object>> prices, int cheapestIndex) {
            this.prices = prices;
            this.cheapestIndex = cheapestIndex;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_store_price, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> p = prices.get(pos);
            h.txtStoreName.setText((String) p.get("storeName"));

            double price = ((Number) p.get("price")).doubleValue();
            h.txtPrice.setText(String.format(Locale.getDefault(), "€%.2f", price));

            Object orig = p.get("originalPrice");
            if (orig != null) {
                h.txtOriginal.setText(String.format(Locale.getDefault(), "€%.2f", ((Number) orig).doubleValue()));
                h.txtOriginal.setPaintFlags(h.txtOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                h.txtOriginal.setVisibility(View.VISIBLE);
            } else {
                h.txtOriginal.setVisibility(View.GONE);
            }

            Object inStock = p.get("inStock");
            boolean stock = inStock == null || (Boolean) inStock;
            h.txtStock.setText(stock ? getString(R.string.in_stock) : getString(R.string.out_of_stock));
            h.txtStock.setTextColor(getColor(stock ? R.color.strength_strong : R.color.strength_weak));

            // Cheapest badge
            if (pos == cheapestIndex) {
                h.txtCheapest.setVisibility(View.VISIBLE);
            } else {
                h.txtCheapest.setVisibility(View.GONE);
            }

            // Click to add this store's price to cart
            h.itemView.setOnClickListener(v -> {
                String storeName = (String) p.get("storeName");
                String storeId = p.get("storeId") != null ? (String) p.get("storeId") : "";
                CartManager.getInstance().addItem(productId, productName, storeName, storeId, price);
                Toast.makeText(ProductDetailActivity.this,
                        getString(R.string.added_to_cart) + " (" + storeName + ")", Toast.LENGTH_SHORT).show();
            });
        }

        @Override public int getItemCount() { return prices.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView txtStoreName, txtPrice, txtOriginal, txtStock, txtCheapest;
            VH(View v) {
                super(v);
                txtStoreName = v.findViewById(R.id.txtStoreName);
                txtPrice = v.findViewById(R.id.txtPrice);
                txtOriginal = v.findViewById(R.id.txtOriginalPrice);
                txtStock = v.findViewById(R.id.txtStockStatus);
                txtCheapest = v.findViewById(R.id.txtCheapest);
            }
        }
    }
}

