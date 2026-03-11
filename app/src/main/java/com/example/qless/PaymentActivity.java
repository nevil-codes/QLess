package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.model.Address;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.PaymentMethodCreateParams;
import com.stripe.android.view.CardInputWidget;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    private static final String TAG = "PaymentActivity";
    
    // TODO: Replace with your Stripe publishable key
    private static final String STRIPE_PUBLISHABLE_KEY = "pk_test_YOUR_PUBLISHABLE_KEY";

    private TextView txtAmount, txtOrderId, txtLoadingMessage;
    private CardInputWidget cardInputWidget;
    private TextInputEditText etCardholderName;
    private CheckBox cbSaveCard;
    private RadioButton radioCard, radioGooglePay;
    private CardView cardInputSection;
    private MaterialButton btnPay;
    private FrameLayout loadingOverlay;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseFunctions functions;

    private double amount;
    private String reservationId;
    private String storeName;

    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_RESERVATION_ID = "reservation_id";
    public static final String EXTRA_STORE_NAME = "store_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        // Initialize Stripe
        PaymentConfiguration.init(getApplicationContext(), STRIPE_PUBLISHABLE_KEY);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();

        // Get intent extras
        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);
        storeName = getIntent().getStringExtra(EXTRA_STORE_NAME);

        initViews();
        setupPaymentMethods();
    }

    private void initViews() {
        txtAmount = findViewById(R.id.txtAmount);
        txtOrderId = findViewById(R.id.txtOrderId);
        txtLoadingMessage = findViewById(R.id.txtLoadingMessage);
        cardInputWidget = findViewById(R.id.cardInputWidget);
        etCardholderName = findViewById(R.id.etCardholderName);
        cbSaveCard = findViewById(R.id.cbSaveCard);
        radioCard = findViewById(R.id.radioCard);
        radioGooglePay = findViewById(R.id.radioGooglePay);
        cardInputSection = findViewById(R.id.cardInputSection);
        btnPay = findViewById(R.id.btnPay);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        // Set amount and order info
        txtAmount.setText(String.format(Locale.getDefault(), "€%.2f", amount));
        txtOrderId.setText("Order #" + (reservationId != null ? reservationId : ""));

        // Update pay button text
        btnPay.setText(String.format(Locale.getDefault(), "Pay €%.2f", amount));

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // Pay button
        btnPay.setOnClickListener(v -> processPayment());
    }

    private void setupPaymentMethods() {
        // Card payment option
        findViewById(R.id.cardPaymentOption).setOnClickListener(v -> {
            radioCard.setChecked(true);
            radioGooglePay.setChecked(false);
            cardInputSection.setVisibility(View.VISIBLE);
        });

        // Google Pay option
        findViewById(R.id.googlePayOption).setOnClickListener(v -> {
            radioGooglePay.setChecked(true);
            radioCard.setChecked(false);
            cardInputSection.setVisibility(View.GONE);
            // In a real app, you would initialize Google Pay here
            Toast.makeText(this, "Google Pay coming soon!", Toast.LENGTH_SHORT).show();
            // Revert to card for now
            radioCard.setChecked(true);
            radioGooglePay.setChecked(false);
            cardInputSection.setVisibility(View.VISIBLE);
        });

        radioCard.setOnClickListener(v -> {
            radioGooglePay.setChecked(false);
            cardInputSection.setVisibility(View.VISIBLE);
        });

        radioGooglePay.setOnClickListener(v -> {
            radioCard.setChecked(false);
            cardInputSection.setVisibility(View.GONE);
        });
    }

    private void processPayment() {
        if (radioGooglePay.isChecked()) {
            // Handle Google Pay
            Toast.makeText(this, "Google Pay not available yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate card input
        PaymentMethodCreateParams params = cardInputWidget.getPaymentMethodCreateParams();
        if (params == null) {
            Toast.makeText(this, R.string.invalid_card, Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate cardholder name
        String cardholderName = etCardholderName.getText() != null ? 
                etCardholderName.getText().toString().trim() : "";
        if (cardholderName.isEmpty()) {
            etCardholderName.setError("Required");
            return;
        }

        showLoading(true, getString(R.string.processing_payment));

        // Create payment method params with billing details
        PaymentMethodCreateParams.BillingDetails billingDetails = 
                new PaymentMethodCreateParams.BillingDetails.Builder()
                        .setName(cardholderName)
                        .setEmail(mAuth.getCurrentUser() != null ? 
                                mAuth.getCurrentUser().getEmail() : null)
                        .build();

        // In a production app, you would:
        // 1. Call your backend to create a PaymentIntent
        // 2. Get the client secret
        // 3. Confirm the payment with Stripe SDK
        
        // For demo purposes, we'll simulate a successful payment
        simulatePayment();
    }

    private void simulatePayment() {
        // Simulate network delay
        btnPay.postDelayed(() -> {
            // Simulate successful payment
            onPaymentSuccess("pi_simulated_" + System.currentTimeMillis());
        }, 2000);
    }

    private void onPaymentSuccess(String paymentIntentId) {
        showLoading(true, "Confirming reservation...");

        // Update reservation with payment info
        if (reservationId != null && mAuth.getCurrentUser() != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("paymentStatus", "paid");
            updates.put("paymentIntentId", paymentIntentId);
            updates.put("paidAt", System.currentTimeMillis());
            updates.put("paymentMethod", "card");

            db.collection("reservations").document(reservationId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        showLoading(false, null);
                        
                        // Show success and return
                        Toast.makeText(this, R.string.payment_successful, Toast.LENGTH_LONG).show();
                        
                        Intent result = new Intent();
                        result.putExtra("payment_success", true);
                        result.putExtra("payment_intent_id", paymentIntentId);
                        setResult(RESULT_OK, result);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false, null);
                        Log.e(TAG, "Failed to update reservation", e);
                        // Payment was successful but failed to update - still return success
                        Intent result = new Intent();
                        result.putExtra("payment_success", true);
                        result.putExtra("payment_intent_id", paymentIntentId);
                        setResult(RESULT_OK, result);
                        finish();
                    });
        } else {
            showLoading(false, null);
            Intent result = new Intent();
            result.putExtra("payment_success", true);
            result.putExtra("payment_intent_id", paymentIntentId);
            setResult(RESULT_OK, result);
            finish();
        }
    }

    private void onPaymentFailure(String errorMessage) {
        showLoading(false, null);
        Toast.makeText(this, getString(R.string.payment_failed) + ": " + errorMessage, 
                Toast.LENGTH_LONG).show();
    }

    private void showLoading(boolean show, @Nullable String message) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (message != null) {
            txtLoadingMessage.setText(message);
        }
        btnPay.setEnabled(!show);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}

