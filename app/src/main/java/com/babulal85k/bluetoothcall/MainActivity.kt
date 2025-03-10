package com.babulal85k.bluetoothcall

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.AudioManager
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
    private lateinit var callHistoryListView: RecyclerView
    private lateinit var clearHistoryButton: Button
    private lateinit var callButton: Button
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var callStartTime: Long = 0
    private lateinit var scanButton: Button
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var callHistoryAdapter: CallHistoryAdapter


    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    if (!discoveredDevices.contains(it)) {
                        discoveredDevices.add(it)
                        updateDeviceList()
                    }
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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        


        callHistoryDatabase = CallHistoryDatabase(this)
        callHistoryListView = findViewById(R.id.call_history_list)
        clearHistoryButton = findViewById(R.id.clear_history_button)
        callButton = findViewById(R.id.call_button)
        scanButton = findViewById(R.id.scan_button)

        // Set up RecyclerView
        callHistoryAdapter = CallHistoryAdapter()
        callHistoryListView.layoutManager = LinearLayoutManager(this)
        callHistoryListView.adapter = callHistoryAdapter

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ),
            1
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }



        // Set up button click listeners
        scanButton.setOnClickListener { scanBluetoothDevices() }
        clearHistoryButton.setOnClickListener { confirmClearHistory() }
        callButton.setOnClickListener { initiateBluetoothCall() }

        val serviceIntent = Intent(this, BluetoothService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent) // Required for Android 8+
        } else {
            startService(serviceIntent) // For older versions
        }


        // ✅ Load call history on startup
        loadCallHistory()
        createNotificationChannel()
    }

    private fun updateDeviceList() {
        val deviceNames = discoveredDevices.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select a Device")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = discoveredDevices[which]
                startBluetoothCall(selectedDevice)
            }
            .show()
    }

    private fun loadCallHistory() {
        val history = callHistoryDatabase.getCallHistory()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, history.map { it.second })
        callHistoryListView.adapter = adapter
    }


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

                    // Start audio streaming
                    startBluetoothAudio()

                    // Simulate a call duration of 10 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopBluetoothAudio()
                        val callDuration = ((System.currentTimeMillis() - callStartTime) / 1000).toString() + " sec"
                        callHistoryDatabase.addCall(device.name, callDuration)
                        loadCallHistory()
                        Toast.makeText(this@MainActivity, "Call ended with ${device.name}", Toast.LENGTH_SHORT).show()
                    }, 10000)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to connect to ${device.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startBluetoothAudio() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!audioManager.isBluetoothScoAvailableOffCall) {
            Toast.makeText(this, "Bluetooth SCO not supported", Toast.LENGTH_SHORT).show()
            return
        }

        audioManager.startBluetoothSco()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false // Use Bluetooth headset if available
    }


    private fun stopBluetoothAudio() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.stopBluetoothSco()
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bluetooth_call_channel",
                "Bluetooth Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Bluetooth calls"
            }

            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
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

    private val requestCode = 1001

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestCode)
        }
    }


}
