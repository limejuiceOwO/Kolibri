package com.filetransfer.kolibri.ui.main;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Messenger;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.filetransfer.kolibri.R;
import com.filetransfer.kolibri.databinding.FragmentMainBinding;
import com.filetransfer.kolibri.db.MainDatabase;
import com.filetransfer.kolibri.db.entity.ChatEntry;
import com.filetransfer.kolibri.db.entity.FileEntry;
import com.filetransfer.kolibri.misc.Util;
import com.filetransfer.kolibri.network.NetworkService;
import com.filetransfer.kolibri.ui.pair.PairDialog;
import com.filetransfer.kolibri.ui.sendapp.SendAppFragment;
import com.filetransfer.kolibri.ui.setting.SettingsFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO: support udp broadcast
public class MainFragment extends Fragment {

    private static final String TAG = MainFragment.class.getSimpleName();
    private static final int FILE_ENTRY_UPDATE_DELAY = 1000;

    private final ArrayList<Long> mRunningFileTaskIds = new ArrayList<>();
    private final Handler mMsgListUpdateHandler = new Handler(Looper.getMainLooper());
    private Activity mActivity;
    private FragmentMainBinding mBinding;
    private Model mModel;
    private PairDialog.Model mPairModel;
    private NetworkService mNetService;
    private MainMsgListAdapter mAdapter;
    private MainDatabase mDatabase;
    private long mLastFileId = -1, mLastChatId = -1;

    private final Runnable mMsgListUpdateTask = new Runnable() {
        @Override
        public void run() {
            FileEntry[] newRunningTasks = mDatabase.fileDao().listByIds(mRunningFileTaskIds);
            if (newRunningTasks.length > 0) {
                mAdapter.updateFileEntries(newRunningTasks);
            }

            Iterator<Long> oldIter = mRunningFileTaskIds.iterator();
            int pNew = 0;
            while (oldIter.hasNext()) {
                while (pNew < newRunningTasks.length && newRunningTasks[pNew].status != FileEntry.STATUS_RUNNING) {
                    ++pNew;
                }
                long newId = pNew < newRunningTasks.length ? newRunningTasks[pNew].id : -1;
                long oldId = oldIter.next();
                if (oldId != newId) {
                    oldIter.remove();
                    continue;
                }
                ++pNew;
            }

            if (mRunningFileTaskIds.size() > 0) {
                mMsgListUpdateHandler.postDelayed(this, FILE_ENTRY_UPDATE_DELAY);
            }
        }
    };

