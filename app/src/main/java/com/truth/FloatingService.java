package com.truth;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private FrameLayout rootContainer;
    private ImageView floatingIcon;
    private WebView webView;
    private WindowManager.LayoutParams params;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = dpToPx(100);

        rootContainer = new FrameLayout(this);

        // -- Setup Floating Icon --
        floatingIcon = new ImageView(this);
        int iconId = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        if (iconId != 0) {
            floatingIcon.setImageResource(iconId);
        } else {
            floatingIcon.setBackgroundColor(Color.parseColor("#a7ff00")); // LIE Neon Green Fallback
        }
        
        int iconSize = dpToPx(50);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        floatingIcon.setLayoutParams(iconParams);
        
        // Drag Logic for Icon
        floatingIcon.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isMoving = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoving = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - initialTouchX;
                        float diffY = event.getRawY() - initialTouchY;
                        if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) {
                            isMoving = true;
                        }
                        if (isMoving) {
                            params.x = initialX + (int) diffX;
                            params.y = initialY + (int) diffY;
                            windowManager.updateViewLayout(rootContainer, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isMoving) {
                            expandMenu();
                        }
                        return true;
                }
                return false;
            }
        });

        // -- Setup WebView --
        webView = new WebView(this);
        int webWidth = dpToPx(340);
        int webHeight = dpToPx(480);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(webWidth, webHeight);
        webView.setLayoutParams(webParams);
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        
        webView.setVisibility(View.GONE); // Start minimized

        // Drag logic for WebView Header (Assuming top 60dp is header)
        webView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (event.getY() <= dpToPx(60)) {
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                    }
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (initialTouchY > 0) { 
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(rootContainer, params);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    initialTouchY = 0;
                }
                return false; 
            }
        });

        rootContainer.addView(webView);
        rootContainer.addView(floatingIcon);

        windowManager.addView(rootContainer, params);
    }

    private void expandMenu() {
        floatingIcon.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    public void minimizeMenu() {
        if (webView != null && floatingIcon != null) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.setVisibility(View.GONE);
                    floatingIcon.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (rootContainer != null) {
            windowManager.removeView(rootContainer);
        }
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void minimize() {
            minimizeMenu();
        }
        
        @JavascriptInterface
        public void exit() {
            stopSelf();
        }
    }
}
