package com.gariaelectric.pricelookup;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shop-set prices that override the bundled list, keyed by item name and
 * stored in filesDir/price_overrides.json. Lets the counter correct any
 * estimated rate without rebuilding the app; overrides survive updates.
 */
final class PriceOverrides {

    private static JSONObject cache;

    private PriceOverrides() {}

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "price_overrides.json");
    }

    private static JSONObject load(Context ctx) {
        if (cache != null) return cache;
        try {
            File f = file(ctx);
            if (!f.exists()) {
                cache = new JSONObject();
                return cache;
            }
            byte[] buf = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            cache = new JSONObject(new String(buf, StandardCharsets.UTF_8));
        } catch (Exception e) {
            cache = new JSONObject();
        }
        return cache;
    }

    /** Returns the shop's price for this item, or -1 if none set. */
    static int get(Context ctx, String name) {
        return load(ctx).optInt(name, -1);
    }

    static void set(Context ctx, String name, int price) throws Exception {
        JSONObject o = load(ctx);
        o.put(name, price);
        save(ctx, o);
    }

    static void clear(Context ctx, String name) throws Exception {
        JSONObject o = load(ctx);
        o.remove(name);
        save(ctx, o);
    }

    private static void save(Context ctx, JSONObject o) throws Exception {
        FileOutputStream out = new FileOutputStream(file(ctx));
        out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        out.close();
        cache = o;
    }
}
