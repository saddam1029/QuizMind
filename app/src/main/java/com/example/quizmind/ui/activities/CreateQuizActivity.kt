package com.example.quizmind.ui.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.example.quizmind.R
import com.example.quizmind.databinding.ActivityCreateQuizBinding

class CreateQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateQuizBinding

    private data class SubjectCard(
        val container: LinearLayout,
        val icon: ImageView,
        val text: TextView,
        val check: ImageView
    )

    private lateinit var subjectCards: List<SubjectCard>

    private val subjectLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val name = result.data?.getStringExtra("selected_subject_name")
            val iconRes = result.data?.getIntExtra("selected_subject_icon", R.drawable.ic_science) ?: R.drawable.ic_science
            if (name != null) {
                // Update 1st subject card with selected subject from SubjectActivity and select it
                binding.tvSubject1.text = name
                binding.ivSubject1.setImageResource(iconRes)
                selectSubjectCard(0)
            }
        }
    }

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

        // Clicking "Other" opens SubjectActivity to select from all subjects
        binding.llOther.setOnClickListener {
            subjectLauncher.launch(Intent(this, SubjectActivity::class.java))
        }

        setupRadioGroup(listOf(binding.tvQ5, binding.tvQ10, binding.tvQ15, binding.tvQ20, binding.tvQCustom), 1)
        setupRadioGroup(listOf(binding.tvEasy, binding.tvMedium, binding.tvHard, binding.tvMixed), 3)

        setupSubjectRadioButtons()
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

    private fun setupSubjectRadioButtons() {
        subjectCards = listOf(
            SubjectCard(binding.cardSubject1, binding.ivSubject1, binding.tvSubject1, binding.checkSubject1),
            SubjectCard(binding.cardSubject2, binding.ivSubject2, binding.tvSubject2, binding.checkSubject2),
            SubjectCard(binding.cardSubject3, binding.ivSubject3, binding.tvSubject3, binding.checkSubject3),
            SubjectCard(binding.cardSubject4, binding.ivSubject4, binding.tvSubject4, binding.checkSubject4),
            SubjectCard(binding.cardSubject5, binding.ivSubject5, binding.tvSubject5, binding.checkSubject5),
            SubjectCard(binding.cardSubject6, binding.ivSubject6, binding.tvSubject6, binding.checkSubject6),
            SubjectCard(binding.cardSubject7, binding.ivSubject7, binding.tvSubject7, binding.checkSubject7),
            SubjectCard(binding.llOther, binding.ivOther, binding.tvOther, binding.checkOther)
        )

        // Default select 1st subject card (Science)
        selectSubjectCard(0)

        subjectCards.forEachIndexed { index, card ->
            card.container.setOnClickListener {
                if (index == subjectCards.size - 1) {
                    // If clicking "Other...", launch SubjectActivity to pick from all subjects
                    subjectLauncher.launch(Intent(this, SubjectActivity::class.java))
                } else {
                    selectSubjectCard(index)
                }
            }
        }
    }

    private fun selectSubjectCard(selectedIndex: Int) {
        subjectCards.forEachIndexed { index, card ->
            if (index == selectedIndex) {
                card.container.setBackgroundResource(R.drawable.bg_pill_selected)
                card.text.setTextColor(Color.WHITE)
                card.text.setTypeface(null, Typeface.BOLD)
                ImageViewCompat.setImageTintList(card.icon, ColorStateList.valueOf(Color.WHITE))
                card.check.visibility = View.VISIBLE
                ImageViewCompat.setImageTintList(card.check, ColorStateList.valueOf(Color.WHITE))
            } else {
                card.container.setBackgroundResource(R.drawable.bg_pill_unselected)
                card.text.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                card.text.setTypeface(null, Typeface.NORMAL)
                ImageViewCompat.setImageTintList(card.icon, ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_color)))
                card.check.visibility = View.GONE
            }
        }
    }
}
