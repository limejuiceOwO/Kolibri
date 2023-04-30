package com.filetransfer.kolibri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.util.Log;

import com.filetransfer.kolibri.ui.main.MainFragment;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERM_CODE = 1;
    private static final ArrayList<String> requiredPermissions = new ArrayList<>();

    static {
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        requiredPermissions.add(Manifest.permission.READ_PHONE_STATE);
//        requiredPermissions.add(Manifest.permission.CHANGE_WIFI_STATE);
//        requiredPermissions.add(Manifest.permission.CHANGE_NETWORK_STATE);
//        requiredPermissions.add(Manifest.permission.REQUEST_INSTALL_PACKAGES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requiredPermissions.add(Manifest.permission.QUERY_ALL_PACKAGES);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
//        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);

        if (!checkAndRequirePermissions()) {
            return;
        }

        if (savedInstanceState == null) {
            launchMainFragment();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERM_CODE) {
            return;
        }

        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                AlertDialog dlg = new AlertDialog.Builder(this)
                        .setTitle("Permission Denied")
                        .setMessage("Please grant all permissions to use this app")
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, which) -> checkAndRequirePermissions())
                        .setNegativeButton("Exit", (d, which) -> finish())
                        .create();
                dlg.show();
                return;
            }
        }

        // all permissions are granted
        launchMainFragment();
    }

    private void launchMainFragment() {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, new MainFragment()).commit();
    }

    private boolean checkAndRequirePermissions() {
        ArrayList<String> permissionNeeded = new ArrayList<>();
        for (String perm : requiredPermissions) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "permission needed: " + perm);
                permissionNeeded.add(perm);
            }
        }

        if (permissionNeeded.size() == 0) {
            return true;
        }

        requestPermissions(requiredPermissions.toArray(new String[0]), PERM_CODE);
        return false;
    }
}