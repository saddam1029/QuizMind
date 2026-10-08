package com.example.quizmind.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.quizmind.MainActivity
import com.example.quizmind.databinding.ActivityOnboardingBinding
import com.example.quizmind.ui.fragments.OnboardingOneFragment
import com.example.quizmind.ui.fragments.OnboardingThreeFragment
import com.example.quizmind.ui.fragments.OnboardingTwoFragment
import com.example.quizmind.utils.AppPreferences

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViewPager()
        setupListeners()
    }

    private fun setupViewPager() {
        val fragments = listOf(
            OnboardingOneFragment(),
            OnboardingTwoFragment(),
            OnboardingThreeFragment()
        )

        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        binding.viewPager.adapter = adapter
        binding.dotsIndicator.setViewPager2(binding.viewPager)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 2) {
                    binding.tvOnboardingId.text = "Get Started"
                    binding.tvSkip.visibility = View.VISIBLE
                } else {
                    binding.tvOnboardingId.text = "Next"
                    binding.tvSkip.visibility = View.GONE
                }
            }
        })
    }

    private fun setupListeners() {
        binding.tvOnboardingId.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < 2) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                finishOnboarding()
            }
        }

        binding.tvSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        AppPreferences.setOnboardingCompleted(this, true)
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
