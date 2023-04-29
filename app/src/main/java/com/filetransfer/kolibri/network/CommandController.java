package com.filetransfer.kolibri.network;

import static com.filetransfer.kolibri.misc.Util.closeSilently;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;

import com.filetransfer.kolibri.db.dao.ChatDao;
import com.filetransfer.kolibri.db.entity.ChatEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

public class CommandController {

    private static final String TAG = CommandController.class.getSimpleName();

    enum State {
        STOPPED,
        STANDBY,
        CONNECTING,
        HANDSHAKING,
        CONNECTED,
    }

    enum Command {
        CHAT,
        VIBRATE,
    }

    interface Callback {
        void onStateChanged(State state);
        void onChat();
        void onVibrate();
    }

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private class ControlTask implements Runnable {

        private final Socket mSocket;
        private final int mMyEpoch;
        private Thread mCmdThread;
        private final byte[] mBuf = new byte[NetProtocol.MAX_CHAT_LEN];
        private InputStream mInputStream;

        ControlTask(Socket socket, int epoch) {
            mSocket = socket;
            mMyEpoch = epoch;
        }

        @Override
        public void run() {
            try {
                // send handshake information
                OutputStream os = mSocket.getOutputStream();
                os.write(NetProtocol.HANDSHAKE_HEADER);
                byte[] devNameBytes = mDevName.getBytes(StandardCharsets.UTF_8);
                os.write(devNameBytes.length);
                os.write(devNameBytes);

                mInputStream = mSocket.getInputStream();
                int headerLen = NetProtocol.HANDSHAKE_HEADER.length;
                readNBytes(headerLen);
                for (int i = 0; i < headerLen; ++i) {
                    if (mBuf[i] != NetProtocol.HANDSHAKE_HEADER[i]) {
                        throw new IOException("handshake failed, wrong header format");
                    }
                }

                readNBytes(1);
                int pairNameLen = mBuf[0] & 0xFF;
                readNBytes(pairNameLen);
                mPairName = new String(Arrays.copyOf(mBuf, pairNameLen), StandardCharsets.UTF_8);

                synchronized (CommandController.this) {
                    if (mEpoch != mMyEpoch) {
                        return;
                    }
                    assert mState == State.HANDSHAKING;
                    setState(State.CONNECTED);
                }

                mTransferController.start(mSocket.getInetAddress(), mPairName);

                mCmdThread = new Thread(new SendCommandTask(os, mPairName));
                mCmdThread.start();

                while (!Thread.interrupted()) {
                    readNBytes(1);
                    byte cmd = mBuf[0];
                    switch (cmd) {
                        case NetProtocol.CMD_CHAT: {
                            readNBytes(2);
                            int msgLen = ByteBuffer.wrap(mBuf, 0, 2).getShort();

                            readNBytes(msgLen);
                            String msg = new String(Arrays.copyOf(mBuf, msgLen), StandardCharsets.UTF_8);

                            mDao.insert(new ChatEntry(msg, mPairName, false));
                            mHandler.post(mCallback::onChat);
                            break;
                        }
                        case NetProtocol.CMD_VIBRATE:
                            mHandler.post(mCallback::onVibrate);
                            break;
                        default:
                            Log.e(TAG, "unknown command " + cmd);
                    }
                }
            } catch (IOException e) {
                synchronized (CommandController.this) {
                    if (mEpoch != mMyEpoch) {
                        return;
                    }
                    disconnectInternal();
                }
            } finally {
                closeSilently(mSocket);
                if (mCmdThread != null) {
                    mCmdThread.interrupt();
                }
                Log.i(TAG, "controller thread is closed");
            }
        }

        private void readNBytes(int n) throws IOException {
            if (n > mBuf.length) {
                throw new IllegalArgumentException("n=" + n + " is too big for the buffer");
            }

            int nRead = 0;
            while (nRead < n) {
                int newRead;
                if ((newRead = mInputStream.read(mBuf, nRead, n - nRead)) < 0) { // EOF
                    throw new IOException("EOF");
                }
                nRead += newRead;
            }
        }
    }

    private class SendCommandTask implements Runnable {

