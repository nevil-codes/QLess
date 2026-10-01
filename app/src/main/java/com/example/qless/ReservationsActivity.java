package com.example.qless;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
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

public class ReservationsActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private RecyclerView rvReservations;
    private LinearLayout emptyState;
    private TextView txtEmptyTitle, txtEmptyDesc;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private List<Map<String, Object>> activeList = new ArrayList<>();
    private List<Map<String, Object>> pastList = new ArrayList<>();
    private boolean showingActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservations);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupTabs();
        loadReservations();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        rvReservations = findViewById(R.id.rvReservations);
        emptyState = findViewById(R.id.emptyState);
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle);
        txtEmptyDesc = findViewById(R.id.txtEmptyDesc);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        rvReservations.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.active_reservations));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.past_reservations));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showingActive = tab.getPosition() == 0;
                updateUI();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadReservations() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // Query without orderBy first to avoid needing composite index
        db.collection("reservations")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    activeList.clear();
                    pastList.clear();

                    long now = System.currentTimeMillis();

                    List<Map<String, Object>> allReservations = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> reservation = doc.getData();
                        reservation.put("id", doc.getId());
                        allReservations.add(reservation);
                    }
                    
                    // Sort by createdAt locally
                    allReservations.sort((a, b) -> {
                        long timeA = a.get("createdAt") != null ? ((Number) a.get("createdAt")).longValue() : 0;
                        long timeB = b.get("createdAt") != null ? ((Number) b.get("createdAt")).longValue() : 0;
                        return Long.compare(timeB, timeA); // Descending
                    });

                    for (Map<String, Object> reservation : allReservations) {
                        String status = (String) reservation.get("status");
                        
                        if ("reserved".equals(status)) {
                            // Check if expired
                            Object deadline = reservation.get("pickupDeadline");
                            if (deadline != null && ((Number) deadline).longValue() < now) {
                                reservation.put("status", "expired");
                                pastList.add(reservation);
                            } else {
                                activeList.add(reservation);
                            }
                        } else {
                            pastList.add(reservation);
                        }
                    }
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ReservationsActivity", "Error loading reservations", e);
                    android.widget.Toast.makeText(this, "Error loading reservations: " + e.getMessage(), 
                            android.widget.Toast.LENGTH_LONG).show();
                    updateUI();
                });
    }

    private void updateUI() {
        List<Map<String, Object>> currentList = showingActive ? activeList : pastList;

        if (currentList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvReservations.setVisibility(View.GONE);
            txtEmptyTitle.setText(showingActive ? R.string.no_reservations : R.string.no_orders_yet);
            txtEmptyDesc.setText(showingActive ? R.string.no_reservations_desc : R.string.no_orders_desc);
        } else {
            emptyState.setVisibility(View.GONE);
            rvReservations.setVisibility(View.VISIBLE);
            rvReservations.setAdapter(new ReservationsAdapter(currentList));
        }
    }

    private class ReservationsAdapter extends RecyclerView.Adapter<ReservationsAdapter.VH> {
        private final List<Map<String, Object>> reservations;

        ReservationsAdapter(List<Map<String, Object>> reservations) {
            this.reservations = reservations;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_reservation, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> res = reservations.get(position);

            String resId = (String) res.get("reservationId");
            holder.txtReservationId.setText("Reservation #" + (resId != null ? resId.substring(4, Math.min(resId.length(), 16)) : ""));

            Object createdAt = res.get("createdAt");
            if (createdAt != null) {
                long ts = ((Number) createdAt).longValue();
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
                holder.txtReservedDate.setText("Reserved on " + sdf.format(new Date(ts)));
            }

            String status = (String) res.get("status");
            holder.txtStatus.setText(getStatusText(status));
            holder.txtStatus.setBackgroundResource(getStatusBackground(status));
            holder.txtStatus.setTextColor(getResources().getColor(getStatusTextColor(status), null));

            holder.txtStoreName.setText((String) res.get("storeName"));
            holder.txtStoreAddress.setText("Available for pickup");

            Object deadline = res.get("pickupDeadline");
            if (deadline != null) {
                long ts = ((Number) deadline).longValue();
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy h:mm a", Locale.getDefault());
                holder.txtPickupDeadline.setText("Pick up before " + sdf.format(new Date(ts)));
            }

            // Build items preview
            List<Map<String, Object>> items = (List<Map<String, Object>>) res.get("items");
            if (items != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(items.size(), 3); i++) {
                    Map<String, Object> item = items.get(i);
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(((Number) item.get("quantity")).intValue()).append("x ");
                    sb.append(item.get("productName"));
                }
                holder.txtItemsPreview.setText(sb.toString());
            }

            Object total = res.get("total");
            if (total != null) {
                holder.txtTotal.setText(String.format(Locale.getDefault(), "€%.2f", ((Number) total).doubleValue()));
            }

            // Hide action button for past reservations
            if (!"reserved".equals(status)) {
                holder.btnAction.setVisibility(View.GONE);
            }
        }

        private String getStatusText(String status) {
            if (status == null) return "";
            switch (status) {
                case "reserved": return "RESERVED";
                case "picked_up": return "PICKED UP";
                case "expired": return "EXPIRED";
                case "cancelled": return "CANCELLED";
                default: return status.toUpperCase();
            }
        }

        private int getStatusBackground(String status) {
            if ("picked_up".equals(status)) return R.drawable.bg_badge_success_container;
            if ("expired".equals(status) || "cancelled".equals(status)) return R.drawable.bg_status_badge_muted;
            return R.drawable.bg_status_badge;
        }

        private int getStatusTextColor(String status) {
            if ("picked_up".equals(status)) return R.color.success;
            if ("expired".equals(status) || "cancelled".equals(status)) return R.color.text_tertiary;
            return R.color.on_primary_container;
        }

        @Override
        public int getItemCount() {
            return reservations.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtReservationId, txtReservedDate, txtStatus;
            TextView txtStoreName, txtStoreAddress, txtPickupDeadline;
            TextView txtItemsPreview, txtTotal;
            MaterialButton btnAction;

            VH(View v) {
                super(v);
                txtReservationId = v.findViewById(R.id.txtReservationId);
                txtReservedDate = v.findViewById(R.id.txtReservedDate);
                txtStatus = v.findViewById(R.id.txtStatus);
                txtStoreName = v.findViewById(R.id.txtStoreName);
                txtStoreAddress = v.findViewById(R.id.txtStoreAddress);
                txtPickupDeadline = v.findViewById(R.id.txtPickupDeadline);
                txtItemsPreview = v.findViewById(R.id.txtItemsPreview);
                txtTotal = v.findViewById(R.id.txtTotal);
                btnAction = v.findViewById(R.id.btnAction);
            }
        }
    }
}

