package com.filetransfer.kolibri.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.filetransfer.kolibri.R;
import com.filetransfer.kolibri.db.entity.BaseEntry;
import com.filetransfer.kolibri.db.entity.ChatEntry;
import com.filetransfer.kolibri.db.entity.FileEntry;
import com.filetransfer.kolibri.misc.Util;

import java.util.ArrayList;
import java.util.Arrays;

public class MainMsgListAdapter extends RecyclerView.Adapter<MainMsgListAdapter.BaseVH<? extends BaseEntry>> {

    public interface Callback {
        void onFileEntryClicked(FileEntry entry);
    }

    private final ArrayList<BaseEntry> mDataSet = new ArrayList<>();
    private final Callback mCallback;

    public MainMsgListAdapter(Callback callback) {
        mCallback = callback;
    }

    public void mergeEntriesIntoDataSet(FileEntry[] fileEntries, ChatEntry[] chatEntries) {
        int oldSize = mDataSet.size();
        int pFile = 0, pChat = 0;
        while (pFile < fileEntries.length || pChat < chatEntries.length) {
            long t1 = pFile == fileEntries.length ? Long.MAX_VALUE : fileEntries[pFile].createdAt;
            long t2 = pChat == chatEntries.length ? Long.MAX_VALUE : chatEntries[pChat].createdAt;
            if (t1 <= t2) {
                mDataSet.add(fileEntries[pFile++]);
            } else {
                mDataSet.add(chatEntries[pChat++]);
            }
        }
        notifyItemRangeInserted(oldSize, pFile + pChat);
    }

    public void updateFileEntries(FileEntry[] newEntries) {
        int pOld = -1, pNew = 0;
        while ((++pOld) < mDataSet.size() && pNew < newEntries.length) {
            BaseEntry oldItem = mDataSet.get(pOld);
            if (!(oldItem instanceof FileEntry) || oldItem.id != newEntries[pNew].id) {
                continue;
            }

            mDataSet.set(pOld, newEntries[pNew++]);
            notifyItemChanged(pOld);
        }
    }

    @Override
    public int getItemViewType(int position) {
        BaseEntry entry = mDataSet.get(position);
        if (entry instanceof FileEntry) {
            return entry.fromSelf ? 0 : 1;
        } else if (entry instanceof ChatEntry) {
            return entry.fromSelf ? 2 : 3;
        } else {
            throw new IllegalArgumentException("unexpected item type at position " + position);
        }
    }

    @NonNull
    @Override
    public BaseVH<? extends BaseEntry> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case 0: // self file
                return new FileVH(inflater.inflate(R.layout.item_file_self, parent, false), mCallback);
            case 1: // pair file
                return new FileVH(inflater.inflate(R.layout.item_file_pair, parent, false), mCallback);
            case 2: // self chat
                return new ChatVH(inflater.inflate(R.layout.item_chat_self, parent, false));
            case 3: // pair chat
                return new ChatVH(inflater.inflate(R.layout.item_chat_pair, parent, false));
            default:
                throw new IllegalArgumentException("unexpected view type " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull BaseVH<? extends BaseEntry> holder, int position) {
        BaseVH<BaseEntry> _holder = (BaseVH<BaseEntry>) holder; // skip type checking, let Adapter ensure type consistency
        _holder.setData(mDataSet.get(position));
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    public static abstract class BaseVH<T extends BaseEntry> extends RecyclerView.ViewHolder {
        public BaseVH(@NonNull View itemView) {
            super(itemView);
        }

        abstract void setData(T data);
    }

    public static class FileVH extends BaseVH<FileEntry> {
        private final TextView mDevName;
        private final TextView mName;
        private final TextView mSize;
        private final TextView mStatus;
        private final ImageView mIcon;
        private final Callback mCallback;
        FileEntry mData;

        public FileVH(@NonNull View itemView, Callback callback) {
            super(itemView);
            mCallback = callback;

            mDevName = itemView.findViewById(R.id.tv_device_name);
            mName = itemView.findViewById(R.id.tv_filename);
            mSize = itemView.findViewById(R.id.tv_size);
            mStatus = itemView.findViewById(R.id.tv_status);
            mIcon = itemView.findViewById(R.id.iv_file_icon);

            View bubble = itemView.findViewById(R.id.msg_bubble);
            bubble.setOnClickListener((v) -> mCallback.onFileEntryClicked(mData));
        }

        @Override
        void setData(FileEntry data) {
            mData = data;

            mName.setText(data.name);
            mIcon.setImageResource(resolveIconByFileName(data.name));
            mDevName.setText(data.fromSelf ? "Me" : data.deviceName);

            switch (data.status) {
                case FileEntry.STATUS_COMPLETED:
                    mSize.setText(formatSize(data.size));
                    mStatus.setText("Completed");
                    break;
                case FileEntry.STATUS_RUNNING:
                    mSize.setText(String.format("%s / %s", formatSize(data.transferred), formatSize(data.size)));
                    mStatus.setText(data.fromSelf ? "Sending" : "Receiving");
                    break;
                case FileEntry.STATUS_FAILED:
                    mSize.setText(formatSize(data.size));
                    mStatus.setText("Failed");
                    break;
            }
        }

        private static String formatSize(double size) {
            String[] sizeUnits = { "B", "KB", "MB", "GB", "TB" };
            int unitIndex = 0;

            while (unitIndex < sizeUnits.length - 1 && size > 1024 - 1e-5) {
                size /= 1024;
                ++unitIndex;
            }

            return String.format("%.2f%s", size, sizeUnits[unitIndex]);
        }

        private static int resolveIconByFileName(String fileName) {
            if (fileName.endsWith(".pdf")) {
                return R.drawable.file_pdf;
            } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                return R.drawable.file_word;
            } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                return R.drawable.file_excel;
            } else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
                return R.drawable.file_ppt;
            } else if (fileName.endsWith(".apk")) {
                return R.drawable.file_apk;
            }

            String[] zipExt = {".zip", ".rar", ".7z", ".tar", ".gz", ".tgz"};
            if (Arrays.stream(zipExt).anyMatch(fileName::endsWith)) {
                return R.drawable.file_zip;
            }

            String mime = Util.resolveMimeType(fileName);
            if (mime == null) {
                return R.drawable.file_unknown;
            } else if (mime.startsWith("image")) {
                return R.drawable.file_img;
            } else if (mime.startsWith("text")) {
                return R.drawable.file_text;
            } else if (mime.startsWith("audio")) {
                return R.drawable.file_audio;
            } else if (mime.startsWith("video")) {
                return R.drawable.file_video;
            }

            return R.drawable.file_unknown;
        }
    }

    public static class ChatVH extends BaseVH<ChatEntry> {
        private final TextView mContent;
        private final TextView mDevName;

        public ChatVH(@NonNull View itemView) {
            super(itemView);
            mContent = itemView.findViewById(R.id.tv_content);
            mDevName = itemView.findViewById(R.id.tv_device_name);
        }

        @Override
        void setData(ChatEntry data) {
            mContent.setText(data.content);
            mDevName.setText(data.fromSelf ? "Me" : data.deviceName);
        }
    }
}
