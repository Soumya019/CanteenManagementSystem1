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

/** Form for the shopkeeper to add a new item (name, price, unit, category,
 *  optional photo picked from the gallery). */
public class AddItemActivity extends Activity implements View.OnClickListener {

    static final String EXTRA_CATS = "cats";
    private static final int REQ_PICK = 41;

    private EditText nameBox, priceBox;
    private Spinner unitSpin, catSpin;
    private ImageView preview;
    private String photoName = "";

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

        Button pick = findViewById(R.id.add_pick_photo);
        Button save = findViewById(R.id.add_save);
        pick.setOnClickListener(this);
        save.setOnClickListener(this);
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
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.add_pick_photo)),
                    REQ_PICK);
        } else if (v.getId() == R.id.add_save) {
            saveItem();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            // copy into app storage, downscaled and re-encoded as JPEG
            InputStream in = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in);
            in.close();
            if (bmp == null) throw new Exception("cannot read image");
            int big = Math.max(bmp.getWidth(), bmp.getHeight());
            if (big > 1600) {
                float k = 1600f / big;
                bmp = Bitmap.createScaledBitmap(bmp,
                        Math.round(bmp.getWidth() * k),
                        Math.round(bmp.getHeight() * k), true);
            }
            String name = "u_" + System.currentTimeMillis() + ".jpg";
            FileOutputStream out = new FileOutputStream(
                    Photos.userFile(this, name));
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
            out.close();
            photoName = name;
            preview.setImageBitmap(bmp);
        } catch (Exception e) {
            Toast.makeText(this, "Photo failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveItem() {
        String name = nameBox.getText().toString().trim();
        String priceStr = priceBox.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.add_need_name, Toast.LENGTH_SHORT).show();
            return;
        }
        int price;
        try {
            price = Integer.parseInt(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.add_need_price, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis());
            o.put("name", name);
            o.put("price", price);
            o.put("unit", String.valueOf(unitSpin.getSelectedItem()));
            o.put("cat", String.valueOf(catSpin.getSelectedItem()));
            o.put("kw", name);
            o.put("photo", photoName);
            UserItems.add(this, o);
            Toast.makeText(this, R.string.add_saved, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
