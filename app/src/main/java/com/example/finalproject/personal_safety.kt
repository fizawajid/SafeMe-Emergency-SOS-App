package com.example.finalproject

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class personal_safety : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var btnBack: ImageView
    private lateinit var etAdditionalMessage: EditText
    private lateinit var contactsContainer: LinearLayout
    private lateinit var btnSendAlert: TextView
    private lateinit var tvContactCount: TextView

    private val contactsList = mutableListOf<EmergencyContact>()
    private var finishData: FinishData? = null
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"

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
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSendAlert.setOnClickListener {
            sendEmergencyAlert()
        }
    }

    private fun loadFinishData() {
        val finishRef = FirebaseDatabase.getInstance()
            .getReference("finish")
            .child(userId)

        finishRef.get().addOnSuccessListener { snapshot ->
            finishData = snapshot.getValue(FinishData::class.java)
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEmergencyContacts() {
        database = FirebaseDatabase.getInstance()
            .getReference("emergency_contacts")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contactsList.clear()

                for (contactSnapshot in snapshot.children) {
                    val contact = contactSnapshot.getValue(EmergencyContact::class.java)
                    contact?.let { contactsList.add(it) }
                }

                contactsList.sortWith(compareBy {
                    when(it.priorityLevel) {
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
                Toast.makeText(
                    this@personal_safety,
                    "Failed to load contacts: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
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
            ).apply {
                bottomMargin = 16
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val imgAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(getAvatarResource(contact))
        }
        contactView.addView(imgAvatar)

        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
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
        tvContactCount?.text = "Contacts to Notify (${contactsList.size})"
    }

    private fun sendEmergencyAlert() {
        if (contactsList.isEmpty()) {
            Toast.makeText(
                this,
                "No emergency contacts available. Please add contacts first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val additionalMessage = etAdditionalMessage.text.toString().trim()
        val alertMessage = buildAlertMessage(additionalMessage)

        // Save alert to Firebase with location
        saveAlertToFirebase(alertMessage, additionalMessage)

        Toast.makeText(
            this,
            "Emergency alert sent to ${contactsList.size} contacts",
            Toast.LENGTH_LONG
        ).show()

        finish()
    }

    private fun buildAlertMessage(additionalMessage: String): String {
        val sb = StringBuilder()
        sb.append("🚨 PERSONAL EMERGENCY ALERT 🚨\n\n")
        sb.append("From: ${FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"}\n")
        sb.append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")

        if (additionalMessage.isNotEmpty()) {
            sb.append("Message: $additionalMessage\n\n")
        }

        finishData?.let { data ->
            if (data.allergies.isNotEmpty()) {
                sb.append("Allergies: ${data.allergies}\n")
            }
            if (data.medication.isNotEmpty()) {
                sb.append("Medication: ${data.medication}\n")
            }
            if (data.notes.isNotEmpty()) {
                sb.append("Medical Notes: ${data.notes}\n")
            }
            if (data.locationEnabled) {
                sb.append("\n📍 Location sharing is enabled\n")
            }
        }

        sb.append("\nThis is an automated emergency alert. Please respond immediately.")
        return sb.toString()
    }

    private fun saveAlertToFirebase(message: String, additionalMessage: String) {
        val alertData = hashMapOf(
            "userId" to userId,
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
                    "email" to it.email,
                    "priority" to it.priorityLevel
                )
            }
        )

        val alertRef = FirebaseDatabase.getInstance()
            .getReference("emergency_alerts")
            .push()

        alertRef.setValue(alertData)
            .addOnSuccessListener {
                // Alert saved successfully
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to log alert: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}