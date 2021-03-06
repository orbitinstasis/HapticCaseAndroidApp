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
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//https://www.thenewboston.com/forum/topic.php?id=3790
public class CameraActivity extends Activity {
    /*
    CONSTANTS
     */
    private static final int MAX_STRIP_CELLS = 2;
    private static final int STRIP_PRESSURE = 0;
    private static final int STRIP_POSITION = 1;
    private static final int END_MARKER_XYZ = 0xFF;
    private static final int ROWS = 10;
    private static final int COLS = 16;
    private static final int MAX_RECEIVE_VALUE = 254;
    protected static final byte[] PDM_RX_VALUE = {(byte)0b00011111};
    protected static final byte[] RX_SLEEP = {0};
    protected static final int BAUD_FULL = 115200;
    protected static final int BAUD_SLEEP = 9600;
    /*
    GLOBALS
     */
    private enum retrieveState { //
        IN_STRIP_1, //fetching strip
        IN_STRIP_2,
        IN_STRIP_3,
        IN_STRIP_4,
        IN_XYZ,
        READY      // ready to save UART data to new sensor set -- check for markers
        ;
    }
    private retrieveState retriever = retrieveState.READY;
    private int resumeSensor = 0; //this will hold the state for whatever thing you were in the middle of reading of when buffer.size was read, so you resume at this point    // in the checkButThread just update the 2D array by doibng for [readModel/(ROWS-2)][readModel % COLS]  or something like this
    private int stripSensor = 0;
    private int xCount = 0;
    private int yCount = 0;
    private boolean gotZero = false;
    private int[][] padCell = new int[ROWS][COLS];
    private int[][] oldPadCell = new int[ROWS][COLS];
    private Thread checkButThread;
    private Handler checkButHandler = new Handler();
    private final String TAG = CameraActivity.class.getSimpleName();
    private static UsbSerialPort sPort = null; //Driver instance, passed in statically via {@link #show(Context, UsbSerialPort)}.
    private int stripModel[][] = {{0,0},{0,0},{0,0},{0,0}}; //for each sensor, you have 1st: pressure, 2nd: position
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {
                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                    checkButHandler.removeCallbacks(checkButThread);
                    stopIoManager();
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
                @Override
                public void onNewData(final byte[] data) {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
                    CameraActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CameraActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };
    static final int REQUEST_IMAGE_CAPTURE = 1;
   // private ImageView mImageView;
    Button btn;
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

        setContentView(R.layout.camera_layout);
        btn = (Button) findViewById(R.id.camButton);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
            btn.setEnabled(false);
        cameraButtonListenerThread();
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
    @Override
    protected void onResume() {
        super.onResume();
        checkButHandler.removeCallbacks(checkButThread);
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
                sPort.setParameters(BAUD_SLEEP, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                checkButHandler.post(checkButThread);
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
        try {
            setControl(PDM_RX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void setControl(byte[] desiredSensors) throws IOException {
        mSerialIoManager.purgeInputBuffer();
        mSerialIoManager.writeAsync(desiredSensors);
        sPort.setParameters(BAUD_FULL, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

    }

    private void cameraButtonListenerThread() {
        checkButThread = new Thread(){
            public void run() {
                if (stripModel[1][STRIP_PRESSURE] > 100) {
                    btn.performClick();
                }
                checkButHandler.post(this);
            }
        };
    }


    @Override
    protected void onPause() {
        super.onPause();
        checkButHandler.removeCallbacks(checkButThread);
        mSerialIoManager.writeAsync(RX_SLEEP);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (sPort != null) {
            try {
                sPort.setParameters(BAUD_SLEEP, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        stopIoManager();
        finish();
    }

    //from a site
    public void launchCamera(View view){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //Take a picture and pass results along to onActivityResult
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
    }

//    @Override
//    public void onBackPressed() {
//        checkButHandler.removeCallbacks(checkButThread);
//        stopIoManager();
//        finish();
//    }

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
        final Intent intent = new Intent(context, CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
}
