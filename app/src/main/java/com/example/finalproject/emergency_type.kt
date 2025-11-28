package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class emergency_type : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.emergency_type)

        // Get the views
        val cardPersonalSafety = findViewById<FrameLayout>(R.id.card_personal_safety)
        val cardTravelEmergency = findViewById<FrameLayout>(R.id.card_travel_emergency)
        val cardMedicalEmergency = findViewById<FrameLayout>(R.id.card_medical_emergency)

        // 👉 When "Personal Safety" card is pressed
        cardPersonalSafety.setOnClickListener {
            startActivity(Intent(this, PersonalSafety::class.java))
        }

        // 👉 When "Travel Emergency" card is pressed
        cardTravelEmergency.setOnClickListener {
            startActivity(Intent(this, TravelSafety::class.java))
        }

        cardMedicalEmergency.setOnClickListener {
            startActivity(Intent(this, MedicalSafety::class.java))
        }
    }
}
