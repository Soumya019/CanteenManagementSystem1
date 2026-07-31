package com.gariaelectric.pricelookup;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/** Form for the shopkeeper to add a new item (name, price, unit, category,
 *  optional photo picked from the gallery). */
public class AddItemActivity extends Activity implements View.OnClickListener {

    static final String EXTRA_CATS = "cats";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_PRICE = "price";
    static final String EXTRA_PRICE_BASIS = "priceBasis";
    static final String EXTRA_PHOTO_FILE = "photoFile";
    private static final int REQ_PICK = 41;

    private EditText nameBox, priceBox;
    private Spinner unitSpin, catSpin;
    private ImageView preview;
    private Button findBtn, saveBtn;
    private final java.util.List<String> photoNames = new java.util.ArrayList<>();
    private boolean saved = false;
    private boolean busy = false;
    private List<String> webCandidates;
    private int webIndex = 0;
    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        nameBox = findViewById(R.id.add_name);
        priceBox = findViewById(R.id.add_price);
        unitSpin = findViewById(R.id.add_unit);
        catSpin = findViewById(R.id.add_cat);
        preview = findViewById(R.id.add_photo_preview);

        String[] units = {"per piece", "per roll", "per packet",
                "per coil", "per meter", "per set"};
        unitSpin.setAdapter(simpleAdapter(units));

        String[] cats = getIntent().getStringArrayExtra(EXTRA_CATS);
        if (cats == null || cats.length == 0) {
            cats = new String[]{getString(R.string.my_items_cat)};
        }
        catSpin.setAdapter(simpleAdapter(cats));

        // pre-fill from a photographed carton, when we came from the camera
        String presetName = getIntent().getStringExtra(EXTRA_NAME);
        if (presetName != null && !presetName.isEmpty()) {
            nameBox.setText(presetName);
            nameBox.setSelection(nameBox.getText().length());
        }
        int presetPrice = getIntent().getIntExtra(EXTRA_PRICE, -1);
        String basis = getIntent().getStringExtra(EXTRA_PRICE_BASIS);
        if (presetPrice > 0) {
            priceBox.setText(String.valueOf(presetPrice));
            Toast.makeText(this, getString(R.string.price_from_label,
                    presetPrice, basis == null ? "" : basis),
                    Toast.LENGTH_LONG).show();
        } else if (presetName != null) {
            // nothing printed on the label, so ask outright
            priceBox.requestFocus();
            Toast.makeText(this, R.string.price_ask, Toast.LENGTH_LONG).show();
        }
        String shot = getIntent().getStringExtra(EXTRA_PHOTO_FILE);
        if (shot != null) {
            android.graphics.Bitmap b =
                    android.graphics.BitmapFactory.decodeFile(shot);
            String stored = PhotoImport.store(this, b);
            if (stored != null) {
                photoNames.add(stored);
                preview.setImageBitmap(Photos.decode(this, stored, 640));
            }
        }

        Button pick = findViewById(R.id.add_pick_photo);
        findBtn = findViewById(R.id.add_find_photo);
        saveBtn = findViewById(R.id.add_save);
        pick.setOnClickListener(this);
        findBtn.setOnClickListener(this);
        saveBtn.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // don't leave an orphan photo file if the form was abandoned
        if (!saved) {
            for (String n : photoNames) {
                Photos.dropThumb(n);
                Photos.userFile(this, n).delete();
            }
        }
    }

    private void replacePhoto(String newName) {
        if (newName != null) photoNames.add(newName);
    }

    private ArrayAdapter<String> simpleAdapter(String[] values) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.add_pick_photo) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.add_pick_photo)),
                    REQ_PICK);
        } else if (v.getId() == R.id.add_find_photo) {
            findPhotoOnline();
        } else if (v.getId() == R.id.add_save) {
            saveItem();
        }
    }

    /** Search the web for the typed item name; each tap shows the next
     *  candidate photo. */
    private void findPhotoOnline() {
        final String name = nameBox.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.need_name_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (busy) return;
        busy = true;
        findBtn.setEnabled(false);
        findBtn.setText(R.string.searching_photo);
        new Thread(new Runnable() {
            @Override public void run() {
                if (webCandidates == null || !name.equals(lastQuery)) {
                    webCandidates = PhotoSearch.search(name + " electrical");
                    webIndex = 0;
                    lastQuery = name;
                }
                String got = null;
                while (webIndex < webCandidates.size() && got == null) {
                    got = PhotoSearch.download(AddItemActivity.this,
                            webCandidates.get(webIndex));
                    webIndex++;
                }
                if (got == null) webCandidates = null;  // allow re-search
                final String photo = got;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        findBtn.setEnabled(true);
                        findBtn.setText(R.string.find_photo);
                        if (photo != null) {
                            replacePhoto(photo);
                            preview.setImageBitmap(
                                    Photos.decode(AddItemActivity.this, photo, 640));
                            Toast.makeText(AddItemActivity.this,
                                    R.string.photo_found, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AddItemActivity.this,
                                    R.string.photo_not_found, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        java.util.List<String> got = PhotoImport.importAll(this, data);
        if (got.isEmpty()) {
            Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_LONG).show();
            return;
        }
        photoNames.addAll(got);
        preview.setImageBitmap(Photos.decode(this,
                photoNames.get(photoNames.size() - 1), 640));
        Toast.makeText(this, getString(R.string.photos_added,
                photoNames.size()), Toast.LENGTH_SHORT).show();
    }

    private void saveItem() {
        final String name = nameBox.getText().toString().trim();
        String priceStr = priceBox.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.add_need_name, Toast.LENGTH_SHORT).show();
            return;
        }
        final int price;
        try {
            price = Integer.parseInt(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.add_need_price, Toast.LENGTH_SHORT).show();
            return;
        }
        if (photoNames.isEmpty()) {
            // no photo chosen: search the web for one, then save
            if (busy) return;
            busy = true;
            saveBtn.setEnabled(false);
            saveBtn.setText(R.string.searching_photo);
            new Thread(new Runnable() {
                @Override public void run() {
                    List<String> cands = PhotoSearch.search(name + " electrical");
                    String got = null;
                    for (int i = 0; i < cands.size() && i < 4 && got == null; i++) {
                        got = PhotoSearch.download(AddItemActivity.this, cands.get(i));
                    }
                    final String photo = got;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            saveBtn.setEnabled(true);
                            saveBtn.setText(R.string.add_save);
                            if (photo != null) {
                                photoNames.add(photo);
                            } else {
                                Toast.makeText(AddItemActivity.this,
                                        R.string.photo_auto_missing,
                                        Toast.LENGTH_LONG).show();
                            }
                            doSave(name, price);
                        }
                    });
                }
            }).start();
            return;
        }
        doSave(name, price);
    }

    private void doSave(String name, int price) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis());
            o.put("name", name);
            o.put("price", price);
            o.put("unit", String.valueOf(unitSpin.getSelectedItem()));
            o.put("cat", String.valueOf(catSpin.getSelectedItem()));
            o.put("kw", name);
            o.put("photo", photoNames.isEmpty() ? "" : photoNames.get(0));
            org.json.JSONArray pa = new org.json.JSONArray();
            for (String n : photoNames) pa.put(n);
            o.put("photos", pa);
            UserItems.add(this, o);
            saved = true;
            Toast.makeText(this, R.string.add_saved, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
