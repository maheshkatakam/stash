package com.maheshkatakam.stash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maheshkatakam.stash.audio.VoiceRecorder
import com.maheshkatakam.stash.data.AttachmentKind
import com.maheshkatakam.stash.data.CaptureDraft
import com.maheshkatakam.stash.data.ImportedFile
import com.maheshkatakam.stash.data.StashCollection
import com.maheshkatakam.stash.data.Tag
import com.maheshkatakam.stash.data.normalizeTag
import com.maheshkatakam.stash.link.LinkPreview
import com.maheshkatakam.stash.link.LinkPreviewFetcher
import com.maheshkatakam.stash.link.Links
import com.maheshkatakam.stash.ui.capture.CaptureSheet
import com.maheshkatakam.stash.ui.theme.StashTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class CaptureMode { SHARE, NOTE, LIST, VOICE }

/**
 * The "Save to Stash" sheet. It slides up over whatever app you shared from
 * (or from the widget, Quick Settings tile, app shortcuts and selected-text menu).
 */
class CaptureActivity : ComponentActivity() {

    private val app get() = application as StashApp
    private val vm: CaptureViewModel by viewModels { viewModelFactory { initializer { CaptureViewModel(app) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) vm.load(intent)

        setContent {
            val settings by app.settingsState.collectAsStateWithLifecycle()
            val dark = when (settings?.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            StashTheme(dark = dark, dynamicColor = settings?.dynamicColor ?: true) {
                CaptureSheet(vm = vm, onClose = { finish() })
            }
        }
    }

    companion object {
        const val ACTION_QUICK_NOTE = "com.maheshkatakam.stash.QUICK_NOTE"
        const val ACTION_QUICK_LIST = "com.maheshkatakam.stash.QUICK_LIST"
        const val ACTION_QUICK_VOICE = "com.maheshkatakam.stash.QUICK_VOICE"
    }
}

class CaptureViewModel(private val app: StashApp) : ViewModel() {
    private val repo = app.repository

    var mode by mutableStateOf(CaptureMode.NOTE)
        private set
    var title by mutableStateOf("")
    var text by mutableStateOf("")
    var url by mutableStateOf<String?>(null)
        private set
    private var subject: String? = null
    var preview by mutableStateOf<LinkPreview?>(null)
        private set
    var previewLoading by mutableStateOf(false)
        private set
    var checklist by mutableStateOf(false)
    var collectionId by mutableStateOf<Long?>(null)
    val tags = mutableStateListOf<String>()
    val files = mutableStateListOf<ImportedFile>()
    var copying by mutableIntStateOf(0)
        private set
    var failedCopies by mutableIntStateOf(0)
        private set
    var saving by mutableStateOf(false)
        private set

    // Voice recording inside the sheet.
    private var recorder: VoiceRecorder? = null
    private var recordFile: File? = null
    var recording by mutableStateOf(false)
        private set
    var recordingSince by mutableLongStateOf(0L)
        private set
    var autoStartedVoice = false

    private var saved = false

    val collections: StateFlow<List<StashCollection>> =
        repo.collections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allTags: StateFlow<List<Tag>> = repo.tags.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasContent: Boolean
        get() = title.isNotBlank() || text.isNotBlank() || url != null || files.isNotEmpty()

    fun load(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> loadShare(intent)
            Intent.ACTION_PROCESS_TEXT -> {
                mode = CaptureMode.SHARE
                applySharedText(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty())
            }
            CaptureActivity.ACTION_QUICK_LIST -> {
                mode = CaptureMode.LIST
                checklist = true
            }
            CaptureActivity.ACTION_QUICK_VOICE -> mode = CaptureMode.VOICE
            else -> mode = CaptureMode.NOTE
        }
    }

    private fun loadShare(intent: Intent) {
        mode = CaptureMode.SHARE
        subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
        applySharedText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())
        // Without a link, a shared subject (e.g. from an email) makes a good title.
        if (url == null && subject != null) title = subject.orEmpty()

        val uris = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        } else {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        }
        if (uris.isEmpty()) {
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it }
            }
        }
        copyUris(uris.distinct())
    }

    private fun applySharedText(shared: String) {
        val found = Links.firstUrl(shared)
        if (found != null) {
            url = Links.clean(found)
            // Instagram/X often share just the link; anything else they send becomes the note.
            text = shared.replace(found, "").trim()
            fetchPreview()
        } else {
            text = shared.trim()
        }
    }

    private fun fetchPreview() {
        val u = url ?: return
        viewModelScope.launch {
            if (!app.settings.flow.first().previews) return@launch
            previewLoading = true
            preview = runCatching { LinkPreviewFetcher.fetch(u) }.getOrNull()
            previewLoading = false
        }
    }

    private fun copyUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        copying += uris.size
        viewModelScope.launch {
            for (u in uris) {
                val imported = repo.files.importUri(u)
                if (imported != null) files += imported else failedCopies++
                copying--
            }
        }
    }

    fun removeFile(file: ImportedFile) {
        files.remove(file)
        repo.files.delete(file.fileName)
    }

    fun removeLink() {
        url = null
        preview = null
    }

    fun addTag(raw: String) {
        val t = normalizeTag(raw) ?: return
        if (t !in tags) tags += t
    }

    fun createCollection(name: String, emoji: String) = viewModelScope.launch {
        collectionId = repo.createCollection(name, emoji)
    }

    fun startRecording(): Boolean {
        if (recording) return true
        val f = repo.files.newFile("m4a")
        val r = VoiceRecorder(app)
        if (!r.start(f)) return false
        recorder = r
        recordFile = f
        recording = true
        recordingSince = SystemClock.elapsedRealtime()
        return true
    }

    fun stopRecording() {
        val r = recorder ?: return
        val duration = r.stop()
        recorder = null
        recording = false
        val f = recordFile ?: return
        recordFile = null
        if (duration != null && f.exists()) {
            files += repo.files.importedFromLocal(f, "Voice note", "audio/mp4", duration)
        }
    }

    fun level(): Float = recorder?.level() ?: 0f

    fun save(onDone: (Boolean) -> Unit) {
        if (saving || copying > 0) return
        if (recording) stopRecording()
        if (!hasContent) {
            onDone(false)
            return
        }
        saving = true
        viewModelScope.launch {
            val lines = if (checklist) text.lines().map { it.trim() }.filter { it.isNotEmpty() } else null
            val p = preview
            val draft = CaptureDraft(
                title = title,
                body = if (checklist) "" else text,
                url = url,
                files = files.toList(),
                checklistLines = lines,
                collectionId = collectionId,
                tags = tags.toList(),
                linkTitle = p?.title ?: subject?.takeIf { url != null },
                linkDescription = p?.description,
                linkImageUrl = p?.imageUrl,
            )
            val previewsOn = app.settings.flow.first().previews
            repo.saveCapture(draft, fetchPreview = previewsOn)
            saved = true
            onDone(true)
        }
    }

    override fun onCleared() {
        recorder?.cancel()
        // Cancelled share: throw away the copies made for it.
        if (!saved) files.forEach { repo.files.delete(it.fileName) }
    }

    val firstImage: ImportedFile?
        get() = files.firstOrNull { it.kind == AttachmentKind.IMAGE }
}
