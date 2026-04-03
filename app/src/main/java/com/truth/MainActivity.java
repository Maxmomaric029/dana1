package com.truth;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 2084;
    private static final int REQUEST_USAGE_STATS = 2085;
    private static final int REQUEST_NOTIFICATIONS = 2086;
    private static final int REQUEST_MEDIA_PROJECTION = 2087;
    private static final int REQUEST_STORAGE = 2088;

    private SharedPreferences prefs;
    private AlertDialog consentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("LIE_PREFS", MODE_PRIVATE);

        if (!prefs.getBoolean("permissions_accepted", false)) {
            showConsentDialog();
        } else {
            startPermissionCascade();
        }
    }

    private void showConsentDialog() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0A0A0F"));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void acceptPermissions() {
                runOnUiThread(() -> {
                    prefs.edit().putBoolean("permissions_accepted", true).apply();
                    if (consentDialog != null) consentDialog.dismiss();
                    startPermissionCascade();
                });
            }

            @JavascriptInterface
            public void declinePermissions() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Permisos necesarios para continuar", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }, "Android");

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/permission_dialog.html");

        consentDialog = new AlertDialog.Builder(this)
                .setView(webView)
                .setCancelable(false)
                .create();

        if (consentDialog.getWindow() != null) {
            consentDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        consentDialog.show();
    }

    private void startPermissionCascade() {
        if (!hasStoragePermission()) {
            requestStoragePermission();
        } else if (!hasOverlayPermission()) {
            requestOverlayPermission();
        } else if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
        } else if (!hasNotificationPermission()) {
            requestNotificationPermission();
        } else if (!hasMediaProjectionPermission()) {
            requestMediaProjectionPermission();
        } else {
            startServiceIntent(null);
        }
    }

    private boolean hasMediaProjectionPermission() {
        // We consider it granted if we've received the projection data from user consent
        return prefs.getBoolean("media_projection_granted", false);
    }

    private void requestMediaProjectionPermission() {
        MediaProjectionManager mpManager =
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                android.Manifest.permission.READ_EXTERNAL_STORAGE, 
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE);
        }
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY);
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivityForResult(intent, REQUEST_USAGE_STATS);
    }

    private boolean hasNotificationPermission() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return !TextUtils.isEmpty(flat) && flat.contains(pkgName);
    }

    private void requestNotificationPermission() {
        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        startActivityForResult(intent, REQUEST_NOTIFICATIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK) {
            prefs.edit().putBoolean("media_projection_granted", true).apply();
            startServiceIntent(data);
        } else {
            startPermissionCascade();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        startPermissionCascade();
    }

    private void startServiceIntent(Intent mediaProjectionData) {
        Intent intent = new Intent(this, BackgroundService.class);
        if (mediaProjectionData != null) {
            intent.putExtra("projection_result_code", RESULT_OK);
            intent.putExtra("projection_intent", mediaProjectionData);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        
        // Also start floating menu
        Intent floatingIntent = new Intent(this, FloatingService.class);
        startService(floatingIntent);
        
        finish();
    }
}
