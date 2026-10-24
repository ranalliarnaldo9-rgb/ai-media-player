# NOVA Media Player: Online Stream Upgrade & Download Manager (Revised)

Comprehensive upgrade plan for NOVA Media Player, adding authenticated direct/HLS streaming with genuine resolution detection, a resilient foreground download service supporting HTTP range resumption, and a dedicated Downloads library tab with persistent Room tracking.

> [!IMPORTANT]
> **Key Technical Guarantees & Reality Checks**:
> - **Truthful Resolution Probing**: Direct progressive streams (MP4/WebM) provide a single stream resolution; NOVA will probe the actual track dimensions via Media3 track inspection rather than fabricating artificial resolution options. For HLS (`.m3u8`), NOVA parses master playlist tags (`#EXT-X-STREAM-INF`) to expose only verified variants with real bandwidth and resolution data.
> - **Resumable HTTP Range Protocol**: HTTP `Range: bytes=X-` resumption is verified against server responses (`Accept-Ranges: bytes` or `206 Partial Content`). Non-resumable servers fall back to restart-safe transfers with clean error propagation.
> - **Background Reliability**: Background tasks execute via an Android `ForegroundService` with dynamic notification telemetry and `FOREGROUND_SERVICE_DATA_SYNC` type, ensuring persistence on modern Android versions without system kills.
> - **Zero Broad Storage Permissions**: Completed media is saved via scoped `MediaStore.Video.Media` and `MediaStore.Downloads`, remaining accessible in system gallery apps without deprecated `READ/WRITE_EXTERNAL_STORAGE` permissions.
> - **DRM & Access Restrictions**: Protected streams (e.g., Widevine DRM) and private platform paywalls are cleanly identified, and downloading is gracefully disabled with user feedback.

---

### 1. Overview & Core Concept

- **What It Does**: Upgrades NOVA with an integrated online streaming dialog (paste/clear, instant format validation, direct and HLS playback) and a download management system. Users can inspect genuine available stream variants, download content in the background with live speed/ETA metrics, and access persistent offline media in a dedicated Downloads tab.
- **Target Audience**: Media enthusiasts requiring a single, high-performance player for both local files and authenticated network streams without third-party ad-laden downloaders.
- **Key Value**: Delivers honest stream discovery, background downloading, and seamless local library playback while preserving NOVA's existing screens, playback engine, and dark theme.

---

### 2. User Experience & Visual Design

#### Visual Identity & Theme Alignment
- **Aesthetic Direction**: Deep cyberpunk/futuristic void matching NOVA's existing UI.
- **Color Palette**:
  - Background Base: Pure Deep Void (`#0B0F17`)
  - Elevated Cards & Sheets: Frosted Slate (`#121824` / `#162030`)
  - Accent / Highlights: Nova Cyan (`#00E5FF`)
  - Success / Active Telemetry: Neon Mint (`#00E676`)
  - Warning / Incomplete: Electric Amber (`#FFB300`)
  - Error: Coral Crimson (`#FF5252`)
  - Typography: Pure White (`#FFFFFF`) with secondary slate (`#8E9CAB`)
- **Spatial Composition**: 16dp outer padding, 12dp card corner radius, minimum 48dp interactive touch targets, and subtle 1dp border strokes (`Color.White.copy(alpha = 0.08f)`).

