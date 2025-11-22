package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class createaccount_phone : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_createaccount_phone)

        auth = FirebaseAuth.getInstance()

        val etPhone = findViewById<EditText>(R.id.etPhone)
        val tvEmail = findViewById<TextView>(R.id.email)
        val tvLoginPhone = findViewById<TextView>(R.id.login)
        val btnContinuePhone = findViewById<Button>(R.id.next)

        tvEmail.setOnClickListener {
            startActivity(Intent(this, createaccount_email::class.java))
        }

        tvLoginPhone.setOnClickListener {
            startActivity(Intent(this, signin_phone::class.java))
        }

        btnContinuePhone.setOnClickListener {
            val phone = etPhone.text.toString().trim()

            if (phone.isEmpty()) {
                etPhone.error = "Phone number is required"
                etPhone.requestFocus()
                return@setOnClickListener
            }

            // Format phone number with country code
            val formattedPhone = if (phone.startsWith("+")) phone else "+92$phone"

            sendVerificationCode(formattedPhone)
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(
                        this@createaccount_phone,
                        "Verification failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@createaccount_phone.verificationId = verificationId
                    Toast.makeText(
                        this@createaccount_phone,
                        "OTP sent successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to OTP screen
                    val intent = Intent(this@createaccount_phone, otp::class.java)
                    intent.putExtra("verificationId", verificationId)
                    intent.putExtra("phoneNumber", phoneNumber)
                    startActivity(intent)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Phone verification successful!", Toast.LENGTH_SHORT).show()
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
