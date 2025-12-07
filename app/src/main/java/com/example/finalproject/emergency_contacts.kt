package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class emergency_contacts : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var contactsContainer: LinearLayout
    private lateinit var searchContacts: EditText
    private lateinit var btnAddContact: TextView
    private lateinit var icBack: ImageView
    private lateinit var emptyState: LinearLayout
    private lateinit var summarySection: LinearLayout
    private val contactsList = mutableListOf<EmergencyContact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.emergency_contacts)

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

        // Initialize views
        initializeViews()
        setupClickListeners()
        loadContactsFromFirebase()
    }

    private fun initializeViews() {
        btnAddContact = findViewById(R.id.btn_add_contact)
        searchContacts = findViewById(R.id.search_contacts)
        icBack = findViewById(R.id.ic_back)
        emptyState = findViewById(R.id.empty_state)
        summarySection = findViewById(R.id.summary_section)

        // Get the contacts container (LinearLayout inside ScrollView)
        contactsContainer = findViewById(R.id.contacts_container)
    }

    private fun setupClickListeners() {
        btnAddContact.setOnClickListener {
            val intent = Intent(this, add_emergency_contact::class.java)
            startActivity(intent)
        }

        icBack.setOnClickListener {
            finish()
        }

        // Search functionality
        searchContacts.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterContacts(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadContactsFromFirebase()
    }

    private fun loadContactsFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contactsList.clear()

                for (contactSnapshot in snapshot.children) {
                    val contact = contactSnapshot.getValue(EmergencyContact::class.java)
                    contact?.let {
                        contactsList.add(it)
                        android.util.Log.d("EmergencyContacts", "Loaded contact: ${it.fullName}")
                    }
                }

                android.util.Log.d("EmergencyContacts", "Total contacts loaded: ${contactsList.size}")

                // Sort by priority: High -> Medium -> Low
                contactsList.sortWith(compareBy {
                    when(it.priorityLevel) {
                        "High" -> 1
                        "Medium" -> 2
                        "Low" -> 3
                        else -> 4
                    }
                })

                // Show/hide empty state
                if (contactsList.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    summarySection.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    summarySection.visibility = View.VISIBLE
                }

                displayContacts(contactsList)
                updateContactSummary()
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("EmergencyContacts", "Failed to load contacts: ${error.message}")
                Toast.makeText(
                    this@emergency_contacts,
                    "Failed to load contacts: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun displayContacts(contacts: List<EmergencyContact>) {
        // Remove all dynamically added contact items
        val childCount = contactsContainer.childCount
        for (i in childCount - 1 downTo 0) {
            val child = contactsContainer.getChildAt(i)
            if (child.tag == "contact_item") {
                contactsContainer.removeViewAt(i)
            }
        }

        // Add new contact items
        val inflater = LayoutInflater.from(this)
        for (contact in contacts) {
            val contactView = createContactView(contact, inflater)
            contactView.tag = "contact_item"
            // Insert before the summary section (last item)
            val insertIndex = contactsContainer.childCount - 1
            contactsContainer.addView(contactView, insertIndex)
        }

        android.util.Log.d("EmergencyContacts", "Displayed ${contacts.size} contacts")
    }

    private fun createContactView(contact: EmergencyContact, inflater: LayoutInflater): View {
        // Create the contact item layout programmatically
        val contactView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    resources.getDimensionPixelSize(R.dimen.margin_standard),
                    0,
                    resources.getDimensionPixelSize(R.dimen.margin_standard),
                    resources.getDimensionPixelSize(R.dimen.margin_medium)
                )
            }
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.padding_standard),
                resources.getDimensionPixelSize(R.dimen.padding_standard),
                resources.getDimensionPixelSize(R.dimen.padding_standard),
                resources.getDimensionPixelSize(R.dimen.padding_standard)
            )
            setBackgroundResource(R.drawable.contact_item_background)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Avatar
        val imgAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.avatar_size),
                resources.getDimensionPixelSize(R.dimen.avatar_size)
            )
            setBackgroundResource(getAvatarResource(contact))
        }
        contactView.addView(imgAvatar)

        // Content container
        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.margin_medium)
            }
            orientation = LinearLayout.VERTICAL
        }

        // Name and priority row
        val nameRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvName = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = contact.fullName
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        nameRow.addView(tvName)

        val tvPriority = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.margin_small)
            }
            text = contact.priorityLevel
            setTextColor(getPriorityTextColor(contact.priorityLevel))
            textSize = 11f
            setBackgroundResource(getPriorityBackground(contact.priorityLevel))
            setPadding(
                resources.getDimensionPixelSize(R.dimen.padding_small),
                resources.getDimensionPixelSize(R.dimen.padding_tiny),
                resources.getDimensionPixelSize(R.dimen.padding_small),
                resources.getDimensionPixelSize(R.dimen.padding_tiny)
            )
        }
        nameRow.addView(tvPriority)
        contentLayout.addView(nameRow)

        // Phone
        val tvPhone = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.margin_tiny)
            }
            text = contact.phoneNumber
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 13f
        }
        contentLayout.addView(tvPhone)

        // Email
        val tvEmail = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.margin_tiny)
            }
            text = contact.email
            setTextColor(resources.getColor(R.color.text_tertiary, null))
            textSize = 12f
        }
        contentLayout.addView(tvEmail)

        contactView.addView(contentLayout)

        // Menu button
        val imgMenu = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.icon_size_small),
                resources.getDimensionPixelSize(R.dimen.icon_size_small)
            )
            setImageResource(R.drawable.ic_menu)
            setColorFilter(resources.getColor(R.color.icon_tint, null))
            isClickable = true
            isFocusable = true
            setOnClickListener { view ->
                showContactOptions(contact, view)
            }
        }
        contactView.addView(imgMenu)

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

            contact.relationship.equals("doctor", ignoreCase = true) ||
                    contact.fullName.startsWith("Dr.", ignoreCase = true) -> R.drawable.female

            else -> R.drawable.male
        }
    }

    private fun getPriorityBackground(priority: String): Int {
        return when (priority) {
            "High" -> R.drawable.priority_high
            "Medium" -> R.drawable.priority_medium
            "Low" -> R.drawable.priority_low
            else -> R.drawable.priority_medium
        }
    }

    private fun getPriorityTextColor(priority: String): Int {
        return when (priority) {
            "High" -> resources.getColor(android.R.color.white, null)
            "Medium" -> resources.getColor(android.R.color.black, null)
            "Low" -> resources.getColor(android.R.color.white, null)
            else -> resources.getColor(android.R.color.white, null)
        }
    }

    private fun showContactOptions(contact: EmergencyContact, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.contact_options_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit -> {
                    editContact(contact)
                    true
                }
                R.id.menu_delete -> {
                    deleteContact(contact)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun editContact(contact: EmergencyContact) {
        val intent = Intent(this, add_emergency_contact::class.java).apply {
            putExtra("EDIT_MODE", true)
            putExtra("CONTACT_ID", contact.id)
        }
        startActivity(intent)
    }

    private fun deleteContact(contact: EmergencyContact) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.fullName}?")
            .setPositiveButton("Delete") { _, _ ->
                database.child(contact.id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "✓ Contact deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterContacts(query: String) {
        val filteredList = if (query.isEmpty()) {
            contactsList
        } else {
            contactsList.filter { contact ->
                contact.fullName.contains(query, ignoreCase = true) ||
                        contact.phoneNumber.contains(query) ||
                        contact.email.contains(query, ignoreCase = true) ||
                        contact.relationship.contains(query, ignoreCase = true)
            }
        }
        displayContacts(filteredList)
    }

    private fun updateContactSummary() {
        val highCount = contactsList.count { it.priorityLevel == "High" }
        val mediumCount = contactsList.count { it.priorityLevel == "Medium" }
        val lowCount = contactsList.count { it.priorityLevel == "Low" }

        findViewById<TextView>(R.id.tv_high_count)?.text = highCount.toString()
        findViewById<TextView>(R.id.tv_medium_count)?.text = mediumCount.toString()
        findViewById<TextView>(R.id.tv_low_count)?.text = lowCount.toString()

        val totalContacts = contactsList.size
        findViewById<TextView>(R.id.tv_contacts_configured)?.text =
            "$totalContacts contact${if (totalContacts != 1) "s" else ""} configured"
    }
}