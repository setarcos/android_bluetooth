package com.pluto.myfirstapp;

import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public class MainActivity extends Activity
{
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private BluetoothDevice btDevice = null;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private ConnectedThread mConnectedThread;
    Handler mHandler;

    private static final int MESSAGE_READ = 1;		// Status  for Handler

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MESSAGE_READ) {
                    byte[] readBuf = (byte[]) msg.obj;
                    String strIncom = new String(readBuf, 0, msg.arg1);					// create string from bytes array
                    TextView viewMsg = (TextView) findViewById(R.id.view_message);
                    viewMsg.append('\n' + strIncom);
                }
            }
        };
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_ENABLE_BT) && (resultCode != RESULT_OK)) {
            TextView viewMsg = (TextView) findViewById(R.id.view_message);
            viewMsg.append("\nBluetooth must be enabled.");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mConnectedThread != null)
            mConnectedThread.cancel();
    }

    public void connClick(View view) {
        if (mConnectedThread != null) return;
        TextView viewMsg = (TextView) findViewById(R.id.view_message);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (checkBTState() < 0) {
            viewMsg.append("\nBluetooth not found/enabled.");
            return;
        }

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                viewMsg.append('\n' + device.getName()); // device.getAddress()
                if (device.getName().indexOf("HC-05") >= 0)
                    btDevice = device;
            }
        }
        if (btDevice != null) {
            mConnectedThread = new ConnectedThread(btDevice);
            mConnectedThread.start();
        } else {
            errorExit("Fatal Error", "没有找到蓝牙串口");
        }
    }

    public void sendClick(View view) {
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String message = editText.getText().toString();
        if (mConnectedThread != null)
            mConnectedThread.write(message);
    }

    private int checkBTState() {
    // Check for Bluetooth support and then check to make sure it is turned on
        if (btAdapter==null) {
            errorExit("Fatal Error", "不支持蓝牙");
            return -1;
        } else {
            if (!btAdapter.isEnabled()) {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return -2;
            }
            return 0;
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothSocket mmSocket;

        public ConnectedThread(BluetoothDevice device) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            BluetoothSocket tmp = null;
            try {
                // using temp objects because member streams are final
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                tmpIn = tmp.getInputStream();
                tmpOut = tmp.getOutputStream();
            } catch (IOException e) { }
            mmSocket = tmp;
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()
            btAdapter.cancelDiscovery();
            try {
                // until it succeeds or throws an exception
                mmSocket.connect(); // requires BLUETOOTH_ADMIN
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();        // Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
            Log.d("MYBT", "BT Thread die");
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) { }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {}
        }
    }
}
