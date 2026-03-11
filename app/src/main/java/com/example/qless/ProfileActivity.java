package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private TextView txtInitials, txtFullName, txtEmail, txtMemberSince;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        txtInitials = findViewById(R.id.txtInitials);
        txtFullName = findViewById(R.id.txtFullName);
        txtEmail = findViewById(R.id.txtEmail);
        txtMemberSince = findViewById(R.id.txtMemberSince);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Menu items
        findViewById(R.id.menuOrders).setOnClickListener(v ->
                startActivity(new Intent(this, OrdersActivity.class)));
        findViewById(R.id.menuReservations).setOnClickListener(v ->
                startActivity(new Intent(this, ReservationsActivity.class)));
        findViewById(R.id.menuSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.menuHelp).setOnClickListener(v ->
                startActivity(new Intent(this, HelpActivity.class)));

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutDialog());

        loadProfile();
    }

    private void loadProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        txtEmail.setText(user.getEmail());

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String first = doc.getString("firstName");
                        String last = doc.getString("lastName");
                        if (first == null) first = "";
                        if (last == null) last = "";

                        String fullName = (first + " " + last).trim();
                        txtFullName.setText(fullName.isEmpty() ? "User" : fullName);

                        String initials = "";
                        if (!first.isEmpty()) initials += first.charAt(0);
                        if (!last.isEmpty()) initials += last.charAt(0);
                        txtInitials.setText(initials.toUpperCase());

                        Object createdAt = doc.get("createdAt");
                        if (createdAt != null) {
                            long ts = ((Number) createdAt).longValue();
                            String date = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(ts));
                            txtMemberSince.setText(getString(R.string.member_since, date));
                        }
                    }
                });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_confirm))
                .setPositiveButton(getString(R.string.yes_logout), (dialog, which) -> {
                    mAuth.signOut();
                    CartManager.getInstance().clear();
                    Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
}

