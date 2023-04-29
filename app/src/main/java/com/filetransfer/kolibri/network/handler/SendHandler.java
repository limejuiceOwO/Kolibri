package com.filetransfer.kolibri.network.handler;

import static com.filetransfer.kolibri.misc.Util.closeSilently;

import android.util.Log;

import com.filetransfer.kolibri.network.FileTaskWrapper;
import com.filetransfer.kolibri.network.ILocalFile;
import com.filetransfer.kolibri.network.NetProtocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class SendHandler implements ITransferHandler {
    private static final String TAG = SendHandler.class.getSimpleName();
    private final ByteBuffer mBuf = ByteBuffer.allocate(NetProtocol.FILE_BLK_SIZE);
    private final ILocalFile mFile;
    private final FileTaskWrapper mTask;
    private final InetAddress mPairAddr;
    private SocketChannel mChannel;
    private int mState = 0;

    public SendHandler(
            FileTaskWrapper task,
            ILocalFile file,
            InetAddress pairAddr) {
//        Log.i(TAG, "created");
        mTask = task;
        mFile = file;
        mPairAddr = pairAddr;
    }

    public boolean register(Selector selector) {
//        Log.i(TAG, "registered");
        mBuf.limit(0);

        try {
            mChannel = SocketChannel.open();
            mChannel.configureBlocking(false);
            mChannel.connect(new InetSocketAddress(mPairAddr, NetProtocol.FILE_PORT));
            mChannel.register(selector, SelectionKey.OP_CONNECT, this);
        } catch (IOException e) {
            e.printStackTrace();
            abort();
            return false;
        }

        // temporarily make path null as there is no need now for the sender to open files
        // also it is difficult to handle permissions
        mTask.start(mFile.name(), null, mFile.size(), true);
        return true;
    }

    public boolean onSelected(SelectionKey key) {
//        Log.i(TAG, "selected");
        try {
            if (key.isConnectable()) {
                if (!mChannel.finishConnect()) {
                    abort();
                    return false;
                }

                key.interestOps(SelectionKey.OP_WRITE);
                return true;
            }

            if (mBuf.hasRemaining()) {
                mChannel.write(mBuf);
                return true;
            }

            mBuf.rewind();
            switch (mState) {
                case 0: { // file name size, file name, file size
                    byte[] fileNameBytes = mFile.name().getBytes(StandardCharsets.UTF_8);
//                    Log.e(TAG, "fileNameSize=" + fileNameBytes.length + " ,fileSize=" + mFileSize);
                    mBuf.limit(4 + 8 + fileNameBytes.length);
                    mBuf.putInt(fileNameBytes.length);
                    mBuf.put(fileNameBytes);
                    // TODO try https://developer.android.com/training/secure-file-sharing/retrieve-info?hl=zh-cn
                    mBuf.putLong(mFile.size());
                    mState = 1;
                    break;
                }
                case 1: { // file content
                    mBuf.limit((int) Math.min(mTask.remaining(), mBuf.capacity()));

                    FileChannel channel = mFile.stream().getChannel();
                    while (mBuf.hasRemaining()) {
                        if (channel.read(mBuf) == -1) {
                            abort();
                            return false;
                        }
                    }

                    if (mTask.proceed(mBuf.limit())) {
                        mState = 2;
                    }
                    break;
                }
                case 2: { // completed
                    terminate(true);
                    return false;
                }
            }
            mBuf.flip();
        } catch (IOException e) {
            e.printStackTrace();
            abort();
            return false;
        }

        return true;
    }

    public void abort() {
        terminate(false);
    }

    private void terminate(boolean completed) {
        Log.i(TAG, "terminated=" + completed);
        if (mChannel != null) {
            closeSilently(mChannel);
        }
        mFile.close();
        mTask.finish(completed);
    }
}