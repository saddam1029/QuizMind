package com.example.quizmind.ui.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.quizmind.databinding.ActivityCustomCropBinding
import java.io.File

class CustomCropActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomCropBinding
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCustomCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sourceUri = intent.getParcelableExtra("source_uri")
        if (sourceUri != null) {
            binding.cropImageView.setImageUriAsync(sourceUri)
        } else {
            finish()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnRotate.setOnClickListener {
            binding.cropImageView.rotateImage(90)
        }

        binding.btnDoneCrop.setOnClickListener {
            val croppedBitmap = binding.cropImageView.croppedImage
            if (croppedBitmap != null) {
                val file = File(cacheDir, "cropped_note_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val croppedUri = Uri.fromFile(file)
                val resultIntent = Intent().apply {
                    putExtra("cropped_uri", croppedUri)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                finish()
            }
        }
    }
}
