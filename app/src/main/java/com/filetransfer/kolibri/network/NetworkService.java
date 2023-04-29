package com.filetransfer.kolibri.network;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.filetransfer.kolibri.db.MainDatabase;
import com.filetransfer.kolibri.misc.Util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class NetworkService extends Service {

    public static final String PREF_DEVICE_NAME = "device_name";
    public static final String ACTIVITY_CALLBACK = "callback";
    public static final int EVENT_STATE_CHANGED = 0;
    public static final int EVENT_NEW_ENTRY = 1;

    public enum State {
        NOT_CONNECTED,
        P2P_GROUP_CREATING,
        P2P_GROUP_CREATED,
        P2P_CONNECTING,
        SRV_CONNECTING,
        CONNECTED,
    }

    public class LocalBinder extends Binder {
        public NetworkService getService() {
            return NetworkService.this;
        }
    }

    private static final String TAG = NetworkService.class.getSimpleName();
    private CommandController mCmdController;
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private volatile State mState = State.NOT_CONNECTED;
    private volatile boolean isRunning = true;
    private Messenger mActivityCb;
    private Vibrator mVibrator;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = (sp, key) -> {
        if (PREF_DEVICE_NAME.equals(key)) {
            String name = getDeviceName(sp);
            setDeviceNameHacked(name);
            mCmdController.setDevName(name);
        }
    };

    private final BroadcastReceiver mP2pReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(intent.getAction())) {
                WifiP2pInfo conn = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                if (conn.groupFormed) {
                    Log.i(TAG, "received P2P connected event, isGroupOwner=" + conn.isGroupOwner);
                    if (conn.isGroupOwner) {
                        if (mState == State.NOT_CONNECTED || mState == State.P2P_GROUP_CREATING || mState == State.P2P_CONNECTING) {
                            setState(State.P2P_GROUP_CREATED);
                        }
                    } else {
                        mCmdController.connect(conn.groupOwnerAddress);
                    }
                } else {
                    Log.i(TAG, "received P2P disconnected event");
                    handleDisconnect();
                }
            }
        }
    };

    private final CommandController.Callback mCommandCb = new CommandController.Callback() {
        @Override
        public void onStateChanged(CommandController.State state) {
            switch (state) {
                case STOPPED:
                    // delay p2p reset to fully close the socket
                    mHandler.postDelayed(NetworkService.this::resetNetworkComponents, 500);
                    break;
                case STANDBY:
                    handleDisconnect();
                    break;
                case CONNECTING:
                    setState(State.SRV_CONNECTING);
                    break;
                case CONNECTED:
                    setState(State.CONNECTED);
                    break;
            }
        }

        @Override
        public void onChat() {
            Message m = Message.obtain(null, NetworkService.EVENT_NEW_ENTRY );
            activityCallback(m);
        }

        @Override
        public void onVibrate() {
            mVibrator.vibrate(500);
        }
    };

    private final ITransferCallback mTransferCb = new ITransferCallback() {
        @Override
        public void onNewTask() {
            Message m = Message.obtain(null, NetworkService.EVENT_NEW_ENTRY);
            activityCallback(m);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mVibrator = getSystemService(Vibrator.class);
        MainDatabase db = MainDatabase.getInstance(getApplicationContext());
        db.fileDao().abortAllRunningTasks();

        mManager = getSystemService(WifiP2pManager.class);
        mChannel = mManager.initialize(this, getMainLooper(), () -> {
            if (isRunning) {
                throw new RuntimeException("channel lost");
            }
        });

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        TransferController tc = new TransferController(getExternalFilesDir("received"), db.fileDao(), mTransferCb);
        mCmdController = new CommandController(tc, mCommandCb, db.chatDao(), getDeviceName(sp));

        setDeviceNameHacked(sp.getString(PREF_DEVICE_NAME, Build.MODEL));
        sp.registerOnSharedPreferenceChangeListener(mPrefListener);

        resetNetworkComponents();

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        registerReceiver(mP2pReceiver, filter);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (mActivityCb != null) {
            throw new RuntimeException("duplicated service bind");
        }
        Log.i(TAG, "activity bound");
        handleBind(intent);
        return new LocalBinder();
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.i(TAG, "activity rebound");
        handleBind(intent);
    }

    private void handleBind(Intent intent) {
        mActivityCb = intent.getParcelableExtra(ACTIVITY_CALLBACK);
        sendStateChangedEvent();
        if (mState == State.NOT_CONNECTED) {
            mManager.discoverPeers(mChannel, null);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "activity unbound");
        if (mState == State.NOT_CONNECTED) {
            mManager.stopPeerDiscovery(mChannel, null);
        }
        mActivityCb = null;
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
//        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "service destroyed");
        isRunning = false;
        unregisterReceiver(mP2pReceiver);
        mCmdController.disconnect();
        mChannel.close();
    }

    public void connect(String pairMac) {
        if (mState != State.NOT_CONNECTED) {
            return;
        }
        setState(State.P2P_CONNECTING);

        WifiP2pConfig cfg = new WifiP2pConfig();
        cfg.deviceAddress = pairMac;
        mManager.connect(mChannel, cfg, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "connect() to " + pairMac +" successfully called");
            }

            @Override
            public void onFailure(int i) {
                Log.e(TAG, "connect() to " + pairMac + " failed, code " + i);
                handleDisconnect();
            }
        });
    }

    public void createGroup() {
        if (mState != State.NOT_CONNECTED) {
            return;
        }
        setState(State.P2P_GROUP_CREATING);

        mManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "createGroup() successfully called");
            }

            @Override
            public void onFailure(int i) {
                Log.e(TAG, "createGroup() failed, code " + i);
                handleDisconnect();
            }
        });
    }

    public void disconnect() {
        mCmdController.disconnect();
    }

    public void sendChat(String msg) {
        mCmdController.sendChat(msg);
    }

    public void sendVibrate() {
        mCmdController.sendVibrate();
    }

    public void sendFile(String uriStr) {
        Uri uri = Uri.parse(uriStr);
        String path = uri.getPath();
        if (path.endsWith("/")) {
            throw new IllegalArgumentException("invalid file path");
        }

        ContentResolver cr = getContentResolver();
        try (Cursor infoCursor = cr.query(uri, null, null, null, null)) {
            int nameIndex = infoCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIndex = infoCursor.getColumnIndex(OpenableColumns.SIZE);
            infoCursor.moveToFirst();

            String name = infoCursor.getString(nameIndex);
            long size = infoCursor.getLong(sizeIndex);
            ParcelFileDescriptor fd = cr.openFileDescriptor(uri, "r");

            mCmdController.sendFile(new ILocalFile() {
                final FileInputStream stream = new FileInputStream(fd.getFileDescriptor());

                @Override
                public String name() {
                    return name;
                }

                @Override
                public long size() {
                    return size;
                }

                @Override
                public FileInputStream stream() {
                    return stream;
                }

                @Override
                public void close() {
                    Util.closeSilently(stream);
                    Util.closeSilently(fd);
                }
            });
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Open file failed", Toast.LENGTH_LONG).show();
            Log.w(TAG, "open file failed, uri = " + uriStr);
        }
    }

    public void sendApp(String name, String sourceDir) {
        try {
            mCmdController.sendFile(new ILocalFile() {
                final FileInputStream stream = new FileInputStream(sourceDir);
                final long size = stream.getChannel().size();

                @Override
                public String name() {
                    return name + ".apk";
                }

                @Override
                public long size() {
                    return size;
                }

                @Override
                public FileInputStream stream() {
                    return stream;
                }

                @Override
                public void close() {
                    Util.closeSilently(stream);
                }
            });
        } catch (IOException e) {
            Toast.makeText(this, "Open app package failed", Toast.LENGTH_LONG).show();
            Log.w(TAG, "open app package failed, name = " + name + ", sourceDir = " + sourceDir);
        }
    }

    public String getPairName() {
        return mCmdController.getPairName();
    }

    public static String getDeviceName(SharedPreferences sp) {
        return sp.getString(NetworkService.PREF_DEVICE_NAME, Build.MODEL);
    }

    private void setState(State state) {
        mState = state;
        Log.i(TAG, "state changed to " + state);
        sendStateChangedEvent();
    }

    private void handleDisconnect() {
        if (mState != State.NOT_CONNECTED) {
            setState(State.NOT_CONNECTED);
        }

        if (mActivityCb != null) {
            mManager.discoverPeers(mChannel, null);
        }
    }

    private void sendStateChangedEvent() {
        Message m = Message.obtain(null, NetworkService.EVENT_STATE_CHANGED, mState );
        activityCallback(m);
    }

    private void activityCallback(Message m) {
        if (mActivityCb != null) {
            try {
                mActivityCb.send(m);
            } catch (RemoteException e) {
                // should never happen as the service is local
                e.printStackTrace();
            }
        } else {
            Log.w(TAG, "activity unbound, message { what=" + m.what + " } not sent");
        }
    }

    private void resetNetworkComponents() {
        Log.i(TAG, "resetNetworkComponents");
        mManager.cancelConnect(mChannel, null);
        mManager.removeGroup(mChannel, null);
        if (isRunning) {
            mCmdController.startListening();
        }
    }

    private void setDeviceNameHacked(String name) {
        Class<?>[] paramTypes = new Class[3];
        paramTypes[0] = WifiP2pManager.Channel.class;
        paramTypes[1] = String.class;
        paramTypes[2] = WifiP2pManager.ActionListener.class;

        try {
            Method setDeviceName = mManager.getClass().getDeclaredMethod("setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);
            setDeviceName.invoke(mManager, mChannel, name, null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
