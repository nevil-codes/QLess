package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SignupActivity extends AppCompatActivity {

    // UI Elements
    private TextInputLayout tilFirstName, tilLastName, tilPhone, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etFirstName, etLastName, etPhone, etEmail, etPassword, etConfirmPassword;
    private LinearLayout passwordStrengthContainer;
    private View strengthBar1, strengthBar2, strengthBar3, strengthBar4;
    private TextView txtPasswordStrength, txtPasswordMatch;
    private MaterialButton btnSignUp, btnGoogleSignUp;
    private FrameLayout loadingOverlay;
    private ImageButton btnBack;
    private TextView txtSignIn;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Password patterns
    private static final Pattern HAS_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWER = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    private static final Pattern SEQUENTIAL = Pattern.compile("(012|123|234|345|456|567|678|789|890|abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{2,}");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupListeners();
    }

    private void initViews() {
        tilFirstName = findViewById(R.id.tilFirstName);
        tilLastName = findViewById(R.id.tilLastName);
        tilPhone = findViewById(R.id.tilPhone);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        passwordStrengthContainer = findViewById(R.id.passwordStrengthContainer);
        strengthBar1 = findViewById(R.id.strengthBar1);
        strengthBar2 = findViewById(R.id.strengthBar2);
        strengthBar3 = findViewById(R.id.strengthBar3);
        strengthBar4 = findViewById(R.id.strengthBar4);
        txtPasswordStrength = findViewById(R.id.txtPasswordStrength);
        txtPasswordMatch = findViewById(R.id.txtPasswordMatch);

        btnSignUp = findViewById(R.id.btnSignUp);
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        btnBack = findViewById(R.id.btnBack);
        txtSignIn = findViewById(R.id.txtSignIn);
    }

    private void setupListeners() {
        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Sign In link
        txtSignIn.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });

        // Email validation
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateEmail();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Password strength indicator
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordStrength(s.toString());
                checkPasswordMatch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Confirm password match
        etConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPasswordMatch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Sign Up button
        btnSignUp.setOnClickListener(v -> attemptSignUp());

        // Google Sign Up - hide since not implemented
        btnGoogleSignUp.setVisibility(View.GONE);
    }

    private boolean validateEmail() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            tilEmail.setError(null);
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            return false;
        } else {
            tilEmail.setError(null);
            return true;
        }
    }

    private int calculatePasswordStrength(String password) {
        if (password.isEmpty()) return 0;

        int strength = 0;

        // Length checks
        if (password.length() >= 8) strength++;
        if (password.length() >= 12) strength++;

        // Character variety
        if (HAS_UPPER.matcher(password).find()) strength++;
        if (HAS_LOWER.matcher(password).find()) strength++;
        if (HAS_DIGIT.matcher(password).find()) strength++;
        if (HAS_SPECIAL.matcher(password).find()) strength++;

        // Penalties for weak patterns
        if (SEQUENTIAL.matcher(password).find()) strength -= 2;
        if (REPEATED.matcher(password).find()) strength -= 2;

        // Common weak passwords
        String[] weakPasswords = {"password", "12345678", "qwerty", "abc123", "letmein", "welcome"};
        for (String weak : weakPasswords) {
            if (password.toLowerCase().contains(weak)) {
                strength -= 3;
                break;
            }
        }

        // Normalize to 0-4 scale
        if (strength < 0) strength = 0;
        if (strength > 6) strength = 4;
        else if (strength >= 5) strength = 4;
        else if (strength >= 4) strength = 3;
        else if (strength >= 3) strength = 2;
        else if (strength >= 1) strength = 1;

        return strength;
    }

    private void updatePasswordStrength(String password) {
        if (password.isEmpty()) {
            passwordStrengthContainer.setVisibility(View.GONE);
            return;
        }

        passwordStrengthContainer.setVisibility(View.VISIBLE);
        int strength = calculatePasswordStrength(password);

        // Reset all bars
        int inactiveRes = R.drawable.strength_bar_inactive;
        strengthBar1.setBackgroundResource(inactiveRes);
        strengthBar2.setBackgroundResource(inactiveRes);
        strengthBar3.setBackgroundResource(inactiveRes);
        strengthBar4.setBackgroundResource(inactiveRes);

        int strengthRes;
        String strengthText;
        int textColor;

        switch (strength) {
            case 1:
                strengthRes = R.drawable.strength_bar_weak;
                strengthText = getString(R.string.password_weak);
                textColor = getColor(R.color.strength_weak);
                strengthBar1.setBackgroundResource(strengthRes);
                break;
            case 2:
                strengthRes = R.drawable.strength_bar_fair;
                strengthText = getString(R.string.password_fair);
                textColor = getColor(R.color.strength_fair);
                strengthBar1.setBackgroundResource(strengthRes);
                strengthBar2.setBackgroundResource(strengthRes);
                break;
            case 3:
                strengthRes = R.drawable.strength_bar_good;
                strengthText = getString(R.string.password_good);
                textColor = getColor(R.color.strength_good);
                strengthBar1.setBackgroundResource(strengthRes);
                strengthBar2.setBackgroundResource(strengthRes);
                strengthBar3.setBackgroundResource(strengthRes);
                break;
            case 4:
                strengthRes = R.drawable.strength_bar_strong;
                strengthText = getString(R.string.password_strong);
                textColor = getColor(R.color.strength_strong);
                strengthBar1.setBackgroundResource(strengthRes);
                strengthBar2.setBackgroundResource(strengthRes);
                strengthBar3.setBackgroundResource(strengthRes);
                strengthBar4.setBackgroundResource(strengthRes);
                break;
            default:
                strengthRes = R.drawable.strength_bar_weak;
                strengthText = getString(R.string.password_weak);
                textColor = getColor(R.color.strength_weak);
                strengthBar1.setBackgroundResource(strengthRes);
        }

        txtPasswordStrength.setText(strengthText);
        txtPasswordStrength.setTextColor(textColor);
    }

    private void checkPasswordMatch() {
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (confirmPassword.isEmpty()) {
            txtPasswordMatch.setVisibility(View.GONE);
            return;
        }

        txtPasswordMatch.setVisibility(View.VISIBLE);

        if (password.equals(confirmPassword)) {
            txtPasswordMatch.setText(getString(R.string.passwords_matched));
            txtPasswordMatch.setTextColor(getColor(R.color.strength_strong));
        } else {
            txtPasswordMatch.setText(getString(R.string.error_password_mismatch));
            txtPasswordMatch.setTextColor(getColor(R.color.strength_weak));
        }
    }

    private boolean validateForm() {
        boolean isValid = true;

        // First Name
        String firstName = etFirstName.getText().toString().trim();
        if (firstName.isEmpty()) {
            tilFirstName.setError(getString(R.string.error_field_required));
            isValid = false;
        } else {
            tilFirstName.setError(null);
        }

        // Last Name
        String lastName = etLastName.getText().toString().trim();
        if (lastName.isEmpty()) {
            tilLastName.setError(getString(R.string.error_field_required));
            isValid = false;
        } else {
            tilLastName.setError(null);
        }

        // Phone
        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            tilPhone.setError(getString(R.string.error_field_required));
            isValid = false;
        } else if (phone.length() < 10) {
            tilPhone.setError(getString(R.string.error_invalid_phone));
            isValid = false;
        } else {
            tilPhone.setError(null);
        }

        // Email
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_field_required));
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        // Password
        String password = etPassword.getText().toString();
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_field_required));
            isValid = false;
        } else if (calculatePasswordStrength(password) < 2) {
            tilPassword.setError(getString(R.string.error_password_weak));
            isValid = false;
        } else {
            tilPassword.setError(null);
        }

        // Confirm Password
        String confirmPassword = etConfirmPassword.getText().toString();
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError(getString(R.string.error_field_required));
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError(getString(R.string.error_password_mismatch));
            isValid = false;
        } else {
            tilConfirmPassword.setError(null);
        }

        return isValid;
    }

    private void attemptSignUp() {
        if (!validateForm()) return;

        showLoading(true);

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToFirestore(user.getUid());
                        }
                    } else {
                        showLoading(false);
                        String errorMessage = task.getException() != null ? 
                                task.getException().getMessage() : getString(R.string.auth_error);
                        Toast.makeText(SignupActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String uid) {
        Map<String, Object> user = new HashMap<>();
        user.put("firstName", etFirstName.getText().toString().trim());
        user.put("lastName", etLastName.getText().toString().trim());
        user.put("phone", etPhone.getText().toString().trim());
        user.put("email", etEmail.getText().toString().trim());
        user.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(SignupActivity.this, getString(R.string.signup_success), Toast.LENGTH_SHORT).show();
                    // Navigate to home
                    Intent intent = new Intent(SignupActivity.this, HomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(SignupActivity.this, getString(R.string.auth_error), Toast.LENGTH_LONG).show();
                });
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSignUp.setEnabled(!show);
    }
}

