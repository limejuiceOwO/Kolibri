package com.filetransfer.kolibri.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "chat_entry")
public class ChatEntry extends BaseEntry {
    @ColumnInfo(name = "content")
    public String content;

    public ChatEntry(String content, String deviceName, boolean fromSelf) {
        this.id = 0;
        this.content = content;
        this.deviceName = deviceName;
        this.fromSelf = fromSelf;
        this.createdAt = System.currentTimeMillis();
    }
}
