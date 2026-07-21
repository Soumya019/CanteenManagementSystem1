package com.gariaelectric.pricelookup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web image search used to auto-fetch a product photo when the shopkeeper
 * adds an item without one. Uses DuckDuckGo's image endpoint (no API key
 * needed). All methods are blocking — call from a background thread.
 */
final class PhotoSearch {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String[] BAD_HOSTS = {"fbsbx", "gstatic",
            "encrypted-tbn", "ytimg", "tiktok", "instagram", "lookaside"};

    private PhotoSearch() {}

    private static byte[] fetch(String url, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", "https://duckduckgo.com/");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > maxBytes) break;
        }
        in.close();
        c.disconnect();
        return out.toByteArray();
    }

    /** Returns candidate image URLs for the query (possibly empty). */
    static List<String> search(String query) {
        List<String> urls = new ArrayList<>();
        try {
            String q = URLEncoder.encode(query, "UTF-8");
            String html = new String(fetch(
                    "https://duckduckgo.com/?q=" + q + "&iax=images&ia=images",
                    2_000_000));
            Matcher m = Pattern.compile("vqd=\"?([\\d-]+)").matcher(html);
            if (!m.find()) return urls;
            String body = new String(fetch(
                    "https://duckduckgo.com/i.js?l=us-en&o=json&q=" + q
                            + "&vqd=" + m.group(1), 4_000_000));
            JSONArray results = new JSONObject(body).optJSONArray("results");
            if (results == null) return urls;
            for (int i = 0; i < results.length() && urls.size() < 10; i++) {
                JSONObject r = results.optJSONObject(i);
                if (r == null) continue;
                String u = r.optString("image", "");
                if (u.isEmpty() || bad(u)) continue;
                int w = r.optInt("width"), h = r.optInt("height");
                if (w > 0 && h > 0 && (Math.min(w, h) < 250
                        || Math.max(w, h) > 3 * Math.min(w, h))) continue;
                urls.add(u);
            }
        } catch (Exception ignored) {
            // no network / endpoint changed — caller falls back gracefully
        }
        return urls;
    }

    private static boolean bad(String url) {
        for (String b : BAD_HOSTS) {
            if (url.contains(b)) return true;
        }
        return false;
    }

    /** Downloads url, stores it as a user photo, returns filename or null. */
    static String download(Context ctx, String url) {
        try {
            byte[] data = fetch(url, 6_000_000);
            if (data.length < 6000) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (Math.min(bounds.outWidth, bounds.outHeight) < 250) return null;
            int big = Math.max(bounds.outWidth, bounds.outHeight);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (big / (opts.inSampleSize * 2) >= 1600) opts.inSampleSize *= 2;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (bmp == null) return null;
            String name = "u_" + System.currentTimeMillis() + ".jpg";
            FileOutputStream out = new FileOutputStream(
                    Photos.userFile(ctx, name));
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
            out.close();
            return name;
        } catch (Exception e) {
            return null;
        }
    }
}
