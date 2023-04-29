package com.filetransfer.kolibri.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "file_entry")
public class FileEntry extends BaseEntry {
    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_FAILED = 2;

    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "path")
    public String path;
    @ColumnInfo(name = "size")
    public long size;
    @ColumnInfo(name = "transferred")
    public long transferred;
    @ColumnInfo(name = "status")
    public int status;

    public FileEntry(String name, String path, long size, String deviceName, boolean fromSelf) {
        this.id = 0;
        this.name = name;
        this.path = path;
        this.size = size;
        this.transferred = 0;
        this.deviceName = deviceName;
        this.fromSelf = fromSelf;
        this.status = STATUS_RUNNING;
        this.createdAt = System.currentTimeMillis();
    }
}
