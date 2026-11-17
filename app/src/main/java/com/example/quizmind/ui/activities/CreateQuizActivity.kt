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
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.quizmind.R
import com.example.quizmind.databinding.ActivityCreateQuizBinding
import com.example.quizmind.ui.viewmodel.CreateQuizUiState
import com.example.quizmind.ui.viewmodel.CreateQuizViewModel
import com.example.quizmind.utils.ImageFileHelper
import com.example.quizmind.utils.PdfMetadataHelper
import com.example.quizmind.utils.TopicValidator
import com.example.quizmind.utils.WordCountHelper
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CreateQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateQuizBinding
    private val viewModel: CreateQuizViewModel by viewModels()

    private var activeSourceType: String = "topic"

    // PDF selection state
    private var selectedPdfUri: Uri? = null

    // Image selection state
    private var selectedImageUri: Uri? = null
    private var cameraCaptureUri: Uri? = null

    // Topic word count guard
    private var isUpdatingTopic = false

    // Quiz configuration state
    private var selectedQuestionCount = 10
    private var selectedDifficulty = "Mixed"

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
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val croppedUri = result.data?.getParcelableExtra<Uri>("cropped_uri")
            if (croppedUri != null) {
                handleImageCropped(croppedUri)
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

        activeSourceType = intent.getStringExtra("source_type") ?: "topic"
        setupCategoryVisibility(activeSourceType)

        // Setup individual category interactions
        setupTopicSection()
        setupTextNotesSection()
        setupPdfSection()
        setupImageSection()

        // Handle prefill if coming from "Give Quiz Again"
        intent.getStringExtra("prefill_topic")?.let { topic ->
            if (topic.isNotBlank()) {
                binding.etTopic.setText(topic)
                binding.etTopic.setSelection(topic.length)
            }
        }

        // Setup shared quiz configuration
        setupSharedQuizConfiguration()

        observeViewModel()
        // Prefetch the AI model while the user fills the form.
        viewModel.warmUpAi()
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
                val letterCount = TopicValidator.countLetters(input)

                if (letterCount > 30) {
                    isUpdatingTopic = true
                    val trimmed = TopicValidator.enforceMaxLetters(input, 30)
                    binding.etTopic.setText(trimmed)
                    binding.etTopic.setSelection(trimmed.length)
                    binding.tvTopicWords.text = "30/30"
                    binding.ivClear.visibility = View.VISIBLE
                    isUpdatingTopic = false
                } else {
                    binding.tvTopicWords.text = "$letterCount/30"
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
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
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
        val intent = Intent(this, CustomCropActivity::class.java).apply {
            putExtra("source_uri", sourceUri)
        }
        cropLauncher.launch(intent)
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

        setupQuestionCountSelection()
        setupDifficultySelection()
        setupSubjectRadioButtons()

        binding.btnGenerateQuiz.setOnClickListener {
            validateAndGenerateQuiz()
        }
    }

    private fun setupQuestionCountSelection() {
        val qViews = listOf(binding.tvQ5, binding.tvQ10, binding.tvQ15, binding.tvQ20, binding.tvQCustom)
        val counts = listOf(5, 10, 15, 20, -1)

        qViews.forEachIndexed { index, view ->
            val isDefault = (index == 1) // 10 Qs default
            if (isDefault) {
                view.setBackgroundResource(R.drawable.bg_pill_selected)
                view.setTextColor(ContextCompat.getColor(this, R.color.white))
                view.setTypeface(null, Typeface.BOLD)
            } else {
                view.setBackgroundResource(R.drawable.bg_pill_unselected)
                view.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                view.setTypeface(null, Typeface.NORMAL)
            }

            view.setOnClickListener {
                if (counts[index] == -1) {
                    showCustomQuestionCountDialog(qViews, index)
                } else {
                    selectedQuestionCount = counts[index]
                    updateSelectionUI(qViews, index)
                }
            }
        }
    }

    private fun showCustomQuestionCountDialog(qViews: List<TextView>, customIndex: Int) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter number (1-50)"
            setText("$selectedQuestionCount")
            setSelection(text.length)
            setPadding(40, 30, 40, 30)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Custom Question Count")
            .setMessage("Enter number of questions (1 to 50):")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val text = input.text.toString().trim()
                val count = text.toIntOrNull() ?: 10
                val clamped = count.coerceIn(1, 50)
                selectedQuestionCount = clamped
                qViews[customIndex].text = "$clamped"
                updateSelectionUI(qViews, customIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupDifficultySelection() {
        val diffViews = listOf(binding.tvEasy, binding.tvMedium, binding.tvHard, binding.tvMixed)
        val diffs = listOf("Easy", "Medium", "Hard", "Mixed")

        diffViews.forEachIndexed { index, view ->
            val isDefault = (index == 3) // Mixed default
            if (isDefault) {
                view.setBackgroundResource(R.drawable.bg_pill_selected)
                view.setTextColor(ContextCompat.getColor(this, R.color.white))
                view.setTypeface(null, Typeface.BOLD)
            } else {
                view.setBackgroundResource(R.drawable.bg_pill_unselected)
                view.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                view.setTypeface(null, Typeface.NORMAL)
            }

            view.setOnClickListener {
                selectedDifficulty = diffs[index]
                updateSelectionUI(diffViews, index)
            }
        }
    }

    private fun updateSelectionUI(views: List<TextView>, selectedIndex: Int) {
        views.forEachIndexed { i, v ->
            if (i == selectedIndex) {
                v.setBackgroundResource(R.drawable.bg_pill_selected)
                v.setTextColor(ContextCompat.getColor(this, R.color.white))
                v.setTypeface(null, Typeface.BOLD)
            } else {
                v.setBackgroundResource(R.drawable.bg_pill_unselected)
                v.setTextColor(ContextCompat.getColor(this, R.color.qm_text_primary))
                v.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun validateAndGenerateQuiz() {
        val subjectName = subjectCards.getOrNull(currentSelectedSubjectIndex)?.text?.text?.toString() ?: "Science"
        val timerEnabled = binding.switchTimer.isChecked

        lifecycleScope.launch {
            when (activeSourceType) {
                "topic" -> {
                    val topic = binding.etTopic.text?.toString()?.trim() ?: ""
                    if (!TopicValidator.isValid(topic)) {
                        Toast.makeText(this@CreateQuizActivity, "Please enter a valid topic (1-30 alphabetic characters).", Toast.LENGTH_SHORT).show()
                        binding.etTopic.requestFocus()
                        return@launch
                    }
                    viewModel.generateQuizFromContent(
                        title = TopicValidator.sanitizeTopic(topic),
                        sourceMaterial = "",
                        subject = subjectName,
                        questionCount = selectedQuestionCount,
                        difficulty = selectedDifficulty,
                        timerEnabled = timerEnabled,
                        isTopic = true,
                        sourceType = activeSourceType
                    )
                }
                "text" -> {
                    val notes = binding.etTextNotes.text?.toString()?.trim() ?: ""
                    if (notes.isBlank()) {
                        Toast.makeText(this@CreateQuizActivity, "Please enter or paste study notes.", Toast.LENGTH_SHORT).show()
                        binding.etTextNotes.requestFocus()
                        return@launch
                    }
                    viewModel.generateQuizFromContent(
                        title = shortTitle(notes, "Study Notes"),
                        sourceMaterial = notes,
                        subject = subjectName,
                        questionCount = selectedQuestionCount,
                        difficulty = selectedDifficulty,
                        timerEnabled = timerEnabled,
                        isTopic = false,
                        sourceType = activeSourceType
                    )
                }
                "pdf" -> {
                    val uri = selectedPdfUri
                    if (uri == null) {
                        Toast.makeText(this@CreateQuizActivity, "Please select a PDF document.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    showCreateQuizSkeleton()
                    binding.btnGenerateQuiz.isEnabled = false
                    val pdfText = extractTextFromPdf(uri)
                    if (pdfText.isBlank()) {
                        hideCreateQuizSkeleton()
                        binding.btnGenerateQuiz.isEnabled = true
                        Toast.makeText(
                            this@CreateQuizActivity,
                            "Could not read text from this PDF. Choose a text-based PDF and try again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    val pdfName = binding.tvPdfName.text?.toString()?.substringBeforeLast('.')?.trim().orEmpty()
                    viewModel.generateQuizFromContent(
                        title = pdfName.ifBlank { "PDF Notes" },
                        sourceMaterial = pdfText,
                        subject = subjectName,
                        questionCount = selectedQuestionCount,
                        difficulty = selectedDifficulty,
                        timerEnabled = timerEnabled,
                        isTopic = false,
                        sourceType = activeSourceType
                    )
                }
                "image" -> {
                    val uri = selectedImageUri
                    if (uri == null) {
                        Toast.makeText(this@CreateQuizActivity, "Please capture or select an image of your notes.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    showCreateQuizSkeleton()
                    binding.btnGenerateQuiz.isEnabled = false
                    val imageText = extractTextFromImage(uri)
                    if (imageText.isBlank()) {
                        hideCreateQuizSkeleton()
                        binding.btnGenerateQuiz.isEnabled = true
                        Toast.makeText(
                            this@CreateQuizActivity,
                            "Could not read any text from this image. Try a clearer photo.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    viewModel.generateQuizFromContent(
                        title = shortTitle(imageText, "Scanned Notes"),
                        sourceMaterial = imageText,
                        subject = subjectName,
                        questionCount = selectedQuestionCount,
                        difficulty = selectedDifficulty,
                        timerEnabled = timerEnabled,
                        isTopic = false,
                        sourceType = activeSourceType
                    )
                }
            }
        }
    }

    private suspend fun extractTextFromPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(applicationContext)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(document).trim()
                    if (text.isNotEmpty()) {
                        return@withContext text
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext ""
    }

    private suspend fun extractTextFromImage(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(this@CreateQuizActivity, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            val text = result.text.trim()
            if (text.isNotEmpty()) {
                return@withContext text
            }
        } catch (_: Exception) {}
        return@withContext ""
    }

    private fun shortTitle(text: String, fallback: String): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val words = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }.take(6).joinToString(" ")
        val title = words.ifBlank { fallback }
        return if (title.length > 60) title.take(57).trimEnd() + "..." else title
    }

    private fun createQuizShimmer(): ShimmerFrameLayout {
        return binding.layoutCreateQuizSkeleton.root as ShimmerFrameLayout
    }

    private fun showCreateQuizSkeleton() {
        binding.scrollCreateQuiz.visibility = View.INVISIBLE
        val shimmer = createQuizShimmer()
        shimmer.visibility = View.VISIBLE
        shimmer.startShimmer()
    }

    private fun hideCreateQuizSkeleton() {
        val shimmer = createQuizShimmer()
        shimmer.stopShimmer()
        shimmer.visibility = View.GONE
        binding.scrollCreateQuiz.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            try {
                createQuizShimmer().stopShimmer()
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is CreateQuizUiState.Idle -> {
                            hideCreateQuizSkeleton()
                            binding.btnGenerateQuiz.isEnabled = true
                        }
                        is CreateQuizUiState.Loading -> {
                            showCreateQuizSkeleton()
                            binding.btnGenerateQuiz.isEnabled = false
                        }
                        is CreateQuizUiState.Success -> {
                            hideCreateQuizSkeleton()
                            binding.btnGenerateQuiz.isEnabled = true
                            if (state.creditsUsed > 0L) {
                                Toast.makeText(
                                    this@CreateQuizActivity,
                                    "-${state.creditsUsed} credits used",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            val quizId = state.quiz.id
                            viewModel.resetState()
                            val intent = Intent(this@CreateQuizActivity, ActiveQuizActivity::class.java).apply {
                                putExtra(ActiveQuizActivity.EXTRA_QUIZ_ID, quizId)
                            }
                            startActivity(intent)
                            finish()
                        }
                        is CreateQuizUiState.Error -> {
                            hideCreateQuizSkeleton()
                            binding.btnGenerateQuiz.isEnabled = true
                            viewModel.resetState()
                            MaterialAlertDialogBuilder(this@CreateQuizActivity)
                                .setTitle("Generation Failed")
                                .setMessage(state.message)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
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
