package dev.pluto.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.Manifest;
import android.annotation.SuppressLint;
import android.util.Log;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;

public class MainActivity extends Activity
{
    private BluetoothDevice btDevice = null;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "MYBT";
    private static final String STATE_BT = "MYBT";
    private static final int REQUEST_PERMS = 2;

    // Bluetooth became a runtime permission in Android 12 (API 31). These constants
    // are inlined at compile time, so they are harmless on older devices.
    @SuppressLint("InlinedApi")
    private static final String[] BT_PERMS = {
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
    };
    private ConnectedThread mConnectedThread;
    Handler mHandler;
    private int mBtState = 0;
    private int err_count = 0;

    private static final int MESSAGE_READ = 1;		// Status  for Handler
    private static final int MESSAGE_FAIL = 2;		// Status  for Handler

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (savedInstanceState != null) {
            mBtState = savedInstanceState.getInt(STATE_BT);
        }
        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MESSAGE_READ) {
                    byte[] readBuf = (byte[]) msg.obj;
                    String strIncom = new String(readBuf, 0, msg.arg1);					// create string from bytes array
                    TextView viewMsg = (TextView) findViewById(R.id.view_message);
                    viewMsg.append('\n' + strIncom);
                }
                if (msg.what == MESSAGE_FAIL) {
                    err_count++;
                    mBtState = 0;
                    mConnectedThread = null;
                    if (err_count < 3) connClick(null);
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
        mConnectedThread = null; // set it to null, since the thread has been stopped.
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBtState == 1)
            connClick(null);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(STATE_BT, mBtState);
    }

    // Permissions are enforced by ensureBtPermissions() / onRequestPermissionsResult().
    // Lint cannot see across that call boundary, so it is suppressed explicitly here.
    @SuppressLint("MissingPermission")
    public void connClick(View view) {
        BluetoothAdapter btAdapter = null;
        if (mConnectedThread != null) return;
        TextView viewMsg = (TextView) findViewById(R.id.view_message);
        if (!ensureBtPermissions()) {
            viewMsg.append("\n需要蓝牙权限，授权后自动连接");
            return;
        }
        btAdapter = getBtAdapter();
        if (checkBTState(btAdapter) < 0) {
            viewMsg.append("\nBluetooth not found/enabled.");
            return;
        }

        Set<BluetoothDevice> pairedDevices;
        try {
            pairedDevices = btAdapter.getBondedDevices();
        } catch (SecurityException e) {
            // the user may revoke the permission mid-session
            errorExit("Fatal Error", "缺少蓝牙权限");
            return;
        }
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                if (name == null) name = device.getAddress();
                if (view != null) viewMsg.append('\n' + name);
                if (name.indexOf("HC-05") >= 0)
                    btDevice = device;
            }
        }
        btAdapter.cancelDiscovery();
        if (btDevice != null) {
            mConnectedThread = new ConnectedThread(btDevice);
            mConnectedThread.start();
            mBtState = 1;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMS) return;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                TextView viewMsg = (TextView) findViewById(R.id.view_message);
                viewMsg.append("\n蓝牙权限被拒绝，无法连接");
                return;
            }
        }
        connClick(null);
    }

    /** Returns true below Android 12; from API 31 on the permission must be granted at runtime. */
    private boolean ensureBtPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        List<String> missing = new ArrayList<>();
        for (String perm : BT_PERMS) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) missing.add(perm);
        }
        if (missing.isEmpty()) return true;
        requestPermissions(missing.toArray(new String[0]), REQUEST_PERMS);
        return false;
    }

    private BluetoothAdapter getBtAdapter() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) { }
    }

    @SuppressLint("MissingPermission")
    private int checkBTState(BluetoothAdapter btAdapter) {
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
            } catch (IOException | SecurityException e) {
                Log.d(TAG, "create socket failed: " + e.getMessage());
                closeQuietly(tmp);
                tmp = null;
                tmpIn = null;
                tmpOut = null;
            }
            mmSocket = tmp;
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            // A failed constructor call leaves all three fields null. Bail out first:
            // an NPE here would not be caught by catch (IOException) and the thread
            // would die silently.
            if (mmSocket == null || mmInStream == null || mmOutStream == null) {
                Log.d(TAG, "socket not ready");
                mHandler.obtainMessage(MESSAGE_FAIL).sendToTarget();
                return;
            }
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()
            try {
                // until it succeeds or throws an exception
                mmSocket.connect(); // requires BLUETOOTH_ADMIN
            } catch (IOException | SecurityException connectException) {
                // Unable to connect; close the socket and get out
                Log.d(TAG, "mmSocket: " + connectException.getMessage());
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                mHandler.obtainMessage(MESSAGE_FAIL).sendToTarget();
                return;
            }

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();        // Send to message queue Handler
                } catch (IOException | SecurityException e) {
                    break;
                }
            }
            Log.d(TAG, "BT Thread die");
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            if (mmOutStream == null) return;
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException | SecurityException e) { }
        }

        public void cancel() {
            closeQuietly(mmSocket);
        }
    }
}
