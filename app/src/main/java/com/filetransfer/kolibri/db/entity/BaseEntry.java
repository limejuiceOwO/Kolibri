package com.filetransfer.kolibri.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

public abstract class BaseEntry {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;
    @ColumnInfo(name = "device_name")
    public String deviceName;
    @ColumnInfo(name = "created_at")
    public long createdAt;
    @ColumnInfo(name = "from_self")
    public boolean fromSelf;
}
