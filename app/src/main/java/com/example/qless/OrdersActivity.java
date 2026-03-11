package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrdersActivity extends AppCompatActivity {

    private RecyclerView rvOrders;
    private LinearLayout emptyState;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private List<Map<String, Object>> ordersList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvOrders = findViewById(R.id.rvOrders);
        emptyState = findViewById(R.id.emptyState);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnStartShopping).setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        loadOrders();
    }

    private void loadOrders() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // Load completed reservations as orders - query without orderBy to avoid composite index
        db.collection("reservations")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    ordersList.clear();
                    List<Map<String, Object>> allOrders = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> order = doc.getData();
                        String status = (String) order.get("status");
                        // Only include picked_up orders
                        if ("picked_up".equals(status)) {
                            order.put("id", doc.getId());
                            allOrders.add(order);
                        }
                    }
                    
                    // Sort by createdAt locally (descending)
                    allOrders.sort((a, b) -> {
                        long timeA = a.get("createdAt") != null ? ((Number) a.get("createdAt")).longValue() : 0;
                        long timeB = b.get("createdAt") != null ? ((Number) b.get("createdAt")).longValue() : 0;
                        return Long.compare(timeB, timeA);
                    });
                    
                    ordersList.addAll(allOrders);
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("OrdersActivity", "Error loading orders", e);
                    android.widget.Toast.makeText(this, "Error loading orders: " + e.getMessage(), 
                            android.widget.Toast.LENGTH_LONG).show();
                    updateUI();
                });
    }

    private void updateUI() {
        if (ordersList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvOrders.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvOrders.setVisibility(View.VISIBLE);
            rvOrders.setAdapter(new OrdersAdapter(ordersList));
        }
    }

    private class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.VH> {
        private final List<Map<String, Object>> orders;

        OrdersAdapter(List<Map<String, Object>> orders) {
            this.orders = orders;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> order = orders.get(position);

            String orderId = (String) order.get("reservationId");
            holder.txtOrderId.setText("Order #" + (orderId != null ? orderId.substring(0, Math.min(orderId.length(), 12)) : ""));

            Object createdAt = order.get("createdAt");
            if (createdAt != null) {
                long ts = ((Number) createdAt).longValue();
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
                holder.txtOrderDate.setText(sdf.format(new Date(ts)));
            }

            String status = (String) order.get("status");
            holder.txtStatus.setText(status != null ? status.replace("_", " ").toUpperCase() : "COMPLETED");
            holder.txtStatus.setBackgroundResource(R.drawable.bg_status_badge);

            holder.txtStoreName.setText((String) order.get("storeName"));

            // Build items preview
            List<Map<String, Object>> items = (List<Map<String, Object>>) order.get("items");
            if (items != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(items.size(), 3); i++) {
                    Map<String, Object> item = items.get(i);
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(item.get("productName"));
                    sb.append(" x").append(((Number) item.get("quantity")).intValue());
                }
                if (items.size() > 3) sb.append("...");
                holder.txtItemsPreview.setText(sb.toString());
            }

            Object total = order.get("total");
            if (total != null) {
                holder.txtTotal.setText(String.format(Locale.getDefault(), "€%.2f", ((Number) total).doubleValue()));
            }
        }

        @Override
        public int getItemCount() {
            return orders.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtOrderId, txtOrderDate, txtStatus, txtStoreName, txtItemsPreview, txtTotal;

            VH(View v) {
                super(v);
                txtOrderId = v.findViewById(R.id.txtOrderId);
                txtOrderDate = v.findViewById(R.id.txtOrderDate);
                txtStatus = v.findViewById(R.id.txtStatus);
                txtStoreName = v.findViewById(R.id.txtStoreName);
                txtItemsPreview = v.findViewById(R.id.txtItemsPreview);
                txtTotal = v.findViewById(R.id.txtTotal);
            }
        }
    }
}

