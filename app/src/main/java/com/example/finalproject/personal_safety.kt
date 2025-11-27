package com.example.finalproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request.Method.POST
import com.android.volley.toolbox.JsonObjectRequest
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

    // EmailJS
    private val SENDER_EMAIL = FirebaseAuth.getInstance().currentUser?.email ?: "noreply@safeme.com"
    private val EMAILJS_SERVICE_ID = "service_7c31t4s"
    private val EMAILJS_TEMPLATE_ID = "template_6woyk8b"
    private val EMAILJS_PUBLIC_KEY = "z0XPnqySWvklrNwlF"

    // Progress counters
    private var emailsSent = 0
    private var emailsFailed = 0
    private var totalEmailsToSend = 0

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
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        etAdditionalMessage = findViewById(R.id.etAdditionalMessage)
        btnSendAlert = findViewById(R.id.btnSendAlert)
        tvContactCount = findViewById(R.id.tvContactCount)
        contactsContainer = findViewById(R.id.contactsContainer)

        progressContainer = findViewById(R.id.progressContainer)
        progressBar = findViewById(R.id.progressBar)
        tvProgressStatus = findViewById(R.id.tvProgressStatus)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnSendAlert.setOnClickListener { sendEmergencyAlert() }
    }

    private fun loadFinishData() {
        FirebaseDatabase.getInstance()
            .getReference("finish")
            .child(userId)
            .get()
            .addOnSuccessListener {
                finishData = it.getValue(FinishData::class.java)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
            }
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

                // Sort by priority
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
                Toast.makeText(this@personal_safety, "Failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayContacts() {
        contactsContainer.removeAllViews()

        if (contactsList.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No emergency contacts found."
                setPadding(16, 16, 16, 16)
            }
            contactsContainer.addView(empty)
            return
        }

        for (contact in contactsList) {
            contactsContainer.addView(createContactView(contact))
        }
    }

    private fun createContactView(contact: EmergencyContact): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val avatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(getAvatarResource(contact))
        }
        layout.addView(avatar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 12 }
        }

        val name = TextView(this).apply {
            text = contact.fullName
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 16f
        }

        val phone = TextView(this).apply {
            text = contact.phoneNumber
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 14f
        }

        content.addView(name)
        content.addView(phone)

        // Show email if available
        if (!contact.email.isNullOrBlank()) {
            val email = TextView(this).apply {
                text = contact.email
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 12f
            }
            content.addView(email)
        }

        layout.addView(content)
        return layout
    }

    private fun getAvatarResource(contact: EmergencyContact): Int {
        return when {
            contact.relationship.equals("mother", true) -> R.drawable.female
            contact.relationship.equals("father", true) -> R.drawable.male
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

        // Show progress
        showProgress()

        btnSendAlert.isEnabled = false
        btnSendAlert.alpha = 0.5f

        val message = buildAlertMessage(etAdditionalMessage.text.toString())
        saveAlertToFirebase(message)

        emailsSent = 0
        emailsFailed = 0
        totalEmailsToSend = contactsWithEmail.size

        for (contact in contactsWithEmail) {
            sendEmail(contact.email!!, contact.fullName, message)
        }

        Toast.makeText(this, "Sending alerts...", Toast.LENGTH_SHORT).show()
    }

    private fun showProgress() {
        progressContainer.visibility = View.VISIBLE
        progressBar.progress = 0
        tvProgressStatus.text = "Sending alerts..."
    }

    private fun hideProgress() {
        progressContainer.visibility = View.GONE
        btnSendAlert.isEnabled = true
        btnSendAlert.alpha = 1f
    }

    private fun updateProgress(done: Int, total: Int) {
        val percent = (done * 100) / total
        progressBar.progress = percent
        tvProgressStatus.text = "Sending alerts... ($done/$total)"
    }

    private fun buildAlertMessage(extra: String): String {
        val sb = StringBuilder()
        sb.append("🚨 PERSONAL SAFETY ALERT 🚨\n\n")
        sb.append("From: $SENDER_EMAIL\n")
        sb.append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}\n\n")

        if (extra.isNotEmpty())
            sb.append("Message: $extra\n\n")

        finishData?.let {
            sb.append("=== Medical Info ===\n")
            if (it.allergies.isNotEmpty()) sb.append("Allergies: ${it.allergies}\n")
            if (it.medication.isNotEmpty()) sb.append("Medication: ${it.medication}\n")
            if (it.notes.isNotEmpty()) sb.append("Notes: ${it.notes}\n")
        }

        sb.append("\nThis is an automated alert. Respond immediately.")
        return sb.toString()
    }

    private fun saveAlertToFirebase(msg: String) {
        val data = hashMapOf(
            "userId" to userId,
            "userEmail" to SENDER_EMAIL,
            "type" to "Personal Safety",
            "message" to msg,
            "timestamp" to System.currentTimeMillis(),
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
            .setValue(data)
    }

    private fun sendEmail(toEmail: String, name: String, message: String) {
        val url = "https://api.emailjs.com/api/v1.0/email/send"

        val jsonBody = JSONObject().apply {
            put("service_id", EMAILJS_SERVICE_ID)
            put("template_id", EMAILJS_TEMPLATE_ID)
            put("user_id", EMAILJS_PUBLIC_KEY)
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("contact_name", name)
                put("alert_message", message)
            })
        }

        val request = object : JsonObjectRequest(POST, url, jsonBody,
            {
                emailsSent++
                updateProgress(emailsSent + emailsFailed, totalEmailsToSend)
                checkCompletion()
            },
            {
                emailsFailed++
                updateProgress(emailsSent + emailsFailed, totalEmailsToSend)
                checkCompletion()
            }) {

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

    private fun checkCompletion() {
        if (emailsSent + emailsFailed >= totalEmailsToSend) {
            tvProgressStatus.text = when {
                emailsFailed == 0 -> "✓ All alerts sent!"
                emailsSent == 0 -> "✗ Failed to send alerts"
                else -> "⚠ Partial: $emailsSent sent, $emailsFailed failed"
            }

            btnSendAlert.postDelayed({
                hideProgress()
                finish()
            }, 2500)
        }
    }
}