        private final OutputStream mOutput;
        private final String mPairName;

        SendCommandTask(OutputStream output, String pairName) {
            mOutput = output;
            mPairName = pairName;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    Pair<Command, Object> cmd = mCmdQueue.take();
                    switch (cmd.first) {
                        case CHAT:
                            String msg = (String) cmd.second;
                            int len = msg.length();
                            if (len > NetProtocol.MAX_CHAT_LEN) {
                                Log.e(TAG, "message is too long");
                                break;
                            }

                            mDao.insert(new ChatEntry(msg, mPairName, true));
                            mCallback.onChat();

                            mOutput.write(new byte[] { NetProtocol.CMD_CHAT });

                            byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
                            ByteBuffer buf = ByteBuffer.allocate(2);
                            buf.putShort((short) msgBytes.length);
                            mOutput.write(buf.array());

                            mOutput.write(msgBytes);
                            break;
                        case VIBRATE:
                            mOutput.write(new byte[] { NetProtocol.CMD_VIBRATE });
                            break;
                    }
                }
            } catch (InterruptedException | IOException ignored) {
                // do nothing, ControlTask will handle this
            }
            Log.i(TAG, "command sender thread is closed");
        }
    }

    private class ActiveComponent {

        private Thread mWorkerThread;
        private Socket mClientSocket;

        void start(InetAddress addr) { // must sync
            mWorkerThread = new Thread(new Worker(addr, mEpoch));
            mWorkerThread.start();
        }

        void stop() { // must sync
            if (mWorkerThread != null) {
                mWorkerThread.interrupt();
                mWorkerThread = null;
            }
            if (mClientSocket != null) {
                closeSilently(mClientSocket);
                mClientSocket = null;
            }
        }

        private class Worker implements Runnable {
            private final InetAddress mAddr;
            private final int mMyEpoch;

            Worker(InetAddress addr, int epoch) {
                mAddr = addr;
                mMyEpoch = epoch;
            }

            @Override
            public void run() {
                Log.i(TAG, "connect thread is starting");
                Socket socket = new Socket();
                try {
                    int tries = 0;
                    while (++tries <= 5) {
                        if (mAddr.isReachable(1000)) {
                            break;
                        }
                        Log.w(TAG, "group owner ip unreachable, retry = " + tries);
                        Thread.sleep(500);
                    }
                    if (tries > 5) {
                        throw new IOException("group owner ip unreachable");
                    }

                    InetSocketAddress socketAddress = new InetSocketAddress(mAddr, NetProtocol.PORT);
                    synchronized (CommandController.this) {
                        if (mEpoch != mMyEpoch) {
                            Log.i(TAG, "connect thread is closed (0)");
                            return;
                        }
                        mClientSocket = socket;
                    }

                    Log.i(TAG, "connecting to socket addr " + socketAddress);
                    socket.connect(socketAddress);

                    synchronized (CommandController.this) {
                        if (mEpoch != mMyEpoch) {
                            closeSilently(socket);
                            Log.i(TAG, "connect thread is closed (1)");
                            return;
                        }
                        // only this task can leave CONNECTING state without epoch change
                        assert mState == State.CONNECTING;
                        setState(State.HANDSHAKING);
                    }
                } catch (IOException | InterruptedException e) {
                    synchronized (CommandController.this) {
                        if (mEpoch != mMyEpoch) {
                            return;
                        }
                        disconnectInternal();
                    }
                    Log.i(TAG, "connect thread is closed (2)");
                    Log.i(TAG, e.getMessage());
                    return;
                }

                Log.i(TAG, "connect thread is launching control task");

                // reuse the same thread
                ControlTask controlTask = new ControlTask(socket, mMyEpoch);
                controlTask.run();
            }
        }
    }

    private class PassiveComponent {

        private Thread mWorkerThread;
        private ServerSocket mServerSocket;
        private Socket mClientSocket;

        void start() { // must sync
            mWorkerThread = new Thread(new Worker(mEpoch));
            mWorkerThread.start();
        }

        void stop() { // must sync
            if (mWorkerThread != null) {
                mWorkerThread.interrupt();
                mWorkerThread = null;
            }
            if (mServerSocket != null) {
                closeSilently(mServerSocket);
                mServerSocket = null;
            }
            if (mClientSocket != null) {
                closeSilently(mClientSocket);
                mClientSocket = null;
            }
        }

        private class Worker implements Runnable {
            private final int mMyEpoch;

            Worker(int epoch) {
                mMyEpoch = epoch;
            }

            @Override
            public void run() {
                Log.i(TAG, "listen thread is starting");
                Socket cli;
                try (ServerSocket srv = new ServerSocket()) {

                    synchronized (CommandController.this) {
                        if (mEpoch != mMyEpoch || mState != State.STANDBY) {
                            Log.i(TAG, "listen thread is closed (1)");
                            return;
                        }
                        srv.bind(new InetSocketAddress(NetProtocol.PORT));
                        mServerSocket = srv;
                    }

                    cli = srv.accept(); // accept() may not be interrupted by Thread.interrupt(), needs manually closing the socket

                    synchronized (CommandController.this) {
                        if (mEpoch != mMyEpoch || mState != State.STANDBY) {
                            closeSilently(cli);
                            return;
                        }
                        mClientSocket = cli;
                        setState(State.HANDSHAKING);
                    }
                } catch (IOException e) {
                    synchronized (CommandController.this) {
                        if (mEpoch == mMyEpoch && mState == State.STANDBY) {
                            Log.e(TAG, "server socket may be unexpectedly shut down");
                            e.printStackTrace();
                            setState(State.STOPPED);
                        }
                    }
                    Log.i(TAG, "listen thread is closed (2)");
                    return;
                } finally {
                    synchronized (CommandController.this) {
                        mServerSocket = null;
                    }
                }

                // reuse the same thread
                if (cli != null) {
                    Log.i(TAG, "listen thread is launching control task");
                    ControlTask controlTask = new ControlTask(cli, mMyEpoch);
                    controlTask.run();
                }
            }
        }
    }

    private final LinkedBlockingQueue<Pair<Command, Object>> mCmdQueue = new LinkedBlockingQueue<>();
    private final Callback mCallback;
    private final TransferController mTransferController;
    private final ChatDao mDao;
    private State mState = State.STOPPED;
    private int mEpoch = 0;
    private final PassiveComponent mPassive = new PassiveComponent();
    private final ActiveComponent mActive = new ActiveComponent();
    private volatile String mDevName;
    private volatile String mPairName;

    CommandController(TransferController transferController, Callback callback, ChatDao dao, String devName) {
        mTransferController = transferController;
        mCallback = callback;
        mDao = dao;
        mDevName = devName;
    }

    void startListening() {
        synchronized (this) {
            if (mState != State.STOPPED) {
                return;
            }
            setState(State.STANDBY);
            mPassive.start();
        }
    }

    void connect(InetAddress addr) {
        synchronized (this) {
            if (mState != State.STANDBY) {
                return;
            }

            setState(State.CONNECTING);

            mPassive.stop();
            mActive.start(addr);
        }
    }

    void disconnect() {
        synchronized (this) {
            if (mState == State.STOPPED) {
                return;
            }
            disconnectInternal();
            mPassive.stop();
            mActive.stop();
        }
    }

    void sendChat(String msg) {
        mCmdQueue.offer(Pair.create(Command.CHAT, msg));
    }

    void sendVibrate() {
        mCmdQueue.offer(Pair.create(Command.VIBRATE, null));
    }

    void sendFile(ILocalFile file) {
        mTransferController.sendFile(file);
    }

    void setDevName(String devName) {
        mDevName = devName;
    }

    String getPairName() {
        return mPairName;
    }

    private void disconnectInternal() {
        assert mState != State.STOPPED;
        setState(State.STOPPED);
        mEpoch += 1;
        mCmdQueue.clear();
        mTransferController.stop();
        mPairName = null;
    }

    private void setState(State state) {
        this.mState = state;
        mHandler.post(() -> {
            // run in main thread to avoid critical area overlapping
            mCallback.onStateChanged(state);
        });
    }
}
