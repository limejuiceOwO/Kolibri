package com.filetransfer.kolibri.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.filetransfer.kolibri.db.entity.ChatEntry;

@Dao
public interface ChatDao {
//    @Query("SELECT * FROM chat_entry")
//    public ChatEntry[] listAll();
    @Query("SELECT * FROM chat_entry WHERE id > :id")
    ChatEntry[] listIdGreaterThan(long id);
    @Insert
    long insert(ChatEntry obj);
    @Query("DELETE FROM chat_entry")
    void removeAll(); // just for dev
}
