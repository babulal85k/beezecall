package com.babulal85k.bluetoothcall

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.*
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryDatabase(context: Context) : SQLiteOpenHelper(context, "CallHistory.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE call_history (id INTEGER PRIMARY KEY AUTOINCREMENT, device_name TEXT, timestamp TEXT, duration TEXT)")
    }



    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS call_history")
        onCreate(db)
    }

    fun addCall(deviceName: String, duration: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("device_name", deviceName)
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("duration", duration)
        }
        db.insert("call_history", null, values)
        db.close()
    }

    fun getCallHistory(): List<Pair<Int, String>> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM call_history ORDER BY id DESC", null)
        val history = mutableListOf<Pair<Int, String>>()
        while (cursor.moveToNext()) {
            val id = cursor.getInt(0)
            val name = cursor.getString(1)
            val time = cursor.getString(2)
            val duration = cursor.getString(3)
            history.add(Pair(id, "$name - $time (Duration: $duration)"))
        }
        cursor.close()
        db.close()
        return history
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var callHistoryDatabase: CallHistoryDatabase
    private lateinit var callHistoryListView: ListView
    private lateinit var clearHistoryButton: Button
    private lateinit var callButton: Button
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var callStartTime: Long = 0
    private lateinit var scanButton: Button
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e("BluetoothReceiver", "Receiver not registered or already unregistered")
        }
    }


//    override fun onDestroy() {
//        super.onDestroy()
//        try {
//            unregisterReceiver(bluetoothReceiver)
//        } catch (e: IllegalArgumentException) {
//            Log.e("BluetoothReceiver", "Receiver not registered or already unregistered")
//        }
//    }



    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        callHistoryDatabase = CallHistoryDatabase(this)
        callHistoryListView = findViewById(R.id.call_history_list)
        clearHistoryButton = findViewById(R.id.clear_history_button)
        callButton = findViewById(R.id.call_button)
        scanButton = findViewById(R.id.scan_button)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ),
            1
        )


        // ✅ Set up button click listeners
        scanButton.setOnClickListener {
            scanBluetoothDevices()
        }

        clearHistoryButton.setOnClickListener {
            confirmClearHistory()
        }

        callButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with the operation
                initiateBluetoothCall()
            } else {
                // Permission not granted, request it from the user
                val REQUEST_CODE = 0
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE)
            }
        }

        // ✅ Load call history on startup
        loadCallHistory()
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startBluetoothCall(device: BluetoothDevice) {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth is disabled. Enable it first.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard UUID for SPP
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                    bluetoothAdapter.cancelDiscovery() // Important before connecting
                socket.connect()

                withContext(Dispatchers.Main) {
                    callStartTime = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()

                    // Simulate a 5-second call
                    Handler(Looper.getMainLooper()).postDelayed({
                        val callDuration = ((System.currentTimeMillis() - callStartTime) / 1000).toString() + " sec"
                        callHistoryDatabase.addCall(device.name, callDuration)
                        loadCallHistory()
                        Toast.makeText(this@MainActivity, "Call ended with ${device.name}", Toast.LENGTH_SHORT).show()
                    }, 5000)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to connect to ${device.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear Call History")
            .setMessage("Are you sure you want to delete all call history?")
            .setPositiveButton("Yes") { _, _ ->
                callHistoryDatabase.writableDatabase.execSQL("DELETE FROM call_history")
                loadCallHistory()
                Toast.makeText(this, "Call history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initiateBluetoothCall() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = pairedDevices.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select a Device")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = pairedDevices.elementAt(which)
                startBluetoothCall(selectedDevice)
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun scanBluetoothDevices() {

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN), 1)
            return
        }

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        if (!bluetoothAdapter.startDiscovery()) {
            Toast.makeText(this, "Failed to start Bluetooth scan", Toast.LENGTH_SHORT).show()
            return
        }

        // Show a progress dialog
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Scanning for devices...")
            setCancelable(false)
        }
        progressDialog.show()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothReceiver, filter)

        Toast.makeText(this, "Scanning for Bluetooth devices...", Toast.LENGTH_SHORT).show()

        // Stop discovery after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.cancelDiscovery()
                Toast.makeText(this, "Scan complete", Toast.LENGTH_SHORT).show()
            }
        }, 10000)
    }




    private fun loadCallHistory() {
        val history = callHistoryDatabase.getCallHistory()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, history.map { it.second })
        callHistoryListView.adapter = adapter
    }
}
