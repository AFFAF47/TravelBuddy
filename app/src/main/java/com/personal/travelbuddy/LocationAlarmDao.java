package com.personal.travelbuddy;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocationAlarmDao {
    @Query("SELECT * FROM location_alarms")
    List<LocationAlarm> getAll();

    @Insert
    void insert(LocationAlarm alarm);

    @Delete
    void delete(LocationAlarm alarm);
}
