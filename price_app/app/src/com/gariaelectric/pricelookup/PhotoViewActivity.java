package com.gariaelectric.pricelookup;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/** Full-screen photo viewer: the image fills the screen at its natural
 *  aspect ratio. When an item carries several photos, ◀ ▶ step through
 *  them; tapping the image closes the viewer. */
public class PhotoViewActivity extends Activity implements View.OnClickListener {

    static final String EXTRA_PHOTOS = "photos";
    static final String EXTRA_CAPTION = "caption";

    private String[] photos;
    private int index;
    private ImageView img;
    private TextView caption;
    private String baseCaption = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);

        img = findViewById(R.id.full_photo);
        caption = findViewById(R.id.photo_caption);
        Button prev = findViewById(R.id.photo_prev);
        Button next = findViewById(R.id.photo_next);

        photos = getIntent().getStringArrayExtra(EXTRA_PHOTOS);
        String cap = getIntent().getStringExtra(EXTRA_CAPTION);
        baseCaption = cap == null ? "" : cap;

        if (photos == null || photos.length == 0) { finish(); return; }

        if (photos.length > 1) {
            prev.setVisibility(View.VISIBLE);
            next.setVisibility(View.VISIBLE);
            prev.setOnClickListener(this);
            next.setOnClickListener(this);
        }
        img.setOnClickListener(this);
        show(0);
    }

    private void show(int i) {
        index = (i + photos.length) % photos.length;
        Bitmap bmp = Photos.decode(this, photos[index], 2048);
        if (bmp == null) { finish(); return; }
        img.setImageBitmap(bmp);
        caption.setText(photos.length > 1
                ? baseCaption + "   (" + (index + 1) + "/" + photos.length + ")"
                : baseCaption);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.photo_prev) show(index - 1);
        else if (id == R.id.photo_next) show(index + 1);
        else finish();
    }
}
