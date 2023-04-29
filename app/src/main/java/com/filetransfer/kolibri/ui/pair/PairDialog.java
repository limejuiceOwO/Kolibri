package com.filetransfer.kolibri.ui.pair;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.filetransfer.kolibri.databinding.DialogPairBinding;

import java.util.List;

public class PairDialog extends DialogFragment {

    public static final String KEY_REQ = PairDialog.class.getName();
    public static final String KEY_DEV_NAME = "dev_name";
    public static final String KEY_DEV_ADDRESS = "dev_address";
    private static final String TAG = PairDialog.class.getSimpleName();

    public static class Model extends ViewModel {
        public MutableLiveData<List<WifiP2pDevice>> devList = new MutableLiveData<>();
    }

    private DialogPairBinding mBinding;

    private Model mModel;

    private DevListAdapter mListAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getParentFragment() == null) {
            Log.w(TAG, "parent fragment not found");
            mModel = new ViewModelProvider(requireActivity()).get(Model.class);
        } else {
            mModel = new ViewModelProvider(getParentFragment()).get(Model.class);
        }

        mListAdapter = new DevListAdapter((data) -> {
            Bundle res = new Bundle();
            res.putString(KEY_DEV_NAME, data.deviceName);
            res.putString(KEY_DEV_ADDRESS, data.deviceAddress);
            getParentFragmentManager().setFragmentResult(KEY_REQ, res);
            dismiss();
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DialogPairBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBinding.rvDeviceList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.rvDeviceList.setAdapter(mListAdapter);
        mModel.devList.observe(getViewLifecycleOwner(), (devList) -> mListAdapter.updateDevList(devList));
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog().getWindow();
//        window.setDimAmount(0.2f);//设置亮度
        window.setGravity(Gravity.CENTER);
//        window.setWindowAnimations(R.style.PopupAnimation);
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.width = (int) (0.85*metrics.widthPixels);
        layoutParams.height = (int) (0.6*metrics.heightPixels);
        window.setLayout(layoutParams.width, layoutParams.height);
//        window.setBackgroundDrawableResource(android.R.color.transparent);
    }
}
