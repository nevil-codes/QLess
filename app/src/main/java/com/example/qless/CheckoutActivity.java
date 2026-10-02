package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.TaskStackBuilder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {

    private static final String TAG = "CheckoutActivity";
    private static final String REGION = "europe-west1";
    private static final int PICKUP_HOLD_HOURS = 12;
    private static final int CARD_HOLD_HOURS = 48;
    private static final int MAX_NO_SHOWS = 3;
    private static final double CARD_SERVICE_FEE = 0.99;

    private TextView txtSubtotal, txtServiceFee, txtTotal;
    private TextView txtStoreName, txtStoreAddress, txtPickupDeadline;
    private TextView txtPickupWindow, txtMissedPickupPolicy, txtPaymentNotice, txtPickupOptionDetail;
    private MaterialCardView optionPickup, optionCard;
    private MaterialRadioButton radioPickup, radioCard;
    private MaterialButton btnConfirm;
    private RecyclerView rvOrderItems;
    private FrameLayout loadingOverlay;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseFunctions functions;
    private PaymentSheet paymentSheet;

    private List<CartManager.CartItem> cartItems;
    private double subtotal;
    private boolean payAtPickup = true;
    private boolean pickupBlocked = false;

    // The createReservation response while the payment sheet is open.
    private Map<String, Object> pendingCheckout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance(REGION);
        // Must be created in onCreate: it registers for an activity result.
        paymentSheet = new PaymentSheet(this, this::onPaymentSheetResult);

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
        btnConfirm.setOnClickListener(v -> reserve());
        optionPickup.setOnClickListener(v -> selectPaymentMethod(true));
        optionCard.setOnClickListener(v -> selectPaymentMethod(false));

        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
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
        txtStoreName.setText(joinStores(storeNames));
        txtStoreAddress.setText("Available for pickup");

        rvOrderItems.setAdapter(new OrderItemsAdapter(cartItems));

        if (BuildConfig.STRIPE_PUBLISHABLE_KEY.isEmpty()) {
            // Builds without a Stripe key (e.g. CI) can't take card payments.
            optionCard.setEnabled(false);
            optionCard.setAlpha(0.5f);
        }
        selectPaymentMethod(true);
        loadNoShowCount();
    }

    private String joinStores(List<String> names) {
        if (names.isEmpty()) return "";
        return names.size() > 1 ? getString(R.string.stores_more, names.get(0), names.size() - 1) : names.get(0);
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
        if (!pickup && !optionCard.isEnabled()) return;
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
        double serviceFee = payAtPickup ? 0 : CARD_SERVICE_FEE;
        txtServiceFee.setText(String.format(Locale.getDefault(), "€%.2f", serviceFee));
        txtTotal.setText(String.format(Locale.getDefault(), "€%.2f", subtotal + serviceFee));

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

    // Prices, stock and reservations are all handled by the createReservation
    // function; card checkouts then go through Stripe's payment sheet.
    private void reserve() {
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
        data.put("paymentMethod", payAtPickup ? "pickup" : "card");
        data.put("items", items);

        showLoading(true);
        functions.getHttpsCallable("createReservation")
                .call(data)
                .addOnSuccessListener(result -> {
                    showLoading(false);
                    onReservationCreated(result.getData());
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    onReservationFailed(e);
                });
    }

    @SuppressWarnings("unchecked")
    private void onReservationCreated(Object body) {
        if (!(body instanceof Map)) {
            Toast.makeText(this, R.string.reservation_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> checkout = (Map<String, Object>) body;
        if (payAtPickup) {
            showConfirmation(checkout);
            return;
        }

        Object clientSecret = checkout.get("clientSecret");
        if (!(clientSecret instanceof String)) {
            Toast.makeText(this, R.string.reservation_failed, Toast.LENGTH_LONG).show();
            return;
        }
        pendingCheckout = checkout;
        PaymentConfiguration.init(getApplicationContext(), BuildConfig.STRIPE_PUBLISHABLE_KEY);
        paymentSheet.presentWithPaymentIntent((String) clientSecret,
                new PaymentSheet.Configuration(getString(R.string.app_name)));
    }

    private void onPaymentSheetResult(PaymentSheetResult result) {
        Map<String, Object> checkout = pendingCheckout;
        pendingCheckout = null;
        if (checkout == null) return;

        if (result instanceof PaymentSheetResult.Completed) {
            // The reservation turns "reserved" when Stripe's webhook confirms
            // the payment; the sheet only tells us it went through.
            showConfirmation(checkout);
            return;
        }

        cancelPendingPayment(checkout.get("paymentIntentId"));
        if (result instanceof PaymentSheetResult.Failed) {
            Log.w(TAG, "Payment failed", ((PaymentSheetResult.Failed) result).getError());
            Toast.makeText(this, R.string.payment_failed, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.payment_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

    // Releases the held stock right away instead of waiting for the
    // abandoned payment to expire.
    private void cancelPendingPayment(Object paymentIntentId) {
        if (!(paymentIntentId instanceof String)) return;
        Map<String, Object> data = new HashMap<>();
        data.put("paymentIntentId", paymentIntentId);
        functions.getHttpsCallable("cancelPendingPayment").call(data)
                .addOnFailureListener(e -> Log.w(TAG, "cancelPendingPayment failed", e));
    }

    // One store: open its reservation (code, countdown). Several stores:
    // open the list. Either way Home sits underneath, not the empty cart.
    @SuppressWarnings("unchecked")
    private void showConfirmation(Map<String, Object> checkout) {
        List<Map<String, Object>> reservations = checkout.get("reservations") instanceof List
                ? (List<Map<String, Object>>) checkout.get("reservations") : new ArrayList<>();
        if (reservations.isEmpty()) {
            Toast.makeText(this, R.string.reservation_failed, Toast.LENGTH_LONG).show();
            return;
        }

        CartManager.getInstance().clear();
        Intent next = reservations.size() == 1
                ? ReservationDetailActivity.intent(this, String.valueOf(reservations.get(0).get("reservationId")))
                : new Intent(this, ReservationsActivity.class);
        TaskStackBuilder.create(this)
                .addNextIntent(new Intent(this, HomeActivity.class))
                .addNextIntent(next)
                .startActivities();
        finish();
    }

    @SuppressWarnings("unchecked")
    private void onReservationFailed(Exception e) {
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
            Log.e(TAG, "createReservation failed", e);
            Toast.makeText(this, R.string.reservation_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

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