#### Key User Flows

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Network Stream Dialog                                                    │
│    [ Clean URL Input Field ]  [ Paste Icon ]  [ Clear Icon ]                │
│    Format Badge: [ MP4 Direct ] or [ HLS Stream (.m3u8) ]                   │
│    Actions: [ Cancel ]     [ Download Options ]     [ Play Stream (Cyan) ]  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. Resolution Selection Bottom Sheet (Authentic Variants Only)              │
│    Probing source stream: "Analyzing master playlist & video tracks..."     │
│    Variants detected:                                                       │
│    ┌───────────────────────────────────────────────────────────────────┐    │
│    │ ● 1080p (FHD)  •  1920x1080  •  4.8 Mbps  •  ~340 MB              │    │
│    │ ○  720p (HD)   •  1280x720   •  2.2 Mbps  •  ~160 MB              │    │
│    │ ○  480p (SD)   •   854x480   •  1.1 Mbps  •   ~85 MB              │    │
│    └───────────────────────────────────────────────────────────────────┘    │
│    Actions: [ Dismiss ]                         [ Start Download Button ]   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. Active Background Download & System Notification                         │
│    Persistent Notification: "Downloading Cyberpunk_Teaser.mp4 (72%)"        │
│    Speed: 3.4 MB/s  •  ETA: 24s  •  Controls: [ Pause ] [ Cancel ]         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. Downloads Library Screen (Bottom Nav Tab)                                │
│    Filter Chips: [ All (4) ]  [ Active (1) ]  [ Completed (3) ]             │
│    Video Card:                                                              │
│    [ Thumbnail ]  Title: Big_Buck_Bunny_1080p.mp4                           │
│                   Size: 158 MB  •  Duration: 09:56  •  Quality: 1080p       │
│                   Status: Completed (Tap to Play in NOVA Player)            │
│                   More: [ Play ]  [ Share ]  [ Show in Files ]  [ Delete ]  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Genuine Resolution Discovery vs. Simulated Variants**
  - *Chosen Approach*: Progressive MP4/WebM files expose exactly the single resolution encoded into their container. For HLS (`.m3u8`), NOVA parses the master playlist to extract actual `#EXT-X-STREAM-INF` variants with verified resolutions and bandwidths.
  - *Why*: Eliminates misleading "fake resolution" selectors that either fail or redownload the identical file under a different label.
  - *Alternatives Considered*: Hardcoded 1080p/720p/480p selectors (rejected as dishonest and error-prone).

- **Decision 2: Foreground Service with OkHttp vs. WorkManager vs. Android DownloadManager**
  - *Chosen Approach*: Native Kotlin Coroutines `ForegroundService` using an optimized `OkHttpClient` instance with buffer streaming, byte counting, and persistent notifications.
  - *Why*: Provides sub-second speed telemetry, seamless pause/resume with byte ranges, immediate cancellation, and prevents background execution limits on Android 12–15.
  - *Alternatives Considered*: Android system `DownloadManager` lacks real-time progress callbacks and has inconsistent HTTP header controls; `WorkManager` introduces execution delays not suited for interactive media downloads.

- **Decision 3: HTTP Range Resumption Protocol**
  - *Chosen Approach*: Verify `Accept-Ranges: bytes` header and initiate resume via `Range: bytes={existingBytes}-`. Confirm server responds with HTTP `206 Partial Content`. If server responds with HTTP `200 OK`, restart from byte 0 to prevent file corruption.
  - *Why*: Guarantees data integrity for interrupted transfers across flaky cellular/Wi-Fi connections.

- **Decision 4: MediaStore Insertion & Scoped Storage**
  - *Chosen Approach*: Download files to an app-isolated cache directory (`context.cacheDir/downloads/`) during transfer; upon 100% completion, stream into `MediaStore.Video.Media` or `MediaStore.Downloads` with `IS_PENDING = 0`.
  - *Why*: Avoids partial/corrupt files appearing in system media pickers while maintaining compatibility across Android 10 through 15 without legacy storage permissions.

- **Decision 5: HLS / Separate Audio-Video Streams & DRM Handling**
  - *Chosen Approach*: For HLS downloads, utilize AndroidX Media3 `DownloadHelper` with segment fetching and TS/FMP4 demuxing, or direct single-stream progressive fallback. For streams with DRM or access restriction signatures, display a clear warning ("Stream is protected or restricted; download is not permitted").
  - *Why*: Upholds platform policies, respects DRM boundaries, and guarantees playable output files.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             UI / Presentation Layer                         │
│  ┌──────────────────────┐  ┌───────────────────────┐  ┌──────────────────┐  │
│  │ NetworkStreamDialog  │  │ ResolutionBottomSheet │  │  DownloadsScreen │  │
│  └──────────┬───────────┘  └───────────┬───────────┘  └────────┬─────────┘  │
│             │                          │                       │            │
│             ▼                          ▼                       ▼            │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │            StreamViewModel      &     DownloadViewModel               │  │
│  └─────────────────────────────────────┬─────────────────────────────────┘  │
└────────────────────────────────────────┼────────────────────────────────────┘
                                         │
┌────────────────────────────────────────▼────────────────────────────────────┐
│                             Domain & Engine Layer                           │
│  ┌─────────────────────────┐               ┌─────────────────────────────┐  │
│  │   StreamInspector       │               │      DownloadManager        │  │
│  │  - HLS Playlist Parser  │               │  - Foreground Service       │  │
│  │  - Progressive Prober   │               │  - OkHttp Resumable Engine  │  │
│  └─────────────────────────┘               └──────────────┬──────────────┘  │
└───────────────────────────────────────────────────────────┼─────────────────┘
                                                            │
