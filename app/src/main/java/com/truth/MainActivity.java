package com.truth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 2084;

    private SharedPreferences prefs;
    private AlertDialog consentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("LIE_PREFS", MODE_PRIVATE);

        // If consent already given, go straight to permission cascade
        if (!prefs.getBoolean("permissions_accepted", false)) {
            showConsentDialog();
        } else {
            startPermissionCascade();
        }
    }

    private void showConsentDialog() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0A0A0F"));
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
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
        // Only request overlay permission — the only one truly needed for FloatingService
        if (!hasOverlayPermission()) {
            requestOverlayPermission();
        } else {
            launchServices();
        }
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        android.net.Uri uri = android.net.Uri.parse("package:" + getPackageName());
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
        startActivityForResult(intent, REQUEST_OVERLAY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // After returning from overlay settings, just try to launch regardless
        launchServices();
    }

    private void launchServices() {
        // Start background service
        Intent bgIntent = new Intent(this, BackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(bgIntent);
        } else {
            startService(bgIntent);
        }

        // Start floating menu
        if (hasOverlayPermission()) {
            Intent floatingIntent = new Intent(this, FloatingService.class);
            startService(floatingIntent);
        }

        finish();
    }
}
