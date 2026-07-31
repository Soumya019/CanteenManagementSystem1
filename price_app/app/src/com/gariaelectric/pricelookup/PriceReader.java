package com.gariaelectric.pricelookup;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out what to charge for one piece from the text on a carton.
 *
 * Three cases, in order of trust:
 *   1. a pencilled counter rate, "@ 03/=" — already a per-piece selling rate,
 *      so it is used as-is;
 *   2. a printed price plus a pack count ("Price Rs 450", "Quantity 100 Pics")
 *      — divided out, then the shop's usual 10% off MRP;
 *   3. a printed price with no pack count — treated as per piece, less 10%.
 * Returns found == false when the label shows no price at all, and the app
 * then asks the shopkeeper what to charge.
 */
final class PriceReader {

    /** Pencilled per-piece rate: "@ 03/=", "@3/-", "@ 12 /=". */
    private static final Pattern HAND = Pattern.compile(
            "@\\s*0*(\\d{1,4})\\s*/\\s*[=\\-]");

    /** Printed price: "M.R.P. Rs. : 650/-", "Price Rs 450", "MRP 20.00",
     *  and the per-piece form "M.R.P. Per Pc. : 20.00". */
    private static final Pattern PRINTED = Pattern.compile(
            "(?:M\\.?\\s?R\\.?\\s?P\\.?|PRICE|RATE|RS\\.?|INR)"
                    + "(?:\\s*PER\\s*(?:PCS|PC|PIECE)\\.?)?"
                    + "(?:\\s*(?:RS|INR)\\.?)?"
                    + "[^0-9A-Z]{0,12}(\\d{1,6}(?:\\.\\d{1,2})?)");

    /** Any "450/-" style figure, used only as a last resort. */
    private static final Pattern SLASHED = Pattern.compile(
            "(\\d{1,6}(?:\\.\\d{1,2})?)\\s*/\\s*[=\\-]");

    /** Pack size: "Quantity : 100 Pics", "20pcs", "10 PCS". */
    private static final Pattern QTY = Pattern.compile(
            "(?:QUANTITY[^0-9]{0,8})?(\\d{1,4})\\s*(?:PCS|PICS|PIECES|NOS|PC)\\b");

    final boolean found;
    final int perPiece;
    final String basis;

    private PriceReader(boolean found, int perPiece, String basis) {
        this.found = found;
        this.perPiece = perPiece;
        this.basis = basis;
    }

    static PriceReader none() {
        return new PriceReader(false, 0, "");
    }

    static PriceReader parse(String rawText) {
        if (rawText == null || rawText.isEmpty()) return none();
        String t = rawText.toUpperCase(Locale.ROOT).replace('\n', ' ');

        Matcher m = HAND.matcher(t);
        if (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (v > 0 && v < 100000) {
                return new PriceReader(true, v,
                        "counter rate @" + v + "/= written on the box");
            }
        }

        double price = -1;
        m = PRINTED.matcher(t);
        while (m.find()) {
            double v = Double.parseDouble(m.group(1));
            // skip standards numbers that follow "ISO"/years
            if (v >= 1 && v <= 100000) { price = v; break; }
        }
        if (price < 0) {
            m = SLASHED.matcher(t);
            if (m.find()) price = Double.parseDouble(m.group(1));
        }
        if (price < 0) return none();

        int qty = 1;
        m = QTY.matcher(t);
        while (m.find()) {
            int q = Integer.parseInt(m.group(1));
            if (q > 1 && q <= 1000) { qty = q; break; }
        }

        double each = price / qty;
        int sell = round(each * 0.9);
        if (sell < 1) sell = 1;
        String basis = qty > 1
                ? "MRP ₹" + trim(price) + " ÷ " + qty + " pcs, less 10%"
                : "MRP ₹" + trim(price) + ", less 10%";
        return new PriceReader(true, sell, basis);
    }

    /** Whole rupees, and convenient 5-rupee steps once past 100. */
    private static int round(double v) {
        if (v >= 100) return (int) (Math.round(v / 5.0) * 5);
        return (int) Math.round(v);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
