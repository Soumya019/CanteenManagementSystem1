package com.gariaelectric.pricelookup;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Persistent store for shopkeeper-added items (filesDir/user_items.json). */
final class UserItems {

    private UserItems() {}

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "user_items.json");
    }

    static JSONArray load(Context ctx) {
        try {
            File f = file(ctx);
            if (!f.exists()) return new JSONArray();
            byte[] buf = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            return new JSONArray(new String(buf, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(Context ctx, JSONArray arr) throws Exception {
        FileOutputStream out = new FileOutputStream(file(ctx));
        out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    static void add(Context ctx, JSONObject item) throws Exception {
        JSONArray arr = load(ctx);
        arr.put(item);
        save(ctx, arr);
    }

    /** Removes the item with the given id; returns its photo ref (or ""). */
    static String removeById(Context ctx, long id) throws Exception {
        JSONArray arr = load(ctx);
        JSONArray keep = new JSONArray();
        String photo = "";
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o.optLong("id") == id) {
                photo = o.optString("photo", "");
            } else {
                keep.put(o);
            }
        }
        save(ctx, keep);
        return photo;
    }
}
