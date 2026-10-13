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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.example.quizmind.R
import com.example.quizmind.databinding.ActivitySubjectBinding

class SubjectActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectBinding

    private data class SubjectViewItem(
        val container: LinearLayout,
        val icon: ImageView,
        val text: TextView,
        val check: ImageView,
        val subjectName: String,
        val iconResId: Int
    )

    private lateinit var subjectItems: List<SubjectViewItem>
    private var currentSelectedIndex = 6 // Default science

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySubjectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDone.setOnClickListener {
            val selected = subjectItems[currentSelectedIndex]
            val intent = Intent().apply {
                putExtra("selected_subject_name", selected.subjectName)
                putExtra("selected_subject_icon", selected.iconResId)
            }
            setResult(RESULT_OK, intent)
            finish()
        }

        setupSubjectSelection()
    }

    private fun setupSubjectSelection() {
        subjectItems = listOf(
            SubjectViewItem(binding.cardComputer, binding.ivComputer, binding.tvComputer, binding.checkComputer, "Computer", R.drawable.ic_splash_ai),
            SubjectViewItem(binding.cardBiology, binding.ivBiology, binding.tvBiology, binding.checkBiology, "Biology", R.drawable.ic_biology),
            SubjectViewItem(binding.cardHistory, binding.ivHistory, binding.tvHistory, binding.checkHistory, "History", R.drawable.ic_histories),
            SubjectViewItem(binding.cardPhysics, binding.ivPhysics, binding.tvPhysics, binding.checkPhysics, "Physics", R.drawable.ic_physics),
            SubjectViewItem(binding.cardArt, binding.ivArt, binding.tvArt, binding.checkArt, "Art", R.drawable.ic_art),
            SubjectViewItem(binding.cardCimistory, binding.ivCimistory, binding.tvChemistry, binding.checkCimistory, "Chemistry", R.drawable.ic_chemistry),
            SubjectViewItem(binding.cardScience, binding.ivScience, binding.tvScience, binding.checkScience, "Science", R.drawable.ic_science),
            SubjectViewItem(binding.cardGeography, binding.ivGeography, binding.tvGeography, binding.checkGeography, "Gegraphy", R.drawable.ic_geography),
            SubjectViewItem(binding.cardEngineering, binding.ivEngineering, binding.tvEngineering, binding.checkEngineering, "Engineering", R.drawable.ic_engineering),
            SubjectViewItem(binding.cardEnglish, binding.ivEnglish, binding.tvEnglish, binding.checkEnglish, "English", R.drawable.ic_english),
            SubjectViewItem(binding.cardBusiness, binding.ivBusiness, binding.tvBusiness, binding.checkBusiness, "Bussiness", R.drawable.ic_business),
            SubjectViewItem(binding.cardMath, binding.ivMath, binding.tvMath, binding.checkMath, "Math", R.drawable.ic_mathematics),
            SubjectViewItem(binding.cardEconomics, binding.ivEconomics, binding.tvEconomics, binding.checkEconomics, "Economics", R.drawable.ic_economics),
            SubjectViewItem(binding.cardLiterature, binding.ivLiterature, binding.tvLiterature, binding.checkLiterature, "Literature", R.drawable.ic_literature),
            SubjectViewItem(binding.cardOther, binding.ivOther, binding.tvOther, binding.checkOther, "Other", R.drawable.ic_other)
        )

        selectSubject(6) // Default Science

        subjectItems.forEachIndexed { index, item ->
            item.container.setOnClickListener {
                currentSelectedIndex = index
                selectSubject(index)
            }
        }
    }

    private fun selectSubject(selectedIndex: Int) {
        currentSelectedIndex = selectedIndex
        subjectItems.forEachIndexed { index, item ->
            if (index == selectedIndex) {
                item.container.setBackgroundResource(R.drawable.bg_pill_selected)
                item.text.setTextColor(Color.WHITE)
                item.text.setTypeface(null, Typeface.BOLD)
                ImageViewCompat.setImageTintList(item.icon, ColorStateList.valueOf(Color.WHITE))
                item.check.visibility = View.VISIBLE
                ImageViewCompat.setImageTintList(item.check, ColorStateList.valueOf(Color.WHITE))
            } else {
                item.container.setBackgroundResource(R.drawable.bg_pill_unselected)
                item.text.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                item.text.setTypeface(null, Typeface.NORMAL)
                ImageViewCompat.setImageTintList(item.icon, ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_color)))
                item.check.visibility = View.GONE
            }
        }
    }
}
