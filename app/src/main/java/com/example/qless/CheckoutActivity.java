package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {

    private static final int PAYMENT_REQUEST_CODE = 1001;
    private static final int PICKUP_HOLD_HOURS = 12;
    private static final int CARD_HOLD_HOURS = 48;
    private static final int MAX_NO_SHOWS = 3;

    private TextView txtSubtotal, txtServiceFee, txtTotal;
    private TextView txtStoreName, txtStoreAddress, txtPickupDeadline;
    private TextView txtPickupWindow, txtMissedPickupPolicy, txtPaymentNotice, txtPickupOptionDetail;
    private MaterialCardView optionPickup, optionCard;
    private MaterialRadioButton radioPickup, radioCard;
    private com.google.android.material.button.MaterialButton btnConfirm;
    private RecyclerView rvOrderItems;
    private FrameLayout loadingOverlay;

    private boolean payAtPickup = true;
    private boolean pickupBlocked = false;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private List<CartManager.CartItem> cartItems;
    private double subtotal, serviceFee, total;
    
    private String pendingReservationId;

    private static final double SERVICE_FEE_RATE = 0.99; // Fixed service fee

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        loadOrderData();
    }

    private void initViews() {
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtServiceFee = findViewById(R.id.txtServiceFee);
        txtTotal = findViewById(R.id.txtTotal);
        txtStoreName = findViewById(R.id.txtStoreName);
        txtStoreAddress = findViewById(R.id.txtStoreAddress);
        txtPickupDeadline = findViewById(R.id.txtPickupDeadline);
        txtPickupWindow = findViewById(R.id.txtPickupWindow);
        txtMissedPickupPolicy = findViewById(R.id.txtMissedPickupPolicy);
        txtPaymentNotice = findViewById(R.id.txtPaymentNotice);
        txtPickupOptionDetail = findViewById(R.id.txtPickupOptionDetail);
        optionPickup = findViewById(R.id.optionPickup);
        optionCard = findViewById(R.id.optionCard);
        radioPickup = findViewById(R.id.radioPickup);
        radioCard = findViewById(R.id.radioCard);
        btnConfirm = findViewById(R.id.btnConfirmReservation);
        rvOrderItems = findViewById(R.id.rvOrderItems);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> {
            if (payAtPickup) reserveForPickup();
            else proceedToPayment();
        });
        optionPickup.setOnClickListener(v -> selectPaymentMethod(true));
        optionCard.setOnClickListener(v -> selectPaymentMethod(false));

        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
    }

    // Users with too many missed pickups must prepay; the server enforces
    // this too, this just avoids offering an option that will be refused.
    private void loadNoShowCount() {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("users").document(mAuth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(doc -> {
                    Object count = doc.get("noShowCount");
                    if (count instanceof Number && ((Number) count).intValue() >= MAX_NO_SHOWS) {
                        blockPickup();
                    }
                });
    }

    private void blockPickup() {
        pickupBlocked = true;
        optionPickup.setEnabled(false);
        optionPickup.setAlpha(0.5f);
        txtPickupOptionDetail.setText(R.string.pay_at_pickup_blocked);
        selectPaymentMethod(false);
    }

    private void selectPaymentMethod(boolean pickup) {
        if (pickup && pickupBlocked) return;
        payAtPickup = pickup;
        styleOption(optionPickup, radioPickup, pickup);
        styleOption(optionCard, radioCard, !pickup);
        refreshSummary();
    }

    private void styleOption(MaterialCardView card, MaterialRadioButton radio, boolean selected) {
        radio.setChecked(selected);
        card.setStrokeColor(getColor(selected ? R.color.primary : R.color.outline));
        card.setStrokeWidth(Math.round(getResources().getDisplayMetrics().density * (selected ? 1.5f : 1f)));
    }

    private void refreshSummary() {
        serviceFee = payAtPickup ? 0 : SERVICE_FEE_RATE;
        total = subtotal + serviceFee;
        txtServiceFee.setText(String.format(Locale.getDefault(), "€%.2f", serviceFee));
        txtTotal.setText(String.format(Locale.getDefault(), "€%.2f", total));

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, payAtPickup ? PICKUP_HOLD_HOURS : CARD_HOLD_HOURS);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy h:mm a", Locale.getDefault());
        txtPickupDeadline.setText(getString(R.string.deadline_before, sdf.format(cal.getTime())));

        txtPickupWindow.setText(payAtPickup ? R.string.pickup_window_pickup : R.string.pickup_window_card);
        txtMissedPickupPolicy.setText(payAtPickup
                ? R.string.missed_pickup_policy_pickup : R.string.missed_pickup_policy_card);
        txtPaymentNotice.setText(payAtPickup ? R.string.payment_notice_pickup : R.string.payment_notice_card);
        btnConfirm.setText(payAtPickup ? R.string.reserve_pay_at_pickup : R.string.continue_to_payment);
    }

    private void loadOrderData() {
        cartItems = CartManager.getInstance().getItems();

        if (cartItems.isEmpty()) {
            finish();
            return;
        }

        subtotal = CartManager.getInstance().getTotal();
        txtSubtotal.setText(String.format(Locale.getDefault(), "€%.2f", subtotal));

        List<String> storeNames = new ArrayList<>();
        for (CartManager.CartItem item : cartItems) {
            if (item.storeName != null && !storeNames.contains(item.storeName)) storeNames.add(item.storeName);
        }
        txtStoreName.setText(storeNames.size() > 1
                ? getString(R.string.stores_more, storeNames.get(0), storeNames.size() - 1)
                : storeNames.isEmpty() ? "" : storeNames.get(0));
        txtStoreAddress.setText("Available for pickup");

        rvOrderItems.setAdapter(new OrderItemsAdapter(cartItems));
        selectPaymentMethod(true);
        loadNoShowCount();
    }

    private void reserveForPickup() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.auth_error, Toast.LENGTH_SHORT).show();
            return;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (CartManager.CartItem item : cartItems) {
            Map<String, Object> line = new HashMap<>();
            line.put("productId", item.productId);
            line.put("storeId", item.storeId);
            line.put("quantity", item.quantity);
            items.add(line);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("paymentMethod", "pickup");
        data.put("items", items);

        showLoading(true);
        FirebaseFunctions.getInstance("europe-west1")
                .getHttpsCallable("createReservation")
                .call(data)
                .addOnSuccessListener(result -> {
                    showLoading(false);
                    onPickupReserved(result.getData());
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onPickupFailed(e);
                });
    }

    @SuppressWarnings("unchecked")
    private void onPickupReserved(Object body) {
        List<Map<String, Object>> reservations = new ArrayList<>();
        if (body instanceof Map && ((Map<String, Object>) body).get("reservations") instanceof List) {
            reservations = (List<Map<String, Object>>) ((Map<String, Object>) body).get("reservations");
        }
        if (reservations.isEmpty()) {
            Toast.makeText(this, R.string.reservation_failed, Toast.LENGTH_LONG).show();
            return;
        }

        Map<String, Object> first = reservations.get(0);
        List<String> storeNames = new ArrayList<>();
        double reservedTotal = 0;
        for (Map<String, Object> r : reservations) {
            storeNames.add(String.valueOf(r.get("storeName")));
            if (r.get("total") instanceof Number) reservedTotal += ((Number) r.get("total")).doubleValue();
        }
        String stores = storeNames.size() > 1
                ? getString(R.string.stores_more, storeNames.get(0), storeNames.size() - 1)
                : storeNames.get(0);

        CartManager.getInstance().clear();
        Intent intent = new Intent(this, PaymentSuccessActivity.class);
        intent.putExtra(PaymentSuccessActivity.EXTRA_ORDER_ID, String.valueOf(first.get("reservationId")));
        intent.putExtra(PaymentSuccessActivity.EXTRA_AMOUNT, reservedTotal);
        intent.putExtra(PaymentSuccessActivity.EXTRA_STORE_NAME, stores);
        intent.putExtra(PaymentSuccessActivity.EXTRA_PAY_AT_PICKUP, true);
        intent.putExtra(PaymentSuccessActivity.EXTRA_PICKUP_CODE, String.valueOf(first.get("pickupCode")));
        if (first.get("pickupDeadline") instanceof Number) {
            intent.putExtra(PaymentSuccessActivity.EXTRA_DEADLINE, ((Number) first.get("pickupDeadline")).longValue());
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @SuppressWarnings("unchecked")
    private void onPickupFailed(Exception e) {
        String reason = null;
        if (e instanceof FirebaseFunctionsException
                && ((FirebaseFunctionsException) e).getDetails() instanceof Map) {
            Object r = ((Map<String, Object>) ((FirebaseFunctionsException) e).getDetails()).get("reason");
            reason = r != null ? r.toString() : null;
        }
        if ("prepay-required".equals(reason)) {
            blockPickup();
            Toast.makeText(this, R.string.reservation_prepay_required, Toast.LENGTH_LONG).show();
        } else if ("unavailable".equals(reason)) {
            Toast.makeText(this, R.string.reservation_unavailable, Toast.LENGTH_LONG).show();
        } else {
            android.util.Log.e("CheckoutActivity", "createReservation failed", e);
            Toast.makeText(this, R.string.reservation_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void proceedToPayment() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, R.string.auth_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (cartItems == null || cartItems.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // First create the reservation (pending payment)
        String userId = mAuth.getCurrentUser().getUid();
        pendingReservationId = "RES-" + System.currentTimeMillis();

        // Build items list for Firestore
        List<Map<String, Object>> items = new ArrayList<>();
        for (CartManager.CartItem item : cartItems) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("productId", item.productId);
            itemMap.put("productName", item.name);
            itemMap.put("storeName", item.storeName);
            itemMap.put("price", item.price);
            itemMap.put("quantity", item.quantity);
            items.add(itemMap);
        }

        // Calculate deadline
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 48);

        // Create reservation document (pending payment)
        Map<String, Object> reservation = new HashMap<>();
        reservation.put("reservationId", pendingReservationId);
        reservation.put("userId", userId);
        reservation.put("items", items);
        reservation.put("subtotal", subtotal);
        reservation.put("serviceFee", serviceFee);
        reservation.put("total", total);
        reservation.put("storeName", cartItems.get(0).storeName);
        reservation.put("status", "pending_payment");
        reservation.put("paymentStatus", "pending");
        reservation.put("createdAt", System.currentTimeMillis());
        reservation.put("pickupDeadline", cal.getTimeInMillis());

        db.collection("reservations").document(pendingReservationId)
                .set(reservation)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    
                    // Open payment activity
                    Intent paymentIntent = new Intent(this, PaymentActivity.class);
                    paymentIntent.putExtra(PaymentActivity.EXTRA_AMOUNT, total);
                    paymentIntent.putExtra(PaymentActivity.EXTRA_RESERVATION_ID, pendingReservationId);
                    paymentIntent.putExtra(PaymentActivity.EXTRA_STORE_NAME, cartItems.get(0).storeName);
                    startActivityForResult(paymentIntent, PAYMENT_REQUEST_CODE);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    android.util.Log.e("CheckoutActivity", "Failed to create reservation", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PAYMENT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getBooleanExtra("payment_success", false)) {
                // Payment successful
                onPaymentSuccess();
            } else {
                // Payment cancelled or failed - delete pending reservation
                onPaymentCancelled();
            }
        }
    }

    private void onPaymentSuccess() {
        // Update reservation status
        if (pendingReservationId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "reserved");
            updates.put("paymentStatus", "paid");

            String storeName = cartItems.get(0).storeName;

            db.collection("reservations").document(pendingReservationId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Clear cart
                        CartManager.getInstance().clear();
                        
                        // Navigate to PaymentSuccessActivity
                        Intent intent = new Intent(this, PaymentSuccessActivity.class);
                        intent.putExtra(PaymentSuccessActivity.EXTRA_ORDER_ID, pendingReservationId);
                        intent.putExtra(PaymentSuccessActivity.EXTRA_AMOUNT, total);
                        intent.putExtra(PaymentSuccessActivity.EXTRA_STORE_NAME, storeName);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        // Still clear cart and navigate - payment was successful
                        CartManager.getInstance().clear();
                        
                        Intent intent = new Intent(this, PaymentSuccessActivity.class);
                        intent.putExtra(PaymentSuccessActivity.EXTRA_ORDER_ID, pendingReservationId);
                        intent.putExtra(PaymentSuccessActivity.EXTRA_AMOUNT, total);
                        intent.putExtra(PaymentSuccessActivity.EXTRA_STORE_NAME, storeName);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    });
        } else {
            CartManager.getInstance().clear();
            
            // Navigate to PaymentSuccessActivity even without reservation ID
            Intent intent = new Intent(this, PaymentSuccessActivity.class);
            intent.putExtra(PaymentSuccessActivity.EXTRA_AMOUNT, total);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }

    private void onPaymentCancelled() {
        // Delete the pending reservation
        if (pendingReservationId != null) {
            db.collection("reservations").document(pendingReservationId)
                    .delete()
                    .addOnCompleteListener(task -> {
                        Toast.makeText(this, R.string.payment_cancelled, Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // Adapter for order items
    private class OrderItemsAdapter extends RecyclerView.Adapter<OrderItemsAdapter.VH> {
        private final List<CartManager.CartItem> items;

        OrderItemsAdapter(List<CartManager.CartItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_checkout_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CartManager.CartItem item = items.get(position);
            holder.txtName.setText(item.name);
            holder.txtQty.setText("x" + item.quantity);
            holder.txtPrice.setText(String.format(Locale.getDefault(), "€%.2f", item.price * item.quantity));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtName, txtQty, txtPrice;

            VH(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtProductName);
                txtQty = v.findViewById(R.id.txtQuantity);
                txtPrice = v.findViewById(R.id.txtPrice);
            }
        }
    }
}

