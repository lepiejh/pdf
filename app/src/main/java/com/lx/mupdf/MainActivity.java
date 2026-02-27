package com.lx.mupdf;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.artifex.mupdflib.ui.AttachPreviewActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    public void openfile(View view) {
        AttachPreviewActivity.startActivity(
                this,
                "",
                "说明书",true
        );
    }
}