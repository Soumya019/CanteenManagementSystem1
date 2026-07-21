package com.gariaelectric.pricelookup;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity
        implements TextWatcher, View.OnClickListener {

    private static final int REQ_SPEECH = 71;

    private static class Item {
        final String name, unit, cat, search;
        final int price;
        Item(String name, int price, String unit, String cat, String kw) {
            this.name = name;
            this.price = price;
            this.unit = unit;
            this.cat = cat;
            this.search = normalize(name + " " + kw + " " + cat);
        }
    }

    private static class Hit {
        final Item item;
        final int score;
        Hit(Item item, int score) { this.item = item; this.score = score; }
    }

    private final List<Item> items = new ArrayList<>();
    private final List<Item> shown = new ArrayList<>();
    private ResultAdapter adapter;
    private EditText searchBox;
    private TextView status;
    private boolean updatingFromSpeech = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadItems();

        searchBox = findViewById(R.id.search_box);
        status = findViewById(R.id.status_text);
        ListView list = findViewById(R.id.results_list);
        Button mic = findViewById(R.id.mic_button);

        adapter = new ResultAdapter(this);
        list.setAdapter(adapter);

        searchBox.addTextChangedListener(this);
        mic.setOnClickListener(this);
    }

    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (!updatingFromSpeech) runSearch(s.toString());
    }

    @Override
    public void onClick(View v) {
        startSpeech();
    }

    private void loadItems() {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    getAssets().open("items.json"), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONArray arr = new JSONObject(sb.toString()).getJSONArray("items");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                items.add(new Item(o.getString("name"), o.getInt("price"),
                        o.getString("unit"), o.getString("cat"),
                        o.getString("kw")));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load price list: " + e,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            status.setText(R.string.status_listening);
            startActivityForResult(intent, REQ_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.speech_unavailable,
                    Toast.LENGTH_LONG).show();
            status.setText(R.string.status_idle);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SPEECH) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText(R.string.status_idle);
            return;
        }
        List<String> alternatives =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (alternatives == null || alternatives.isEmpty()) {
            status.setText(R.string.status_idle);
            return;
        }
        // use the first spoken alternative that matches something
        String best = alternatives.get(0);
        for (String alt : alternatives) {
            if (!search(alt).isEmpty()) { best = alt; break; }
        }
        updatingFromSpeech = true;
        searchBox.setText(best);
        searchBox.setSelection(best.length());
        updatingFromSpeech = false;
        runSearch(best);
    }

    private void runSearch(String query) {
        shown.clear();
        String q = query.trim();
        if (q.isEmpty()) {
            status.setText(R.string.status_idle);
            adapter.notifyDataSetChanged();
            return;
        }
        List<Hit> hits = search(q);
        for (Hit h : hits) shown.add(h.item);
        if (shown.isEmpty()) {
            status.setText(getString(R.string.status_no_match, q));
        } else {
            status.setText(getString(R.string.status_results, shown.size(), q));
        }
        adapter.notifyDataSetChanged();
    }

    private List<Hit> search(String query) {
        String q = normalize(query);
        List<Hit> hits = new ArrayList<>();
        if (q.isEmpty()) return hits;
        String[] tokens = q.split(" ");
        for (Item it : items) {
            int score = 0, matched = 0;
            for (String t : tokens) {
                if (t.isEmpty()) continue;
                if (containsWord(it.search, t)) { score += 3; matched++; }
                else if (it.search.contains(t)) { score += 2; matched++; }
                else if (t.length() >= 3 && prefixMatch(it.search, t)) {
                    score += 1; matched++;
                }
            }
            if (matched == 0) continue;
            // full-phrase bonus, and prefer items matching every spoken word
            if (it.search.contains(q)) score += 4;
            if (matched == tokens.length) score += 2;
            hits.add(new Hit(it, score));
        }
        Collections.sort(hits, new HitComparator());
        // drop weak tail matches when strong ones exist
        if (!hits.isEmpty()) {
            int top = hits.get(0).score;
            List<Hit> keep = new ArrayList<>();
            for (Hit h : hits) {
                if (h.score >= Math.max(3, top / 2)) keep.add(h);
                if (keep.size() >= 40) break;
            }
            return keep;
        }
        return hits;
    }

    private static class HitComparator implements Comparator<Hit> {
        @Override public int compare(Hit a, Hit b) {
            if (a.score != b.score) return b.score - a.score;
            return a.item.name.length() - b.item.name.length();
        }
    }

    private static boolean containsWord(String text, String word) {
        int i = text.indexOf(word);
        while (i >= 0) {
            boolean startOk = i == 0 || text.charAt(i - 1) == ' ';
            int end = i + word.length();
            boolean endOk = end == text.length() || text.charAt(end) == ' ';
            if (startOk && endOk) return true;
            i = text.indexOf(word, i + 1);
        }
        return false;
    }

    private static boolean prefixMatch(String text, String token) {
        for (String w : text.split(" ")) {
            if (w.startsWith(token)) return true;
        }
        return false;
    }

    /** lowercase, unify watt/amp/mm phrasing, strip punctuation */
    static String normalize(String s) {
        s = s.toLowerCase(Locale.ROOT);
        s = s.replace("—", " ").replace("-", " ").replace("/", " ");
        s = s.replaceAll("(\\d+(?:\\.\\d+)?)\\s*(watts|watt|w)\\b", "$1w");
        s = s.replaceAll("(\\d+(?:\\.\\d+)?)\\s*(amps|amp|ampere|a)\\b", "$1a");
        s = s.replaceAll("(\\d+(?:\\.\\d+)?)\\s*(sq\\.?\\s*mm|sqmm)", "$1sqmm");
        s = s.replaceAll("(\\d+(?:\\.\\d+)?)\\s*(mm|millimeter|millimetre)\\b", "$1mm");
        s = s.replaceAll("[^a-z0-9. ]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private class ResultAdapter extends ArrayAdapter<Item> {
        private final LayoutInflater inflater;

        ResultAdapter(Context ctx) {
            super(ctx, 0, shown);
            inflater = LayoutInflater.from(ctx);
        }

        @Override public int getCount() { return shown.size(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : inflater.inflate(R.layout.row_item, parent, false);
            Item it = shown.get(position);
            ((TextView) v.findViewById(R.id.item_name)).setText(it.name);
            ((TextView) v.findViewById(R.id.item_sub)).setText(it.cat);
            ((TextView) v.findViewById(R.id.item_price)).setText(
                    String.format(Locale.ROOT, "₹ %d", it.price));
            ((TextView) v.findViewById(R.id.item_unit)).setText(it.unit);
            return v;
        }
    }
}
