package com.babulal85k.bluetoothcall

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.*
import android.os.*
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.material.snackbar.Snackbar
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
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        db.execSQL("INSERT INTO call_history (device_name, timestamp, duration) VALUES (?, ?, ?)", arrayOf(deviceName, timestamp, duration))
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
    private var deletedHistory: List<Pair<Int, String>> = emptyList()
    private var callStartTime: Long = 0
    private lateinit var scanButton: Button
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    discoveredDevices.add(it)
                    Toast.makeText(context, "Found: ${it.name} - ${it.address}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        callHistoryDatabase = CallHistoryDatabase(this)
        callHistoryListView = findViewById(R.id.call_history_list)
        clearHistoryButton = findViewById(R.id.clear_history_button)
        callButton = findViewById(R.id.call_button)
        scanButton = findViewById(R.id.scan_button)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // ✅ Set up button click listeners
        scanButton.setOnClickListener {
            scanBluetoothDevices()
        }

        clearHistoryButton.setOnClickListener {
            confirmClearHistory()
        }

        callButton.setOnClickListener {
            initiateBluetoothCall()
        }

        // ✅ Load call history on startup
        loadCallHistory()
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startBluetoothCall(device: BluetoothDevice) {
        try {
            val socket = device.createRfcommSocketToServiceRecord(UUID.randomUUID())
            socket.connect()
            callStartTime = System.currentTimeMillis()
            Toast.makeText(this, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                val callDuration = ((System.currentTimeMillis() - callStartTime) / 1000).toString() + " sec"
                callHistoryDatabase.addCall(device.name, callDuration)
                loadCallHistory()
                Toast.makeText(this, "Call ended with ${device.name}", Toast.LENGTH_SHORT).show()
            }, 5000) // Simulating 5-second call duration

        } catch (e: IOException) {
            Toast.makeText(this, "Failed to connect to ${device.name}", Toast.LENGTH_SHORT).show()
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

    private fun scanBluetoothDevices() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        if (!bluetoothAdapter.startDiscovery()) {
            Toast.makeText(this, "Failed to start Bluetooth scan", Toast.LENGTH_SHORT).show()
            return
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothReceiver, filter)

        Toast.makeText(this, "Scanning for Bluetooth devices...", Toast.LENGTH_SHORT).show()
    }


    private fun loadCallHistory() {
        val history = callHistoryDatabase.getCallHistory()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, history.map { it.second })
        callHistoryListView.adapter = adapter
    }
}
