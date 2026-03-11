package com.example.qless;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductDetailActivity extends AppCompatActivity {

    private TextView txtCategory, txtProductName, txtBrand, txtRating, txtReviews, txtDescription, txtBestPrice;
    private RecyclerView rvPrices, rvSimilar, rvPopular;
    private TextView txtSimilarHeader, txtPopularHeader;
    private FirebaseFirestore db;
    private RecommendationEngine recEngine;

    private String productId;
    private String productName = "";
    private String productCategory = "";
    private String productBrand = "";
    private double cheapestPrice = 0;
    private String cheapestStore = "";
    private String cheapestStoreId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        db = FirebaseFirestore.getInstance();
        recEngine = new RecommendationEngine();
        productId = getIntent().getStringExtra("productId");

        txtCategory = findViewById(R.id.txtCategory);
        txtProductName = findViewById(R.id.txtProductName);
        txtBrand = findViewById(R.id.txtBrand);
        txtRating = findViewById(R.id.txtRating);
        txtReviews = findViewById(R.id.txtReviews);
        txtDescription = findViewById(R.id.txtDescription);
        txtBestPrice = findViewById(R.id.txtBestPrice);
        rvPrices = findViewById(R.id.rvPrices);
        rvSimilar = findViewById(R.id.rvSimilar);
        rvPopular = findViewById(R.id.rvPopular);
        txtSimilarHeader = findViewById(R.id.txtSimilarHeader);
        txtPopularHeader = findViewById(R.id.txtPopularHeader);

        rvPrices.setLayoutManager(new LinearLayoutManager(this));
        rvSimilar.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvPopular.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnAddToCart).setOnClickListener(v -> {
            if (!productName.isEmpty()) {
                CartManager.getInstance().addItem(productId, productName, cheapestStore, cheapestStoreId, cheapestPrice);
                Toast.makeText(this, getString(R.string.added_to_cart), Toast.LENGTH_SHORT).show();

                // Log add_to_cart event for ML
                EventLogger.getInstance().logEvent(
                        EventLogger.ADD_TO_CART, productId, productCategory, productBrand, cheapestPrice, cheapestStoreId);
            }
        });

        if (productId != null) loadProduct();
    }

    private void loadProduct() {
        db.collection("products").document(productId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    productName = doc.getString("name");
                    productCategory = doc.getString("category");
                    productBrand = doc.getString("brand");

                    txtProductName.setText(productName);
                    txtCategory.setText(productCategory);
                    txtBrand.setText(productBrand);

                    Object desc = doc.get("description");
                    txtDescription.setText(desc != null ? (String) desc : "");

                    // Load product image
                    String imageUrl = doc.getString("imageUrl");
                    ImageView imgProduct = findViewById(R.id.imgProduct);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(ProductDetailActivity.this)
                                .load(imageUrl)
                                .placeholder(R.drawable.placeholder_product)
                                .error(R.drawable.placeholder_product)
                                .centerCrop()
                                .into(imgProduct);
                    }

                    Object rating = doc.get("rating");
                    if (rating != null) txtRating.setText(String.format(Locale.getDefault(), "%.1f", ((Number) rating).doubleValue()));

                    Object reviews = doc.get("reviews");
                    if (reviews != null) txtReviews.setText("(" + ((Number) reviews).intValue() + " reviews)");

                    // Prices
                    List<Map<String, Object>> prices = (List<Map<String, Object>>) doc.get("prices");
                    if (prices != null && !prices.isEmpty()) {
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

                    // === ML: Log view event ===
                    EventLogger.getInstance().logView(productId, productCategory, productBrand);

                    // === ML: Load Similar Products ===
                    recEngine.getSimilarProducts(productId, productCategory, productBrand, cheapestPrice, similar -> {
                        if (!similar.isEmpty()) {
                            txtSimilarHeader.setVisibility(View.VISIBLE);
                            rvSimilar.setVisibility(View.VISIBLE);
                            rvSimilar.setAdapter(new MiniProductAdapter(similar));
                        }
                    });

                    // === ML: Load Popular in Same Category ===
                    if (productCategory != null) {
                        recEngine.getPopularInCategory(productCategory, productId, popular -> {
                            if (!popular.isEmpty()) {
                                txtPopularHeader.setVisibility(View.VISIBLE);
                                rvPopular.setVisibility(View.VISIBLE);
                                rvPopular.setAdapter(new MiniProductAdapter(popular));
                            }
                        });
                    }
                });
    }

    // =================== MINI PRODUCT ADAPTER (for recommendations) ===================
    private class MiniProductAdapter extends RecyclerView.Adapter<MiniProductAdapter.VH> {
        private final List<Map<String, Object>> items;
        MiniProductAdapter(List<Map<String, Object>> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deal, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> product = items.get(pos);
            h.txtProductName.setText((String) product.get("name"));

            // Load image
            String imageUrl = (String) product.get("imageUrl");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(h.itemView.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.placeholder_product)
                        .error(R.drawable.placeholder_product)
                        .centerCrop()
                        .into(h.imgProduct);
            } else {
                h.imgProduct.setImageResource(R.drawable.placeholder_product);
            }

            List<Map<String, Object>> prices = (List<Map<String, Object>>) product.get("prices");
            if (prices != null && !prices.isEmpty()) {
                double cheapest = Double.MAX_VALUE;
                String storeName = "";
                double original = 0;
                for (Map<String, Object> p : prices) {
                    double pr = ((Number) p.get("price")).doubleValue();
                    if (pr < cheapest) {
                        cheapest = pr;
                        storeName = (String) p.get("storeName");
                        Object o = p.get("originalPrice");
                        original = o != null ? ((Number) o).doubleValue() : 0;
                    }
                }
                h.txtPrice.setText(String.format(Locale.getDefault(), "€%.2f", cheapest));
                h.txtStoreName.setText("at " + storeName);

                if (original > cheapest) {
                    h.txtOriginalPrice.setText(String.format(Locale.getDefault(), "€%.2f", original));
                    h.txtOriginalPrice.setPaintFlags(h.txtOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    h.txtOriginalPrice.setVisibility(View.VISIBLE);
                    int disc = (int) (((original - cheapest) / original) * 100);
                    h.txtDiscount.setText(disc + "% OFF");
                    h.txtDiscount.setVisibility(View.VISIBLE);
                } else {
                    h.txtOriginalPrice.setVisibility(View.GONE);
                    h.txtDiscount.setVisibility(View.GONE);
                }
                h.txtStoreCount.setText(prices.size() + " store" + (prices.size() > 1 ? "s" : ""));
            }

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ProductDetailActivity.this, ProductDetailActivity.class);
                intent.putExtra("productId", (String) product.get("id"));
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView txtProductName, txtStoreName, txtPrice, txtOriginalPrice, txtDiscount, txtStoreCount;
            ImageView imgProduct;
            VH(View v) {
                super(v);
                txtProductName = v.findViewById(R.id.txtProductName);
                txtStoreName = v.findViewById(R.id.txtStoreName);
                txtPrice = v.findViewById(R.id.txtPrice);
                txtOriginalPrice = v.findViewById(R.id.txtOriginalPrice);
                txtDiscount = v.findViewById(R.id.txtDiscount);
                txtStoreCount = v.findViewById(R.id.txtStoreCount);
                imgProduct = v.findViewById(R.id.imgProduct);
            }
        }
    }

    // =================== PRICE ADAPTER ===================
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

            if (pos == cheapestIndex) {
                h.txtCheapest.setVisibility(View.VISIBLE);
            } else {
                h.txtCheapest.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                String storeName = (String) p.get("storeName");
                String storeId = p.get("storeId") != null ? (String) p.get("storeId") : "";
                CartManager.getInstance().addItem(productId, productName, storeName, storeId, price);

                // Log add_to_cart for this specific store
                EventLogger.getInstance().logEvent(
                        EventLogger.ADD_TO_CART, productId, productCategory, productBrand, price, storeId);

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
