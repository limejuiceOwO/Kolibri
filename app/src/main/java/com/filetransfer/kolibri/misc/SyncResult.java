package com.filetransfer.kolibri.misc;

public class SyncResult<T> {

    private boolean mResultSet = false;
    private T mResult;

    public synchronized T get() {
        while (!mResultSet) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return mResult;
    }

    public synchronized void set(T result) {
        mResultSet = true;
        mResult = result;
        notifyAll();
    }
}
