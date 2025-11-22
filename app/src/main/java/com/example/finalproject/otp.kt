package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider

class otp : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private lateinit var otpBoxes: List<EditText>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        auth = FirebaseAuth.getInstance()

        // Get verification ID from intent
        verificationId = intent.getStringExtra("verificationId")
        val phoneNumber = intent.getStringExtra("phoneNumber")

        val otpContainer = findViewById<LinearLayout>(R.id.etotp)
        val btnVerify = findViewById<Button>(R.id.verify)

        // Get all 6 EditText boxes
        otpBoxes = listOf(
            otpContainer.getChildAt(0) as EditText,
            otpContainer.getChildAt(1) as EditText,
            otpContainer.getChildAt(2) as EditText,
            otpContainer.getChildAt(3) as EditText,
            otpContainer.getChildAt(4) as EditText,
            otpContainer.getChildAt(5) as EditText
        )

        // Setup auto-focus between boxes
        setupOtpBoxes()

        btnVerify.setOnClickListener {
            val otp = getOtpFromBoxes()

            if (otp.length != 6) {
                Toast.makeText(this, "Please enter complete 6-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            verifyOtp(otp)
        }
    }

    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1 && index < otpBoxes.size - 1) {
                        // Move to next box
                        otpBoxes[index + 1].requestFocus()
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            // Handle backspace to move to previous box
            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editText.text.isEmpty() && index > 0) {
                        otpBoxes[index - 1].requestFocus()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private fun getOtpFromBoxes(): String {
        return otpBoxes.joinToString("") { it.text.toString().trim() }
    }

    private fun verifyOtp(otp: String) {
        if (verificationId == null) {
            Toast.makeText(this, "Verification ID is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = PhoneAuthProvider.getCredential(verificationId!!, otp)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Verification successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, dashboard::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        "Verification failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}