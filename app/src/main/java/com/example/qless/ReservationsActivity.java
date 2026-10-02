package com.example.qless;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Active (reserved or awaiting payment) and past (expired or cancelled)
 * reservations. Picked-up ones live in OrdersActivity. Statuses come from
 * the server, which expires reservations on time.
 */
public class ReservationsActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private RecyclerView rvReservations;
    private LinearLayout emptyState;
    private TextView txtEmptyTitle, txtEmptyDesc;

    private final List<Map<String, Object>> activeList = new ArrayList<>();
    private final List<Map<String, Object>> pastList = new ArrayList<>();
    private boolean showingActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservations);

        tabLayout = findViewById(R.id.tabLayout);
        rvReservations = findViewById(R.id.rvReservations);
        emptyState = findViewById(R.id.emptyState);
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle);
        txtEmptyDesc = findViewById(R.id.txtEmptyDesc);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        rvReservations.setLayoutManager(new LinearLayoutManager(this));
        setupTabs();
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenToReservations();
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

    // Activity-scoped listener: detached automatically in onStop.
    private void listenToReservations() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("reservations")
                .whereEqualTo("userId", userId)
                .addSnapshotListener(this, (snap, e) -> {
                    if (e != null || snap == null) {
                        Log.e("ReservationsActivity", "Error loading reservations", e);
                        Toast.makeText(this, R.string.reservations_load_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<Map<String, Object>> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        Map<String, Object> reservation = doc.getData();
                        reservation.put("id", doc.getId());
                        all.add(reservation);
                    }
                    all.sort((a, b) -> Long.compare(number(b.get("createdAt")), number(a.get("createdAt"))));

                    activeList.clear();
                    pastList.clear();
                    for (Map<String, Object> r : all) {
                        String status = (String) r.get("status");
                        if ("reserved".equals(status) || "pending_payment".equals(status)) activeList.add(r);
                        else if ("expired".equals(status) || "cancelled".equals(status)) pastList.add(r);
                    }
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

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
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

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> res = reservations.get(position);
            String id = (String) res.get("id");
            String status = (String) res.get("status");
            boolean paid = "card".equals(res.get("paymentMethod"));

            String code = (String) res.get("pickupCode");
            holder.txtReservationId.setText(code != null ? code : getString(R.string.reservation_number,
                    id != null ? id.substring(4, Math.min(id.length(), 16)) : ""));

            SimpleDateFormat day = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
            holder.txtReservedDate.setText(getString(R.string.reserved_on, day.format(new Date(number(res.get("createdAt"))))));

            holder.txtStatus.setText(statusText(status));
            holder.txtStatus.setBackgroundResource(statusBackground(status));
            holder.txtStatus.setTextColor(getColor(statusTextColor(status)));

            holder.txtStoreName.setText((String) res.get("storeName"));
            String address = (String) res.get("storeAddress");
            holder.txtStoreAddress.setText(address != null && !address.isEmpty() ? address : getString(R.string.available_for_pickup));

            SimpleDateFormat time = new SimpleDateFormat("MMMM dd, yyyy h:mm a", Locale.getDefault());
            holder.txtPickupDeadline.setText(getString(R.string.pick_up_before, time.format(new Date(number(res.get("pickupDeadline"))))));

            StringBuilder preview = new StringBuilder();
            Object items = res.get("items");
            if (items instanceof List) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) items;
                for (int i = 0; i < Math.min(list.size(), 3); i++) {
                    if (preview.length() > 0) preview.append(", ");
                    preview.append(getString(R.string.items_preview_line,
                            (int) number(list.get(i).get("quantity")), list.get(i).get("productName")));
                }
            }
            holder.txtItemsPreview.setText(preview.toString());

            holder.txtAmountLabel.setText(paid ? R.string.amount_paid : R.string.amount_to_pay);
            Object total = res.get("total");
            holder.txtTotal.setText(String.format(Locale.getDefault(), "€%.2f",
                    total instanceof Number ? ((Number) total).doubleValue() : 0));

            boolean active = "reserved".equals(status) || "pending_payment".equals(status);
            holder.btnAction.setVisibility(active ? View.VISIBLE : View.GONE);
            View.OnClickListener open = v -> startActivity(ReservationDetailActivity.intent(ReservationsActivity.this, id));
            holder.btnAction.setOnClickListener(open);
            holder.itemView.setOnClickListener(open);
        }

        private int statusText(String status) {
            if ("pending_payment".equals(status)) return R.string.status_awaiting_payment;
            if ("picked_up".equals(status)) return R.string.status_picked_up;
            if ("expired".equals(status)) return R.string.status_expired;
            if ("cancelled".equals(status)) return R.string.status_cancelled;
            return R.string.status_reserved;
        }

        private int statusBackground(String status) {
            if ("picked_up".equals(status)) return R.drawable.bg_badge_success_container;
            if ("expired".equals(status) || "cancelled".equals(status)) return R.drawable.bg_status_badge_muted;
            return R.drawable.bg_status_badge;
        }

        private int statusTextColor(String status) {
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
            TextView txtItemsPreview, txtAmountLabel, txtTotal;
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
                txtAmountLabel = v.findViewById(R.id.txtAmountLabel);
                txtTotal = v.findViewById(R.id.txtTotal);
                btnAction = v.findViewById(R.id.btnAction);
            }
        }
    }
}
