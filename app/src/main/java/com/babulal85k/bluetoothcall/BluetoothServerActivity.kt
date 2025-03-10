package com.babulal85k.bluetoothcall

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothServerActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var serverSocket: BluetoothServerSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        statusText = findViewById(R.id.status_text)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Check if Bluetooth is supported and enabled
        if (bluetoothAdapter == null) {
            statusText.text = "Bluetooth is not supported on this device"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            statusText.text = "Please enable Bluetooth"
            return
        }

        // Start listening for incoming Bluetooth connections
        startBluetoothServer()
    }

    private val requestCode = 1001

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
         }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
             permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestCode)
        }
}


    private fun startBluetoothServer() {
        coroutineScope.launch {
            try {
                // UUID for SPP (Serial Port Profile)
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("BluetoothCall", uuid)

                withContext(Dispatchers.Main) {
                    statusText.text = "Waiting for incoming connections..."
                }

                // Accept incoming connections in a background thread
                while (true) {
                    val socket: BluetoothSocket? = serverSocket?.accept()
                    if (socket != null) {
                        // Handle the connection
                        withContext(Dispatchers.Main) {
                            statusText.text = "Connected to ${socket.remoteDevice.name}"
                        }
                        // You can now communicate with the connected device using the socket
                        break // Exit the loop after accepting a connection
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Failed to start server: ${e.message}"
                }
            } finally {
                serverSocket?.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up the server socket
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
