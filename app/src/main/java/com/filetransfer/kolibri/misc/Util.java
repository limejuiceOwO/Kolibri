package com.filetransfer.kolibri.misc;

import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.MimeTypeMap;

import com.filetransfer.kolibri.network.NetworkService;

import java.io.Closeable;
import java.io.IOException;

public class Util {
    public static void closeSilently(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String resolveMimeType(String fileName) {
        int dotPos = fileName.lastIndexOf('.');
        if (dotPos != -1 && dotPos != fileName.length() - 1) {
            String ext = fileName.substring(dotPos + 1);
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }
        return null;
    }
}
