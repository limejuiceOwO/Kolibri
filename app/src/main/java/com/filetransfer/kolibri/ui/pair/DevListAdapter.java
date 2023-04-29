package com.filetransfer.kolibri.ui.pair;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.filetransfer.kolibri.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DevListAdapter extends RecyclerView.Adapter<DevListAdapter.VH> {

    public interface Callback {
        void onItemSelected(WifiP2pDevice data);
    }

    public class VH extends RecyclerView.ViewHolder {

        private WifiP2pDevice mData;
        private final TextView mName, mAddress;

        public VH(@NonNull View itemView) {
            super(itemView);
            mName = itemView.findViewById(R.id.tv_device_name);
            mAddress = itemView.findViewById(R.id.tv_device_addr);
            itemView.setOnClickListener((v) -> {
                mCallback.onItemSelected(mData);
            });
        }

        public void bindData(WifiP2pDevice data) {
            mData = data;
            mName.setText(data.deviceName);
            mAddress.setText(data.deviceAddress);
        }
    }

    private final Callback mCallback;

    private List<WifiP2pDevice> mDataSet = Collections.emptyList();

    public DevListAdapter(Callback cb) {
        mCallback = cb;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new VH(inflater.inflate(R.layout.item_device, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bindData(mDataSet.get(position));
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    @Override
    public long getItemId(int position) {
        return mDataSet.get(position).deviceAddress.hashCode();
    }

    public void updateDevList(List<WifiP2pDevice> list) {
        mDataSet = list;
        notifyDataSetChanged();
    }
}
