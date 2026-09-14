package com.nihalthakral.nihalhome

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class SocialHackingActivity : ComponentActivity() {

    private val images = listOf(
        R.drawable.social_img_1,
        R.drawable.social_img_2,
        R.drawable.social_img_3,
        R.drawable.social_img_4,
        R.drawable.social_img_5
    )

    private var currentIndex = 0

    private lateinit var imageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social_hacking)

        imageView = findViewById(R.id.imageSocialHacking)
        val buttonPrevious = findViewById<Button>(R.id.buttonPrevious)
        val buttonNext = findViewById<Button>(R.id.buttonNext)

        updateImage()

        buttonPrevious.setOnClickListener {
            currentIndex = if (currentIndex == 0) images.size - 1 else currentIndex - 1
            updateImage()
        }

        buttonNext.setOnClickListener {
            currentIndex = if (currentIndex == images.size - 1) 0 else currentIndex + 1
            updateImage()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToMainScreen()
            }
        })
    }

    private fun updateImage() {
        imageView.setImageResource(images[currentIndex])
    }

    private fun goToMainScreen() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
