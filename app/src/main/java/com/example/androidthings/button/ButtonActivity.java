/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.button;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

/**
 * Example of using Button driver for toggling a LED.
 *
 * This activity initialize an InputDriver to emit key events when the button GPIO pin state change
 * and flip the state of the LED GPIO pin.
 *
 * You need to connect an LED and a push button switch to pins specified in {@link BoardDefaults}
 * according to the schematic provided in the sample README.
 */
public class ButtonActivity extends Activity {
    private static final String TAG = ButtonActivity.class.getSimpleName();

    private Gpio mLedGpio;
    private ButtonInputDriver mButtonInputDriver;
    private PubsubPublisher mPubsubPublisher;
    private boolean mLock = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting ButtonActivity");
        setContentView(R.layout.activity_main);

        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            Log.i(TAG, "Configuring GPIO pins");
            mLedGpio = pioService.openGpio(BoardDefaults.getGPIOForLED());
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            Log.i(TAG, "Registering button driver");
            // Initialize and register the InputDriver that will emit SPACE key events
            // on GPIO state changes.
            mButtonInputDriver = new ButtonInputDriver(
                    BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_SPACE);
            mButtonInputDriver.register();
        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }

        // start Cloud PubSub Publisher if cloud credentials are present.
        int credentialId = getResources().getIdentifier("credentials", "raw", getPackageName());
        if (credentialId != 0) {
            try {
                mPubsubPublisher = new PubsubPublisher(this, "sample-button",
                        BuildConfig.PROJECT_ID, BuildConfig.PUBSUB_TOPIC, credentialId);
            } catch (IOException e) {
                Log.e(TAG, "error creating pubsub publisher", e);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Turn on the LED
            setLedValue(true);
            sendrequest();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Turn off the LED
            setLedValue(false);
            mLock = false;
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    /**
     * Update the value of the LED output.
     */
    private void setLedValue(boolean value) {
        try {
            mLedGpio.setValue(value);
        } catch (IOException e) {
            Log.e(TAG, "Error updating GPIO value", e);
        }
    }

    /**
     * リクエスト送信しちゃうぞ
     */
    private void sendrequest() {
        // onKeyDownの間、ずっと通信されない用の対応
        if (!mLock) {
            mLock = true;
            Toast.makeText(this, "Press attendance contact button!!", Toast.LENGTH_LONG).show();
            if (mPubsubPublisher != null) {
                mPubsubPublisher.start();
            }
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        if (mButtonInputDriver != null) {
            mButtonInputDriver.unregister();
            try {
                mButtonInputDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing Button driver", e);
            } finally{
                mButtonInputDriver = null;
            }
        }

        if (mLedGpio != null) {
            try {
                mLedGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing LED GPIO", e);
            } finally{
                mLedGpio = null;
            }
            mLedGpio = null;
        }

        if (mPubsubPublisher != null) {
            mPubsubPublisher.close();
            mPubsubPublisher = null;
        }
    }
}
