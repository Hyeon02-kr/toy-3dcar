package com.example.carproject;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

public class MainActivity extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useGL30 = false;   // OpenGL ES 2.0 — 저사양 에뮬레이터 호환
        config.numSamples = 0;    // MSAA 끄기 (저사양 최적화)
        config.useAccelerometer = false;
        config.useCompass = false;
        initialize(new CarParkingGame(), config);
    }
}
