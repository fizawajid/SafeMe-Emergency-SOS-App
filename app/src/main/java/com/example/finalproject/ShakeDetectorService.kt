package com.example.finalproject

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime: Long = 0
    private var shakeThreshold = 15.0f // Will be loaded from preferences
    private val SHAKE_COOLDOWN = 30000L // 30 seconds cooldown between shakes

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var locationAddress: String = "Location unavailable"

    private lateinit var database: DatabaseReference
    private val contactsList = mutableListOf<EmergencyContact>()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"
    private val senderEmail = FirebaseAuth.getInstance().currentUser?.email ?: "noreply@safeme.com"

    // EmailJS credentials
    private val EMAILJS_SERVICE_ID = "service_7c31t4s"
    private val EMAILJS_TEMPLATE_ID = "template_6woyk8b"
    private val EMAILJS_PUBLIC_KEY = "z0XPnqySWvklrNwlF"


    // Test broadcast receiver
    private val testReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.example.finalproject.TEST_SHAKE") {
                Log.d(TAG, "🧪 TEST SHAKE TRIGGERED")
                onShakeDetected()
            }
        }
    }

    companion object {
        private const val TAG = "ShakeDetectorService"
        const val CHANNEL_ID = "ShakeDetectorChannel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ShakeDetectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShakeDetectorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "╔════════════════════════════════════════╗")
        Log.d(TAG, "║  SHAKE DETECTOR SERVICE CREATED!!!    ║")
        Log.d(TAG, "╚════════════════════════════════════════╝")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Shake detection active"))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Load shake sensitivity from preferences
        loadShakeSensitivity()

        loadEmergencyContacts()

        // Register test broadcast receiver
        val filter = android.content.IntentFilter("com.example.finalproject.TEST_SHAKE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }

        Log.d(TAG, "Service created and shake detection started with threshold: $shakeThreshold")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

            if (acceleration > shakeThreshold) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShakeTime > SHAKE_COOLDOWN) {
                    lastShakeTime = currentTime
                    onShakeDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun onShakeDetected() {
        Log.d(TAG, "🚨 SHAKE DETECTED! Sending emergency alert...")

        // Show immediate feedback notification
        showImmediateNotification("🚨 SHAKE DETECTED!", "Getting your location and sending alerts...")

        // Vibrate for feedback
        vibratePhone()

        // Update service notification
        val notification = createNotification("Emergency detected! Sending alerts...")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Get current location and send alerts
        getCurrentLocationAndSendAlerts()
    }

    private fun vibratePhone() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 100, 200, 100, 200),
                    -1
                ))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 200, 100, 200, 100, 200), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private fun showImmediateNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(false)
            .setOngoing(true)
            .setColor(0xFFFF0000.toInt())
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 10, notification)
    }

    private fun getCurrentLocationAndSendAlerts() {
        try {
            val cancellationTokenSource = CancellationTokenSource()

            Log.d(TAG, "📍 Getting current location...")
            showImmediateNotification("📍 Getting Location", "Acquiring your current position...")

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    locationAddress = "Lat: ${location.latitude}, Lng: ${location.longitude}"

                    Log.d(TAG, "✅ Location obtained: $locationAddress")

                    // Try to get address
                    try {
                        val geocoder = android.location.Geocoder(this, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            locationAddress = addresses[0].getAddressLine(0) ?: locationAddress
                            Log.d(TAG, "📍 Address: $locationAddress")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Geocoding failed", e)
                    }
                } else {
                    Log.w(TAG, "⚠️ Location is null, proceeding without location")
                }
                sendEmergencyAlerts()
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get location: ${e.message}", e)
                showImmediateNotification("⚠️ Location Failed", "Sending alerts without location...")
                sendEmergencyAlerts() // Send without location
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Location permission not granted", e)
            showImmediateNotification("⚠️ No Location Permission", "Sending alerts without location...")
            sendEmergencyAlerts() // Send without location
        }
    }

    private fun sendEmergencyAlerts() {
        val contactsWithEmail = contactsList.filter { !it.email.isNullOrBlank() }

        if (contactsWithEmail.isEmpty()) {
            Log.w(TAG, "❌ No contacts with email found")
            showResultNotification("❌ Alert Failed", "No emergency contacts with email found", false)
            return
        }

        Log.d(TAG, "📧 Sending to ${contactsWithEmail.size} contacts...")
        showImmediateNotification("📧 Sending Alerts", "Sending to ${contactsWithEmail.size} contacts...")

        val message = buildShakeEmergencyMessage()

        var emailsSent = 0
        var emailsFailed = 0
        val totalEmails = contactsWithEmail.size

        for (contact in contactsWithEmail) {
            sendEmail(contact.email!!, contact.fullName, message) { success ->
                if (success) {
                    emailsSent++
                    Log.d(TAG, "✅ Email sent to ${contact.fullName}")
                } else {
                    emailsFailed++
                    Log.e(TAG, "❌ Email failed to ${contact.fullName}")
                }

                // Update progress notification
                showImmediateNotification(
                    "📧 Sending Alerts ($emailsSent/$totalEmails)",
                    "Sent: $emailsSent | Failed: $emailsFailed"
                )

                if (emailsSent + emailsFailed >= totalEmails) {
                    val success = emailsSent > 0
                    val title = if (success) "✅ Alerts Sent Successfully!" else "❌ Alert Failed"
                    val resultMessage = "Sent: $emailsSent | Failed: $emailsFailed"

                    Log.d(TAG, "📊 FINAL RESULT: $resultMessage")
                    showResultNotification(title, resultMessage, success)
                }
            }
        }
    }

    private fun showResultNotification(title: String, message: String, success: Boolean) {
        // Cancel the immediate notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID + 10)

        // Show result notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(if (success) android.R.drawable.ic_dialog_info else android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(if (success) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
            .setVibrate(if (success) longArrayOf(0, 100, 100, 100) else longArrayOf(0, 500))
            .build()

        notificationManager.notify(NOTIFICATION_ID + 20, notification)

        // Reset service notification back to normal after 5 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val serviceNotification = createNotification("Shake detection active")
            notificationManager.notify(NOTIFICATION_ID, serviceNotification)
        }, 5000)
    }

    private fun buildShakeEmergencyMessage(): String {
        val sb = StringBuilder()
        sb.append("🚨 SHAKE EMERGENCY ALERT 🚨\n\n")
        sb.append("EMERGENCY DETECTED BY SHAKE!\n\n")
        sb.append("From: $senderEmail\n")
        sb.append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        sb.append("📍 CURRENT LOCATION:\n")
        if (currentLocation != null) {
            sb.append("Address: $locationAddress\n")
            sb.append("Coordinates: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}\n")
            sb.append("Google Maps: https://www.google.com/maps?q=${currentLocation!!.latitude},${currentLocation!!.longitude}\n\n")
        } else {
            sb.append("Location: Unable to determine\n\n")
        }

        sb.append("⚠️ IMMEDIATE ASSISTANCE REQUIRED ⚠️\n")
        sb.append("This person may be in danger. Please respond immediately.\n")
        sb.append("\nThis is an automated emergency alert triggered by device shake detection.")

        return sb.toString()
    }

    private fun sendEmail(toEmail: String, contactName: String, message: String, callback: (Boolean) -> Unit) {
        val url = "https://api.emailjs.com/api/v1.0/email/send"

        Log.d(TAG, "📤 Sending email to $contactName ($toEmail)...")

        val jsonBody = JSONObject().apply {
            put("service_id", EMAILJS_SERVICE_ID)
            put("template_id", EMAILJS_TEMPLATE_ID)
            put("user_id", EMAILJS_PUBLIC_KEY)
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("contact_name", contactName)
                put("alert_message", message)
                if (currentLocation != null) {
                    put("location_address", locationAddress)
                    put("location_coordinates", "${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
                    put("google_maps_link", "https://www.google.com/maps?q=${currentLocation!!.latitude},${currentLocation!!.longitude}")
                }
            })
        }

        // Use StringRequest because EmailJS returns plain "OK" text, not JSON
        val request = object : com.android.volley.toolbox.StringRequest(
            com.android.volley.Request.Method.POST, url,
            { response ->
                // EmailJS returns "OK" as plain text on success
                Log.d(TAG, "✅ Email sent successfully to $toEmail (Response: $response)")
                callback(true)
            },
            { error ->
                // Check if it's actually a success (HTTP 200) but Volley thinks it's an error
                if (error.networkResponse?.statusCode == 200) {
                    Log.d(TAG, "✅ Email sent successfully to $toEmail (HTTP 200)")
                    callback(true)
                } else {
                    val errorMsg = when {
                        error.networkResponse != null -> {
                            val statusCode = error.networkResponse.statusCode
                            val errorBody = String(error.networkResponse.data ?: byteArrayOf())
                            "HTTP $statusCode: $errorBody"
                        }
                        error.cause is java.net.ConnectException -> {
                            "Connection failed - check internet connection"
                        }
                        else -> error.message ?: "Unknown error"
                    }
                    Log.e(TAG, "❌ Failed to send email to $toEmail: $errorMsg")
                    callback(false)
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "Content-Type" to "application/json",
                    "origin" to "http://localhost",
                    "Authorization" to "Bearer $EMAILJS_PUBLIC_KEY"
                )
            }

            override fun getBody(): ByteArray {
                return jsonBody.toString().toByteArray(Charsets.UTF_8)
            }

            override fun getBodyContentType(): String {
                return "application/json; charset=utf-8"
            }
        }

        // Set timeout to 15 seconds with retries
        request.setRetryPolicy(
            com.android.volley.DefaultRetryPolicy(
                15000,
                1, // Only 1 retry for 200 responses
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        )

        Volley.newRequestQueue(this).add(request)
    }

    private fun loadEmergencyContacts() {
        database = FirebaseDatabase.getInstance().getReference("emergency_contacts")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contactsList.clear()
                for (contactSnapshot in snapshot.children) {
                    val contact = contactSnapshot.getValue(EmergencyContact::class.java)
                    contact?.let { contactsList.add(it) }
                }
                Log.d(TAG, "Loaded ${contactsList.size} emergency contacts")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load contacts", error.toException())
            }
        })
    }

    private fun loadShakeSensitivity() {
        val prefs = getSharedPreferences("ShakeDetectionPrefs", Context.MODE_PRIVATE)
        val sensitivity = prefs.getInt("shake_sensitivity", 50) // Default medium

        // Convert progress to threshold (inverse relationship)
        shakeThreshold = when {
            sensitivity < 34 -> 20.0f - (sensitivity / 33f * 5f) // 20 to 15
            sensitivity < 67 -> 15.0f - ((sensitivity - 33) / 33f * 5f) // 15 to 10
            else -> 10.0f - ((sensitivity - 66) / 34f * 5f) // 10 to 5
        }

        Log.d(TAG, "Shake sensitivity loaded: $sensitivity -> threshold: $shakeThreshold")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shake Emergency Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Detects shake gestures for emergency alerts"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, dashboard::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeMe - Shake Detection")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showAlertNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Emergency Alert Sent")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try {
            unregisterReceiver(testReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering test receiver", e)
        }
        Log.d(TAG, "Service destroyed, shake detection stopped")
    }
}