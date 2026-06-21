package com.personal.travelbuddy;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements LocationAdapter.OnLocationClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText etAlarmName, etLatitude, etLongitude, etRadius;
    private Button btnStartAlarm, btnStopAlarm, btnUseCurrentLocation, btnSaveLocation;
    private TextView tvStatus;
    private RecyclerView rvSavedLocations;
    private LocationAdapter adapter;
    
    private SharedPreferences sharedPreferences;
    private LocationManager locationManager;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("LocationAlarmPrefs", Context.MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        db = AppDatabase.getInstance(this);

        etAlarmName = findViewById(R.id.etAlarmName);
        etLatitude = findViewById(R.id.etLatitude);
        etLongitude = findViewById(R.id.etLongitude);
        etRadius = findViewById(R.id.etRadius);
        btnStartAlarm = findViewById(R.id.btnStartAlarm);
        btnStopAlarm = findViewById(R.id.btnStopAlarm);
        btnUseCurrentLocation = findViewById(R.id.btnUseCurrentLocation);
        btnSaveLocation = findViewById(R.id.btnSaveLocation);
        tvStatus = findViewById(R.id.tvStatus);
        rvSavedLocations = findViewById(R.id.rvSavedLocations);

        setupRecyclerView();
        loadSavedData();
        loadSavedLocations();

        btnStartAlarm.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                startAlarmService();
            }
        });

        btnUseCurrentLocation.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                setCurrentLocationAsDestination();
            }
        });

        btnStopAlarm.setOnClickListener(v -> stopAlarmService());

        btnSaveLocation.setOnClickListener(v -> saveCurrentLocationToDb());
    }

    private void setupRecyclerView() {
        adapter = new LocationAdapter(new ArrayList<>(), this);
        rvSavedLocations.setLayoutManager(new LinearLayoutManager(this));
        rvSavedLocations.setAdapter(adapter);
    }

    private void loadSavedLocations() {
        executorService.execute(() -> {
            List<LocationAlarm> alarms = db.locationAlarmDao().getAll();
            new Handler(Looper.getMainLooper()).post(() -> adapter.setLocations(alarms));
        });
    }

    private void saveCurrentLocationToDb() {
        String name = etAlarmName.getText().toString();
        String latStr = etLatitude.getText().toString();
        String lngStr = etLongitude.getText().toString();
        String radStr = etRadius.getText().toString();

        if (name.isEmpty() || latStr.isEmpty() || lngStr.isEmpty() || radStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            int radius = Integer.parseInt(radStr);

            LocationAlarm alarm = new LocationAlarm(name, lat, lng, radius);
            executorService.execute(() -> {
                db.locationAlarmDao().insert(alarm);
                loadSavedLocations();
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(MainActivity.this, "Location saved!", Toast.LENGTH_SHORT).show()
                );
            });
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid format", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLocationClick(LocationAlarm location) {
        etAlarmName.setText(location.name);
        etLatitude.setText(String.valueOf(location.latitude));
        etLongitude.setText(String.valueOf(location.longitude));
        etRadius.setText(String.valueOf(location.radius));
        Toast.makeText(this, "Location loaded: " + location.name, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleteClick(LocationAlarm location) {
        executorService.execute(() -> {
            db.locationAlarmDao().delete(location);
            loadSavedLocations();
        });
    }

    private void setCurrentLocationAsDestination() {
        try {
            Location location = null;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (location == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (location != null) {
                etLatitude.setText(String.valueOf(location.getLatitude()));
                etLongitude.setText(String.valueOf(location.getLongitude()));
                Toast.makeText(this, "Destination set to current location", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Could not get current location. Ensure GPS is on and has a fix.", Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedData() {
        etAlarmName.setText(sharedPreferences.getString("alarmName", ""));
        etLatitude.setText(sharedPreferences.getString("lat", ""));
        etLongitude.setText(sharedPreferences.getString("lng", ""));
        etRadius.setText(sharedPreferences.getString("radius", "500"));
    }

    private void saveData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("alarmName", etAlarmName.getText().toString());
        editor.putString("lat", etLatitude.getText().toString());
        editor.putString("lng", etLongitude.getText().toString());
        editor.putString("radius", etRadius.getText().toString());
        editor.apply();
    }

    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions are required for the alarm to function.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startAlarmService() {
        String name = etAlarmName.getText().toString();
        String latStr = etLatitude.getText().toString();
        String lngStr = etLongitude.getText().toString();
        String radStr = etRadius.getText().toString();

        if (name.isEmpty() || latStr.isEmpty() || lngStr.isEmpty() || radStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            int radius = Integer.parseInt(radStr);

            saveData();

            Intent serviceIntent = new Intent(this, LocationForegroundService.class);
            serviceIntent.putExtra("alarmName", name);
            serviceIntent.putExtra("lat", lat);
            serviceIntent.putExtra("lng", lng);
            serviceIntent.putExtra("radius", radius);

            ContextCompat.startForegroundService(this, serviceIntent);

            btnStartAlarm.setVisibility(View.GONE);
            btnStopAlarm.setVisibility(View.VISIBLE);
            tvStatus.setText("Status: Tracking " + name);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid coordinate or radius format", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAlarmService() {
        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        stopService(serviceIntent);

        btnStartAlarm.setVisibility(View.VISIBLE);
        btnStopAlarm.setVisibility(View.GONE);
        tvStatus.setText("Status: Inactive");
    }
}
