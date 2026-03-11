package com.example.qless;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class CartActivity extends AppCompatActivity {

    private RecyclerView rvCart;
    private LinearLayout emptyState, bottomSection;
    private TextView txtTotal;
    private CartAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        rvCart = findViewById(R.id.rvCart);
        emptyState = findViewById(R.id.emptyState);
        bottomSection = findViewById(R.id.bottomSection);
        txtTotal = findViewById(R.id.txtTotal);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        MaterialButton btnBrowse = findViewById(R.id.btnBrowse);
        btnBrowse.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        findViewById(R.id.btnCheckout).setOnClickListener(v -> {
            if (!CartManager.getInstance().getItems().isEmpty()) {
                startActivity(new Intent(this, CheckoutActivity.class));
            }
        });

        rvCart.setLayoutManager(new LinearLayoutManager(this));
        refreshCart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    private void refreshCart() {
        List<CartManager.CartItem> items = CartManager.getInstance().getItems();
        boolean empty = items.isEmpty();

        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvCart.setVisibility(empty ? View.GONE : View.VISIBLE);
        bottomSection.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            adapter = new CartAdapter(items);
            rvCart.setAdapter(adapter);
            updateTotal();
        }
    }

    private void updateTotal() {
        txtTotal.setText(String.format(Locale.getDefault(), "€%.2f", CartManager.getInstance().getTotal()));
    }

    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
        private final List<CartManager.CartItem> items;
        CartAdapter(List<CartManager.CartItem> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CartManager.CartItem item = items.get(pos);
            h.txtName.setText(item.name);
            h.txtStore.setText(item.storeName);
            h.txtPrice.setText(String.format(Locale.getDefault(), "€%.2f", item.price * item.quantity));
            h.txtQty.setText(String.valueOf(item.quantity));

            h.btnPlus.setOnClickListener(v -> {
                CartManager.getInstance().updateQuantity(h.getAdapterPosition(), item.quantity + 1);
                notifyItemChanged(h.getAdapterPosition());
                updateTotal();
            });

            h.btnMinus.setOnClickListener(v -> {
                if (item.quantity <= 1) {
                    CartManager.getInstance().removeItem(h.getAdapterPosition());
                    notifyItemRemoved(h.getAdapterPosition());
                    Toast.makeText(CartActivity.this, getString(R.string.item_removed), Toast.LENGTH_SHORT).show();
                    if (items.isEmpty()) refreshCart();
                } else {
                    CartManager.getInstance().updateQuantity(h.getAdapterPosition(), item.quantity - 1);
                    notifyItemChanged(h.getAdapterPosition());
                }
                updateTotal();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView txtName, txtStore, txtPrice, txtQty;
            ImageButton btnPlus, btnMinus;
            VH(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtProductName);
                txtStore = v.findViewById(R.id.txtStoreName);
                txtPrice = v.findViewById(R.id.txtPrice);
                txtQty = v.findViewById(R.id.txtQuantity);
                btnPlus = v.findViewById(R.id.btnPlus);
                btnMinus = v.findViewById(R.id.btnMinus);
            }
        }
    }
}

