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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {

    private static final int PAYMENT_REQUEST_CODE = 1001;

    private TextView txtSubtotal, txtServiceFee, txtTotal;
    private TextView txtStoreName, txtStoreAddress, txtPickupDeadline;
    private RecyclerView rvOrderItems;
    private FrameLayout loadingOverlay;

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
        rvOrderItems = findViewById(R.id.rvOrderItems);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnConfirmReservation).setOnClickListener(v -> proceedToPayment());

        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadOrderData() {
        cartItems = CartManager.getInstance().getItems();

        if (cartItems.isEmpty()) {
            finish();
            return;
        }

        // Calculate totals
        subtotal = CartManager.getInstance().getTotal();
        serviceFee = SERVICE_FEE_RATE;
        total = subtotal + serviceFee;

        // Display totals
        txtSubtotal.setText(String.format(Locale.getDefault(), "€%.2f", subtotal));
        txtServiceFee.setText(String.format(Locale.getDefault(), "€%.2f", serviceFee));
        txtTotal.setText(String.format(Locale.getDefault(), "€%.2f", total));

        // Set store info (using first item's store)
        if (!cartItems.isEmpty()) {
            txtStoreName.setText(cartItems.get(0).storeName);
            txtStoreAddress.setText("Available for pickup");
        }

        // Calculate pickup deadline (48 hours from now)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 48);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy h:mm a", Locale.getDefault());
        txtPickupDeadline.setText("Before " + sdf.format(cal.getTime()));

        // Set order items adapter
        rvOrderItems.setAdapter(new OrderItemsAdapter(cartItems));
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

