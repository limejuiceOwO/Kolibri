package com.filetransfer.kolibri.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.filetransfer.kolibri.db.entity.FileEntry;

import java.util.Collection;

@Dao
public interface FileDao {
//    @Query("SELECT * FROM file_entry WHERE id = :id")
//    FileEntry findById(long id);
    @Query("SELECT * FROM file_entry WHERE id IN (:ids)")
    FileEntry[] listByIds(Collection<Long> ids);
    @Query("SELECT * FROM file_entry WHERE id > :id")
    FileEntry[] listIdGreaterThan(long id);
    @Query("UPDATE file_entry SET status = :status WHERE id = :id")
    void updateStatusById(long id, int status);
    @Query("UPDATE file_entry SET transferred = :transferred WHERE id = :id")
    void updateTransferredById(long id, long transferred);
    @Query("UPDATE file_entry SET status = " + FileEntry.STATUS_FAILED + " WHERE status = " + FileEntry.STATUS_RUNNING)
    void abortAllRunningTasks();
    @Insert
    long insert(FileEntry obj);
    @Query("DELETE FROM file_entry")
    void removeAll(); // just for dev
}
