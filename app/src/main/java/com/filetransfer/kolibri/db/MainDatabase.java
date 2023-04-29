package com.filetransfer.kolibri.db;

import android.app.Application;
import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.filetransfer.kolibri.db.dao.ChatDao;
import com.filetransfer.kolibri.db.dao.FileDao;
import com.filetransfer.kolibri.db.entity.*;

@Database(entities = {FileEntry.class, ChatEntry.class}, version = 1, exportSchema = false)
public abstract class MainDatabase extends RoomDatabase {
    public static final String NAME = "main-database";
    public abstract ChatDao chatDao();
    public abstract FileDao fileDao();

    private static volatile MainDatabase INSTANCE = null;

    public static MainDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (MainDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx, MainDatabase.class, MainDatabase.NAME)
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
