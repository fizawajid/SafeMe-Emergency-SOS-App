package com.example.finalproject

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.volley.Request.Method.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.finalproject.repository.AlertRepository
import com.example.finalproject.utils.NetworkUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.example.finalproject.utils.Config
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class TravelSafety : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var btnBack: ImageView
    private lateinit var etAdditionalMessage: EditText
    private lateinit var contactsContainer: LinearLayout
    private lateinit var btnSendAlert: TextView
    private lateinit var tvContactCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressStatus: TextView
    private lateinit var progressContainer: LinearLayout

    private val contactsList = mutableListOf<EmergencyContact>()
    private var finishData: FinishData? = null
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var locationAddress: String = "Location unavailable"

    // Offline storage
    private lateinit var alertRepository: AlertRepository
    private val gson = Gson()

    // Email tracking
    private var emailsSent = 0
    private var emailsFailed = 0
    private var totalEmailsToSend = 0

    // Get sender email from logged in user
    private val SENDER_EMAIL = FirebaseAuth.getInstance().currentUser?.email ?: "noreply@safeme.com"

    // EmailJS credentials
    private val EMAILJS_SERVICE_ID = "service_7c31t4s"
    private val EMAILJS_TEMPLATE_ID = "template_6woyk8b"
    private val EMAILJS_PUBLIC_KEY = "z0XPnqySWvklrNwlF"
    // Image handling
    private lateinit var btnCamera: LinearLayout
    private lateinit var btnGallery: LinearLayout
    private lateinit var ivSelectedImage: ImageView

    private var selectedImageUri: Uri? = null
    private var selectedImageBase64: String? = null

    // Add Launchers
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            handleGalleryImage(it)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleCameraImage()
        }
    }

    private var currentPhotoPath: String? = null

    // Permission codes
    private val CAMERA_PERMISSION_REQUEST_CODE = 1004
    private val STORAGE_PERMISSION_REQUEST_CODE = 1003

    private val storagePermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    companion object {
        private const val TAG = "TravelSafety"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.travel_safety)

        // Initialize repository and location client
        alertRepository = AlertRepository(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initializeViews()
        setupClickListeners()
        loadFinishData()
        loadEmergencyContacts()
        checkLocationPermission()

        Log.d(TAG, "Sender email: $SENDER_EMAIL")
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        etAdditionalMessage = findViewById(R.id.etAdditionalMessage)
        btnSendAlert = findViewById(R.id.btnSendAlert)
        tvContactCount = findViewById(R.id.tvContactCount)
        contactsContainer = findViewById(R.id.contactsContainer)
        progressBar = findViewById(R.id.progressBar)
        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        progressContainer = findViewById(R.id.progressContainer)

        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        ivSelectedImage = findViewById(R.id.ivSelectedImage)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnSendAlert.setOnClickListener { sendEmergencyAlert() }
        btnCamera.setOnClickListener { checkCameraPermission() } // Still need camera permission
        btnGallery.setOnClickListener { openGallery() } // No permission needed!
        ivSelectedImage.setOnClickListener { showImageOptions() }
    }

    private fun showImageOptions() {
        val options = arrayOf("View Image", "Remove Image")
        AlertDialog.Builder(this)
            .setTitle("Image Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> selectedImageUri?.let { viewImage(it) }
                    1 -> removeSelectedImage()
                }
            }
            .show()
    }

    private fun viewImage(uri: Uri) {
        val intent = Intent(this, ImageViewActivity::class.java).apply {
            putExtra("image_uri", uri.toString())
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun removeSelectedImage() {
        selectedImageUri = null
        selectedImageBase64 = null
        currentPhotoPath = null
        ivSelectedImage.visibility = View.GONE
        ivSelectedImage.setImageBitmap(null)
    }

    // Camera and Gallery Permission Handling
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Required")
                    .setMessage("This app needs camera access to take photos for travel emergency alerts.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestCameraPermission()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                requestCameraPermission()
            }
        }
    }

    private fun checkStoragePermission() {
        // Check if we already have the required permission
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            openGallery()
        } else {
            // Check if we should show explanation
            val shouldShowRationale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if (shouldShowRationale) {
                AlertDialog.Builder(this)
                    .setTitle("Photo Access Required")
                    .setMessage("This app needs access to your photos to select images for emergency alerts.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestStoragePermission()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            storagePermissions, // Use the dynamic permissions array
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation()
                } else {
                    Toast.makeText(
                        this,
                        "Location permission denied. Alerts will be sent without location.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createImageFile()

        photoFile?.let {
            val photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                it
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            takePictureLauncher.launch(intent)
        }
    }

    @Throws(Exception::class)
    private fun createImageFile(): File {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun handleCameraImage() {
        currentPhotoPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                selectedImageUri = Uri.fromFile(file)
                displaySelectedImage(selectedImageUri!!)
                compressAndEncodeImage(selectedImageUri!!)
            }
        }
    }

    private fun handleGalleryImage(uri: Uri) {
        selectedImageUri = uri
        displaySelectedImage(uri)
        compressAndEncodeImage(uri)
    }

    private fun displaySelectedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            ivSelectedImage.setImageBitmap(bitmap)
            ivSelectedImage.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying image", e)
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressAndEncodeImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Compress image
            val outputStream = ByteArrayOutputStream()
            originalBitmap?.compress(Bitmap.CompressFormat.JPEG, Config.IMAGE_COMPRESSION_QUALITY, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // Convert to base64
            selectedImageBase64 = Base64.encodeToString(compressedBytes, Base64.DEFAULT)

        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission granted, get location
                getCurrentLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show explanation dialog
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("This app needs location access to send your current location in travel emergency alerts. This is especially important when traveling in unfamiliar areas.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestLocationPermission()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                // Request permission
                requestLocationPermission()
            }
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                    locationAddress = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                    Log.d(TAG, "Location obtained: $locationAddress")

                    // Optionally reverse geocode to get address
                    reverseGeocodeLocation(location)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location error", e)
        }
    }

    private fun reverseGeocodeLocation(location: Location) {
        try {
            val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                locationAddress = buildString {
                    address.getAddressLine(0)?.let { append(it) }
                }
                Log.d(TAG, "Address: $locationAddress")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            // Keep using coordinates if geocoding fails
        }
    }

    private fun getGoogleMapsLink(): String {
        return currentLocation?.let {
            "https://www.google.com/maps?q=${it.latitude},${it.longitude}"
        } ?: "Location unavailable"
    }

    private fun loadFinishData() {
        val finishRef = FirebaseDatabase.getInstance().getReference("finish").child(userId)

        finishRef.get().addOnSuccessListener { snapshot ->
            finishData = snapshot.getValue(FinishData::class.java)
            Log.d(TAG, "Finish data loaded: $finishData")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to load finish data", e)
            Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEmergencyContacts() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance()
            .reference
            .child("users")
            .child(currentUser.uid)
            .child("emergency_contacts")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contactsList.clear()
                for (contactSnapshot in snapshot.children) {
                    val contact = contactSnapshot.getValue(EmergencyContact::class.java)
                    contact?.let {
                        contactsList.add(it)
                        Log.d(TAG, "Loaded contact: ${it.fullName}, Email: ${it.email}")
                    }
                }

                contactsList.sortWith(compareBy {
                    when (it.priorityLevel) {
                        "High" -> 1
                        "Medium" -> 2
                        "Low" -> 3
                        else -> 4
                    }
                })
                displayContacts()
                updateContactCount()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load contacts", error.toException())
                Toast.makeText(this@TravelSafety, "Failed to load contacts: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayContacts() {
        contactsContainer.removeAllViews()
        if (contactsList.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No emergency contacts found. Please add contacts first."
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 14f
                setPadding(16, 16, 16, 16)
            }
            contactsContainer.addView(emptyView)
            return
        }
        for (contact in contactsList) {
            val contactView = createContactView(contact)
            contactsContainer.addView(contactView)
        }
    }

    private fun createContactView(contact: EmergencyContact): View {
        val contactView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val imgAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(getAvatarResource(contact))
        }
        contactView.addView(imgAvatar)

        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
            }
            orientation = LinearLayout.VERTICAL
        }

        val tvName = TextView(this).apply {
            text = contact.fullName
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentLayout.addView(tvName)

        val tvPhone = TextView(this).apply {
            text = contact.phoneNumber
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 14f
        }
        contentLayout.addView(tvPhone)

        if (!contact.email.isNullOrBlank()) {
            val tvEmail = TextView(this).apply {
                text = contact.email
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 12f
            }
            contentLayout.addView(tvEmail)
        }

        contactView.addView(contentLayout)
        return contactView
    }

    private fun getAvatarResource(contact: EmergencyContact): Int {
        return when {
            contact.fullName.contains("mom", ignoreCase = true) ||
                    contact.fullName.contains("mother", ignoreCase = true) ||
                    contact.relationship.equals("mother", ignoreCase = true) -> R.drawable.female

            contact.fullName.contains("dad", ignoreCase = true) ||
                    contact.fullName.contains("father", ignoreCase = true) ||
                    contact.relationship.equals("father", ignoreCase = true) -> R.drawable.male

            else -> R.drawable.male
        }
    }

    private fun updateContactCount() {
        val emailCount = contactsList.count { !it.email.isNullOrBlank() }
        tvContactCount.text = "Contacts to Notify ($emailCount with email / ${contactsList.size} total)"
    }

    private fun sendEmergencyAlert() {
        val contactsWithEmail = contactsList.filter { !it.email.isNullOrBlank() }

        if (contactsWithEmail.isEmpty()) {
            Toast.makeText(this, "No contacts with email found.", Toast.LENGTH_LONG).show()
            return
        }

        // Refresh location before sending
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        }

        showProgress()
        btnSendAlert.isEnabled = false
        btnSendAlert.alpha = 0.5f

        val message = buildAlertMessage(etAdditionalMessage.text.toString())

        // Save alert to local DB or Firebase based on connectivity
        lifecycleScope.launch {
            saveAlertToStorage(message, contactsWithEmail)
        }

        // Handle image upload and alert creation
        if (NetworkUtils.isNetworkAvailable(this)) {
            if (selectedImageBase64 != null) {
                // Upload image first, then send emails
                uploadImageAndCreateAlert(message, contactsWithEmail)
            } else {
                // No image, just send emails
                sendEmailsToContacts(contactsWithEmail, message)
            }
        } else {
            // Offline mode
            handleOfflineAlert(message, contactsWithEmail)
        }
    }

    private fun uploadImageAndCreateAlert(message: String, contacts: List<EmergencyContact>) {
        tvProgressStatus.text = "Uploading image..."

        val requestQueue = Volley.newRequestQueue(this)
        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("image_data", selectedImageBase64)
            put("emergency_type", "travel") // Changed to "travel"
            put("image_name", "travel_emergency_${System.currentTimeMillis()}.jpg")
            put("mime_type", "image/jpeg")
        }

        val uploadRequest = JsonObjectRequest(
            Request.Method.POST,
            Config.UPLOAD_IMAGE_ENDPOINT,
            jsonBody,
            { response ->
                val imageId = response.optString("image_id")
                if (imageId.isNotEmpty()) {
                    createImageAlert(message, contacts, imageId)
                } else {
                    // Image upload failed but continue without image
                    sendEmailsToContacts(contacts, message)
                }
            },
            { error ->
                Log.e(TAG, "Image upload failed", error)
                Toast.makeText(this, "Image upload failed, sending alert without image", Toast.LENGTH_SHORT).show()
                sendEmailsToContacts(contacts, message)
            }
        )

        requestQueue.add(uploadRequest)
    }

    private fun createImageAlert(message: String, contacts: List<EmergencyContact>, imageId: String) {
        val requestQueue = Volley.newRequestQueue(this)
        val locationData = currentLocation?.let {
            JSONObject().apply {
                put("latitude", it.latitude)
                put("longitude", it.longitude)
                put("address", locationAddress)
            }
        } ?: JSONObject()

        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("alert_id", generateAlertId())
            put("image_ids", JSONArray().put(imageId))
            put("message", message)
            put("location_data", locationData.toString())
            put("emergency_type", "travel") // Changed to "travel"
        }

        val alertRequest = JsonObjectRequest(
            Request.Method.POST,
            Config.CREATE_ALERT_ENDPOINT,
            jsonBody,
            { response ->
                Log.d(TAG, "Alert created in PHP: ${response.toString()}")
                sendEmailsToContacts(contacts, message)
            },
            { error ->
                Log.e(TAG, "Alert creation failed", error)
                // Continue sending emails even if alert creation fails
                sendEmailsToContacts(contacts, message)
            }
        )

        requestQueue.add(alertRequest)
    }

    private fun sendEmailsToContacts(contacts: List<EmergencyContact>, message: String) {
        emailsSent = 0
        emailsFailed = 0
        totalEmailsToSend = contacts.size

        for (contact in contacts) {
            sendEmail(contact.email!!, contact.fullName, message)
        }
    }

    private fun generateAlertId(): String {
        return "travel_alert_${userId}_${System.currentTimeMillis()}"
    }

    private fun handleOfflineAlert(message: String, contacts: List<EmergencyContact>) {
        tvProgressStatus.text = "Alert saved locally (offline)"
        Toast.makeText(this, "No internet. Alert saved locally and will sync when online.", Toast.LENGTH_LONG).show()

        btnSendAlert.postDelayed({
            hideProgress()
            finish()
        }, 2000)
    }

    private suspend fun saveAlertToStorage(message: String, contacts: List<EmergencyContact>) {
        val contactsJson = gson.toJson(contacts.map {
            hashMapOf(
                "name" to it.fullName,
                "phone" to it.phoneNumber,
                "email" to (it.email ?: ""),
                "priority" to it.priorityLevel
            )
        })

        val locationString = currentLocation?.let {
            "$locationAddress (${it.latitude}, ${it.longitude})"
        } ?: "Location unavailable"

        val result = alertRepository.saveAlert(
            userId = userId,
            userEmail = SENDER_EMAIL,
            type = "Travel Emergency",
            message = message,
            additionalMessage = etAdditionalMessage.text.toString(),
            contactsJson = contactsJson,
            contactsNotified = contacts.size,
            location = locationString
        )

        result.onSuccess {
            Log.d(TAG, "Alert saved: ${it}")
        }.onFailure {
            Log.e(TAG, "Failed to save alert", it)
        }
    }

    private fun showProgress() {
        progressContainer.visibility = View.VISIBLE
        progressBar.progress = 0
        tvProgressStatus.text = "Sending alerts..."
    }

    private fun hideProgress() {
        progressContainer.visibility = View.GONE
        btnSendAlert.isEnabled = true
        btnSendAlert.alpha = 1.0f
    }

    private fun updateProgress(completed: Int, total: Int) {
        val progress = if (total > 0) (completed * 100) / total else 0
        progressBar.progress = progress
        tvProgressStatus.text = "Sending alerts... ($completed/$total)"
    }

    private fun buildAlertMessage(additionalMessage: String): String {
        val sb = StringBuilder()
        sb.append("🚨 TRAVEL EMERGENCY ALERT 🚨\n\n")
        sb.append("From: $SENDER_EMAIL\n")
        sb.append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")

        // Add image info if available
        if (selectedImageUri != null) {
            sb.append("📷 IMAGE ATTACHED: A photo has been included with this alert\n\n")
        }

        // Add location information - CRITICAL FOR TRAVEL EMERGENCIES
        sb.append("📍 CURRENT LOCATION:\n")
        if (currentLocation != null) {
            sb.append("Address: $locationAddress\n")
            sb.append("Coordinates: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}\n")
            sb.append("Google Maps: ${getGoogleMapsLink()}\n\n")
        } else {
            sb.append("Location: Unable to determine current location\n\n")
        }

        if (additionalMessage.isNotEmpty()) sb.append("Message: $additionalMessage\n\n")

        finishData?.let { data ->
            sb.append("=== Medical Information ===\n")
            if (data.allergies.isNotEmpty()) sb.append("Allergies: ${data.allergies}\n")
            if (data.medication.isNotEmpty()) sb.append("Medication: ${data.medication}\n")
            if (data.notes.isNotEmpty()) sb.append("Medical Notes: ${data.notes}\n")
            if (data.locationEnabled) sb.append("\n📍 Location sharing is enabled\n")
        }

        sb.append("\n🌍 TRAVEL EMERGENCY - IMMEDIATE ASSISTANCE REQUIRED 🌍")
        sb.append("\nThis is an automated emergency alert. Please respond immediately.")
        return sb.toString()
    }

    private fun sendEmail(toEmail: String, contactName: String, message: String) {
        val url = "https://api.emailjs.com/api/v1.0/email/send"

        val jsonBody = JSONObject().apply {
            put("service_id", EMAILJS_SERVICE_ID)
            put("template_id", EMAILJS_TEMPLATE_ID)
            put("user_id", EMAILJS_PUBLIC_KEY)
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("contact_name", contactName)
                put("alert_message", message)
                // Add separate location fields for better email formatting
                if (currentLocation != null) {
                    put("location_address", locationAddress)
                    put("location_coordinates", "${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
                    put("google_maps_link", getGoogleMapsLink())
                }
            })
        }

        val request = object : JsonObjectRequest(
            POST, url, jsonBody,
            { response ->
                emailsSent++
                updateProgress(emailsSent + emailsFailed, totalEmailsToSend)
                checkIfAllEmailsSent()
            },
            { error ->
                emailsFailed++
                updateProgress(emailsSent + emailsFailed, totalEmailsToSend)
                checkIfAllEmailsSent()
                Log.e(TAG, "Error sending email: ${error.message}")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "Content-Type" to "application/json",
                    "origin" to "http://localhost",
                    "Authorization" to "Bearer $EMAILJS_PUBLIC_KEY"
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun checkIfAllEmailsSent() {
        if (emailsSent + emailsFailed >= totalEmailsToSend) {
            runOnUiThread {
                updateProgress(totalEmailsToSend, totalEmailsToSend)


                btnSendAlert.postDelayed({
                    hideProgress()
                    if (emailsSent > 0) finish()
                }, 3000)
            }
        }
    }
}