package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class add_emergency_contact : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    // Form fields
    private lateinit var etFullName: EditText
    private lateinit var spinnerRelationship: Spinner
    private lateinit var spinnerPriority: Spinner
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var cbSms: CheckBox
    private lateinit var cbCall: CheckBox
    private lateinit var cbEmail: CheckBox
    private lateinit var etMedicalInfo: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnCancel: TextView
    private lateinit var btnAddContact: TextView
    private lateinit var btnBack: ImageButton

    // Contact list display
    private lateinit var contactsContainer: LinearLayout
    private lateinit var tvContactCount: TextView
    private val contactsList = mutableListOf<EmergencyContact>()

    // Edit mode variables
    private var isEditMode = false
    private var editContactId: String? = null
    private var existingContact: EmergencyContact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_emergency_contact)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // FIXED: Use user-specific path instead of global path
        database = FirebaseDatabase.getInstance()
            .reference
            .child("users")
            .child(currentUser.uid)
            .child("emergency_contacts")

        // Check if we're in edit mode
        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        editContactId = intent.getStringExtra("CONTACT_ID")

        // Initialize views
        initializeViews()
        setupSpinners()
        setupClickListeners()

        // Load and display existing contacts
        loadEmergencyContacts()

        // Load existing contact data if in edit mode
        if (isEditMode && editContactId != null) {
            loadContactData()
        }
    }

    private fun initializeViews() {
        etFullName = findViewById(R.id.et_full_name)
        spinnerRelationship = findViewById(R.id.spinner_relationship)
        spinnerPriority = findViewById(R.id.spinner_priority)
        etPhone = findViewById(R.id.et_phone)
        etEmail = findViewById(R.id.et_email)
        cbSms = findViewById(R.id.cb_sms)
        cbCall = findViewById(R.id.cb_call)
        cbEmail = findViewById(R.id.cb_email)
        etMedicalInfo = findViewById(R.id.et_medical_info)
        etNotes = findViewById(R.id.et_notes)
        btnCancel = findViewById(R.id.btn_cancel)
        btnAddContact = findViewById(R.id.btn_add_contact)
        btnBack = findViewById(R.id.btn_back)

        // Initialize contact list views (add these to your layout if not present)
        try {
            contactsContainer = findViewById(R.id.contactsContainer)
            tvContactCount = findViewById(R.id.tvContactCount)
        } catch (e: Exception) {
            // If these views don't exist in layout, that's okay
            android.util.Log.w("AddContact", "Contact list views not found in layout")
        }

        // Update button text if in edit mode
        if (isEditMode) {
            btnAddContact.text = "Save Changes"
        }
    }

    private fun loadEmergencyContacts() {
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
                Toast.makeText(this@add_emergency_contact, "Failed to load contacts: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayContacts() {
        // Check if contactsContainer exists
        if (!::contactsContainer.isInitialized) return

        contactsContainer.removeAllViews()

        if (contactsList.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No emergency contacts yet. Add your first contact above."
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
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
                setPadding(12, 12, 12, 12)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }

        // Avatar
        val imgAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(getAvatarResource(contact))
        }
        contactView.addView(imgAvatar)

        // Content
        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
            }
            orientation = LinearLayout.VERTICAL
        }

        val tvName = TextView(this).apply {
            text = contact.fullName
            setTextColor(resources.getColor(android.R.color.black, null))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentLayout.addView(tvName)

        val tvPhone = TextView(this).apply {
            text = "${contact.phoneNumber} • ${contact.priorityLevel} Priority"
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
            textSize = 14f
        }
        contentLayout.addView(tvPhone)

        if (!contact.email.isNullOrBlank()) {
            val tvEmail = TextView(this).apply {
                text = contact.email
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                textSize = 12f
            }
            contentLayout.addView(tvEmail)
        }

        contactView.addView(contentLayout)

        // Delete button
        val btnDelete = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40)
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(resources.getColor(android.R.color.transparent, null))
            setOnClickListener {
                deleteContact(contact)
            }
        }
        contactView.addView(btnDelete)

        return contactView
    }

    private fun getAvatarResource(contact: EmergencyContact): Int {
        return when {
            contact.fullName.contains("mom", ignoreCase = true) ||
                    contact.fullName.contains("mother", ignoreCase = true) ||
                    contact.relationship.equals("mother", ignoreCase = true) -> android.R.drawable.ic_menu_myplaces

            contact.fullName.contains("dad", ignoreCase = true) ||
                    contact.fullName.contains("father", ignoreCase = true) ||
                    contact.relationship.equals("father", ignoreCase = true) -> android.R.drawable.ic_menu_myplaces

            else -> android.R.drawable.ic_menu_myplaces
        }
    }

    private fun updateContactCount() {
        if (!::tvContactCount.isInitialized) return

        val emailCount = contactsList.count { !it.email.isNullOrBlank() }
        tvContactCount.text = "Total Contacts: ${contactsList.size} ($emailCount with email)"
    }

    private fun deleteContact(contact: EmergencyContact) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.fullName}?")
            .setPositiveButton("Delete") { _, _ ->
                database.child(contact.id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to delete contact", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSpinners() {
        // Relationship Spinner
        val relationships = arrayOf(
            "Select Relationship",
            "Parent",
            "Sibling",
            "Spouse",
            "Child",
            "Friend",
            "Doctor",
            "Neighbor",
            "Colleague",
            "Other"
        )
        val relationshipAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            relationships
        )
        relationshipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRelationship.adapter = relationshipAdapter

        // Priority Spinner
        val priorities = arrayOf(
            "Select Priority",
            "High",
            "Medium",
            "Low"
        )
        val priorityAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            priorities
        )
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnCancel.setOnClickListener {
            clearForm()
        }

        btnAddContact.setOnClickListener {
            if (isEditMode) {
                validateAndUpdateContact()
            } else {
                validateAndSaveContact()
            }
        }
    }

    private fun clearForm() {
        etFullName.text.clear()
        etPhone.text.clear()
        etEmail.text.clear()
        etMedicalInfo.text.clear()
        etNotes.text.clear()
        spinnerRelationship.setSelection(0)
        spinnerPriority.setSelection(0)
        cbSms.isChecked = false
        cbCall.isChecked = false
        cbEmail.isChecked = false
    }

    private fun loadContactData() {
        editContactId?.let { id ->
            database.child(id).get().addOnSuccessListener { snapshot ->
                val contact = snapshot.getValue(EmergencyContact::class.java)
                contact?.let {
                    existingContact = it
                    populateFields(it)
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to load contact data", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun populateFields(contact: EmergencyContact) {
        etFullName.setText(contact.fullName)
        etPhone.setText(contact.phoneNumber)
        etEmail.setText(contact.email)
        etMedicalInfo.setText(contact.medicalInfo)
        etNotes.setText(contact.notes)

        cbSms.isChecked = contact.smsEnabled
        cbCall.isChecked = contact.callEnabled
        cbEmail.isChecked = contact.emailEnabled

        // Set spinner selections
        val relationshipAdapter = spinnerRelationship.adapter as ArrayAdapter<String>
        val relationshipPosition = relationshipAdapter.getPosition(contact.relationship)
        if (relationshipPosition >= 0) {
            spinnerRelationship.setSelection(relationshipPosition)
        }

        val priorityAdapter = spinnerPriority.adapter as ArrayAdapter<String>
        val priorityPosition = priorityAdapter.getPosition(contact.priorityLevel)
        if (priorityPosition >= 0) {
            spinnerPriority.setSelection(priorityPosition)
        }
    }

    private fun validateAndSaveContact() {
        val fullName = etFullName.text.toString().trim()
        val relationship = spinnerRelationship.selectedItem.toString()
        val priority = spinnerPriority.selectedItem.toString()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()

        // Validation
        if (fullName.isEmpty()) {
            etFullName.error = "Please enter full name"
            etFullName.requestFocus()
            return
        }

        if (relationship == "Select Relationship") {
            Toast.makeText(this, "Please select a relationship", Toast.LENGTH_SHORT).show()
            return
        }

        if (priority == "Select Priority") {
            Toast.makeText(this, "Please select a priority level", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            etPhone.error = "Please enter phone number"
            etPhone.requestFocus()
            return
        }

        if (email.isEmpty()) {
            etEmail.error = "Please enter email address"
            etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address"
            etEmail.requestFocus()
            return
        }

        // Create contact object
        val contactId = database.push().key ?: return

        val contact = EmergencyContact(
            id = contactId,
            fullName = fullName,
            relationship = relationship,
            priorityLevel = priority,
            phoneNumber = phone,
            email = email,
            smsEnabled = cbSms.isChecked,
            callEnabled = cbCall.isChecked,
            emailEnabled = cbEmail.isChecked,
            medicalInfo = etMedicalInfo.text.toString().trim(),
            notes = etNotes.text.toString().trim(),
            timestamp = System.currentTimeMillis()
        )

        // Save to Firebase
        saveContactToFirebase(contactId, contact)
    }

    private fun validateAndUpdateContact() {
        val fullName = etFullName.text.toString().trim()
        val relationship = spinnerRelationship.selectedItem.toString()
        val priority = spinnerPriority.selectedItem.toString()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()

        // Validation
        if (fullName.isEmpty()) {
            etFullName.error = "Please enter full name"
            etFullName.requestFocus()
            return
        }

        if (relationship == "Select Relationship") {
            Toast.makeText(this, "Please select a relationship", Toast.LENGTH_SHORT).show()
            return
        }

        if (priority == "Select Priority") {
            Toast.makeText(this, "Please select a priority level", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.isEmpty()) {
            etPhone.error = "Please enter phone number"
            etPhone.requestFocus()
            return
        }

        if (email.isEmpty()) {
            etEmail.error = "Please enter email address"
            etEmail.requestFocus()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address"
            etEmail.requestFocus()
            return
        }

        // Update contact object
        editContactId?.let { id ->
            val updatedContact = EmergencyContact(
                id = id,
                fullName = fullName,
                relationship = relationship,
                priorityLevel = priority,
                phoneNumber = phone,
                email = email,
                smsEnabled = cbSms.isChecked,
                callEnabled = cbCall.isChecked,
                emailEnabled = cbEmail.isChecked,
                medicalInfo = etMedicalInfo.text.toString().trim(),
                notes = etNotes.text.toString().trim(),
                timestamp = existingContact?.timestamp ?: System.currentTimeMillis()
            )

            updateContactInFirebase(id, updatedContact)
        }
    }

    private fun saveContactToFirebase(contactId: String, contact: EmergencyContact) {
        database.child(contactId).setValue(contact)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "✓ Emergency contact added successfully",
                    Toast.LENGTH_SHORT
                ).show()

                val currentUser = auth.currentUser
                // Log for debugging
                android.util.Log.d("AddContact", "Contact saved: $contactId at path: users/${currentUser?.uid}/emergency_contacts/$contactId")

                // Clear form after successful save
                clearForm()
                navigateToEmergencyContacts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to add contact: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                // Log error
                android.util.Log.e("AddContact", "Failed to save contact", e)
            }
    }

    private fun navigateToEmergencyContacts() {
        val intent = Intent(this, emergency_contacts::class.java)
        // Clear the back stack so user doesn't come back to this form
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun updateContactInFirebase(contactId: String, contact: EmergencyContact) {
        database.child(contactId).setValue(contact)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "✓ Contact updated successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Log for debugging
                android.util.Log.d("AddContact", "Contact updated: $contactId")

                clearForm()
                isEditMode = false
                editContactId = null
                btnAddContact.text = "Add Contact"
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to update contact: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                // Log error
                android.util.Log.e("AddContact", "Failed to update contact", e)
            }
    }
}