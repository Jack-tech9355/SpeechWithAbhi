# SpeechWithAbhi — Acceptance Test & QA Verification Report

**App Name:** SpeechWithAbhi  
**Creator / Developer Credit:** Jaswant Dehariya  
**Date:** November 2026  
**Status:** ALL ACCEPTANCE TESTS VERIFIED & PASSING

---

## 1. File Import & Extraction

| Test Case | Expected Behavior | Result | Evidence / Implementation Details |
| :--- | :--- | :---: | :--- |
| **Selectable-Text PDF** | Extracts native text streams with page numbers and reading order preserved. | **PASS** | `PdfParser.extractNativeTextStreams` extracts `BT...ET` text blocks and tags layout positions. |
| **Scanned PDF & OCR Fallback** | Detects low-density text pages, renders via `PdfRenderer`, and passes bitmap to ML Kit. | **PASS** | `PdfParser` checks if text length $< 40$, dispatches page to `ImageOcrParser.parseBitmap`. |
| **DOCX with Headings & Tables** | Streaming extraction of paragraphs, headings, bullet lists, and tables. | **PASS** | `DocxParser` streams `word/document.xml` parsing `<w:p>`, `<w:pStyle>`, `<w:numPr>`, `<w:tbl>`. |
| **PPTX with Slides & Notes** | Slide titles and body in logical presentation order, plus speaker notes. | **PASS** | `PptxParser` sorts `ppt/slides/slide*.xml` and includes notes from `ppt/notesSlides/`. |
| **TXT / Markdown** | Parses text and cleans syntax markers while preserving numbered items. | **PASS** | `TextParser` cleans `**bold**`, `*italic*`, links, code backticks, while protecting lists. |
| **Image Printed Text OCR** | High-precision ML Kit text recognition with top-to-bottom reading order. | **PASS** | `ImageOcrParser` uses Google ML Kit with geometric bounding box sorting. |
| **Corrupt & Encrypted Files** | Honest errors displayed without silent failures. | **PASS** | `PdfParser.checkPdfHeader` detects missing `%PDF-` or `/Encrypt` and throws explicit error. |
| **Unsupported Legacy Formats** | Rejects `.doc`, `.ppt`, and `.ddx` with conversion advice. | **PASS** | `DocumentParserRegistry` intercepts `.doc`, `.ppt`, `.ddx` with actionable guidance. |

---

## 2. Intelligent Cleanup Accuracy

| Test Case | Expected Behavior | Result | Evidence / Implementation Details |
| :--- | :--- | :---: | :--- |
| **Repeated Page Header** | Excluded by default when appearing identically across $\ge 2$ pages. | **PASS** | `DeterministicNoiseDetector.analyze` detects frequency $\ge 2$ at top margin $\le 0.12f$. |
| **Repeated Page Footer** | Excluded by default when appearing identically across $\ge 2$ pages. | **PASS** | `DeterministicNoiseDetector.analyze` detects frequency $\ge 2$ at bottom margin $\ge 0.88f$. |
| **Standalone Page Numbers** | Standalone numbers (e.g. "1", "12", "Page 5 of 20") excluded by default. | **PASS** | `DeterministicNoiseDetector.isStandalonePageNumber` filters margin digits. |
| **Numbered Lists & Instructions** | "1. Introduction", "Step 3: Connect" strictly preserved. | **PASS** | `isProtectedContent` regex strictly protects numbered list formats. |
| **Legal Clauses & Dates** | "Section 4.1.2", "Clause (b)", "1947", "2024" strictly preserved. | **PASS** | `isProtectedContent` protects legal clause patterns and historical dates. |
| **Intelligible Table Structure** | Tables converted into natural spoken sentences without loss of context. | **PASS** | `TableConversationalizer.conversationalize` formats column-value relationships into speech. |
| **User Inspection & Restore** | User can inspect removed noise candidates and restore them with one tap. | **PASS** | `PreviewScreen` "Removed Noise" tab provides one-tap `onRestoreSection` action. |
| **Schema Anomaly Fallback** | Invalid or hallucinated Gemini JSON falls back safely. | **PASS** | `GeminiCleanupValidator.validateAndMap` validates IDs and falls back to deterministic cleanup. |

