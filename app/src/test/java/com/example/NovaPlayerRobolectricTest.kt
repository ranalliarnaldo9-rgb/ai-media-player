package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.database.DownloadTaskEntity
import com.example.database.MediaItemEntity
import com.example.database.NovaDatabase
import com.example.download.StreamInspectionResult
import com.example.download.StreamInspector
import com.example.model.AspectRatioMode
import com.example.model.MediaItemModel
import com.example.model.MediaType
import com.example.playback.PlaybackManager
import com.example.viewmodel.DownloadFilter
import com.example.viewmodel.MediaViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NovaPlayerRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: NovaDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDownloadDaoOperations() = runBlocking {
        val dao = db.downloadDao()
        val task = DownloadTaskEntity(
            id = "test_1",
            url = "https://test.com/sample.mp4",
            title = "Test Video",
            resolution = "1080p FHD",
            status = "DOWNLOADING"
        )
        dao.insertOrUpdate(task)

        var list = dao.getAllDownloads().first()
        assertEquals(1, list.size)
        assertEquals("DOWNLOADING", list[0].status)

        // Update progress
        dao.updateProgress(
            id = "test_1",
            downloaded = 5000L,
            total = 10000L,
            speed = 1024L,
            eta = 5L,
            status = "DOWNLOADING"
        )

        var updated = dao.getDownloadById("test_1").first()
        assertNotNull(updated)
        assertEquals(5000L, updated?.downloadedBytes)
        assertEquals(10000L, updated?.totalBytes)
        assertEquals(1024L, updated?.speedBytesPerSec)

        // Mark completed
        dao.markCompleted("test_1", "content://media/external/video/media/99", 10000L)
        updated = dao.getDownloadById("test_1").first()
        assertEquals("COMPLETED", updated?.status)
        assertEquals("content://media/external/video/media/99", updated?.localUri)

        // Delete
        dao.deleteById("test_1")
        list = dao.getAllDownloads().first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun testMediaDaoOperations() = runBlocking {
        val dao = db.mediaDao()
        val item = MediaItemEntity(
            id = "vid_1",
            title = "Sample Video",
            path = "/storage/emulated/0/Movies/sample.mp4",
            uri = "content://media/1",
            durationMs = 60000L,
            sizeBytes = 10485760L,
            mimeType = "video/mp4",
            mediaType = "VIDEO",
            resolution = "1080p",
            width = 1920,
            height = 1080,
            dateAdded = System.currentTimeMillis(),
            folderPath = "/storage/emulated/0/Movies",
            folderName = "Movies"
        )
        dao.insertMediaItems(listOf(item))

        val videos = dao.getMediaByType("VIDEO").first()
        assertEquals(1, videos.size)
        assertEquals("Sample Video", videos[0].title)

        dao.setFavorite("vid_1", true)
        val favs = dao.getFavoriteMedia().first()
        assertEquals(1, favs.size)

        dao.updatePlaybackPosition("vid_1", 25000L, System.currentTimeMillis())
        val continueList = dao.getContinueWatching().first()
        assertEquals(1, continueList.size)
        assertEquals(25000L, continueList[0].playbackPositionMs)
    }

    @Test
    fun testStreamInspectorInvalidUrl() = runBlocking {
        val inspector = StreamInspector()
        val result = inspector.inspectUrl("not_a_valid_url")
        assertTrue(result is StreamInspectionResult.Error)
        assertEquals("Please enter a valid HTTP or HTTPS stream URL", (result as StreamInspectionResult.Error).message)
    }

    @Test
    fun testPlaybackManagerControls() {
        val pm = PlaybackManager.getPlaybackManager(context)
        val dummyMedia = MediaItemModel(
            id = "dummy_1",
            title = "Test Stream",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )

        pm.playMedia(dummyMedia)
        assertEquals(dummyMedia, pm.currentMedia.value)

        pm.setSpeed(1.5f)
        assertEquals(1.5f, pm.playbackSpeed.value, 0.01f)

        pm.setAspectRatio(AspectRatioMode.ZOOM)
        assertEquals(AspectRatioMode.ZOOM, pm.aspectRatioMode.value)

        pm.stopPlayback()
        assertNull(pm.currentMedia.value)
        assertFalse(pm.isPlaying.value)
    }

    @Test
    fun testPlayStreamUrlOnlinePlayback() {
        val pm = PlaybackManager.getPlaybackManager(context)
        val mp4Url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        pm.playStreamUrl(mp4Url)
        assertNotNull(pm.currentMedia.value)
        assertEquals(mp4Url, pm.currentMedia.value?.uri)
        assertEquals("video/mp4", pm.currentMedia.value?.mimeType)

        val hlsUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        pm.playStreamUrl(hlsUrl)
        assertNotNull(pm.currentMedia.value)
        assertEquals(hlsUrl, pm.currentMedia.value?.uri)
        assertEquals("application/x-mpegURL", pm.currentMedia.value?.mimeType)

        val ytUrl = "https://youtu.be/fQtHJvMr9ik"
        pm.playStreamUrl(ytUrl)
        assertNotNull(pm.currentMedia.value)
        assertEquals(true, pm.currentMedia.value?.isWebStream)
        assertEquals("video/youtube", pm.currentMedia.value?.mimeType)
        assertTrue(pm.currentMedia.value?.webEmbedUrl?.contains("fQtHJvMr9ik") == true)

        pm.stopPlayback()
    }

    @Test
    fun testAllBottomNavigationTabsClickableWithoutCrash() {
        val application = ApplicationProvider.getApplicationContext<NovaApplication>()
        val viewModel = MediaViewModel(application)

        composeTestRule.setContent {
            NovaApp(viewModel)
        }

        composeTestRule.waitForIdle()

        // Click Videos Tab
        composeTestRule.onNodeWithTag("nav_tab_videos").performClick()
        composeTestRule.waitForIdle()

        // Click Folders Tab
        composeTestRule.onNodeWithTag("nav_tab_folders").performClick()
        composeTestRule.waitForIdle()

        // Click Audio Tab
        composeTestRule.onNodeWithTag("nav_tab_audio").performClick()
        composeTestRule.waitForIdle()

        // Click Downloads Tab
        composeTestRule.onNodeWithTag("nav_tab_downloads").performClick()
        composeTestRule.waitForIdle()

        // Click Vault Tab
        composeTestRule.onNodeWithTag("nav_tab_vault").performClick()
        composeTestRule.waitForIdle()

        // Click back to Videos
        composeTestRule.onNodeWithTag("nav_tab_videos").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testDownloadsScreenFilterChipsClickableWithoutCrash() {
        val application = ApplicationProvider.getApplicationContext<NovaApplication>()
        val viewModel = MediaViewModel(application)

        composeTestRule.setContent {
            NovaApp(viewModel)
        }

        composeTestRule.waitForIdle()

        // Navigate to Downloads tab
        composeTestRule.onNodeWithTag("nav_tab_downloads").performClick()
        composeTestRule.waitForIdle()

        // Click Filter Chips
        composeTestRule.onNodeWithTag("filter_all_chip").performClick()
        composeTestRule.waitForIdle()
        assertEquals(DownloadFilter.ALL, viewModel.downloadFilter.value)

        composeTestRule.onNodeWithTag("filter_active_chip").performClick()
        composeTestRule.waitForIdle()
        assertEquals(DownloadFilter.ACTIVE, viewModel.downloadFilter.value)

        composeTestRule.onNodeWithTag("filter_completed_chip").performClick()
        composeTestRule.waitForIdle()
        assertEquals(DownloadFilter.COMPLETED, viewModel.downloadFilter.value)
    }

    @Test
    fun testStreamDialogClickableWithoutCrash() {
        val application = ApplicationProvider.getApplicationContext<NovaApplication>()
        val viewModel = MediaViewModel(application)

        composeTestRule.setContent {
            NovaApp(viewModel)
        }

        composeTestRule.waitForIdle()

        // Open stream dialog from downloads screen (the sole single location for download stream)
        composeTestRule.onNodeWithTag("nav_tab_downloads").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("downloads_add_stream_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.showStreamDialog.value)

        // Cancel dialog
        composeTestRule.onNodeWithTag("cancel_stream_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.showStreamDialog.value)
    }

    @Test
    fun testThreeDotsMenusClickableWithoutCrash() {
        val application = ApplicationProvider.getApplicationContext<NovaApplication>()
        val viewModel = MediaViewModel(application)

        composeTestRule.setContent {
            NovaApp(viewModel)
        }

        composeTestRule.waitForIdle()

        // Click sort dropdown menu button
        composeTestRule.onNodeWithTag("sort_videos_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testCanceledDownloadRetryDobaraKaro() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<NovaApplication>()
        val viewModel = MediaViewModel(application)

        val canceledTask = DownloadTaskEntity(
            id = "canceled_task_1",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            title = "Canceled Video Stream",
            resolution = "1080p FHD",
            status = "CANCELED"
        )
        NovaDatabase.getDatabase(application).downloadDao().insertOrUpdate(canceledTask)

        composeTestRule.setContent {
            NovaApp(viewModel)
        }

        composeTestRule.waitForIdle()

        // Navigate to Downloads screen
        composeTestRule.onNodeWithTag("nav_tab_downloads").performClick()
        composeTestRule.waitForIdle()

        // Tap the Restart Download (Dobara Karo) button
        composeTestRule.onNodeWithTag("retry_download_button_canceled_task_1").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testWebStreamControlsWithWebPlayerController() {
        val pm = PlaybackManager.getPlaybackManager(context)
        val ytUrl = "https://www.youtube.com/watch?v=fQtHJvMr9ik"
        pm.playStreamUrl(ytUrl)

        var playCalled = false
        var pauseCalled = false
        var seekSec = 0f
        var speedRate = 0f

        pm.webPlayerController = object : PlaybackManager.WebPlayerController {
            override fun webPlay() { playCalled = true }
            override fun webPause() { pauseCalled = true }
            override fun webSeekTo(seconds: Float) { seekSec = seconds }
            override fun webSetPlaybackRate(rate: Float) { speedRate = rate }
        }

        // Bridge updates progress and state from WebView
        pm.setWebPlaybackState(playing = true, buffering = false)
        pm.setWebProgress(currentSec = 15f, durationSec = 180f)

        assertTrue(pm.isPlaying.value)
        assertEquals(15000L, pm.currentPositionMs.value)
        assertEquals(180000L, pm.durationMs.value)

        // Test toggle play/pause invokes webPlayerController
        pm.togglePlayPause()
        assertTrue(pauseCalled)
        assertFalse(pm.isPlaying.value)

        pm.togglePlayPause()
        assertTrue(playCalled)
        assertTrue(pm.isPlaying.value)

        // Test seek forward (+10s)
        pm.seekForward(10000L)
        assertEquals(25f, seekSec, 0.01f)

        // Test seek backward (-10s)
        pm.seekBackward(10000L)
        assertEquals(15f, seekSec, 0.01f)

        // Test speed change
        pm.setSpeed(1.5f)
        assertEquals(1.5f, speedRate, 0.01f)

        // Test A/B loop
        val msg1 = pm.toggleABLoop()
        assertTrue(msg1.contains("A Point Set"))
        assertNotNull(pm.abLoop.value)
        assertEquals(15000L, pm.abLoop.value?.startMs)

        pm.setWebProgress(currentSec = 45f, durationSec = 180f)
        val msg2 = pm.toggleABLoop()
        assertTrue(msg2.contains("A/B Loop Active"))
        assertEquals(45000L, pm.abLoop.value?.endMs)

        val msg3 = pm.toggleABLoop()
        assertTrue(msg3.contains("Disabled"))
        assertNull(pm.abLoop.value)

        pm.stopPlayback()
    }

    @Test
    fun testBookmarkDaoOperations() = runBlocking {
        val dao = db.bookmarkDao()
        val bookmark = com.example.database.BookmarkEntity(
            id = "bm_test_1",
            mediaId = "video_bm_1",
            mediaTitle = "Test Scene Video",
            mediaUri = "https://test.com/sample.mp4",
            timestampMs = 45000L,
            label = "Awesome Climax Scene",
            note = "Best part of movie"
        )
        dao.insertNewBookmark(bookmark)

        var list = dao.fetchAllBookmarks().first()
        assertEquals(1, list.size)
        assertEquals("Awesome Climax Scene", list[0].label)
        assertEquals(45000L, list[0].timestampMs)

        val forMedia = dao.fetchBookmarksForMedia("video_bm_1").first()
        assertEquals(1, forMedia.size)
        assertEquals("bm_test_1", forMedia[0].id)

        dao.removeBookmarkRecord("bm_test_1")
        list = dao.fetchAllBookmarks().first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun testPlayMediaFromBookmark() {
        val application = ApplicationProvider.getApplicationContext<NovaApplication>()
        val viewModel = MediaViewModel(application)
        val dummyMedia = MediaItemModel(
            id = "video_bm_1",
            title = "Test Scene Video",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )
        viewModel.playMediaFromBookmark(dummyMedia, 45000L)
        assertEquals(dummyMedia, viewModel.playbackManager.currentMedia.value)
    }
}
