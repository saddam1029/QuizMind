package com.example.quizmind.ui.activities

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.quizmind.R
import com.example.quizmind.databinding.ActivityCreateQuizBinding

class CreateQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateQuizBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCreateQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.llOther.setOnClickListener {
            startActivity(Intent(this, SubjectActivity::class.java))
        }

        setupRadioGroup(listOf(binding.tvQ5, binding.tvQ10, binding.tvQ15, binding.tvQ20, binding.tvQCustom), 1) // default index 1 (10)
        setupRadioGroup(listOf(binding.tvEasy, binding.tvMedium, binding.tvHard, binding.tvMixed), 3) // default index 3 (Mixed)
    }

    private fun setupRadioGroup(views: List<TextView>, defaultIndex: Int) {
        views.forEachIndexed { index, view ->
            if (index == defaultIndex) {
                view.setBackgroundResource(R.drawable.bg_pill_selected)
                view.setTextColor(ContextCompat.getColor(this, R.color.white))
                view.setTypeface(null, Typeface.BOLD)
            } else {
                view.setBackgroundResource(R.drawable.bg_pill_unselected)
                view.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                view.setTypeface(null, Typeface.NORMAL)
            }

            view.setOnClickListener {
                views.forEach { v ->
                    v.setBackgroundResource(R.drawable.bg_pill_unselected)
                    v.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                    v.setTypeface(null, Typeface.NORMAL)
                }
                view.setBackgroundResource(R.drawable.bg_pill_selected)
                view.setTextColor(ContextCompat.getColor(this, R.color.white))
                view.setTypeface(null, Typeface.BOLD)
            }
        }
    }
}
