package com.project.laundryappui.network;

import android.util.Log;
import com.project.laundryappui.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Basic Supabase REST Client for Production Ready status.
 * Handles communication with Supabase PostgREST API.
 */
public class SupabaseClient {
    private static final String TAG = "SupabaseClient";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public static void post(String table, JSONObject data, Callback<String> callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                if (BuildConfig.SUPABASE_URL.isEmpty()) {
                    callback.onError(new Exception("SUPABASE_URL is not configured"));
                    return;
                }

                URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/" + table);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);
                conn.setRequestProperty("Prefer", "return=minimal");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(data.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    callback.onSuccess("Success");
                } else {
                    callback.onError(new Exception("HTTP " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error posting to " + table, e);
                callback.onError(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public static void fetch(String table, String select, Callback<JSONArray> callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                if (BuildConfig.SUPABASE_URL.isEmpty()) {
                    callback.onError(new Exception("SUPABASE_URL is not configured"));
                    return;
                }

                URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/" + table + "?select=" + select);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();
                    callback.onSuccess(new JSONArray(response.toString()));
                } else {
                    callback.onError(new Exception("HTTP " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching from " + table, e);
                callback.onError(e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}
