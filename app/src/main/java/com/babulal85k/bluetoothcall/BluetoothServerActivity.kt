package com.babulal85k.bluetoothcall

import android.bluetooth.*
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*

class BluetoothServerActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var bluetoothAdapter: BluetoothAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        statusText = findViewById(R.id.status_text)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // 🔹 Start listening for incoming Bluetooth calls automatically
        startBluetoothServer()
    }

    private fun startBluetoothServer() {
        val serverSocket: BluetoothServerSocket? =
            bluetoothAdapter.listenUsingRfcommWithServiceRecord("BluetoothCall", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (true) { // Keep listening for incoming calls
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        serverSocket.close() // Close server socket once connected

                        withContext(Dispatchers.Main) {
                            statusText.text = "Call Accepted!"
                            Toast.makeText(this@BluetoothServerActivity, "Connected!", Toast.LENGTH_SHORT).show()
                        }

                        // 🔹 Start audio streaming once connected
                        BluetoothHelper.startAudioStreaming(socket)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
