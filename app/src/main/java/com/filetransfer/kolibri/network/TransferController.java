package com.filetransfer.kolibri.network;

import static com.filetransfer.kolibri.misc.Util.closeSilently;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.filetransfer.kolibri.db.dao.FileDao;
import com.filetransfer.kolibri.network.handler.ITransferHandler;
import com.filetransfer.kolibri.network.handler.ReceiveHandler;
import com.filetransfer.kolibri.network.handler.SendHandler;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

class TransferController {

    class MultiplexTask implements Runnable {

        @Override
        public void run() {
            try (ServerSocketChannel srv = ServerSocketChannel.open()) {

                synchronized (MultiplexTask.this) {
                    if (!isRunning) {
                        return;
                    }
                    srv.bind(new InetSocketAddress(NetProtocol.FILE_PORT));
                    mServerChannel = srv;
                }

                srv.configureBlocking(false);
                srv.register(mSelector, SelectionKey.OP_ACCEPT);

                while (!Thread.interrupted()) {
                    mSelector.select();

                    synchronized (MultiplexTask.this) {
                        if (!isRunning) {
                            return;
                        }

                        Iterator<ITransferHandler> iter = mNewHandlers.iterator();
                        while (iter.hasNext()) {
                            ITransferHandler handler = iter.next();
                            iter.remove();
                            if (handler.register(mSelector)) {
                                mHandlers.add(handler);
                            }
                        }
                    }

                    Iterator<SelectionKey> iter = mSelector.selectedKeys().iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();

                        if (key.isAcceptable()) {
                            SocketChannel cli = srv.accept();
                            cli.configureBlocking(false);

                            ReceiveHandler handler = new ReceiveHandler(
                                    new FileTaskWrapper(mDao, mCallback, mPairName),
                                    cli,
                                    mBaseDir);

                            synchronized (MultiplexTask.this) {
                                if (!isRunning) {
                                    handler.abort();
                                    return;
                                }

                                InetSocketAddress addr = (InetSocketAddress) cli.getRemoteAddress();
                                if (!mPairAddr.equals(addr.getAddress())) {
                                    Log.w(TAG, "rejected another incoming transfer request with different pair address " + addr.getAddress());
                                    handler.abort();
                                    continue;
                                }

                                if (handler.register(mSelector)) {
                                    mHandlers.add(handler);
                                }
                            }
                        } else if (key.isReadable() || key.isWritable() || key.isConnectable()) {
                            ITransferHandler handler = (ITransferHandler) key.attachment();
                            if (!handler.onSelected(key)) {
                                mHandlers.remove(handler);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (!(e instanceof InterruptedIOException) && !Thread.interrupted()) {
                    Log.e(TAG, "multiplexer may be unexpected stopped");
                    e.printStackTrace();
                }
            }
        }
    }

    private final String TAG = TransferController.class.getSimpleName();
    private final Selector mSelector;
    private final File mBaseDir;
    private final FileDao mDao;
    private final ITransferCallback mCallback;
    private final HashSet<ITransferHandler> mHandlers = new HashSet<>();
    private final LinkedList<ITransferHandler> mNewHandlers = new LinkedList<>();
    private boolean isRunning = false;
    private InetAddress mPairAddr;
    private Thread mMultiplexThread;
    private ServerSocketChannel mServerChannel;
    private String mPairName;

    public TransferController(File baseDir, FileDao dao, ITransferCallback callback) {
        try {
            mSelector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mBaseDir = baseDir;
        mDao = dao;
        mCallback = callback;
    }

    void start(InetAddress pairAddr, String pairName) {
        synchronized (this) {
            if (isRunning) {
                return;
            }

            mDao.abortAllRunningTasks();
            isRunning = true;
            mPairName = pairName;
            mPairAddr = pairAddr;
            mMultiplexThread = new Thread(new MultiplexTask());
            mMultiplexThread.start();
        }
    }

    void stop() {
        synchronized (this) {
            if (!isRunning) {
                return;
            }

            isRunning = false;
            mMultiplexThread.interrupt();

            if (mServerChannel != null) {
                // force close the server channel to release listening port
                closeSilently(mServerChannel);
                mServerChannel = null;
            }

            //  key set is not thread-safe in java 8, will throw ConcurrentModificationException
//            for (SelectionKey key : mSelector.keys()) {
//                Object handler = key.attachment();
//                if (handler instanceof ITransferHandler) {
//                    ((ITransferHandler) handler).abort();
//                }
//            }

            for (ITransferHandler handler : mHandlers) {
                handler.abort();
            }
            for (ITransferHandler handler : mNewHandlers) {
                handler.abort();
            }
            mHandlers.clear();
        }
    }

    void sendFile(ILocalFile file) {

        SendHandler handler = new SendHandler(
                new FileTaskWrapper(mDao, mCallback, mPairName),
                file,
                mPairAddr);

        synchronized (this) {
            if (!isRunning) {
                handler.abort();
                return;
            }

            mNewHandlers.offer(handler);
            mSelector.wakeup();
        }
    }
}
