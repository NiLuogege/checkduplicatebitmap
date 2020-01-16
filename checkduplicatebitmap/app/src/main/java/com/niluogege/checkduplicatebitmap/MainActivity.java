package com.niluogege.checkduplicatebitmap;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.view.View;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {


    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.a);


        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Debug.dumpHprofData(Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator+"a.hprof");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


    }
}
