package com.personal.travelbuddy;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "location_alarms")
public class LocationAlarm {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String name;
    public double latitude;
    public double longitude;
    public int radius;

    public LocationAlarm(String name, double latitude, double longitude, int radius) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }
}
