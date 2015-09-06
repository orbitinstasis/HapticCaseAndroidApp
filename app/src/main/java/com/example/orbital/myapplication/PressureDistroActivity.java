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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.widget.LinearLayout;


import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PressureDistroActivity extends Activity {
    /*
    CONSTANTS
     */
    private static final int MAX_STRIP_CELLS = 2;
    private static final int STRIP_PRESSURE = 0;
    private static final int STRIP_POSITION = 1;
    private static final int END_MARKER_XYZ = 0xFF;
    private static final int ROWS = 10;
    private static final int COLS = 16;
    private static final int RECT_SIZE = 90;
    private static final int TOP_STRIP_FORCE = 175;
    private static final int MAX_RECEIVE_VALUE = 254;
    private static final int TOP_PAD_FORCE = 130;

    /*
    GLOBALS
     */
    private enum retrieveState { 
        IN_STRIP_1, 
        IN_STRIP_2,
        IN_STRIP_3,
        IN_STRIP_4,
        IN_XYZ,
        READY      // ready to save UART data to new sensor set -- check for markers
        ;
    }
    private retrieveState retriever = retrieveState.READY;
    private int resumeSensor = 0; //this will hold the state for whatever thing you were in the middle of reading of when buffer.size was read, so you resume at this point    // in the drawThread just update the 2D array by doibng for [readModel/(ROWS-2)][readModel % COLS]  or something like this
    private int stripSensor = 0;
    private int xCount = 0;
    private int yCount = 0;
    private boolean gotZero = false;
    private int[][] padCell = new int[ROWS][COLS];
    private int[][] oldPadCell = new int[ROWS][COLS];
    private Thread drawThread;
    private Handler drawHandler = new Handler();
    private Paint blank = new Paint();
    private final String TAG = PressureDistroActivity.class.getSimpleName();
    private static UsbSerialPort sPort = null; //Driver instance, passed in statically via {@link #show(Context, UsbSerialPort)}.
    private Paint stripPaint = new Paint();
    private Bitmap bg = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888);
    private Canvas canvas = new Canvas(bg);
    private int oldStripForce[] = {0,0,0,0};
    private int stripModel[][] = {{0,0},{0,0},{0,0},{0,0}}; //for each sensor, you have 1st: pressure, 2nd: position
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;
   // private Paint greyed = new Paint();
    private Paint padPaint = new Paint();
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {
                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                    drawHandler.removeCallbacks(drawThread);
                    stopIoManager();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
                @Override
                public void onNewData(final byte[] data) {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
                    PressureDistroActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PressureDistroActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
        for (int i = 0; i < ROWS; i++) { //initialising the oldPadForce Array
            for (int j = 0; j < COLS; j++) {
                padCell[i][j] = 0;
                oldPadCell[i][j] = 0;
            }

        }
        setContentView(R.layout.pressure_distro_layout);
        canvas.drawColor(Color.WHITE);
        blank.setColor(Color.WHITE);
        stripPaint.setColor(Color.parseColor("#d35400")); // pumpkin orange
        padPaint.setColor(Color.parseColor("#c0392b"));
//        greyed.setColor(Color.parseColor("#ecf0f1")); //silver #95a5a6
//        canvas.drawRect(90,0,990,240,greyed);
//        canvas.drawRect(90,1680,990,1920,greyed);
        drawStripsThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        drawHandler.removeCallbacks(drawThread);
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            programError();
        } else {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                Log.d(TAG, "Opening device failed");
                programError();
                //return;
            }
            try {
                sPort.open(connection);
                sPort.setParameters(230400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                drawHandler.post(drawThread);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                programError();
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            };
        }
        onDeviceStateChange();
    }

    private void programError(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Opening device failed");
        alertDialogBuilder
                .setMessage("Application will quit now")
                .setCancelable(false)
                .setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                moveTaskToBack(true);
                                android.os.Process.killProcess(android.os.Process.myPid());
                                System.exit(1);
                            }
                        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void drawStripsThread() {
        drawThread = new Thread(){
            //ArgbEvaluator interpolatedColor =  new ArgbEvaluator();
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
                for (int drawSensor = 0; drawSensor < 4; drawSensor++) { // for each side strip sensor
                    int force = stripModel[drawSensor][STRIP_PRESSURE];
                    if (force != oldStripForce[drawSensor]) { // if we're only dealing with changing cells
                        int tempX = 0;
                        int wipeLeft = 0;
                        int wipeTop = 0;
                        int wipeRight = 0;
                        int wipeBottom = 0;
                        int position = (int)map(stripModel[drawSensor][STRIP_POSITION], 0, 254, 45, 915);
                        switch (drawSensor) {
                            case 0: // right top
                                tempX = 1035;
                                wipeLeft = 990;
                                wipeTop = 0;
                                wipeRight = 1080;
                                wipeBottom = 960;
                                break;
                            case 1: // right bottom
                                tempX = 1035;
                                wipeLeft = 990;
                                wipeTop = 960;
                                wipeRight = 1080;
                                wipeBottom = 1920;
                                break;
                            case 2: // left bottom
                                tempX = 45;
                                wipeLeft = 0;
                                wipeTop = 960;
                                wipeRight = 90;
                                wipeBottom = 1920;
                                break;
                            case 3: // left top
                                tempX = 45;
                                wipeLeft = 0;
                                wipeTop = 0;
                                wipeRight = 90;
                                wipeBottom = 960;
                                break;

                        }
                        stripPaint.setAlpha((int) (map(force, 0, TOP_STRIP_FORCE, 10, 255)));
                        oldStripForce[drawSensor] = force;
                        canvas.drawRect(wipeLeft,wipeTop,wipeRight,wipeBottom,blank);
                        if (force > 0) {
                            canvas.drawCircle(tempX, wipeTop+position, (int)(map(force, 0, TOP_STRIP_FORCE, 18, 45)), stripPaint);
                        }
                    }
                }
                // do XYZ here
                for (int i = 0; i<ROWS; i++) {
                    for (int j = 0; j < COLS; j++) {
                        if (padCell[i][j] != oldPadCell[i][j]) {
                            padPaint.setAlpha((int) map(padCell[i][j], 0, TOP_PAD_FORCE, 10, 255));
                            oldPadCell[i][j] = padCell[i][j];
                            canvas.drawRect(RECT_SIZE + (i * RECT_SIZE), 240 + (j * RECT_SIZE), RECT_SIZE + RECT_SIZE + (i * RECT_SIZE), RECT_SIZE + 240 + (j * RECT_SIZE), blank);
                            if (padCell[i][j] > 0) {
                                canvas.drawRect(RECT_SIZE + (i * RECT_SIZE), 240 + (j * RECT_SIZE), RECT_SIZE + RECT_SIZE + (i * RECT_SIZE), RECT_SIZE + 240 + (j * RECT_SIZE), padPaint);
                            }
                        }
                    }
                }
                LinearLayout ll = (LinearLayout) findViewById(R.id.rect);
                ll.setBackground(new BitmapDrawable(getResources(), bg));
                drawHandler.post(this);
            }
        };
    }

    private long map(long x, long in_min, long in_max, long out_min, long out_max)
    {
        if (x > in_max) x = in_max;
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        drawHandler.removeCallbacks(drawThread);
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        drawHandler.removeCallbacks(drawThread);
        stopIoManager();
        finish();
    }
    /*
    side strips return 255 max for force appluied to the top side of the strip relative to the boards orientation
        therefore, strips on the right hand side had to have their positions flipped.

        can read strip sensors force and if it's 0 then auto assign the posiotion a 0 and i++
     */
    private void updateReceivedData(byte[] data) {
        int i = 0;
        boolean isFirstStateChange = false;
        if (i < data.length) {
            while (retriever == retrieveState.READY) {   //this READY bit is only called at teh very beginnig of program execution
                if (i >= data.length)        //finished checking buffer and nothing found then exit method
                    return;
                if (isMarker(data[i])) {//found what we want, change state to first sensor, set bool, break whil;e before incrementing to continue code after while
                    sensorSelector();
                    isFirstStateChange = true;
                }
                i++;
            }
            if (i < data.length) {
                if (isMarker(data[i])) {
                    if (!isFirstStateChange) {
                        sensorSelector();
                    }
                    resumeSensor = 0;
                    i++; //jump over marker position
                }
                for (int j = i; j < data.length; j++) {
                    int tempVal = 0;
                    if (!isMarker(data[j]) && inStripReading() && (resumeSensor < MAX_STRIP_CELLS)) { // if you're reading force+position of strips
                        if ((retriever == retrieveState.IN_STRIP_1 || retriever == retrieveState.IN_STRIP_2) && resumeSensor == STRIP_POSITION)
                            tempVal = MAX_RECEIVE_VALUE - ((int) data[j] & END_MARKER_XYZ);  // read comment above sig for explanation
                        else tempVal = ((int) data[j] & END_MARKER_XYZ);
                        stripModel[stripSensor][resumeSensor] = tempVal;
                        resumeSensor++;
                        if (retriever == retrieveState.IN_STRIP_4 && resumeSensor > STRIP_POSITION) { //entering XYZ
                            sensorSelector();
                            resumeSensor = 0;
                            xCount = 0;
                            yCount = 0;
                        }
                    }
                    else { //data[i] is marker or first strip reading or in XYZ
                        if (retriever != retrieveState.IN_XYZ || isMarker(data[j])) {
                            sensorSelector();
                            resumeSensor = 0;
                        }
                        if (!isMarker(data[j]) && inStripReading() ) { // if you've entered a new strip
                            if ((retriever == retrieveState.IN_STRIP_1 || retriever == retrieveState.IN_STRIP_2)&& resumeSensor == STRIP_POSITION)
                                tempVal = MAX_RECEIVE_VALUE - ((int) data[j] & END_MARKER_XYZ);  // read comment above sig for explanation
                            else
                                tempVal = ((int) data[j] & END_MARKER_XYZ);
                            stripModel[stripSensor][resumeSensor] = tempVal;
                            resumeSensor++;
                        }
                        else if (!isMarker(data[j]) && retriever == retrieveState.IN_XYZ) {
                            //do XYZ stuff here
                            if (gotZero) { // if you're printing out arrays of zeroes (optimisation on firmware)
                                for (int z = 0; z < ((int) data[j] & END_MARKER_XYZ); z++) {
                                    padCell[xCount][yCount] = 0;
                                    yCount++;
                                    if (yCount >= COLS) {
                                        yCount = 0;
                                        xCount++;
                                        if (xCount >= ROWS) {
                                            xCount = ROWS - 1;
                                        }
                                    }
                                }
                                gotZero = false;
                            }
                            else if (((int) data[j] & END_MARKER_XYZ) == 0) {
                                gotZero = true;
                            }
                            else {
                                padCell[xCount][yCount] = ((int) data[j] & END_MARKER_XYZ);
                                yCount++;
                                if (yCount >= COLS) {
                                    yCount = 0;
                                    xCount++;
                                    if (xCount >= ROWS) {
                                        xCount = ROWS - 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isMarker(byte in) {
        if (((int)in & END_MARKER_XYZ) == END_MARKER_XYZ) {//found what we want, change state to first sensor, set bool, break whil;e before incrementing to continue code after while
            return true;
        }   else return false;
    }

    private boolean inStripReading() {
        if (retriever == retrieveState.IN_STRIP_1 || retriever == retrieveState.IN_STRIP_2 || retriever == retrieveState.IN_STRIP_3 || retriever == retrieveState.IN_STRIP_4) {
            return true;
        }
        else return false;
    }

    private void sensorSelector() {
        if (retriever == retrieveState.READY) {retriever = retrieveState.IN_STRIP_1; stripSensor = 0;}
        else if (retriever == retrieveState.IN_STRIP_1) {retriever = retrieveState.IN_STRIP_2; stripSensor = 1;}
        else if (retriever == retrieveState.IN_STRIP_2) {retriever = retrieveState.IN_STRIP_3; stripSensor = 2;} //change this and update following
        else if (retriever == retrieveState.IN_STRIP_3) {retriever = retrieveState.IN_STRIP_4;stripSensor = 3;}
        else if (retriever == retrieveState.IN_STRIP_4) {retriever = retrieveState.IN_XYZ;}
        else if (retriever == retrieveState.IN_XYZ) {retriever = retrieveState.IN_STRIP_1; stripSensor = 0;}
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param port
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, PressureDistroActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
}