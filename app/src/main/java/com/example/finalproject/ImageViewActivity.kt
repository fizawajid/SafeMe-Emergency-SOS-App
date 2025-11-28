package com.example.finalproject

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.finalproject.R

class ImageViewActivity : AppCompatActivity() {

    private lateinit var ivFullImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)

        initializeViews()
        setupClickListeners()
        loadImage()
    }

    private fun initializeViews() {
        ivFullImage = findViewById(R.id.ivFullImage)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        // Close on image click as well
        ivFullImage.setOnClickListener {
            finish()
        }
    }

    private fun loadImage() {
        val imageUriString = intent.getStringExtra("image_uri")

        if (imageUriString.isNullOrEmpty()) {
            showError("No image found")
            return
        }

        try {
            val imageUri = Uri.parse(imageUriString)
            progressBar.visibility = View.VISIBLE

            Glide.with(this)
                .load(imageUri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(ivFullImage)
                .also { request ->
                    // Simple approach - hide progress after a short delay
                    ivFullImage.postDelayed({
                        progressBar.visibility = View.GONE
                    }, 500)
                }

        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            showError("Error loading image")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        ivFullImage.setImageResource(R.drawable.error)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}