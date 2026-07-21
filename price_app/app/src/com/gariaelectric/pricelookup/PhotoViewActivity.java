package com.gariaelectric.pricelookup;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** Full-screen photo viewer: image scaled to fill the screen while keeping
 *  its aspect ratio; tap anywhere to close. */
public class PhotoViewActivity extends Activity implements View.OnClickListener {

    static final String EXTRA_PHOTO = "photo";
    static final String EXTRA_CAPTION = "caption";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);

        ImageView img = findViewById(R.id.full_photo);
        TextView caption = findViewById(R.id.photo_caption);

        String ref = getIntent().getStringExtra(EXTRA_PHOTO);
        String cap = getIntent().getStringExtra(EXTRA_CAPTION);
        caption.setText(cap == null ? "" : cap);

        Bitmap bmp = Photos.decode(this, ref, 2048);
        if (bmp != null) {
            img.setImageBitmap(bmp);
        } else {
            finish();
            return;
        }
        findViewById(R.id.photo_root).setOnClickListener(this);
        img.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        finish();
    }
}
