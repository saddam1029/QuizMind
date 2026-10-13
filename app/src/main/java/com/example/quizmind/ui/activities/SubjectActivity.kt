package com.example.quizmind.ui.activities

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
        val check: ImageView
    )

    private lateinit var subjectItems: List<SubjectViewItem>

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
            finish()
        }

        setupSubjectSelection()
    }

    private fun setupSubjectSelection() {
        subjectItems = listOf(
            SubjectViewItem(binding.cardComputer, binding.ivComputer, binding.tvComputer, binding.checkComputer),
            SubjectViewItem(binding.cardBiology, binding.ivBiology, binding.tvBiology, binding.checkBiology),
            SubjectViewItem(binding.cardHistory, binding.ivHistory, binding.tvHistory, binding.checkHistory),
            SubjectViewItem(binding.cardPhysics, binding.ivPhysics, binding.tvPhysics, binding.checkPhysics),
            SubjectViewItem(binding.cardArt, binding.ivArt, binding.tvArt, binding.checkArt),
            SubjectViewItem(binding.cardCimistory, binding.ivCimistory, binding.tvChemistry, binding.checkCimistory),
            SubjectViewItem(binding.cardScience, binding.ivScience, binding.tvScience, binding.checkScience),
            SubjectViewItem(binding.cardGeography, binding.ivGeography, binding.tvGeography, binding.checkGeography),
            SubjectViewItem(binding.cardEngineering, binding.ivEngineering, binding.tvEngineering, binding.checkEngineering),
            SubjectViewItem(binding.cardEnglish, binding.ivEnglish, binding.tvEnglish, binding.checkEnglish),
            SubjectViewItem(binding.cardBusiness, binding.ivBusiness, binding.tvBusiness, binding.checkBusiness),
            SubjectViewItem(binding.cardMath, binding.ivMath, binding.tvMath, binding.checkMath),
            SubjectViewItem(binding.cardEconomics, binding.ivEconomics, binding.tvEconomics, binding.checkEconomics),
            SubjectViewItem(binding.cardLiterature, binding.ivLiterature, binding.tvLiterature, binding.checkLiterature),
            SubjectViewItem(binding.cardOther, binding.ivOther, binding.tvOther, binding.checkOther)
        )

        // Default selected is Science (index 6)
        selectSubject(6)

        subjectItems.forEachIndexed { index, item ->
            item.container.setOnClickListener {
                selectSubject(index)
            }
        }
    }

    private fun selectSubject(selectedIndex: Int) {
        subjectItems.forEachIndexed { index, item ->
            if (index == selectedIndex) {
                // Selected state: blue background, white text, white icon tint, check visible
                item.container.setBackgroundResource(R.drawable.bg_pill_selected)
                item.text.setTextColor(Color.WHITE)
                item.text.setTypeface(null, Typeface.BOLD)
                ImageViewCompat.setImageTintList(item.icon, ColorStateList.valueOf(Color.WHITE))
                item.check.visibility = View.VISIBLE
                ImageViewCompat.setImageTintList(item.check, ColorStateList.valueOf(Color.WHITE))
            } else {
                // Unselected state: white/gray background, primary text color, app_color icon tint, check gone
                item.container.setBackgroundResource(R.drawable.bg_pill_unselected)
                item.text.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                item.text.setTypeface(null, Typeface.NORMAL)
                ImageViewCompat.setImageTintList(item.icon, ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_color)))
                item.check.visibility = View.GONE
            }
        }
    }
}
