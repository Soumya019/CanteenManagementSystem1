package com.gariaelectric.pricelookup;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the printed words off a photographed carton (brand, way-count,
 * wattage, amperage) using ML Kit's bundled Latin recogniser — fully
 * on-device, so the counter needs no internet.
 *
 * The recognised words are handed to the app's normal search, which already
 * understands shop vocabulary, so "AJONTA 6 NANO" or "KAMLA BED SWITCH"
 * lands on the right rows without any extra matching logic.
 */
final class Recognizer {

    interface Callback {
        /** words: recognised text (may be empty). */
        void onResult(List<String> words);
    }

    private Recognizer() {}

    static void readText(Bitmap bitmap, final Callback cb) {
        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(new com.google.android.gms.tasks
                        .OnSuccessListener<Text>() {
                    @Override public void onSuccess(Text text) {
                        cb.onResult(collect(text));
                    }
                })
                .addOnFailureListener(new com.google.android.gms.tasks
                        .OnFailureListener() {
                    @Override public void onFailure(Exception e) {
                        cb.onResult(new ArrayList<String>());
                    }
                });
    }

    /** Larger blocks are more likely to be the brand, so keep reading order
     *  but drop noise: single characters and pure punctuation. */
    private static List<String> collect(Text text) {
        List<String> out = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (String w : line.getText().split("[^A-Za-z0-9./]+")) {
                    String s = w.trim();
                    if (s.length() < 2) continue;
                    if (!s.matches(".*[A-Za-z0-9].*")) continue;
                    out.add(s);
                }
            }
        }
        return out;
    }
}
