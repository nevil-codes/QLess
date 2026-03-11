package com.example.qless;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchNotifications, switchEmailNotifications;
    private TextView txtLanguage;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("qless_prefs", MODE_PRIVATE);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        switchNotifications = findViewById(R.id.switchNotifications);
        switchEmailNotifications = findViewById(R.id.switchEmailNotifications);
        txtLanguage = findViewById(R.id.txtLanguage);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void loadSettings() {
        // Load from SharedPreferences
        switchNotifications.setChecked(prefs.getBoolean("push_notifications", true));
        switchEmailNotifications.setChecked(prefs.getBoolean("email_notifications", true));
        txtLanguage.setText(prefs.getString("language", "English"));
    }

    private void setupListeners() {
        // Edit Profile
        findViewById(R.id.menuEditProfile).setOnClickListener(v -> {
            Toast.makeText(this, "Edit Profile functionality", Toast.LENGTH_SHORT).show();
            // Could open EditProfileActivity
        });

        // Change Password
        findViewById(R.id.menuChangePassword).setOnClickListener(v -> {
            showChangePasswordDialog();
        });

        // Notifications toggle
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("push_notifications", isChecked).apply();
            Toast.makeText(this, isChecked ? "Notifications enabled" : "Notifications disabled", Toast.LENGTH_SHORT).show();
        });

        // Email notifications toggle
        switchEmailNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("email_notifications", isChecked).apply();
        });

        // Language
        findViewById(R.id.menuLanguage).setOnClickListener(v -> {
            showLanguageDialog();
        });

        // Privacy Policy
        findViewById(R.id.menuPrivacy).setOnClickListener(v -> {
            Toast.makeText(this, "Privacy Policy", Toast.LENGTH_SHORT).show();
            // Could open WebView with privacy policy
        });

        // Terms of Service
        findViewById(R.id.menuTerms).setOnClickListener(v -> {
            Toast.makeText(this, "Terms of Service", Toast.LENGTH_SHORT).show();
            // Could open WebView with terms
        });

        // Delete Account
        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> {
            showDeleteAccountDialog();
        });
    }

    private void showChangePasswordDialog() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_password)
                .setMessage("We'll send a password reset link to " + user.getEmail())
                .setPositiveButton("Send", (dialog, which) -> {
                    mAuth.sendPasswordResetEmail(user.getEmail())
                            .addOnSuccessListener(aVoid -> 
                                    Toast.makeText(this, R.string.password_reset_sent, Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> 
                                    Toast.makeText(this, R.string.password_reset_failed, Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showLanguageDialog() {
        String[] languages = {"English", "Deutsch", "Español", "Français"};
        int currentSelection = 0;
        String currentLang = prefs.getString("language", "English");
        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(currentLang)) {
                currentSelection = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(languages, currentSelection, (dialog, which) -> {
                    prefs.edit().putString("language", languages[which]).apply();
                    txtLanguage.setText(languages[which]);
                    dialog.dismiss();
                    Toast.makeText(this, "Language changed to " + languages[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account)
                .setMessage(R.string.delete_account_confirm)
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteAccount();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        // Delete user data from Firestore
        db.collection("users").document(user.getUid())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Delete Firebase Auth account
                    user.delete()
                            .addOnSuccessListener(aVoid2 -> {
                                Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show();
                                // Sign out and go to login
                                mAuth.signOut();
                                finishAffinity();
                            })
                            .addOnFailureListener(e -> 
                                    Toast.makeText(this, "Failed to delete account. Please re-login and try again.", Toast.LENGTH_LONG).show());
                });
    }
}