    private final ActivityResultLauncher<String[]> mDocPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument() {
                @NonNull
                @Override
                public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
                    return super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE);
                }
            }, (uri) -> {
                if (mNetService != null && uri != null) {
                    mNetService.sendFile(uri.toString());
                }
            });

    public static class Model extends ViewModel {
        final MutableLiveData<String> devName = new MutableLiveData<>(null);
    }

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "service bound");
            mNetService = ((NetworkService.LocalBinder) service).getService();
            mNetService.registerActivityCallback(new Messenger(mServiceMsgHandler));

            // update message list
            addNewEntries();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "service temporarily lost");
            mNetService = null;
        }
    };

    private final Handler mServiceMsgHandler = new Handler(Looper.getMainLooper(), msg -> {
        switch (msg.what) {
            case NetworkService.EVENT_STATE_CHANGED:
                Log.i(TAG, "service state changed to " + msg.obj);
                onServiceStateChanged((NetworkService.State) msg.obj);
                break;
            case NetworkService.EVENT_NEW_ENTRY:
                addNewEntries();
                break;
        }
        return true;
    });

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                Log.i(TAG, "update peer list");
                WifiP2pDeviceList devList = intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
                mPairModel.devList.postValue(
                    devList.getDeviceList()
                            .stream()
                            .filter((dev) -> dev.status == WifiP2pDevice.AVAILABLE)
                            .collect(Collectors.toList()));
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize members
        ViewModelProvider vp = new ViewModelProvider(this);
        mModel = vp.get(Model.class);
        mPairModel = vp.get(PairDialog.Model.class);
        mActivity = requireActivity();
        mDatabase = MainDatabase.getInstance(mActivity.getApplicationContext());

        mAdapter = new MainMsgListAdapter(new MainMsgListAdapter.Callback() {
            @Override
            public void onFileEntryClicked(FileEntry entry) {
                if (entry.fromSelf || entry.status != FileEntry.STATUS_COMPLETED) {
                    return;
                }

                File file = new File(entry.path, entry.name);
                if (!file.exists()) {
                    Toast.makeText(mActivity, "File deleted", Toast.LENGTH_SHORT).show();
                    return;
                }

                Uri fileUri = FileProvider.getUriForFile(mActivity, "com.filetransfer.kolibri.MainFileProvider", file);
                String mime = Util.resolveMimeType(entry.name);
                if (mime == null) {
                    mime = "*/*";
                }

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, mime);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        });

        // start NetworkService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mActivity.startForegroundService(new Intent(mActivity, NetworkService.class));
        } else {
            mActivity.startService(new Intent(mActivity, NetworkService.class));
        }

        // bind to NetworkService
        mActivity.bindService(new Intent(mActivity, NetworkService.class), mConn, 0);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mBinding = FragmentMainBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBinding.toolbar.setOnMenuItemClickListener((item) -> {
            if (item.getItemId() == R.id.menu_settings) {
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, new SettingsFragment())
                        .addToBackStack(null)
                        .commit();
                return true;
            }
            return false;
        });

        // init main message list
        LinearLayoutManager lm = new LinearLayoutManager(mActivity);
        lm.setStackFromEnd(true);
        mBinding.rvMainMsg.setLayoutManager(lm);

        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int cnt = mAdapter.getItemCount();
                if (cnt > 0) {
                    mBinding.rvMainMsg.smoothScrollToPosition(cnt - 1);
                }
            }
        });

        ((SimpleItemAnimator) Objects.requireNonNull(mBinding.rvMainMsg.getItemAnimator())).setSupportsChangeAnimations(false);
        mBinding.rvMainMsg.setAdapter(mAdapter);

        // change toolbar title based on connected device name
        mModel.devName.observe(getViewLifecycleOwner(), (devName) -> {
            if (devName == null) {
                mBinding.toolbar.setTitle(R.string.app_name);
            } else {
                mBinding.toolbar.setTitle(devName);
            }
        });

        mBinding.btnConnect.setOnClickListener((v) -> {
            PairDialog dlg = new PairDialog();
            dlg.show(getChildFragmentManager(), PairDialog.class.getSimpleName());
        });

        mBinding.btnCreateGroup.setOnClickListener((v) -> {
            if (mNetService == null) {
                return;
            }
            mNetService.createGroup();
        });

        mBinding.btnDisconnect.setOnClickListener((v) -> {
            if (mNetService == null) {
                return;
            }
            mNetService.disconnect();
        });

        mBinding.btnCancel.setOnClickListener((v) -> {
            if (mNetService == null) {
                return;
            }
            mNetService.disconnect();
        });

        mBinding.btnSendFile.setOnClickListener((v) -> mDocPicker.launch(new String[]{ "*/*" }));

        mBinding.btnSendApp.setOnClickListener((v) -> getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.container, new SendAppFragment())
                .addToBackStack(null)
                .commit());

        mBinding.btnSendText.setOnClickListener((v) -> {
            if (mNetService == null) {
                return;
            }
            String chatMsg = String.valueOf(mBinding.chatTextInput.getText());
            mBinding.chatTextInput.setText("");
            mNetService.sendChat(chatMsg);
        });

        mBinding.btnVibe.setOnClickListener((v) -> {
            if (mNetService == null) {
                return;
            }
            mNetService.sendVibrate();
        });

        // register pairing dialog result listener
        getChildFragmentManager().setFragmentResultListener(PairDialog.KEY_REQ, getViewLifecycleOwner(), (key, result) -> {
            if (mNetService == null) {
                return;
            }

            mModel.devName.setValue(result.getString(PairDialog.KEY_DEV_NAME));
            mNetService.connect(result.getString(PairDialog.KEY_DEV_ADDRESS));
        });

        getParentFragmentManager().setFragmentResultListener(SendAppFragment.KEY_REQ, getViewLifecycleOwner(), (key, result) -> {
            if (mNetService == null) {
                return;
            }

            mNetService.sendApp(
                    result.getString(SendAppFragment.KEY_APP_NAME),
                    result.getString(SendAppFragment.KEY_SOURCE_DIR));
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        // register wifi p2p event receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mActivity.registerReceiver(mReceiver, filter);

        // register activity callback at NetworkService
        if (mNetService != null) {
            mNetService.registerActivityCallback(new Messenger(mServiceMsgHandler));
            addNewEntries();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        // stop message list update
        mActivity.unregisterReceiver(mReceiver);
        mMsgListUpdateHandler.removeCallbacks(mMsgListUpdateTask);

        // unregister activity callback at NetworkService
        if (mNetService != null) {
            mNetService.unregisterActivityCallback();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mNetService = null;
        mActivity.unbindService(mConn);
    }

    private void onServiceStateChanged(NetworkService.State state) {
        int[] visibility = {View.GONE, View.GONE, View.GONE};
        switch (state) {
            case CONNECTED:
                mBinding.toolbar.setSubtitle("Connected");
                if (mNetService != null) {
                    mModel.devName.setValue(mNetService.getPairName());
                }
                mBinding.chatTextInput.setText("");
                visibility[2] = View.VISIBLE;
                break;
            case NOT_CONNECTED:
                mBinding.toolbar.setSubtitle("Not Connected");
                mModel.devName.setValue(null);
                visibility[0] = View.VISIBLE;
                break;
            case SRV_CONNECTING:
            case P2P_CONNECTING:
                mBinding.toolbar.setSubtitle("Connecting to pair device");
                visibility[1] = View.VISIBLE;
                break;
            case P2P_GROUP_CREATING:
            case P2P_GROUP_CREATED:
                mBinding.toolbar.setSubtitle("Waiting for the pair device to connect");
                visibility[1] = View.VISIBLE;
                break;
        }

        mBinding.cmdGroupNotConnected.setVisibility(visibility[0]);
        mBinding.cmdGroupConnecting.setVisibility(visibility[1]);
        mBinding.cmdGroupConnected.setVisibility(visibility[2]);
    }

    private void addNewEntries() {
        FileEntry[] fileEntries = mDatabase.fileDao().listIdGreaterThan(mLastFileId);
        ChatEntry[] chatEntries = mDatabase.chatDao().listIdGreaterThan(mLastChatId);
        if (fileEntries.length > 0) {
            mLastFileId = fileEntries[fileEntries.length - 1].id;
        }
        if (chatEntries.length > 0) {
            mLastChatId = chatEntries[chatEntries.length - 1].id;
        }

        mAdapter.mergeEntriesIntoDataSet(fileEntries, chatEntries);

        for (FileEntry e : fileEntries) {
            if (e.status == FileEntry.STATUS_RUNNING) {
                mRunningFileTaskIds.add(e.id);
            }
        }

        if (mRunningFileTaskIds.size() > 0) {
            mMsgListUpdateHandler.removeCallbacks(mMsgListUpdateTask);
            mMsgListUpdateHandler.postDelayed(mMsgListUpdateTask, FILE_ENTRY_UPDATE_DELAY);
        }
    }

//    private static String resolveP2pErrorCode(int i) {
//        switch (i) {
//            case WifiP2pManager.P2P_UNSUPPORTED:
//                return "P2p not supported";
//            case WifiP2pManager.ERROR:
//                return "Unknown framework error";
//            case WifiP2pManager.BUSY:
//                return "Wifi not enabled or framework is busy";
//            default:
//                throw new IllegalArgumentException("Unknown error code " + i);
//        }
//    }
}