package com.gariaelectric.pricelookup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads item photos. A photo reference is either an APK asset path
 * ("photos/bulb_9w.jpg") or the bare filename of a user-added photo stored
 * in filesDir/photos ("u_1721455.jpg").
 */
final class Photos {

    private static final Map<String, Bitmap> THUMBS = new HashMap<>();

    private Photos() {}

    static boolean isAsset(String ref) {
        return ref != null && ref.startsWith("photos/");
    }

    static File userFile(Context ctx, String name) {
        File dir = new File(ctx.getFilesDir(), "photos");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    private static InputStream open(Context ctx, String ref) throws IOException {
        if (isAsset(ref)) return ctx.getAssets().open(ref);
        return new java.io.FileInputStream(userFile(ctx, ref));
    }

    /** Decode at roughly the requested max dimension (power-of-2 sampling). */
    static Bitmap decode(Context ctx, String ref, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = open(ctx, ref);
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();
            int big = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (big / (sample * 2) >= maxDim) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            in = open(ctx, ref);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            in.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Small cached thumbnail for list rows. */
    static Bitmap thumb(Context ctx, String ref) {
        if (ref == null || ref.isEmpty()) return null;
        Bitmap b = THUMBS.get(ref);
        if (b == null) {
            b = decode(ctx, ref, 160);
            if (b != null) THUMBS.put(ref, b);
        }
        return b;
    }

    static void dropThumb(String ref) {
        THUMBS.remove(ref);
    }
}
