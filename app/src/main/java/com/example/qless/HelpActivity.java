package com.example.qless;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        initViews();
        setupFAQ();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Contact options
        findViewById(R.id.menuEmail).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@qless.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "QLess Support Request");
            startActivity(Intent.createChooser(intent, "Send email"));
        });

        findViewById(R.id.menuPhone).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:+491234567890"));
            startActivity(intent);
        });

        // Social media
        findViewById(R.id.btnFacebook).setOnClickListener(v -> openUrl("https://facebook.com/qless"));
        findViewById(R.id.btnTwitter).setOnClickListener(v -> openUrl("https://twitter.com/qless"));
        findViewById(R.id.btnInstagram).setOnClickListener(v -> openUrl("https://instagram.com/qless"));
    }

    private void setupFAQ() {
        // FAQ 1
        View faq1 = findViewById(R.id.faq1);
        TextView faq1Answer = findViewById(R.id.faq1Answer);
        faq1.setOnClickListener(v -> toggleFAQ(faq1Answer));

        // FAQ 2
        View faq2 = findViewById(R.id.faq2);
        TextView faq2Answer = findViewById(R.id.faq2Answer);
        faq2.setOnClickListener(v -> toggleFAQ(faq2Answer));

        // FAQ 3
        View faq3 = findViewById(R.id.faq3);
        TextView faq3Answer = findViewById(R.id.faq3Answer);
        faq3.setOnClickListener(v -> toggleFAQ(faq3Answer));

        // FAQ 4
        View faq4 = findViewById(R.id.faq4);
        TextView faq4Answer = findViewById(R.id.faq4Answer);
        faq4.setOnClickListener(v -> toggleFAQ(faq4Answer));
    }

    private void toggleFAQ(TextView answerView) {
        if (answerView.getVisibility() == View.VISIBLE) {
            answerView.setVisibility(View.GONE);
        } else {
            answerView.setVisibility(View.VISIBLE);
        }
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }
}

