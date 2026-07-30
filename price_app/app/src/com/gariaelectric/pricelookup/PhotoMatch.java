package com.gariaelectric.pricelookup;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Colour fingerprint of a photo, used as the fallback when the camera shot
 * carries no readable text. Must stay byte-for-byte identical to
 * signature() in export_items.py, which precomputes the same numbers for
 * every bundled product photo.
 */
final class PhotoMatch {

    static final int GRID = 4;
    static final int HUE_BINS = 12;
    static final int LEN = HUE_BINS + GRID * GRID * 3;
    private static final int WHITE_V = 232, WHITE_S = 26;

    private PhotoMatch() {}

    /** Returns a LEN-length signature, or null if the frame is blank. */
    static int[] signature(Bitmap src) {
        Bitmap bmp = Bitmap.createScaledBitmap(src, 64, 64, true);
        double[] hue = new double[HUE_BINS];
        double[] cellR = new double[GRID * GRID];
        double[] cellG = new double[GRID * GRID];
        double[] cellB = new double[GRID * GRID];
        int[] cellN = new int[GRID * GRID];
        int kept = 0;

        for (int y = 0; y < 64; y++) {
            int cy = y * GRID / 64;
            for (int x = 0; x < 64; x++) {
                int p = bmp.getPixel(x, y);
                int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
                int mx = Math.max(r, Math.max(g, b));
                int mn = Math.min(r, Math.min(g, b));
                int chroma = mx - mn;
                if (mx >= WHITE_V && chroma <= WHITE_S) continue;  // background
                kept++;
                int c = cy * GRID + x * GRID / 64;
                cellR[c] += r; cellG[c] += g; cellB[c] += b; cellN[c]++;
                if (chroma > 20) {
                    double h;
                    if (mx == r) {
                        h = (60.0 * (g - b) / chroma) % 360.0;
                        if (h < 0) h += 360.0;
                    } else if (mx == g) {
                        h = 60.0 * (b - r) / chroma + 120.0;
                    } else {
                        h = 60.0 * (r - g) / chroma + 240.0;
                    }
                    int bin = (int) (h * HUE_BINS / 360.0) % HUE_BINS;
                    if (bin < 0) bin += HUE_BINS;
                    hue[bin] += chroma;
                }
            }
        }
        if (bmp != src) bmp.recycle();
        if (kept < 40) return null;

        double peak = 0;
        for (double v : hue) peak = Math.max(peak, v);
        if (peak <= 0) peak = 1;

        int[] sig = new int[LEN];
        for (int i = 0; i < HUE_BINS; i++) {
            sig[i] = (int) Math.round(255.0 * hue[i] / peak);
        }
        for (int c = 0; c < GRID * GRID; c++) {
            int o = HUE_BINS + c * 3;
            if (cellN[c] > 0) {
                sig[o] = (int) (cellR[c] / cellN[c]);
                sig[o + 1] = (int) (cellG[c] / cellN[c]);
                sig[o + 2] = (int) (cellB[c] / cellN[c]);
            } else {
                sig[o] = sig[o + 1] = sig[o + 2] = 255;
            }
        }
        return sig;
    }

    /** 0 = identical, 1 = maximally different. */
    static double distance(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length) return 1.0;
        double h = 0;
        for (int i = 0; i < HUE_BINS; i++) h += Math.abs(a[i] - b[i]);
        h /= HUE_BINS * 255.0;
        double g = 0;
        for (int i = HUE_BINS; i < a.length; i++) g += Math.abs(a[i] - b[i]);
        g /= (a.length - HUE_BINS) * 255.0;
        return 0.6 * h + 0.4 * g;
    }
}
