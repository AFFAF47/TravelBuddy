package com.personal.travelbuddy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class LocationForegroundService extends Service implements LocationListener {

    private static final String TAG = "LocationAlarmService";
    private static final String CHANNEL_ID = "LocationAlarmChannel";
    private static final int NOTIFICATION_ID = 1;

    private LocationManager locationManager;
    private MediaPlayer mediaPlayer;
    private PowerManager.WakeLock wakeLock;

    private String alarmName;
    private double targetLat;
    private double targetLng;
    private int radius;
    private boolean isAlarmTriggered = false;
    private long currentInterval = -1;

    // Battery Optimization Intervals
    private static final long INTERVAL_VERY_SLOW = 420000; // 7 minutes (> 12km)
    private static final long INTERVAL_SLOW = 180000;       // 3 minute (5km - 12km)
    private static final long INTERVAL_ULTRA_SLOW = 1200000; // 20 minutes (> 20kms)
    private static final long INTERVAL_NORMAL = 30000;     // 30 seconds (< 5km)
    private static final long INTERVAL_FAST = 5000;     // 5 seconds (Final Approach)
    
    private static final float MIN_DISTANCE = 10f; // 10 meters

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createNotificationChannel();
        
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TravelBuddy:LocationTracking");
            wakeLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            alarmName = intent.getStringExtra("alarmName");
            targetLat = intent.getDoubleExtra("lat", 0);
            targetLng = intent.getDoubleExtra("lng", 0);
            radius = intent.getIntExtra("radius", 500);

            startForeground(NOTIFICATION_ID, createNotification("Tracking: " + alarmName));
            requestLocationUpdates(INTERVAL_NORMAL);
        }
        return START_STICKY;
    }

    private void requestLocationUpdates(long minTime) {
        if (minTime == currentInterval) return;
        currentInterval = minTime;
        
        Log.d(TAG, "Setting location update interval to: " + (minTime / 1000) + " seconds");
        
        try {
            locationManager.removeUpdates(this);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, MIN_DISTANCE, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, MIN_DISTANCE, this);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        float[] results = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), targetLat, targetLng, results);
        float distanceInMeters = results[0];

        Log.d(TAG, "Current distance: " + distanceInMeters + "m");

        if (distanceInMeters <= radius && !isAlarmTriggered) {
            triggerAlarm();
        } else {
            // Adaptive tracking logic based on your requirements
            long newInterval;
            if (distanceInMeters > 20000) {
                newInterval = INTERVAL_ULTRA_SLOW; // 20 mins
            } else if (distanceInMeters > 12000) {
                newInterval = INTERVAL_VERY_SLOW; // 7 mins
            } else if (distanceInMeters > 5000) {
                newInterval = INTERVAL_SLOW;       // 3 min
            } else if (distanceInMeters > radius + 1000) {
                newInterval = INTERVAL_NORMAL;     // 30 secs
            } else {
                newInterval = INTERVAL_FAST;       // 5 secs (Final Approach)
            }
            requestLocationUpdates(newInterval);
        }
    }

    private void triggerAlarm() {
        isAlarmTriggered = true;
        Log.d(TAG, "ALARM TRIGGERED!");
        
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification("ARRIVED: " + alarmName));
        }

        playAlarmSound();
    }

    private void playAlarmSound() {
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, alarmUri);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mediaPlayer.setAudioAttributes(audioAttributes);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            Log.e(TAG, "Error playing alarm sound", e);
        }
    }

    private Notification createNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TravelBuddy Active")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Alarm Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {}
}