┌───────────────────────────────────────────────────────────▼─────────────────┐
│                             Persistence & OS Layer                          │
│  ┌─────────────────────────┐               ┌─────────────────────────────┐  │
│  │  Room: DownloadDao      │               │     Android MediaStore      │  │
│  │  (downloads table)      │               │  (Scoped Video Storage)     │  │
│  └─────────────────────────┘               └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Database Schema (`downloads` Table)
```kotlin
@Entity(tableName = "downloads")
data class DownloadTask(
    @PrimaryKey val id: String, // UUID or hashed URL
    val url: String,
    val title: String,
    val mimeType: String,
    val resolutionLabel: String, // e.g. "1080p (FHD)" or "720p"
    val width: Int,
    val height: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus, // QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val localUri: String?, // MediaStore ContentUri once finished
    val tempFilePath: String?,
    val errorMessage: String?,
    val isResumable: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
```

#### Exact Files to Modify & New Files to Add

| File Path | Type | Role & Changes |
| :--- | :--- | :--- |
| `app/src/main/java/com/example/ui/screens/MainScreen.kt` | Modify | Add `Downloads` tab to `NavigationBar` with icon `Icons.Default.Download` without disturbing `Videos`, `Folders`, `Audio`, or other destinations. |
| `app/src/main/java/com/example/ui/components/NetworkStreamDialog.kt` | Modify / Create | Upgraded dialog with URL input, clipboard paste, clear icon, format badges, and buttons for "Play Stream" & "Download". |
| `app/src/main/java/com/example/ui/components/ResolutionSelectionSheet.kt` | Create | Compact M3 bottom sheet displaying genuine probe results, file size estimations, and download confirmation. |
| `app/src/main/java/com/example/ui/screens/DownloadsScreen.kt` | Create | Complete Downloads library page with filter chips, active download cards with speed/ETA, completed media cards, and action menus. |
| `app/src/main/java/com/example/ui/screens/VideoPlayerScreen.kt` | Modify | Add quick "Download Stream" button in the top bar / menu when playing an active online network stream. |
| `app/src/main/java/com/example/download/StreamInspector.kt` | Create | Detects stream type, parses HLS master playlist variants (`#EXT-X-STREAM-INF`), and queries progressive headers/metadata. |
| `app/src/main/java/com/example/download/NovaDownloadService.kt` | Create | Android `ForegroundService` with notification channel, live telemetry emitter, and OkHttp chunked streaming. |
| `app/src/main/java/com/example/download/DownloadRepository.kt` | Create | Coordinates Room database entries, start/pause/resume/cancel actions, and MediaStore publishing. |
| `app/src/main/java/com/example/data/db/NovaDatabase.kt` | Modify / Create | Room Database definition with `DownloadTask` entity and migration logic. |
| `app/src/main/java/com/example/data/db/DownloadDao.kt` | Create | DAO for reactive Flow queries (`getAllDownloads()`, `getActiveDownloads()`, CRUD ops). |
| `app/src/main/AndroidManifest.xml` | Modify | Declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `INTERNET`, and service component. |
| `app/build.gradle.kts` | Modify | Ensure `androidx.room`, `androidx.media3:media3-exoplayer-hls`, and `okhttp3:okhttp` dependencies are linked. |

---

### 5. Verification Plan

1. **Compilation & Build**:
   - Run `compile_applet` to verify clean build, correct Kotlin DSL configurations, and zero missing symbols.
2. **Unit & Logic Tests**:
   - Execute Robolectric tests via `gradle :app:testDebugUnitTest`:
     - Test HLS master playlist parsing with multi-bitrate streams.
     - Test HTTP range header calculation and partial resumption logic.
     - Test Room DAO insert, update status, and reactive Flow emissions.
3. **End-to-End User Journey Verification**:
   - **Stream Playback**: Input direct MP4 and HLS (`.m3u8`) URLs in Network Stream dialog; verify player loads and renders video.
   - **Resolution Detection**: Verify that progressive MP4 shows its exact single resolution, while multi-variant HLS lists authentic renditions with no fake options.
   - **Download Execution**: Start download, observe real-time speed/progress/ETA in UI and notification, test pause and resume.
   - **Persistence & Playback**: Close and reopen app; confirm download history is intact in the Downloads tab and completed media plays in NOVA.
