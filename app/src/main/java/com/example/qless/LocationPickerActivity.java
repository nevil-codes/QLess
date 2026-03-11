package com.example.qless;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationPickerActivity extends AppCompatActivity {

    private EditText etSearch;
    private ImageButton btnClear;
    private TextView txtCurrentLocation, txtSearchTitle;
    private RecyclerView rvSavedLocations, rvSearchResults;
    private ProgressBar progressBar;

    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<SavedLocation> savedLocations = new ArrayList<>();
    private List<SearchResult> searchResults = new ArrayList<>();

    private double currentLat = 0, currentLng = 0;
    private Runnable searchRunnable;

    public static final String EXTRA_LOCATION_NAME = "location_name";
    public static final String EXTRA_LOCATION_ADDRESS = "location_address";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        prefs = getSharedPreferences("qless_locations", MODE_PRIVATE);

        initViews();
        loadSavedLocations();
        getCurrentLocation();
        setupSearch();
    }

    private void initViews() {
        etSearch = findViewById(R.id.etSearch);
        btnClear = findViewById(R.id.btnClear);
        txtCurrentLocation = findViewById(R.id.txtCurrentLocation);
        txtSearchTitle = findViewById(R.id.txtSearchTitle);
        rvSavedLocations = findViewById(R.id.rvSavedLocations);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        progressBar = findViewById(R.id.progressBar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnCurrentLocation).setOnClickListener(v -> {
            if (currentLat != 0 && currentLng != 0) {
                selectLocation("Current Location", txtCurrentLocation.getText().toString(), currentLat, currentLng);
            } else {
                Toast.makeText(this, R.string.no_location, Toast.LENGTH_SHORT).show();
            }
        });

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            btnClear.setVisibility(View.GONE);
        });

        rvSavedLocations.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                
                // Debounce search
                if (searchRunnable != null) {
                    handler.removeCallbacks(searchRunnable);
                }
                
                if (s.length() >= 3) {
                    searchRunnable = () -> searchLocation(s.toString());
                    handler.postDelayed(searchRunnable, 500);
                } else {
                    txtSearchTitle.setVisibility(View.GONE);
                    searchResults.clear();
                    rvSearchResults.setAdapter(new SearchResultsAdapter(searchResults));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            txtCurrentLocation.setText(R.string.no_location);
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLng = location.getLongitude();
                        reverseGeocode(currentLat, currentLng);
                    } else {
                        txtCurrentLocation.setText(R.string.no_location);
                    }
                })
                .addOnFailureListener(e -> txtCurrentLocation.setText(R.string.no_location));
    }

    private void reverseGeocode(double lat, double lng) {
        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String locationText = "";
                    if (address.getSubLocality() != null) {
                        locationText = address.getSubLocality();
                    } else if (address.getLocality() != null) {
                        locationText = address.getLocality();
                    }
                    if (address.getAdminArea() != null) {
                        locationText += ", " + address.getAdminArea();
                    }
                    
                    String finalText = locationText.isEmpty() ? address.getAddressLine(0) : locationText;
                    handler.post(() -> txtCurrentLocation.setText(finalText));
                }
            } catch (Exception e) {
                handler.post(() -> txtCurrentLocation.setText(R.string.no_location));
            }
        });
    }

    private void searchLocation(String query) {
        progressBar.setVisibility(View.VISIBLE);
        
        executor.execute(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + 
                        "&format=json&limit=10&addressdetails=1";
                
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "QLess Android App");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONArray results = new JSONArray(response.toString());
                List<SearchResult> newResults = new ArrayList<>();
                
                for (int i = 0; i < results.length(); i++) {
                    JSONObject obj = results.getJSONObject(i);
                    String displayName = obj.getString("display_name");
                    double lat = obj.getDouble("lat");
                    double lon = obj.getDouble("lon");
                    
                    // Parse address components
                    String name = displayName;
                    String address = "";
                    if (displayName.contains(",")) {
                        String[] parts = displayName.split(",", 2);
                        name = parts[0].trim();
                        address = parts.length > 1 ? parts[1].trim() : "";
                    }
                    
                    newResults.add(new SearchResult(name, address, lat, lon));
                }
                
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    searchResults = newResults;
                    txtSearchTitle.setVisibility(View.VISIBLE);
                    rvSearchResults.setAdapter(new SearchResultsAdapter(searchResults));
                });
                
            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadSavedLocations() {
        savedLocations.clear();
        
        // Load from SharedPreferences
        int count = prefs.getInt("saved_count", 0);
        for (int i = 0; i < count; i++) {
            String name = prefs.getString("loc_" + i + "_name", "");
            String address = prefs.getString("loc_" + i + "_address", "");
            double lat = Double.longBitsToDouble(prefs.getLong("loc_" + i + "_lat", 0));
            double lng = Double.longBitsToDouble(prefs.getLong("loc_" + i + "_lng", 0));
            
            if (!name.isEmpty()) {
                savedLocations.add(new SavedLocation(name, address, lat, lng));
            }
        }
        
        rvSavedLocations.setAdapter(new SavedLocationsAdapter(savedLocations));
    }

    private void saveLocation(String name, String address, double lat, double lng) {
        // Check if already saved
        for (SavedLocation loc : savedLocations) {
            if (Math.abs(loc.lat - lat) < 0.0001 && Math.abs(loc.lng - lng) < 0.0001) {
                return; // Already saved
            }
        }
        
        savedLocations.add(0, new SavedLocation(name, address, lat, lng));
        
        // Limit to 10 saved locations
        while (savedLocations.size() > 10) {
            savedLocations.remove(savedLocations.size() - 1);
        }
        
        // Save to SharedPreferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("saved_count", savedLocations.size());
        for (int i = 0; i < savedLocations.size(); i++) {
            SavedLocation loc = savedLocations.get(i);
            editor.putString("loc_" + i + "_name", loc.name);
            editor.putString("loc_" + i + "_address", loc.address);
            editor.putLong("loc_" + i + "_lat", Double.doubleToLongBits(loc.lat));
            editor.putLong("loc_" + i + "_lng", Double.doubleToLongBits(loc.lng));
        }
        editor.apply();
        
        rvSavedLocations.setAdapter(new SavedLocationsAdapter(savedLocations));
    }

    private void deleteSavedLocation(int position) {
        savedLocations.remove(position);
        
        // Update SharedPreferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("saved_count", savedLocations.size());
        for (int i = 0; i < savedLocations.size(); i++) {
            SavedLocation loc = savedLocations.get(i);
            editor.putString("loc_" + i + "_name", loc.name);
            editor.putString("loc_" + i + "_address", loc.address);
            editor.putLong("loc_" + i + "_lat", Double.doubleToLongBits(loc.lat));
            editor.putLong("loc_" + i + "_lng", Double.doubleToLongBits(loc.lng));
        }
        editor.apply();
        
        rvSavedLocations.setAdapter(new SavedLocationsAdapter(savedLocations));
    }

    private void selectLocation(String name, String address, double lat, double lng) {
        // Save to recent locations
        saveLocation(name, address, lat, lng);
        
        // Return result
        Intent result = new Intent();
        result.putExtra(EXTRA_LOCATION_NAME, name);
        result.putExtra(EXTRA_LOCATION_ADDRESS, address);
        result.putExtra(EXTRA_LATITUDE, lat);
        result.putExtra(EXTRA_LONGITUDE, lng);
        setResult(RESULT_OK, result);
        finish();
    }

    // Data classes
    static class SavedLocation {
        String name, address;
        double lat, lng;
        
        SavedLocation(String name, String address, double lat, double lng) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
        }
    }

    static class SearchResult {
        String name, address;
        double lat, lng;
        
        SearchResult(String name, String address, double lat, double lng) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
        }
    }

    // Adapters
    private class SavedLocationsAdapter extends RecyclerView.Adapter<SavedLocationsAdapter.VH> {
        private final List<SavedLocation> locations;

        SavedLocationsAdapter(List<SavedLocation> locations) {
            this.locations = locations;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SavedLocation loc = locations.get(position);
            holder.txtName.setText(loc.name);
            holder.txtAddress.setText(loc.address);
            holder.btnDelete.setVisibility(View.VISIBLE);
            
            holder.itemView.setOnClickListener(v -> selectLocation(loc.name, loc.address, loc.lat, loc.lng));
            holder.btnDelete.setOnClickListener(v -> deleteSavedLocation(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return locations.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtName, txtAddress;
            ImageButton btnDelete;

            VH(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtLocationName);
                txtAddress = v.findViewById(R.id.txtLocationAddress);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }

    private class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.VH> {
        private final List<SearchResult> results;

        SearchResultsAdapter(List<SearchResult> results) {
            this.results = results;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SearchResult result = results.get(position);
            holder.txtName.setText(result.name);
            holder.txtAddress.setText(result.address);
            
            holder.itemView.setOnClickListener(v -> selectLocation(result.name, result.address, result.lat, result.lng));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtName, txtAddress;

            VH(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtLocationName);
                txtAddress = v.findViewById(R.id.txtLocationAddress);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

