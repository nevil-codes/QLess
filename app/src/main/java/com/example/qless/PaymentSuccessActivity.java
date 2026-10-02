package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PaymentSuccessActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_STORE_NAME = "store_name";
    public static final String EXTRA_PAY_AT_PICKUP = "pay_at_pickup";
    public static final String EXTRA_PICKUP_CODE = "pickup_code";
    public static final String EXTRA_DEADLINE = "deadline";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        // Get intent extras
        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        String storeName = getIntent().getStringExtra(EXTRA_STORE_NAME);

        // Set order details
        TextView txtOrderId = findViewById(R.id.txtOrderId);
        TextView txtAmount = findViewById(R.id.txtAmount);
        TextView txtStore = findViewById(R.id.txtStore);
        TextView txtDeadline = findViewById(R.id.txtDeadline);

        boolean payAtPickup = getIntent().getBooleanExtra(EXTRA_PAY_AT_PICKUP, false);
        String pickupCode = getIntent().getStringExtra(EXTRA_PICKUP_CODE);

        txtOrderId.setText(orderId != null ? orderId : "N/A");
        txtAmount.setText(String.format(Locale.getDefault(), "€%.2f", amount));
        txtStore.setText(storeName != null ? storeName : "N/A");

        if (payAtPickup) {
            ((TextView) findViewById(R.id.txtSuccessTitle)).setText(R.string.reserved_title);
            ((TextView) findViewById(R.id.txtConfirmationMessage)).setText(R.string.reserved_pickup_message);
            ((TextView) findViewById(R.id.txtAmountLabel)).setText(R.string.pay_at_pickup_amount);
        }
        if (pickupCode != null) {
            ((TextView) findViewById(R.id.txtOrderIdLabel)).setText(R.string.pickup_code);
            txtOrderId.setText(pickupCode);
        }

        long deadline = getIntent().getLongExtra(EXTRA_DEADLINE, 0);
        if (deadline == 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 48);
            deadline = cal.getTimeInMillis();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault());
        txtDeadline.setText(sdf.format(deadline));

        // View Reservations button
        findViewById(R.id.btnViewReservations).setOnClickListener(v -> {
            Intent intent = new Intent(this, ReservationsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        // Back to Home button
        findViewById(R.id.btnBackToHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Go to home instead of back
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

