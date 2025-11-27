package com.example.finalproject

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

data class EmergencyAlert(
    val alertId: String = "",
    val userId: String = "",
    val type: String = "",
    val message: String = "",
    val additionalMessage: String = "",
    val timestamp: Long = 0,
    val contactsNotified: Int = 0,
    val location: String = "",
    val status: String = "Unresolved"
)

class alerthistory : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var searchBar: EditText
    private lateinit var btnFilterType: Button
    private lateinit var btnFilterStatus: Button
    private lateinit var alertsContainer: LinearLayout
    private lateinit var tvTotalAlerts: TextView
    private lateinit var tvResolved: TextView
    private lateinit var tvUnresolved: TextView
    private lateinit var tvFalseAlarm: TextView
    private lateinit var btnBack: ImageView

    private val alertsList = mutableListOf<EmergencyAlert>()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerthistory)

        initializeViews()
        setupClickListeners()
        loadAlertsFromFirebase()
    }

    private fun initializeViews() {
        searchBar = findViewById(R.id.search_alerts)
        btnFilterType = findViewById(R.id.btnFilterType)
        btnFilterStatus = findViewById(R.id.btnFilterStatus)
        alertsContainer = findViewById(R.id.alerts_container)
        tvTotalAlerts = findViewById(R.id.tv_total_alerts)
        tvResolved = findViewById(R.id.tv_resolved)
        tvUnresolved = findViewById(R.id.tv_unresolved)
        tvFalseAlarm = findViewById(R.id.tv_false_alarm)
        btnBack = findViewById(R.id.btn_back)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        // Search functionality
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAlerts(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter by type
        btnFilterType.setOnClickListener {
            showTypeFilterDialog()
        }

        // Filter by status
        btnFilterStatus.setOnClickListener {
            showStatusFilterDialog()
        }
    }

    private fun loadAlertsFromFirebase() {
        database = FirebaseDatabase.getInstance().getReference("emergency_alerts")

        database.orderByChild("userId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    alertsList.clear()

                    for (alertSnapshot in snapshot.children) {
                        val alert = alertSnapshot.getValue(EmergencyAlert::class.java)
                        alert?.let {
                            alertsList.add(it.copy(alertId = alertSnapshot.key ?: ""))
                        }
                    }

                    // Sort by timestamp (newest first)
                    alertsList.sortByDescending { it.timestamp }

                    displayAlerts(alertsList)
                    updateSummary()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@alerthistory,
                        "Failed to load alerts: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun displayAlerts(alerts: List<EmergencyAlert>) {
        alertsContainer.removeAllViews()

        if (alerts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No alerts found"
                setTextColor(resources.getColor(android.R.color.white, null))
                textSize = 16f
                setPadding(16, 32, 16, 32)
                gravity = android.view.Gravity.CENTER
            }
            alertsContainer.addView(emptyView)
            return
        }

        val inflater = LayoutInflater.from(this)
        for (alert in alerts) {
            val alertView = createAlertView(alert)
            alertsContainer.addView(alertView)
        }
    }

    private fun createAlertView(alert: EmergencyAlert): View {
        val alertView = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.margin_medium))
            }
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.main_card_border, null)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.padding_medium),
                resources.getDimensionPixelSize(R.dimen.padding_medium),
                resources.getDimensionPixelSize(R.dimen.padding_medium),
                resources.getDimensionPixelSize(R.dimen.padding_medium)
            )
        }

        // Header row with icon and title
        val headerRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.margin_small)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(32, 32).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.margin_medium)
            }
            setImageResource(getAlertIcon(alert.type))
        }
        headerRow.addView(icon)

        val titleLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }

        val tvTitle = TextView(this).apply {
            text = alert.type
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleLayout.addView(tvTitle)

        val tvDescription = TextView(this).apply {
            text = alert.additionalMessage.ifEmpty { "Emergency situation" }
            setTextColor(resources.getColor(R.color.grey_primary, null))
            textSize = 11f
        }
        titleLayout.addView(tvDescription)

        headerRow.addView(titleLayout)
        alertView.addView(headerRow)

        // Details row
        val detailsRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val leftDetails = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }

        val tvLocation = TextView(this).apply {
            text = alert.location.ifEmpty { "Location not available" }
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        leftDetails.addView(tvLocation)

        val tvDateTime = TextView(this).apply {
            text = formatTimestamp(alert.timestamp)
            setTextColor(resources.getColor(R.color.grey_primary, null))
            textSize = 10f
        }
        leftDetails.addView(tvDateTime)

        detailsRow.addView(leftDetails)

        val rightDetails = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
        }

        val tvDate = TextView(this).apply {
            text = formatDate(alert.timestamp)
            setTextColor(resources.getColor(android.R.color.white, null))
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        rightDetails.addView(tvDate)

        val tvTime = TextView(this).apply {
            text = formatTime(alert.timestamp)
            setTextColor(resources.getColor(R.color.grey_primary, null))
            textSize = 10f
        }
        rightDetails.addView(tvTime)

        val tvStatus = TextView(this).apply {
            text = alert.status
            setTextColor(getStatusColor(alert.status))
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 0)
        }
        rightDetails.addView(tvStatus)

        detailsRow.addView(rightDetails)
        alertView.addView(detailsRow)

        // Make alert clickable to change status
        alertView.setOnClickListener {
            showStatusChangeDialog(alert)
        }

        return alertView
    }

    private fun getAlertIcon(type: String): Int {
        return when {
            type.contains("Personal", ignoreCase = true) -> R.drawable.red_warning
            type.contains("Travel", ignoreCase = true) -> R.drawable.group
            else -> R.drawable.error
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy, h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun getStatusColor(status: String): Int {
        return when (status) {
            "Resolved" -> resources.getColor(R.color.green_primary, null)
            "False Alarm" -> resources.getColor(R.color.grey_primary, null)
            else -> resources.getColor(android.R.color.holo_red_light, null)
        }
    }

    private fun updateSummary() {
        val total = alertsList.size
        val resolved = alertsList.count { it.status == "Resolved" }
        val unresolved = alertsList.count { it.status == "Unresolved" }
        val falseAlarm = alertsList.count { it.status == "False Alarm" }

        tvTotalAlerts.text = total.toString()
        tvResolved.text = resolved.toString()
        tvUnresolved.text = unresolved.toString()
        tvFalseAlarm.text = falseAlarm.toString()
    }

    private fun filterAlerts(query: String) {
        val filteredList = if (query.isEmpty()) {
            alertsList
        } else {
            alertsList.filter { alert ->
                alert.type.contains(query, ignoreCase = true) ||
                        alert.additionalMessage.contains(query, ignoreCase = true) ||
                        alert.location.contains(query, ignoreCase = true) ||
                        formatTimestamp(alert.timestamp).contains(query, ignoreCase = true) ||
                        formatDate(alert.timestamp).contains(query, ignoreCase = true)
            }
        }
        displayAlerts(filteredList)
    }

    private fun showTypeFilterDialog() {
        val types = arrayOf("All Types", "Personal Emergency", "Travel Emergency", "Medical Emergency")

        AlertDialog.Builder(this)
            .setTitle("Filter by Type")
            .setItems(types) { _, which ->
                val filteredList = when (which) {
                    0 -> alertsList
                    else -> alertsList.filter { it.type.contains(types[which], ignoreCase = true) }
                }
                displayAlerts(filteredList)
            }
            .show()
    }

    private fun showStatusFilterDialog() {
        val statuses = arrayOf("All Statuses", "Resolved", "Unresolved", "False Alarm")

        AlertDialog.Builder(this)
            .setTitle("Filter by Status")
            .setItems(statuses) { _, which ->
                val filteredList = when (which) {
                    0 -> alertsList
                    else -> alertsList.filter { it.status == statuses[which] }
                }
                displayAlerts(filteredList)
            }
            .show()
    }

    private fun showStatusChangeDialog(alert: EmergencyAlert) {
        val statuses = arrayOf("Resolved", "Unresolved", "False Alarm")

        AlertDialog.Builder(this)
            .setTitle("Update Alert Status")
            .setItems(statuses) { _, which ->
                updateAlertStatus(alert.alertId, statuses[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAlertStatus(alertId: String, newStatus: String) {
        database.child(alertId).child("status").setValue(newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}