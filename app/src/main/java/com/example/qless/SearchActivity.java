package com.example.qless;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SearchActivity extends AppCompatActivity {

    private EditText etSearch;
    private RecyclerView rvResults;
    private LinearLayout emptyState;
    private FirebaseFirestore db;
    private List<Map<String, Object>> allProducts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        db = FirebaseFirestore.getInstance();
        etSearch = findViewById(R.id.etSearch);
        rvResults = findViewById(R.id.rvResults);
        emptyState = findViewById(R.id.emptyState);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        loadAllProducts();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts(s.toString());
            }
        });

        etSearch.requestFocus();
    }

    private void loadAllProducts() {
        db.collection("products").get().addOnSuccessListener(snap -> {
            allProducts.clear();
            for (QueryDocumentSnapshot doc : snap) {
                Map<String, Object> p = doc.getData();
                p.put("id", doc.getId());
                allProducts.add(p);
            }
            rvResults.setAdapter(new ResultsAdapter(allProducts));
            updateEmpty(allProducts.size());
        });
    }

    private void filterProducts(String query) {
        if (query.isEmpty()) {
            rvResults.setAdapter(new ResultsAdapter(allProducts));
            updateEmpty(allProducts.size());
            return;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> p : allProducts) {
            String name = ((String) p.get("name")).toLowerCase();
            String cat = p.get("category") != null ? ((String) p.get("category")).toLowerCase() : "";
            String brand = p.get("brand") != null ? ((String) p.get("brand")).toLowerCase() : "";
            if (name.contains(query.toLowerCase()) || cat.contains(query.toLowerCase()) || brand.contains(query.toLowerCase())) {
                filtered.add(p);
            }
        }
        rvResults.setAdapter(new ResultsAdapter(filtered));
        updateEmpty(filtered.size());
    }

    private void updateEmpty(int count) {
        emptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        rvResults.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.VH> {
        private final List<Map<String, Object>> items;
        ResultsAdapter(List<Map<String, Object>> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Map<String, Object> p = items.get(pos);
            h.txtName.setText((String) p.get("name"));
            h.txtCategory.setText(p.get("category") != null ? (String) p.get("category") : "");

            // Load product image
            String imageUrl = (String) p.get("imageUrl");
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

            List<Map<String, Object>> prices = (List<Map<String, Object>>) p.get("prices");
            if (prices != null && !prices.isEmpty()) {
                double cheapest = Double.MAX_VALUE;
                for (Map<String, Object> pr : prices)
                    cheapest = Math.min(cheapest, ((Number) pr.get("price")).doubleValue());
                h.txtPrice.setText(String.format(Locale.getDefault(), "€%.2f", cheapest));
                h.txtStoreCount.setText(prices.size() + " store" + (prices.size() > 1 ? "s" : ""));
            }

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(SearchActivity.this, ProductDetailActivity.class);
                intent.putExtra("productId", (String) p.get("id"));
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView txtName, txtCategory, txtPrice, txtStoreCount;
            ImageView imgProduct;
            VH(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtProductName);
                txtCategory = v.findViewById(R.id.txtCategory);
                txtPrice = v.findViewById(R.id.txtPrice);
                txtStoreCount = v.findViewById(R.id.txtStoreCount);
                imgProduct = v.findViewById(R.id.imgProduct);
            }
        }
    }
}