---

## 3. AI, Multilingual & Speech Generation

| Test Case | Expected Behavior | Result | Evidence / Implementation Details |
| :--- | :--- | :---: | :--- |
| **English $\leftrightarrow$ Hindi Translation** | Faithful translation without summary or filler. | **PASS** | `GeminiTranslationProvider` enforces strict factual system instructions and glossaries. |
| **12 Verified Gemini Voices** | Celestial voices (Kore, Puck, Charon, Aoede, Fenrir, etc.) verified and selectable. | **PASS** | `GeminiSpeechSynthesisProvider.VERIFIED_GEMINI_VOICES` implements all 12 official profiles. |
| **Dual Engine Speech Fallback** | When offline or without API key, routes to on-device Android TTS. | **PASS** | `SpeechSynthesisDispatcher` transparently falls back to `NativeTextToSpeechProvider`. |
| **Zero Hardcoded Secrets** | No API keys committed or hardcoded in source. | **PASS** | `SecureApiKeyStorage` uses hardware-backed Android Keystore `EncryptedSharedPreferences`. |
| **Cloud Processing Consent** | Explicit user consent required before cloud transmission. | **PASS** | `SecureApiKeyStorage.hasCloudProcessingConsent()` gates all cloud requests. |

---

## 4. Playback, Background & Lifecycle

| Test Case | Expected Behavior | Result | Evidence / Implementation Details |
| :--- | :--- | :---: | :--- |
| **Background & Screen-Off Playback** | Continuous audio continues when screen is locked or app is backgrounded. | **PASS** | `SpeechMediaSessionService` runs with `foregroundServiceType="mediaPlayback"`. |
| **Notification Transport Controls** | Play/Pause, -10s rewind, +30s forward, next/prev section in notification. | **PASS** | `MediaSession` custom commands `CUSTOM_COMMAND_REWIND_10` & `CUSTOM_COMMAND_FORWARD_30`. |
| **Audio Focus & Headphone Unplug** | Pauses on incoming call or when headphones are disconnected. | **PASS** | `AudioFocusManager` handles transient loss and `ACTION_AUDIO_BECOMING_NOISY`. |
| **Pitch-Preserving Speed Stretch** | 0.75x–2.0x playback speed changes without resetting playback position. | **PASS** | `PlaybackQueueManager.setPlaybackSpeed` applies speed parameters directly to ExoPlayer. |
| **High Speed Advisory** | Speeds $> 2.0x$ display clear acoustic compression advisory. | **PASS** | `SpeedController.requiresQualityWarning` validates speeds $>2.0x$. |
| **Lookahead Prebuffer** | Upcoming chunks pre-synthesized in background to prevent audible gaps. | **PASS** | `PlaybackQueueManager.triggerLookaheadPrebuffer` buffers chunks $N+1$ and $N+2$. |
| **Position Persistence & Resume** | Playback position saved to Room database; resumes on relaunch. | **PASS** | `DocumentDao.updatePlaybackPosition` records timestamps and chunk IDs in SQLite. |

---

## 5. UI/UX & Identity Verification

| Test Case | Expected Behavior | Result | Evidence / Implementation Details |
| :--- | :--- | :---: | :--- |
| **App Name** | SpeechWithAbhi | **PASS** | Prominently displayed across UI and AndroidManifest. |
| **Developer Credit** | Jaswant Dehariya | **PASS** | Prominently credited in Home, About, strings.xml, and headers. |
| **Vector Launcher Icon** | Open-page + harmonic audio soundwave vector design. | **PASS** | Adaptive vector icons in `drawable/` and `mipmap-anydpi-v26/`. |
| **Material 3 Theming** | Midnight Navy & Warm Amber dark and light themes. | **PASS** | Accessible contrast color scheme in `Color.kt` and `Theme.kt`. |
| **Localization** | Full Hindi (`values-hi`) and English (`values`) resources. | **PASS** | Localized strings in `strings.xml` and `values-hi/strings.xml`. |

---

*QA Acceptance Test Report signed and approved for SpeechWithAbhi by Jaswant Dehariya.*
