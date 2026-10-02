package com.example.qless;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One reservation: pickup code, time left, items, store, cancel. Updates live. */
public class ReservationDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RESERVATION_ID = "reservation_id";

    private static final long TICK_MS = 30_000;
    private static final long RUNNING_OUT_MS = 2 * 60 * 60 * 1000L;

    public static Intent intent(Context context, String reservationId) {
        return new Intent(context, ReservationDetailActivity.class)
                .putExtra(EXTRA_RESERVATION_ID, reservationId);
    }

    private TextView txtTitle, txtSubtitle, txtPickupCode, txtCountdown, txtHeldUntil;
    private TextView txtTotalLabel, txtTotal, txtStoreName, txtStoreAddress;
    private FrameLayout statusIcon;
    private ImageView imgStatus;
    private View countdownSection;
    private LinearProgressIndicator progressHold;
    private LinearLayout itemsContainer;
    private MaterialButton btnDirections, btnCancel;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::renderCountdown;
    private String reservationId;
    private Map<String, Object> reservation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservation_detail);

        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);
        if (reservationId == null) {
            finish();
            return;
        }

        txtTitle = findViewById(R.id.txtTitle);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        txtPickupCode = findViewById(R.id.txtPickupCode);
        txtCountdown = findViewById(R.id.txtCountdown);
        txtHeldUntil = findViewById(R.id.txtHeldUntil);
        txtTotalLabel = findViewById(R.id.txtTotalLabel);
        txtTotal = findViewById(R.id.txtTotal);
        txtStoreName = findViewById(R.id.txtStoreName);
        txtStoreAddress = findViewById(R.id.txtStoreAddress);
        statusIcon = findViewById(R.id.statusIcon);
        imgStatus = findViewById(R.id.imgStatus);
        countdownSection = findViewById(R.id.countdownSection);
        progressHold = findViewById(R.id.progressHold);
        itemsContainer = findViewById(R.id.itemsContainer);
        btnDirections = findViewById(R.id.btnDirections);
        btnCancel = findViewById(R.id.btnCancel);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        btnDirections.setOnClickListener(v -> openDirections());
        btnCancel.setOnClickListener(v -> confirmCancel());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (reservationId == null) return;
        // Live: the webhook marks payments, the server sweep expires holds.
        // Activity-scoped, so it detaches itself in onStop.
        FirebaseFirestore.getInstance()
                .collection("reservations").document(reservationId)
                .addSnapshotListener(this, (snap, e) -> {
                    if (snap != null && snap.exists()) render(snap);
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        ticker.removeCallbacks(tick);
    }

    private void render(DocumentSnapshot snap) {
        reservation = snap.getData();
        String status = snap.getString("status");
        boolean paid = "card".equals(snap.getString("paymentMethod"));
        String store = text(snap.getString("storeName"));

        txtPickupCode.setText(text(snap.getString("pickupCode")));
        txtStoreName.setText(store);
        String address = snap.getString("storeAddress");
        txtStoreAddress.setText(address == null || address.isEmpty() ? store : address);
        txtTotalLabel.setText(paid ? R.string.total_paid : R.string.total_pay_at_pickup);
        txtTotal.setText(euro(snap.getDouble("total")));
        renderItems(snap.get("items"));

        boolean active = "reserved".equals(status);
        btnCancel.setVisibility(active ? View.VISIBLE : View.GONE);
        btnDirections.setVisibility(active || "pending_payment".equals(status) ? View.VISIBLE : View.GONE);
        countdownSection.setVisibility(active ? View.VISIBLE : View.GONE);
        txtPickupCode.setAlpha(active ? 1f : 0.4f);

        if ("pending_payment".equals(status)) {
            setHeader(false, getString(R.string.confirming_payment), getString(R.string.confirming_payment_desc));
        } else if ("picked_up".equals(status)) {
            setHeader(true, getString(R.string.reservation_picked_up), getString(R.string.reservation_picked_up_desc));
        } else if ("expired".equals(status)) {
            setHeader(false, getString(R.string.reservation_expired), getString(paid
                    ? R.string.reservation_expired_desc_paid : R.string.reservation_expired_desc_pickup));
        } else if ("cancelled".equals(status)) {
            setHeader(false, getString(R.string.reservation_cancelled), getString(paid
                    ? R.string.reservation_cancelled_desc_paid : R.string.reservation_cancelled_desc_pickup));
        } else {
            setHeader(true, getString(R.string.reserved_at, store),
                    getString(paid ? R.string.detail_subtitle_paid : R.string.detail_subtitle_pickup));
        }

        ticker.removeCallbacks(tick);
        if (active) renderCountdown();
    }

    private void setHeader(boolean success, String title, String subtitle) {
        statusIcon.setBackgroundResource(success ? R.drawable.bg_circle_success_container : R.drawable.bg_circle_muted);
        imgStatus.setImageResource(success ? R.drawable.ic_check : R.drawable.ic_clock);
        imgStatus.setColorFilter(getColor(success ? R.color.success : R.color.text_tertiary));
        txtTitle.setText(title);
        txtSubtitle.setText(subtitle);
    }

    @SuppressWarnings("unchecked")
    private void renderItems(Object items) {
        itemsContainer.removeAllViews();
        if (!(items instanceof List)) return;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Object o : (List<Object>) items) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) o;
            int qty = item.get("quantity") instanceof Number ? ((Number) item.get("quantity")).intValue() : 1;
            double price = item.get("price") instanceof Number ? ((Number) item.get("price")).doubleValue() : 0;
            String name = text((String) item.get("productName"));

            View row = inflater.inflate(R.layout.item_reservation_line, itemsContainer, false);
            ((TextView) row.findViewById(R.id.txtLineName))
                    .setText(qty > 1 ? getString(R.string.line_with_quantity, name, qty) : name);
            ((TextView) row.findViewById(R.id.txtLinePrice)).setText(euro(price * qty));
            itemsContainer.addView(row);
        }
    }

    private void renderCountdown() {
        if (reservation == null) return;
        long now = System.currentTimeMillis();
        long deadline = number(reservation.get("pickupDeadline"));
        long start = number(reservation.get("paidAt")) > 0
                ? number(reservation.get("paidAt")) : number(reservation.get("createdAt"));
        long left = Math.max(0, deadline - now);

        long hours = left / 3_600_000;
        long minutes = (left % 3_600_000) / 60_000;
        txtCountdown.setText(hours > 0
                ? getString(R.string.countdown_hours_minutes, (int) hours, (int) minutes)
                : getString(R.string.countdown_minutes, (int) minutes));
        txtCountdown.setTextColor(getColor(left < RUNNING_OUT_MS ? R.color.warning : R.color.text_primary));

        long window = Math.max(1, deadline - start);
        progressHold.setProgress((int) Math.round(1000.0 * left / window));
        progressHold.setIndicatorColor(getColor(left < RUNNING_OUT_MS ? R.color.warning : R.color.primary));

        SimpleDateFormat fmt = new SimpleDateFormat("EEE, d MMM · HH:mm", Locale.getDefault());
        txtHeldUntil.setText(getString(R.string.held_until, fmt.format(new Date(deadline))));

        if (left > 0) ticker.postDelayed(tick, TICK_MS);
    }

    private void openDirections() {
        String address = txtStoreAddress.getText().toString();
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_maps_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmCancel() {
        if (reservation == null) return;
        boolean paid = "card".equals(reservation.get("paymentMethod"));
        String message = paid
                ? getString(R.string.cancel_confirm_paid, euro(reservation.get("total")))
                : getString(R.string.cancel_confirm_pickup);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cancel_confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.cancel_confirm_yes, (d, w) -> cancel(paid))
                .setNegativeButton(R.string.cancel_confirm_no, null)
                .show();
    }

    private void cancel(boolean paid) {
        btnCancel.setEnabled(false);
        Map<String, Object> data = new HashMap<>();
        data.put("reservationId", reservationId);
        FirebaseFunctions.getInstance("europe-west1")
                .getHttpsCallable("cancelReservation")
                .call(data)
                .addOnSuccessListener(r -> Toast.makeText(this,
                        paid ? R.string.cancel_done_paid : R.string.cancel_done_pickup, Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> {
                    btnCancel.setEnabled(true);
                    Toast.makeText(this, R.string.cancel_failed, Toast.LENGTH_LONG).show();
                });
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String euro(Object amount) {
        double value = amount instanceof Number ? ((Number) amount).doubleValue() : 0;
        return String.format(Locale.getDefault(), "€%.2f", value);
    }
}
