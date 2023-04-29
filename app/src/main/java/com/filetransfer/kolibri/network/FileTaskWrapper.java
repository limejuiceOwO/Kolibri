package com.filetransfer.kolibri.network;

import android.os.Handler;
import android.os.Looper;

import com.filetransfer.kolibri.db.dao.FileDao;
import com.filetransfer.kolibri.db.entity.FileEntry;

public class FileTaskWrapper {
    private static final long DB_UPDATE_INTERVAL = 1000;
    private final FileDao mDao;
    private final ITransferCallback mCallback;
    private final String mPairName;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private long mTaskId = -1;
    private long mSize;
    private long mTransferred = 0;
    private long mLastUpdate = 0;

    public FileTaskWrapper(FileDao dao, ITransferCallback cb, String pairName) {
        mDao = dao;
        mCallback = cb;
        mPairName = pairName;
    }

    public synchronized void start(String name, String path, long size, boolean fromSelf) {
        if (mTaskId == -2) {
            // already finished
            return;
        }
        FileEntry e = new FileEntry(name, path, size, mPairName, fromSelf);
        mSize = size;
        mTaskId = mDao.insert(e);
        mHandler.post(mCallback::onNewTask);
    }

    public synchronized boolean proceed(long transferred) {
        if (mTaskId < 0) {
            // not started or already finished
            return false;
        }
        mTransferred += transferred;

        long time = System.currentTimeMillis();
        if (time - mLastUpdate > DB_UPDATE_INTERVAL) {
            mDao.updateTransferredById(mTaskId, mTransferred);
            mLastUpdate = time;
        }

        return mTransferred >= mSize;
    }

    public synchronized long remaining() {
        if (mTaskId < 0) {
            // not started or already finished
            return 0;
        }
        return Math.max(0, mSize - mTransferred);
    }

    public synchronized long size() {
        return mSize;
    }

    public synchronized void finish(boolean completed) {
        if (mTaskId < 0) {
            return;
        }
        mDao.updateStatusById(mTaskId, completed ? FileEntry.STATUS_COMPLETED : FileEntry.STATUS_FAILED);
        mTaskId = -2;
    }
}
