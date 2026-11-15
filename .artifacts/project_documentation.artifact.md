# QuizMind - Project Documentation

QuizMind is an advanced, AI-powered Android application built using Kotlin and XML Views. It enables users to generate interactive multiple-choice quizzes instantly from various study sources—such as short topics, pasted notes, PDF documents, and scanned images—utilizing Firebase AI Logic (Gemini API).

---

## 1. Tech Stack & Dependencies

- **Language**: Kotlin 2.0+ / 1.7+ Target Compatibility
- **UI Toolkit**: Android Views & XML Layouts (Material Design 3, ConstraintLayout, RecyclerView, Activity/Fragment KTX)
- **Dependency Injection**: Dagger Hilt (`2.60.1`)
- **Architecture**: MVVM (Model-View-ViewModel) with StateFlow and Coroutines
- **Local Storage**: Room Database (`2.8.5`) with Kotlin Coroutines support
- **Preferences**: Jetpack DataStore Preferences (`1.2.1`)
- **Backend & AI**: Firebase BoM (`34.19.0`), Firebase Auth, Firebase Firestore, Firebase AI Logic (`gemini-3.8-flash`) via Gemini Developer API
- **Google Sign-In / Credentials**: Android Credential Manager API (`1.6.0`)
- **Camera & Scanning**: CameraX (`1.6.2`), Android Image Cropper (`4.7.0`)
- **OCR & PDF Extraction**: Google ML Kit Text Recognition (`16.0.1`), PDFBox Android (`2.0.27.0`)
- **Testing**: JUnit (`4.13.2`), AndroidX JUnit (`1.3.0`), Espresso (`3.7.0`)

---

## 2. Architecture & Project Structure

The project follows a clean MVVM architecture organized by packages under `com.example.quizmind`:

```
com.example.quizmind/
├── data/
│   ├── local/               # Room Database, DAOs, Entities
│   ├── model/               # Data Classes (Quiz, Question, QuizResult, QuizScore)
│   ├── remote/              # Firebase Firestore & Gemini AI Service
│   └── repository/          # QuizRepository coordinating data flow
├── di/                      # Hilt Modules (DatabaseModule)
├── ui/
│   ├── activities/          # Activities (Splash, Onboarding, Login, Main, CreateQuiz, ActiveQuiz, QuizResult, Subject, CustomCrop)
│   ├── adapters/            # RecyclerView Adapters (RecentQuizAdapter)
│   ├── custom/              # Custom Views (CircularScoreView)
│   ├── fragments/           # Onboarding Fragments
│   └── viewmodel/           # ViewModels (CreateQuizViewModel, ActiveQuizViewModel, QuizResultViewModel)
└── utils/                   # Helpers (AppPreferences, ImageFileHelper, PdfMetadataHelper, QuizScoreCalculator, ResultImageGenerator, TopicValidator, WordCountHelper)
```

---

## 3. Core Features & Implementation Details

### A. AI Quiz Generation (Gemini API)
- **Service**: `GeminiQuizService` integrates Firebase AI Logic using the `gemini-3.8-flash` model.
- **Prompt Engineering**: Sends structured prompts requiring strict JSON arrays of 4-option multiple-choice questions with answers, explanations, and hints.
- **Source Modes**:
  - **Topic**: Short topic generation validated via `TopicValidator`.
  - **Text Notes**: Pasted study material or lecture notes.
  - **PDF Source**: Text extraction using PDFBox Android (`PDDocument`, `PDFTextStripper`).
  - **Scan Notes / Image**: OCR extraction using Google ML Kit Text Recognition.

### B. Active Quiz & Scoring
- **ActiveQuizActivity & ActiveQuizViewModel**: Manages real-time quiz progression, option selection, timers, hints, and score calculation (`QuizScoreCalculator`).
- **QuizResultActivity**: Displays final scores, correct/wrong breakdowns, percentages, circular score visuals (`CircularScoreView`), and allows saving/downloading results to local Room database or Firestore.

### C. Local Persistence & Sync
- **Room Database**: `QuizDatabase` stores completed and downloaded quizzes and questions (`QuizEntity`, `QuestionEntity`).
- **Firebase Firestore**: `FirestoreQuizDataSource` handles remote persistence of user quiz results.

---

## 4. Key Activities & Flows

| Activity | Description |
| :--- | :--- |
| `SplashActivity` | Initial splash screen with branding and App Check initialization. |
| `OnboardingActivity` | First-time user introduction screens (Fragments 1-3). |
| `LoginActivity` | Firebase Authentication & Google Sign-In using Credential Manager. |
| `MainActivity` | Home dashboard showing recent quizzes, subject categories, and quick create actions. |
| `CreateQuizActivity` | Hub for configuring quiz creation (Source mode, Subject, Question count, Difficulty, Timer). |
| `ActiveQuizActivity` | Interactive quiz-taking screen with timer, question progress, option selection, and hints. |
| `QuizResultActivity` | Score breakdown, performance summary, and result sharing/downloading. |
| `SubjectActivity` | Comprehensive subject selection catalog. |
| `CustomCropActivity` | Image cropping tool for scanned study notes. |

---

## 5. Build & Verification
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) with KSP for annotation processing.
- **Verification**: Successfully builds with `assembleDebug` and passes all local unit tests (`testDebugUnitTest`).
