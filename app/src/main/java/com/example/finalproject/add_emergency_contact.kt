package com.example.finalproject

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class add_emergency_contact : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
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

        // Initialize database reference with user-specific path
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

        // Update button text if in edit mode
        if (isEditMode) {
            btnAddContact.text = "Save Changes"
        }
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
            finish()
        }

        btnAddContact.setOnClickListener {
            if (isEditMode) {
                validateAndUpdateContact()
            } else {
                validateAndSaveContact()
            }
        }
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
            notes = etNotes.text.toString().trim()
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
                    "Emergency contact added successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to add contact: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateContactInFirebase(contactId: String, contact: EmergencyContact) {
        database.child(contactId).setValue(contact)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Contact updated successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to update contact: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}