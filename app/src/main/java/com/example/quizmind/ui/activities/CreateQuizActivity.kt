package com.example.quizmind.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.example.quizmind.R
import com.example.quizmind.databinding.ActivityCreateQuizBinding
import com.example.quizmind.utils.ImageFileHelper
import com.example.quizmind.utils.PdfMetadataHelper
import com.example.quizmind.utils.WordCountHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CreateQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateQuizBinding
    private var activeSourceType: String = "topic"

    // PDF selection state
    private var selectedPdfUri: Uri? = null

    // Image selection state
    private var selectedImageUri: Uri? = null
    private var cameraCaptureUri: Uri? = null

    // Topic word count guard
    private var isUpdatingTopic = false

    private data class SubjectCard(
        val container: LinearLayout,
        val icon: ImageView,
        val text: TextView,
        val check: ImageView
    )

    private lateinit var subjectCards: List<SubjectCard>
    private var currentSelectedSubjectIndex = 0

    // Subject selection launcher
    private val subjectLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val name = result.data?.getStringExtra("selected_subject_name")
            val iconRes = result.data?.getIntExtra("selected_subject_icon", R.drawable.ic_science)
                ?: R.drawable.ic_science
            if (name != null) {
                val matchedIndex = subjectCards.indices.take(7).find { index ->
                    val cardText = subjectCards[index].text.text.toString()
                    cardText.equals(name, ignoreCase = true) ||
                    (name.equals("Math", ignoreCase = true) && cardText.equals("Mathematics", ignoreCase = true)) ||
                    (name.equals("Mathematics", ignoreCase = true) && cardText.equals("Math", ignoreCase = true))
                }

                if (matchedIndex != null) {
                    selectSubjectCard(matchedIndex)
                } else {
                    binding.tvSubject1.text = name
                    binding.ivSubject1.setImageResource(iconRes)
                    selectSubjectCard(0)
                }
            }
        }
    }

    // PDF selection via Storage Access Framework
    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handlePdfSelected(uri)
        }
    }

    // Gallery image selection via Photo Picker / SAF fallback
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            startCrop(uri)
        }
    }

    // Camera photo capture
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraCaptureUri?.let { uri ->
                startCrop(uri)
            }
        }
    }

    // Camera runtime permission
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Camera Permission Required")
                    .setMessage("Camera access is needed to capture photos of your study notes. Please enable it in the app settings.")
                    .setPositiveButton("Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "Camera permission is required to capture notes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Image cropping launcher
    private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                handleImageCropped(croppedUri)
            }
        } else if (result.error != null) {
            Toast.makeText(this, "Cropping failed: ${result.error?.localizedMessage}", Toast.LENGTH_SHORT).show()
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

        activeSourceType = intent.getStringExtra("source_type") ?: "topic"
        setupCategoryVisibility(activeSourceType)

        // Setup individual category interactions
        setupTopicSection()
        setupTextNotesSection()
        setupPdfSection()
        setupImageSection()

        // Setup shared quiz configuration
        setupSharedQuizConfiguration()
    }

    private fun setupCategoryVisibility(sourceType: String) {
        when (sourceType) {
            "text" -> {
                binding.clText.visibility = View.VISIBLE
                binding.llTopic.visibility = View.GONE
                binding.clPdf.visibility = View.GONE
                binding.clImage.visibility = View.GONE
            }
            "pdf" -> {
                binding.clPdf.visibility = View.VISIBLE
                binding.llTopic.visibility = View.GONE
                binding.clText.visibility = View.GONE
                binding.clImage.visibility = View.GONE
            }
            "image" -> {
                binding.clImage.visibility = View.VISIBLE
                binding.llTopic.visibility = View.GONE
                binding.clText.visibility = View.GONE
                binding.clPdf.visibility = View.GONE
            }
            else -> {
                binding.llTopic.visibility = View.VISIBLE
                binding.clText.visibility = View.GONE
                binding.clPdf.visibility = View.GONE
                binding.clImage.visibility = View.GONE
            }
        }
    }

    // ==========================================
    // 2. Topic Category Logic
    // ==========================================
    private fun setupTopicSection() {
        binding.tvTopicWords.text = "0/30"
        binding.ivClear.visibility = View.GONE

        binding.etTopic.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingTopic) return
                val input = s?.toString() ?: ""
                val words = WordCountHelper.getWords(input)
                val count = words.size

                if (count > 30) {
                    isUpdatingTopic = true
                    val trimmed = WordCountHelper.trimToMaxWords(input, 30)
                    binding.etTopic.setText(trimmed)
                    binding.etTopic.setSelection(trimmed.length)
                    binding.tvTopicWords.text = "30/30"
                    binding.ivClear.visibility = View.VISIBLE
                    isUpdatingTopic = false
                } else {
                    binding.tvTopicWords.text = "$count/30"
                    binding.ivClear.visibility = if (input.trim().isNotEmpty()) View.VISIBLE else View.GONE
                }
            }
        })

        binding.ivClear.setOnClickListener {
            binding.etTopic.text?.clear()
            binding.tvTopicWords.text = "0/30"
            binding.ivClear.visibility = View.GONE
        }
    }

    // ==========================================
    // 3. Text / Notes Category Logic
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTextNotesSection() {
        binding.tvTotalWords.text = "0 words"

        // Ensure smooth multiline scrolling inside NestedScrollView
        binding.etTextNotes.setOnTouchListener { v, event ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        binding.etTextNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val count = WordCountHelper.countWords(text)
                binding.tvTotalWords.text = "$count ${if (count == 1) "word" else "words"}"
            }
        })

        binding.llPasteText.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val pasteText = item.coerceToText(this)?.toString()
                if (!pasteText.isNullOrEmpty()) {
                    val start = binding.etTextNotes.selectionStart.coerceAtLeast(0)
                    val end = binding.etTextNotes.selectionEnd.coerceAtLeast(0)
                    val editable = binding.etTextNotes.text ?: Editable.Factory.getInstance().newEditable("")
                    val replaceStart = minOf(start, end)
                    val replaceEnd = maxOf(start, end)
                    editable.replace(replaceStart, replaceEnd, pasteText)
                    binding.etTextNotes.setSelection(replaceStart + pasteText.length)
                    binding.etTextNotes.requestFocus()
                } else {
                    Toast.makeText(this, "No text found in clipboard", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // 4. PDF Category Logic
    // ==========================================
    private fun setupPdfSection() {
        // Initially show unselected, hide selected
        binding.llUnselectedPdf.visibility = View.VISIBLE
        binding.llSelectedPdf.visibility = View.GONE

        binding.llUnselectedPdf.setOnClickListener {
            pdfPickerLauncher.launch(arrayOf("application/pdf"))
        }

        binding.llReplacePdf.setOnClickListener {
            pdfPickerLauncher.launch(arrayOf("application/pdf"))
        }

        binding.llRemovePdf.setOnClickListener {
            selectedPdfUri = null
            binding.tvPdfName.text = ""
            binding.tvPagesSize.text = ""
            binding.llSelectedPdf.visibility = View.GONE
            binding.llUnselectedPdf.visibility = View.VISIBLE
        }
    }

    private fun handlePdfSelected(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        val metadata = PdfMetadataHelper.extractPdfMetadata(this, uri)
        if (metadata.isCorruptedOrInaccessible && metadata.sizeBytes <= 0L) {
            Toast.makeText(
                this,
                "Unable to read selected PDF. The file may be damaged or inaccessible.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        selectedPdfUri = uri
        binding.tvPdfName.text = metadata.name

        val detailsText = when {
            metadata.isPasswordProtected -> {
                "Password Protected · ${metadata.formattedSize}"
            }
            metadata.pageCount > 0 -> {
                val pageStr = if (metadata.pageCount == 1) "page" else "pages"
                "${metadata.pageCount} $pageStr · ${metadata.formattedSize}"
            }
            else -> {
                metadata.formattedSize
            }
        }
        binding.tvPagesSize.text = detailsText

        binding.llUnselectedPdf.visibility = View.GONE
        binding.llSelectedPdf.visibility = View.VISIBLE
    }

    // ==========================================
    // 5. Scan Notes / Image Category Logic
    // ==========================================
    private fun setupImageSection() {
        // Initially show unselected, hide selected
        binding.llUnselectedImage.visibility = View.VISIBLE
        binding.llSelectedImage.visibility = View.GONE

        binding.llCameraAction.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.llGalleryAction.setOnClickListener {
            launchGallery()
        }

        binding.llImageReplace.setOnClickListener {
            showImageSourceDialog()
        }

        binding.llImageRemove.setOnClickListener {
            selectedImageUri = null
            Glide.with(this).clear(binding.ivSelectedImage)
            binding.ivSelectedImage.setImageResource(R.drawable.ic_scan_notes)
            binding.tvImageName.text = ""
            binding.tvImageSize.text = ""
            binding.llSelectedImage.visibility = View.GONE
            binding.llUnselectedImage.visibility = View.VISIBLE
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> launchGallery()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val uri = ImageFileHelper.createTempCameraImageUri(this)
            cameraCaptureUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun startCrop(sourceUri: Uri) {
        val options = CropImageContractOptions(
            uri = sourceUri,
            cropImageOptions = CropImageOptions().apply {
                guidelines = CropImageView.Guidelines.ON
                activityTitle = "Crop Notes"
                toolbarColor = ContextCompat.getColor(this@CreateQuizActivity, R.color.app_color)
                toolbarTitleColor = ContextCompat.getColor(this@CreateQuizActivity, R.color.white)
                toolbarBackButtonColor = ContextCompat.getColor(this@CreateQuizActivity, R.color.white)
                activityMenuIconColor = ContextCompat.getColor(this@CreateQuizActivity, R.color.white)
                cropMenuCropButtonTitle = "Done"
                outputCompressFormat = Bitmap.CompressFormat.JPEG
                outputCompressQuality = 90
            }
        )
        cropImageLauncher.launch(options)
    }

    private fun handleImageCropped(uri: Uri) {
        selectedImageUri = uri
        val metadata = ImageFileHelper.extractImageMetadata(this, uri)
        binding.tvImageName.text = metadata.name
        binding.tvImageSize.text = metadata.formattedSize

        Glide.with(this)
            .load(uri)
            .centerCrop()
            .placeholder(R.drawable.ic_scan_notes)
            .error(R.drawable.ic_scan_notes)
            .into(binding.ivSelectedImage)

        binding.llUnselectedImage.visibility = View.GONE
        binding.llSelectedImage.visibility = View.VISIBLE
    }

    // ==========================================
    // 7. Shared UI and Quiz Configuration
    // ==========================================
    private fun setupSharedQuizConfiguration() {
        // Clicking "Other" opens SubjectActivity to select from all subjects
        binding.llOther.setOnClickListener {
            val currentName = subjectCards.getOrNull(currentSelectedSubjectIndex)?.text?.text?.toString() ?: "Science"
            val intent = Intent(this, SubjectActivity::class.java).apply {
                putExtra("current_subject_name", currentName)
            }
            subjectLauncher.launch(intent)
        }

        setupRadioGroup(listOf(binding.tvQ5, binding.tvQ10, binding.tvQ15, binding.tvQ20, binding.tvQCustom), 1)
        setupRadioGroup(listOf(binding.tvEasy, binding.tvMedium, binding.tvHard, binding.tvMixed), 3)

        setupSubjectRadioButtons()

        binding.btnGenerateQuiz.setOnClickListener {
            validateAndGenerateQuiz()
        }
    }

    private fun validateAndGenerateQuiz() {
        when (activeSourceType) {
            "topic" -> {
                val topic = binding.etTopic.text?.toString()?.trim()
                if (topic.isNullOrEmpty()) {
                    Toast.makeText(this, "Please enter a topic", Toast.LENGTH_SHORT).show()
                    binding.etTopic.requestFocus()
                    return
                }
            }
            "text" -> {
                val notes = binding.etTextNotes.text?.toString()?.trim()
                if (notes.isNullOrEmpty()) {
                    Toast.makeText(this, "Please enter or paste study notes", Toast.LENGTH_SHORT).show()
                    binding.etTextNotes.requestFocus()
                    return
                }
            }
            "pdf" -> {
                if (selectedPdfUri == null) {
                    Toast.makeText(this, "Please select a PDF document", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            "image" -> {
                if (selectedImageUri == null) {
                    Toast.makeText(this, "Please capture or select an image of your notes", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        val subjectName = subjectCards.getOrNull(currentSelectedSubjectIndex)?.text?.text?.toString() ?: "Science"
        Toast.makeText(this, "Ready to generate $subjectName quiz!", Toast.LENGTH_SHORT).show()
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
                    val currentName = subjectCards.getOrNull(currentSelectedSubjectIndex)?.text?.text?.toString() ?: "Science"
                    val intent = Intent(this, SubjectActivity::class.java).apply {
                        putExtra("current_subject_name", currentName)
                    }
                    subjectLauncher.launch(intent)
                } else {
                    selectSubjectCard(index)
                }
            }
        }
    }

    private fun selectSubjectCard(selectedIndex: Int) {
        currentSelectedSubjectIndex = selectedIndex
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
