package com.filetransfer.kolibri.ui.sendapp;


import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.filetransfer.kolibri.R;

import java.util.List;

public class SendAppListAdapter extends RecyclerView.Adapter<SendAppListAdapter.VH> {

        public interface Callback {
                void onAppItemSelected(AppData data);
        }

        private final List<AppData> mDataSet;
        private final Callback mCallback;

        public SendAppListAdapter(List<AppData> dataSet, Callback cb) {
                mDataSet = dataSet;
                mCallback = cb;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                return new VH(inflater.inflate(R.layout.item_app, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
                holder.setData(mDataSet.get(position));
        }

        @Override
        public int getItemCount() {
                return mDataSet.size();
        }

        public class VH extends RecyclerView.ViewHolder {
                private final ImageView mIcon;
                private final TextView mName;
                private AppData mData;

                public VH(@NonNull View itemView) {
                        super(itemView);
                        mIcon = itemView.findViewById(R.id.iv_app_icon);
                        mName = itemView.findViewById(R.id.tv_app_name);
                        itemView.setOnClickListener((v) -> mCallback.onAppItemSelected(mData));
                }

                void setData(AppData data) {
                        mData = data;
                        mIcon.setImageDrawable(data.icon);
                        mName.setText(data.name);
                }
        }
}
