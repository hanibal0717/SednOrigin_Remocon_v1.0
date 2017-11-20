package com.inucreative.sednremocon;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

/**
 * Splash Screen
 * ghlee 2017.11.17
 */
public class IntroActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        Handler hd = new Handler();
        hd.postDelayed(new SplashHandler(), 2000);
    }


    class SplashHandler implements Runnable {

        @Override
        public void run() {
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
            IntroActivity.this.finish();
        }
    }

}
