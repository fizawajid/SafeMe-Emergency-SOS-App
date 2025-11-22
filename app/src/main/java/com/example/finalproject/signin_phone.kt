package com.example.finalproject

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class signin_phone : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signin_phone)

        auth = FirebaseAuth.getInstance()

        val etPhone = findViewById<EditText>(R.id.etPhone)
        val btnSignInPhone = findViewById<Button>(R.id.btnSignIn)
        val tvSignupPhone = findViewById<TextView>(R.id.tvSignUp)

        btnSignInPhone.setOnClickListener {
            val phone = etPhone.text.toString().trim()

            if (phone.isEmpty()) {
                etPhone.error = "Phone number is required"
                etPhone.requestFocus()
                return@setOnClickListener
            }

            // Check if user is already registered (has Firebase account)
            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.phoneNumber != null) {
                // User already signed in, go to dashboard
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, dashboard::class.java))
                finish()
            } else {
                // User not found, redirect to create account
                Toast.makeText(this, "Account not found. Please create an account first.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, createaccount_phone::class.java))
            }
        }

        tvSignupPhone.setOnClickListener {
            startActivity(Intent(this, createaccount_phone::class.java))
        }
    }
}