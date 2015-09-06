/*
   Haptic Feedback Case - Applications 
   Copyright (C) 2015: Ben Kazemi, ben.kazemi@gmail.com
   Copyright 2011-2013 Google Inc.
   Copyright 2013 mike wakerly <opensource@hoho.com>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package com.example.orbital.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.hoho.android.usbserial.driver.UsbSerialPort;
public class DemoAppSelector extends Activity {
    /**
     * Driver instance, passed in statically via
     * {@link #show(Context, UsbSerialPort)}.
     */
    private static UsbSerialPort sPort = null;
    Button pdeBtn;
    Button cameraBtn;
    Button scrollerBtn;
    Button audioBtn;
    private static final String ACTION_USB_PERMISSION = "com.example.orbital.myapplication.action.USB_PERMISSION";
    private PendingIntent mPermissionIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.demo_app_selector_layout);

        pdeBtn = (Button) findViewById(R.id.pde_button); // find View-elements
        // create click listener
        OnClickListener pdeModeSelect = new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPortToDemoApps(sPort, 1);
            }
        };
        pdeBtn.setOnClickListener(pdeModeSelect);// assign click listener to the OK button (btnOK)

        scrollerBtn = (Button) findViewById(R.id.scroll_button); // find View-elements
        // create click listener
        OnClickListener scrollModeSelect = new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPortToDemoApps(sPort, 2);
            }
        };
        scrollerBtn.setOnClickListener(scrollModeSelect);

        cameraBtn = (Button) findViewById(R.id.cameraButton); // find View-elements
        // create click listener
        OnClickListener cameraModeSelect = new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPortToDemoApps(sPort, 3);
            }
        };
        cameraBtn.setOnClickListener(cameraModeSelect);

        audioBtn = (Button) findViewById(R.id.audioButton); // find View-elements
        // create click listener
        OnClickListener audioModeSelect = new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPortToDemoApps(sPort, 4);
            }
        };
        audioBtn.setOnClickListener(audioModeSelect);


        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (!usbManager.hasPermission(sPort.getDriver().getDevice())) {
            usbManager.requestPermission(sPort.getDriver().getDevice(),mPermissionIntent);
        }
    }

    //source https://stackoverflow.com/questions/17719634/how-to-exit-an-android-app-using-code
    //http://pulse7.net/android/show-alert-dialog-box-android/
    @Override
    public void onBackPressed() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Exit Application?");
        alertDialogBuilder
                .setMessage("Click yes to exit!")
                .setCancelable(false)
                .setPositiveButton("Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                moveTaskToBack(true);
                                android.os.Process.killProcess(android.os.Process.myPid());
                                System.exit(1);
                            }
                        })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    /*
    opens desired demo app

    add other demo apps here with the right id number passed
     */
    private void sendPortToDemoApps(UsbSerialPort port, int id) {
        switch (id) {
            case 1: PressureDistroActivity.show(this, port); break;
            case 2: ScrollerActivity.show(this, port); break;
            case 3: CameraActivity.show(this, port); break;
            case 4: AudioActivity.show(this, port); break;
        }
    }

    //http://javatechig.com/android/pass-a-data-from-one-activity-to-another-in-android
    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param port
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, DemoAppSelector.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
