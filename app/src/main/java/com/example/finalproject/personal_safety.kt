package com.example.finalproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request.Method.*
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONObject

class personal_safety : AppCompatActivity() {

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

    // Email tracking
    private var emailsSent = 0
    private var emailsFailed = 0
    private var totalEmailsToSend = 0

    // Get sender email from logged in user
    private val SENDER_EMAIL = FirebaseAuth.getInstance().currentUser?.email ?: "noreply@safeme.com"

    // EmailJS credentials (replace with your real values)
    private val EMAILJS_SERVICE_ID = "service_7c31t4s"
    private val EMAILJS_TEMPLATE_ID = "template_6woyk8b"
    private val EMAILJS_PUBLIC_KEY = "z0XPnqySWvklrNwlF" // user_id / public key

    companion object {
        private const val TAG = "PersonalSafety"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.personal_safety)

        initializeViews()
        setupClickListeners()
        loadFinishData()
        loadEmergencyContacts()

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
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnSendAlert.setOnClickListener { sendEmergencyAlert() }
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
        database = FirebaseDatabase.getInstance().getReference("emergency_contacts")

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
                // Sort by priority: High -> Medium -> Low
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
                Toast.makeText(this@personal_safety, "Failed to load contacts: ${error.message}", Toast.LENGTH_SHORT).show()
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
        if (contactsList.isEmpty()) {
            Toast.makeText(this, "No emergency contacts available. Please add contacts first.", Toast.LENGTH_LONG).show()
            return
        }

        // Validate contacts have emails
        val contactsWithEmail = contactsList.filter { !it.email.isNullOrBlank() }

        if (contactsWithEmail.isEmpty()) {
            Toast.makeText(this, "No contacts have email addresses! Please add email addresses to your contacts.", Toast.LENGTH_LONG).show()
            return
        }

        // Show progress UI
        showProgress()

        // Disable button to prevent multiple clicks
        btnSendAlert.isEnabled = false
        btnSendAlert.alpha = 0.5f

        val additionalMessage = etAdditionalMessage.text.toString().trim()
        val alertMessage = buildAlertMessage(additionalMessage)

        // Save alert in Firebase
        saveAlertToFirebase(alertMessage)

        // Reset counters
        emailsSent = 0
        emailsFailed = 0
        totalEmailsToSend = contactsWithEmail.size

        Log.d(TAG, "Sending alerts to ${contactsWithEmail.size} contacts")
        updateProgress(0, totalEmailsToSend)

        // Send emails (using EmailJS)
        // If you send many emails, consider spacing them out to avoid provider limits.
        for ((index, contact) in contactsWithEmail.withIndex()) {
            contact.email?.let { email ->
                // Optional: stagger requests slightly to avoid rate limits:
                // Handler(Looper.getMainLooper()).postDelayed({ sendEmail(email, contact.fullName, alertMessage) }, index * 250L)
                sendEmail(email, contact.fullName, alertMessage)
            }
        }

        Toast.makeText(this, "Sending alerts to ${contactsWithEmail.size} contacts...", Toast.LENGTH_SHORT).show()
    }
        // Save alert to Firebase with location
        saveAlertToFirebase(alertMessage, additionalMessage)

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
        sb.append("🚨 PERSONAL EMERGENCY ALERT 🚨\n\n")
        sb.append("From: $SENDER_EMAIL\n")
        sb.append("From: ${FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"}\n")
        sb.append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
        if (additionalMessage.isNotEmpty()) sb.append("Message: $additionalMessage\n\n")

        if (additionalMessage.isNotEmpty()) {
            sb.append("Message: $additionalMessage\n\n")
        }

        finishData?.let { data ->
            sb.append("=== Medical Information ===\n")
            if (data.allergies.isNotEmpty()) sb.append("Allergies: ${data.allergies}\n")
            if (data.medication.isNotEmpty()) sb.append("Medication: ${data.medication}\n")
            if (data.notes.isNotEmpty()) sb.append("Medical Notes: ${data.notes}\n")
            if (data.locationEnabled) sb.append("\n📍 Location sharing is enabled\n")
        }

        sb.append("\nThis is an automated emergency alert. Please respond immediately.")
        return sb.toString()
    }

    private fun saveAlertToFirebase(message: String, additionalMessage: String) {
        val alertData = hashMapOf(
            "userId" to userId,
            "userEmail" to SENDER_EMAIL,
            "type" to "Personal Emergency",
            "message" to message,
            "additionalMessage" to additionalMessage,
            "timestamp" to System.currentTimeMillis(),
            "contactsNotified" to contactsList.size,
            "location" to "Current Location", // You can integrate actual location here
            "status" to "Unresolved",
            "contacts" to contactsList.map {
                hashMapOf(
                    "name" to it.fullName,
                    "phone" to it.phoneNumber,
                    "email" to (it.email ?: ""),
                    "priority" to it.priorityLevel
                )
            }
        )

        FirebaseDatabase.getInstance()
            .getReference("emergency_alerts")
            .push()
            .setValue(alertData)
            .addOnSuccessListener { Log.d(TAG, "Alert saved to Firebase successfully") }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save alert to Firebase", e)
                Toast.makeText(this, "Failed to log alert: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                Log.e("EmailJS", "Error sending email: ${error.message}")
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

                val message = when {
                    emailsFailed == 0 -> {
                        tvProgressStatus.text = "✓ All alerts sent successfully!"
                        "✓ All alerts sent successfully! ($emailsSent/$totalEmailsToSend)"
                    }
                    emailsSent == 0 -> {
                        tvProgressStatus.text = "✗ Failed to send all alerts"
                        "✗ Failed to send all alerts. Please check your internet connection and try again."
                    }
                    else -> {
                        tvProgressStatus.text = "⚠ Partially sent: $emailsSent/$totalEmailsToSend"
                        "⚠ Sent: $emailsSent, Failed: $emailsFailed (Total: $totalEmailsToSend)"
                    }
                }

                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.d(TAG, "Email sending complete: $message")

                // Hide progress and finish after delay
                btnSendAlert.postDelayed({
                    hideProgress()
                    if (emailsSent > 0) finish()
                }, 3000)
            }
        }
    }
}
