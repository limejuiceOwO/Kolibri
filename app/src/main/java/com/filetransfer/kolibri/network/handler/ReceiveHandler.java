package com.filetransfer.kolibri.network.handler;

import static com.filetransfer.kolibri.misc.Util.closeSilently;

import android.util.Log;

import com.filetransfer.kolibri.network.FileTaskWrapper;
import com.filetransfer.kolibri.network.NetProtocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ReceiveHandler implements ITransferHandler {
    private static final String TAG = ReceiveHandler.class.getSimpleName();
    private final FileTaskWrapper mTask;
    private final File mBaseDir;
    private final SocketChannel mChannel;
    private FileOutputStream mFileStream;
    private File mFile;
    private final ByteBuffer mBuf = ByteBuffer.allocate(NetProtocol.FILE_BLK_SIZE);
    private int mState = 0;

    public ReceiveHandler(
            FileTaskWrapper task,
            SocketChannel channel,
            File baseDir) {
//        Log.i(TAG, "created");
        mBaseDir = baseDir;
        mTask = task;
        mChannel = channel;
    }

    public boolean register(Selector selector) {
//        Log.i(TAG, "registered");
        mBuf.limit(4);
        try {
            mChannel.register(selector, SelectionKey.OP_READ, this);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
            abort();
            return false;
        }
        return true;
    }

    public boolean onSelected(SelectionKey key) {
//        Log.i(TAG, "selected");
        try {
            if (mBuf.hasRemaining()) {
                if (mChannel.read(mBuf) == -1) {
                    Log.w(TAG, "EOF size=" + mTask.size() + " remaining=" + mTask.remaining() + " buf=" + mBuf.remaining());
                    abort();
                    return false;
                }
                return true;
            }

            mBuf.flip();
            switch (mState) {
                case 0: { // filename size
                    int fileNameLength = mBuf.getInt(); // TODO restrict
//                    Log.e(TAG, "fileNameLength=" + fileNameLength);
                    mBuf.limit(fileNameLength);
                    mState = 1;
                    break;
                }
                case 1: {// filename
                    byte[] fileNameBytes = new byte[mBuf.limit()];
                    mBuf.get(fileNameBytes);

                    String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                    mFile = new File(mBaseDir, fileName);
                    while (mFile.exists()) {
                        fileName = "_" + fileName;
                        mFile = new File(mBaseDir, fileName);
                    }

                    mBuf.limit(8);
                    mState = 2;
                    break;
                }
                case 2: { // file size
                    long fileSize = mBuf.getLong();
//                    Log.e(TAG, "fileSize=" + fileSize);
                    mState = 3;
                    mFileStream = new FileOutputStream(mFile);

                    mTask.start(mFile.getName(), mBaseDir.getAbsolutePath(), fileSize, false);
                    mBuf.limit((int) Math.min(NetProtocol.FILE_BLK_SIZE, mTask.remaining()));
                    break;
                }
                case 3: { // file content
                    mFileStream.getChannel().write(mBuf);

                    if (mTask.proceed(mBuf.limit())) {
                        // completed
                        terminate(true);
                        return false;
                    }

                    mBuf.limit((int) Math.min(NetProtocol.FILE_BLK_SIZE, mTask.remaining()));
                    break;
                }
            }
            mBuf.rewind();
        } catch (IOException e) {
            e.printStackTrace();
            abort();
            return false;
        }
        return true;
    }

    private void terminate(boolean completed) {
        Log.i(TAG, "terminated=" + completed);
        closeSilently(mChannel);
        mTask.finish(completed);
        if (mFileStream != null) {
            closeSilently(mFileStream);
        }
    }

    public void abort() {
        terminate(false);
    }
}
