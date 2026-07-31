package com.gariaelectric.pricelookup;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies picked or captured images into the app's own photo folder, shrunk
 * and re-encoded as JPEG. Handles a multi-select result (ClipData) as well
 * as a single pick, so one item can carry several pictures.
 */
final class PhotoImport {

    private PhotoImport() {}

    /** Saves every image in the picker result; returns the stored filenames. */
    static List<String> importAll(Context ctx, Intent data) {
        List<String> saved = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                String n = importOne(ctx, clip.getItemAt(i).getUri());
                if (n != null) saved.add(n);
            }
        } else if (data.getData() != null) {
            String n = importOne(ctx, data.getData());
            if (n != null) saved.add(n);
        }
        return saved;
    }

    static String importOne(Context ctx, Uri uri) {
        if (uri == null) return null;
        try {
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            return store(ctx, bmp);
        } catch (Exception e) {
            return null;
        }
    }

    /** Saves a bitmap as a new item photo; returns the filename or null. */
    static String store(Context ctx, Bitmap bmp) {
        if (bmp == null) return null;
        try {
            int big = Math.max(bmp.getWidth(), bmp.getHeight());
            if (big > 1600) {
                float k = 1600f / big;
                bmp = Bitmap.createScaledBitmap(bmp,
                        Math.round(bmp.getWidth() * k),
                        Math.round(bmp.getHeight() * k), true);
            }
            String name = "u_" + System.currentTimeMillis() + "_"
                    + (int) (Math.random() * 1000) + ".jpg";
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
