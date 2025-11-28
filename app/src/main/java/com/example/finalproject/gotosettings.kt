package com.example.finalproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class gotosettings : AppCompatActivity() {

    private lateinit var switchShakeDetection: SwitchCompat
    private lateinit var seekBarSensitivity: SeekBar
    private lateinit var btnTestShake: LinearLayout

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 3001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        initializeViews()
        setupClickListeners()
        loadShakeSettings()
    }

    private fun initializeViews() {
        switchShakeDetection = findViewById(R.id.switchShakeDetection)
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity)
        btnTestShake = findViewById(R.id.btnTestShake)

        // Set up sensitivity seekbar (0-100, where 0=low, 50=medium, 100=high)
        seekBarSensitivity.max = 100
        seekBarSensitivity.progress = 50 // Default to medium
    }

    private fun setupClickListeners() {
        // Offline alerts
        val btnoffline = findViewById<ImageView>(R.id.offline)
        btnoffline.setOnClickListener {
            startActivity(android.content.Intent(this, OfflineAlertsActivity::class.java))
        }

        // Test shake detection button
        btnTestShake.setOnClickListener {
            if (ShakeDetectionManager.isEnabled(this)) {
                testShakeDetection()
            } else {
                Toast.makeText(this, "Please enable shake detection first", Toast.LENGTH_SHORT).show()
            }
        }

        // Shake detection switch
        switchShakeDetection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndEnableShake()
            } else {
                disableShakeDetection()
            }
        }

        // Sensitivity seekbar
        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ShakeDetectionManager.isEnabled(this@gotosettings)) {
                    updateShakeSensitivity(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun testShakeDetection() {
        AlertDialog.Builder(this)
            .setTitle("🧪 Test Shake Detection")
            .setMessage("This will simulate a shake and send test emergency alerts to all your contacts.\n\n⚠️ Real emails will be sent!\n\nContinue?")
            .setPositiveButton("Yes, Send Test") { _, _ ->
                // Trigger the shake detection manually
                val intent = android.content.Intent("com.example.finalproject.TEST_SHAKE")
                sendBroadcast(intent)

                Toast.makeText(
                    this,
                    "🧪 Test alert triggered! Check notifications and logcat",
                    Toast.LENGTH_LONG
                ).show()

                // Also show info about what to check
                android.os.Handler(mainLooper).postDelayed({
                    AlertDialog.Builder(this)
                        .setTitle("📱 What to Check")
                        .setMessage(
                            "You should see:\n\n" +
                                    "1. 🚨 SHAKE DETECTED notification\n" +
                                    "2. 📍 Getting Location notification\n" +
                                    "3. 📧 Sending Alerts notification\n" +
                                    "4. ✅ Final success notification\n" +
                                    "5. Emails in your contacts' inboxes\n\n" +
                                    "Check Logcat for detailed logs with emojis!"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }, 1000)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadShakeSettings() {
        // Load shake detection state
        val isEnabled = ShakeDetectionManager.isEnabled(this)
        switchShakeDetection.isChecked = isEnabled

        // Load sensitivity settings
        val prefs = getSharedPreferences("ShakeDetectionPrefs", MODE_PRIVATE)
        val sensitivity = prefs.getInt("shake_sensitivity", 50)
        seekBarSensitivity.progress = sensitivity

        // Enable/disable sensitivity control based on shake detection state
        seekBarSensitivity.isEnabled = isEnabled
    }

    private fun checkPermissionsAndEnableShake() {
        val permissionsNeeded = mutableListOf<String>()

        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            showPermissionDialog(permissionsNeeded)
        } else {
            enableShakeDetection()
        }
    }

    private fun showPermissionDialog(permissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Shake detection requires:\n\n" +
                    "📍 Location - To send your location in emergency alerts\n" +
                    "🔔 Notifications - To show service status\n\n" +
                    "These permissions are essential for emergency response.")
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    LOCATION_PERMISSION_REQUEST
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                switchShakeDetection.isChecked = false
                Toast.makeText(this, "Permissions required for shake detection", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableShakeDetection()
            } else {
                switchShakeDetection.isChecked = false
                Toast.makeText(this, "Permissions denied. Cannot enable shake detection.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableShakeDetection() {
        ShakeDetectionManager.setEnabled(this, true)
        seekBarSensitivity.isEnabled = true

        // Save sensitivity
        val sensitivity = seekBarSensitivity.progress
        saveSensitivity(sensitivity)

        Toast.makeText(
            this,
            "✅ Shake detection enabled\nShake your phone to send emergency alerts",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun disableShakeDetection() {
        ShakeDetectionManager.setEnabled(this, false)
        seekBarSensitivity.isEnabled = false
        Toast.makeText(this, "Shake detection disabled", Toast.LENGTH_SHORT).show()
    }

    private fun updateShakeSensitivity(progress: Int) {
        saveSensitivity(progress)

        // Calculate threshold based on progress (inverse relationship)
        // Low sensitivity (0-33): threshold = 20-25 (harder to trigger)
        // Medium sensitivity (34-66): threshold = 12-19 (moderate)
        // High sensitivity (67-100): threshold = 5-11 (easier to trigger)
        val threshold = when {
            progress < 34 -> 20.0f - (progress / 33f * 5f) // 20 to 15
            progress < 67 -> 15.0f - ((progress - 33) / 33f * 5f) // 15 to 10
            else -> 10.0f - ((progress - 66) / 34f * 5f) // 10 to 5
        }

        // Restart service with new threshold if it's running
        if (ShakeDetectionManager.isEnabled(this)) {
            ShakeDetectorService.stop(this)

            // Small delay to ensure service stops before restarting
            android.os.Handler(mainLooper).postDelayed({
                ShakeDetectorService.start(this)
            }, 500)
        }

        val sensitivityText = when {
            progress < 34 -> "Low"
            progress < 67 -> "Medium"
            else -> "High"
        }

        Toast.makeText(
            this,
            "Sensitivity: $sensitivityText",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveSensitivity(progress: Int) {
        getSharedPreferences("ShakeDetectionPrefs", MODE_PRIVATE)
            .edit()
            .putInt("shake_sensitivity", progress)
            .apply()
    }

    override fun onResume() {
        super.onResume()
        loadShakeSettings()
    }
}