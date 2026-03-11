package com.example.qless;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_CODE = 1001;

    private TextView txtGreeting, txtLocation;
    private RecyclerView rvDeals, rvStores;
    private LinearLayout categoriesContainer;

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private double userLat = 0, userLng = 0;

    private final String[][] categories = {
            {"🥦", "Groceries"},
            {"📱", "Electronics"},
            {"👕", "Clothing"},
            {"🏠", "Home"},
            {"💊", "Health"},
            {"⚽", "Sports"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        loadUserGreeting();
        setupCategories();
        requestLocationAndLoad();
    }

    private void initViews() {
        txtGreeting = findViewById(R.id.txtGreeting);
        txtLocation = findViewById(R.id.txtLocation);
        rvDeals = findViewById(R.id.rvDeals);
        rvStores = findViewById(R.id.rvStores);
        categoriesContainer = findViewById(R.id.categoriesContainer);

        rvDeals.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvStores.setLayoutManager(new LinearLayoutManager(this));

        txtLocation.setText(getString(R.string.detecting_location));

        // Search bar → SearchActivity
        findViewById(R.id.searchBar).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));

        // Bottom Navigation
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            }
            if (id == R.id.nav_cart) {
                startActivity(new Intent(this, CartActivity.class));
                return true;
            }
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    private void loadUserGreeting() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String firstName = doc.getString("firstName");
                            if (firstName != null && !firstName.isEmpty()) {
                                txtGreeting.setText(getString(R.string.hi_user, firstName));
                            } else {
                                txtGreeting.setText(getString(R.string.hi_user, "there"));
                            }
                        } else {
                            txtGreeting.setText(getString(R.string.hi_user, "there"));
                        }
                    })
                    .addOnFailureListener(e ->
                            txtGreeting.setText(getString(R.string.hi_user, "there")));
        } else {
            txtGreeting.setText(getString(R.string.hi_user, "there"));
        }
    }

    private void setupCategories() {
        categoriesContainer.removeAllViews();
        for (String[] cat : categories) {
            View chip = LayoutInflater.from(this).inflate(R.layout.item_category, categoriesContainer, false);
            TextView emoji = chip.findViewById(R.id.txtEmoji);
            TextView label = chip.findViewById(R.id.txtLabel);
            emoji.setText(cat[0]);
            label.setText(cat[1]);
            categoriesContainer.addView(chip);
        }
    }

    private void requestLocationAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else {
            getCurrentLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                txtLocation.setText(getString(R.string.no_location));
                loadDeals();
                loadStores();
            }
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        userLat = location.getLatitude();
                        userLng = location.getLongitude();
                        reverseGeocode(userLat, userLng);
                    } else {
                        txtLocation.setText(getString(R.string.no_location));
                    }
                    loadDeals();
                    loadStores();
                })
                .addOnFailureListener(e -> {
                    txtLocation.setText(getString(R.string.no_location));
                    loadDeals();
                    loadStores();
                });
    }

    private void reverseGeocode(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String locality = address.getSubLocality();
                if (locality == null) locality = address.getLocality();
                String area = address.getAdminArea();
                if (area == null) area = address.getCountryName();

                String locationText = (locality != null ? locality : "") +
                        (area != null ? ", " + area : "");
                if (locationText.startsWith(",")) locationText = locationText.substring(2);
                txtLocation.setText(locationText.isEmpty() ? address.getAddressLine(0) : locationText);
            }
        } catch (IOException e) {
            txtLocation.setText(getString(R.string.no_location));
        }
    }

    // ---- Load products from Firestore ----
    private void loadDeals() {
        db.collection("products").get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Map<String, Object>> dealsList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> product = doc.getData();
                        product.put("id", doc.getId());
                        dealsList.add(product);
                    }
                    rvDeals.setAdapter(new DealsAdapter(dealsList));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load deals", Toast.LENGTH_SHORT).show());
    }

    // ---- Load stores from Firestore ----
    private void loadStores() {
        db.collection("stores").get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Map<String, Object>> storesList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> store = doc.getData();
                        store.put("id", doc.getId());

                        // Calculate distance if user location available
                        if (userLat != 0 && userLng != 0) {
                            Map<String, Object> address = (Map<String, Object>) store.get("address");
                            if (address != null) {
                                Object latObj = address.get("latitude");
                                Object lngObj = address.get("longitude");
                                if (latObj != null && lngObj != null) {
                                    double storeLat = ((Number) latObj).doubleValue();
                                    double storeLng = ((Number) lngObj).doubleValue();
                                    double distance = haversine(userLat, userLng, storeLat, storeLng);
                                    store.put("distance", distance);
                                }
                            }
                        }
                        storesList.add(store);
                    }

                    // Sort by distance
                    storesList.sort((a, b) -> {
                        double da = a.containsKey("distance") ? (double) a.get("distance") : 999;
                        double db2 = b.containsKey("distance") ? (double) b.get("distance") : 999;
                        return Double.compare(da, db2);
                    });

                    rvStores.setAdapter(new StoresAdapter(storesList));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load stores", Toast.LENGTH_SHORT).show());
    }

    // Haversine formula for distance in km
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // =================== DEALS ADAPTER ===================
    private class DealsAdapter extends RecyclerView.Adapter<DealsAdapter.VH> {
        private final List<Map<String, Object>> items;

        DealsAdapter(List<Map<String, Object>> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deal, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> product = items.get(position);
            holder.txtProductName.setText((String) product.get("name"));

            // Load product image
            String imageUrl = (String) product.get("imageUrl");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.placeholder_product)
                        .error(R.drawable.placeholder_product)
                        .centerCrop()
                        .into(holder.imgProduct);
            } else {
                holder.imgProduct.setImageResource(R.drawable.placeholder_product);
            }

            List<Map<String, Object>> prices = (List<Map<String, Object>>) product.get("prices");
            if (prices != null && !prices.isEmpty()) {
                // Find cheapest price
                Map<String, Object> cheapest = prices.get(0);
                for (Map<String, Object> p : prices) {
                    if (((Number) p.get("price")).doubleValue() < ((Number) cheapest.get("price")).doubleValue()) {
                        cheapest = p;
                    }
                }

                double price = ((Number) cheapest.get("price")).doubleValue();
                holder.txtPrice.setText(String.format(Locale.getDefault(), "€%.2f", price));
                holder.txtStoreName.setText("Cheapest at " + cheapest.get("storeName"));

                Object origObj = cheapest.get("originalPrice");
                if (origObj != null) {
                    double original = ((Number) origObj).doubleValue();
                    holder.txtOriginalPrice.setText(String.format(Locale.getDefault(), "€%.2f", original));
                    holder.txtOriginalPrice.setPaintFlags(holder.txtOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.txtOriginalPrice.setVisibility(View.VISIBLE);

                    int discount = (int) (((original - price) / original) * 100);
                    holder.txtDiscount.setText(discount + "% OFF");
                    holder.txtDiscount.setVisibility(View.VISIBLE);
                } else {
                    holder.txtOriginalPrice.setVisibility(View.GONE);
                    holder.txtDiscount.setVisibility(View.GONE);
                }

                holder.txtStoreCount.setText("Available in " + prices.size() + " store" + (prices.size() > 1 ? "s" : ""));
            }

            // Click to view product detail with all store prices
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, ProductDetailActivity.class);
                intent.putExtra("productId", (String) product.get("id"));
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtProductName, txtStoreName, txtPrice, txtOriginalPrice, txtDiscount, txtStoreCount;
            ImageView imgProduct;

            VH(View v) {
                super(v);
                txtProductName = v.findViewById(R.id.txtProductName);
                txtStoreName = v.findViewById(R.id.txtStoreName);
                txtPrice = v.findViewById(R.id.txtPrice);
                txtOriginalPrice = v.findViewById(R.id.txtOriginalPrice);
                txtDiscount = v.findViewById(R.id.txtDiscount);
                txtStoreCount = v.findViewById(R.id.txtStoreCount);
                imgProduct = v.findViewById(R.id.imgProduct);
            }
        }
    }

    // =================== STORES ADAPTER ===================
    private class StoresAdapter extends RecyclerView.Adapter<StoresAdapter.VH> {
        private final List<Map<String, Object>> items;

        StoresAdapter(List<Map<String, Object>> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_store, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> store = items.get(position);
            holder.txtStoreName.setText((String) store.get("name"));

            Object ratingObj = store.get("rating");
            if (ratingObj != null) {
                holder.txtRating.setText(String.format(Locale.getDefault(), "%.1f", ((Number) ratingObj).doubleValue()));
            }

            Object distanceObj = store.get("distance");
            if (distanceObj != null) {
                holder.txtDistance.setText(String.format(Locale.getDefault(), "%.1f km", (double) distanceObj));
            } else {
                holder.txtDistance.setText("—");
            }

            // Simple open/closed logic based on current hour
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            boolean isOpen = hour >= 8 && hour < 22;
            holder.txtStatus.setText(isOpen ? getString(R.string.open_now) : getString(R.string.closed));
            holder.txtStatus.setBackgroundResource(isOpen ? R.drawable.bg_price_tag : R.drawable.bg_discount_badge);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtStoreName, txtRating, txtDistance, txtStatus;

            VH(View v) {
                super(v);
                txtStoreName = v.findViewById(R.id.txtStoreName);
                txtRating = v.findViewById(R.id.txtRating);
                txtDistance = v.findViewById(R.id.txtDistance);
                txtStatus = v.findViewById(R.id.txtStatus);
            }
        }
    }
}

