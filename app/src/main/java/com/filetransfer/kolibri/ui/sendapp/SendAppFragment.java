package com.filetransfer.kolibri.ui.sendapp;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.filetransfer.kolibri.databinding.FragmentSendAppBinding;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class SendAppFragment extends Fragment {
    private static final String TAG = SendAppFragment.class.getSimpleName();
    public static final String KEY_REQ = SendAppFragment.class.getName();
    public static final String KEY_APP_NAME = "app_name";
    public static final String KEY_SOURCE_DIR = "source_dir";
    private FragmentSendAppBinding mBinding;
    private FragmentActivity mActivity;
    private final List<AppData> mAppDataList = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = requireActivity();

        PackageManager pm = mActivity.getPackageManager();
        List<PackageInfo> packageInfoList = pm.getInstalledPackages(0);

        for (PackageInfo info : packageInfoList) {
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }

            AppData data = new AppData();
            data.icon = info.applicationInfo.loadIcon(pm);
            data.name = pm.getApplicationLabel(info.applicationInfo).toString();
            data.sourceDir = info.applicationInfo.sourceDir;
            mAppDataList.add(data);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentSendAppBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBinding.toolbar.setNavigationOnClickListener((v) -> {
            getParentFragmentManager().popBackStack();
        });

        mBinding.rvSendApp.setLayoutManager(new GridLayoutManager(mActivity, 4));
        mBinding.rvSendApp.setAdapter(new SendAppListAdapter(mAppDataList, data -> {
            Bundle result = new Bundle();
            result.putString(KEY_SOURCE_DIR, data.sourceDir);
            result.putString(KEY_APP_NAME, data.name);
            getParentFragmentManager().setFragmentResult(KEY_REQ, result);
            getParentFragmentManager().popBackStack();
        }));
    }
}
