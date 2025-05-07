package io.github.luposolitario.mbi

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import io.github.luposolitario.mbi.model.Hit
import io.github.luposolitario.mbi.model.HitRadio
import io.github.luposolitario.mbi.service.PixbayImageService
import io.github.luposolitario.mbi.service.PixbayVideoService
import io.github.luposolitario.mbi.service.RadioBrowserService
import io.github.luposolitario.mbi.util.SettingsActivity
import io.github.luposolitario.mbi.viewmodel.ImageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // Variabile globale per tenere traccia del tipo di media selezionato
    companion object {
        private const val TAG = "MainActivity"

        // Costanti per identificare il tipo di media
        const val MEDIA_TYPE_IMAGE = 0
        const val MEDIA_TYPE_VIDEO = 1
        const val MEDIA_TYPE_AUDIO = 2

    }


    // Variabile per tenere traccia del tipo di media attualmente selezionato
    private var currentMediaType = MEDIA_TYPE_IMAGE

    // Riferimenti ai pulsanti per un accesso più semplice
    private lateinit var btnImages: MaterialButton
    private lateinit var btnVideos: MaterialButton
    private lateinit var btnAudios: MaterialButton
    private lateinit var topAppBar: MaterialToolbar

    // Riferimenti ai visualizzatori di media
    private lateinit var playerContainer: FrameLayout
    private lateinit var imageViewer: PhotoView
    private var playerView: PlayerView? = null
    private var player: ExoPlayer? = null
    private lateinit var searchInput: EditText

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private var isFullScreen = false
    private var query = ""
    private var currentScaleTypeIndex = 0
    private var offset = 10
    @Inject
    lateinit var pixbayImageService: PixbayImageService
    private lateinit var pixbayVideoService: PixbayVideoService
    private lateinit var radioBrowserService: RadioBrowserService
    private var currentMedia: Hit? = null
    private var currentRadio: HitRadio? = null

//    private lateinit var imageViewModel: ImageViewModel

    private val imageViewModel: ImageViewModel by viewModels()

    private val scaleTypes = arrayOf(
        ImageView.ScaleType.CENTER_CROP,
        ImageView.ScaleType.FIT_CENTER,
        ImageView.ScaleType.CENTER_INSIDE,
        ImageView.ScaleType.FIT_XY,
        ImageView.ScaleType.CENTER,
        ImageView.ScaleType.FIT_START,
        ImageView.ScaleType.FIT_END
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // --- Setup Tema e ViewModel ---
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE) // [Source 7]
        val themePref = sharedPref.getString("selected_theme", "light")
        if (!themePref.equals("dark")) {
            setTheme(R.style.Theme_MBI)
        } else {
            setTheme(R.style.Theme_MBI_Dark) // [Source 8]
        }

        super.onCreate(savedInstanceState)

        // Inizializza ImageService passando il contesto dell'applicazione
//        pixbayImageService = PixbayImageService(applicationContext)
        pixbayVideoService = PixbayVideoService(applicationContext)
        radioBrowserService = RadioBrowserService(applicationContext)

        // --- Inizializza ViewModel ---
        // CREA L'ISTANZA DELLA FACTORY (CONTROLLA BENE GLI ARGOMENTI QUI!)
//        val factory = ImageViewModelFactory(this, pixbayImageService, intent?.extras)

        // USA LA FACTORY CON by viewModels
//        val imageViewModel: ImageViewModel by viewModels { factory }
//        this.imageViewModel = imageViewModel


        this.imageViewModel.currentImage.observe(this) {
            uiScope.launch {
                loadImageWithGlide(it?.largeImageURL.toString())
                updateResultsInfo()
            }
        }

        // Questo disattiva l'adattamento automatico
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            view.setPadding(0, statusBarHeight, 0, navBarHeight)
            insets
        }

        // --- 1. Inizializza SEMPRE le view principali ---
        initializeCoreViews() // Prende riferimenti a mediaViewer, bottoni, searchInput

        // --- 2. Configura i listener che devono essere sempre attivi ---
        setupListeners(savedInstanceState) // Imposta onClickListeners per bottoni, menu, search

        // --- 3. Imposta lo stato iniziale ---
        Log.d(TAG, "onCreate: Setting up initial state.")
        setupInitialContent() // Imposta mediaType=IMAGE, carica gif, ecc. chiama setupMediaViewer per immagine

    }

    // Funzione per ottenere i riferimenti alle View
    private fun initializeCoreViews() {
        Log.d(TAG, "Initializing core views...")
        playerContainer = findViewById(R.id.mediaLayerCard) // [Source 18]
        searchInput = findViewById(R.id.searchInput) // [Source 18]
        btnImages = findViewById(R.id.btnImages) // [Source 18]
        btnVideos = findViewById(R.id.btnVideos) // [Source 18]
        btnAudios = findViewById(R.id.btnAudios) // [Source 18]
        topAppBar = findViewById<MaterialToolbar>(R.id.topBar)
    }

    // Funzione per impostare i listener
    private fun setupListeners(savedInstanceState: Bundle?) {
        Log.d(TAG, "Setting up listeners...")
        btnImages.setOnClickListener {
            selectMediaType(MEDIA_TYPE_IMAGE)
            displayPlaceHolder()
        } // [Source 19]
        btnVideos.setOnClickListener { selectMediaType(MEDIA_TYPE_VIDEO) } // [Source 19]
        btnAudios.setOnClickListener { selectMediaType(MEDIA_TYPE_AUDIO) } // [Source 19]
        findViewById<MaterialButton>(R.id.btnPrevious).setOnClickListener {
            Log.d(TAG, "Previous button clicked")
            // Qui implementerai la navigazione al media precedente
            when (getCurrentMediaType()) {
                MEDIA_TYPE_IMAGE -> {
                    uiScope.launch {
                        imageViewModel.previous()
                    }
                }

                MEDIA_TYPE_VIDEO -> {
                    uiScope.launch {
                        pixbayVideoService.movePrevious { hit ->
                            currentMedia = hit
                            currentMedia?.let { loadVideoPlayer(it.videos?.medium?.url) }
                        }
                        updateResultsInfo()
                    }
                }

                MEDIA_TYPE_AUDIO -> {
                    uiScope.launch {
                        radioBrowserService.movePrevious { hit ->
                            currentRadio = hit
                            currentRadio?.let {
                                loadRadioPlayer(
                                    it.url,
                                    it.favicon.toString(),
                                    it.url.toString()
                                )
                            }
                        }
                        updateResultsInfo()
                    }
                }
            }
            vibrate()
            updateResultsInfo()
        } // [Source 29]
        findViewById<MaterialButton>(R.id.btnFirst).setOnClickListener {
            Log.d(TAG, "First button clicked")
            // Qui implementerai la navigazione al primo media
            when (getCurrentMediaType()) {
                MEDIA_TYPE_IMAGE -> {
                    uiScope.launch {
                        imageViewModel.first()
                        updateResultsInfo()
                    }
                }

                MEDIA_TYPE_VIDEO -> {
                    uiScope.launch {
                        pixbayVideoService.moveToFirst { hit ->
                            currentMedia = hit
                            currentMedia?.let { loadVideoPlayer(it.videos?.medium?.url) }
                        }
                        updateResultsInfo()
                    }
                }

                MEDIA_TYPE_AUDIO -> {
                    uiScope.launch {
                        radioBrowserService.moveToFirst { hit ->
                            currentRadio = hit
                            currentRadio?.let {
                                loadRadioPlayer(
                                    it.url,
                                    it.favicon.toString(),
                                    it.url.toString()
                                )
                            }
                        }
                        updateResultsInfo()
                    }
                }
            }
            // Se non ci sono immagini, carica la lista per navigazione
            vibrate()
            updateResultsInfo()
        } // [Source 33]
        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener {
            Log.d(TAG, "Next button clicked")
            // Qui implementerai la navigazione al media successivo
            when (getCurrentMediaType()) {
                MEDIA_TYPE_IMAGE -> {
                    uiScope.launch {
                        imageViewModel.next()
                    }
                }

                MEDIA_TYPE_VIDEO -> {
                    uiScope.launch {
                        pixbayVideoService.moveNext { hit ->
                            currentMedia = hit
                            currentMedia?.let { loadVideoPlayer(it.videos?.medium?.url) }
                        }
                        updateResultsInfo()
                    }
                }

                MEDIA_TYPE_AUDIO -> {
                    uiScope.launch {
                        radioBrowserService.moveNext { hit ->
                            currentRadio = hit
                            currentRadio?.let {
                                loadRadioPlayer(
                                    it.url,
                                    it.favicon.toString(),
                                    it.url.toString()
                                )
                            }
                        }
                        updateResultsInfo()
                    }
                }
            }
            vibrate()
            updateResultsInfo()
        } // [Source 37]
//        findViewById<MaterialButton>(R.id.topBar).setOnClickListener { view ->
//            Log.d(TAG, "Menu button clicked")
//            showPopupMenu(view)
//        } // [Source 20]


        topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {

                R.id.search -> {
                    val searchInput: EditText = findViewById(R.id.searchInput)
                    Log.d(TAG, "Search initiated with query: ${searchInput.text}")
                    // Qui andrebbe implementata la logica di ricerca
                    query = searchInput.text.toString()
                    when (getCurrentMediaType()) {
                        MEDIA_TYPE_IMAGE -> {
                            imageViewModel.search(query)
                        }

                        MEDIA_TYPE_VIDEO -> {
                            pixbayVideoService.searchMedia(query) { apiResponse ->
                                handleMediaResponse(apiResponse)
                                updateResultsInfo()
                            }
                        }

                        MEDIA_TYPE_AUDIO -> {
                            radioBrowserService.searchMedia(query) { apiResponse ->
                                handleRadioResponse(apiResponse)
                                updateResultsInfo()
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }

        findViewById<MaterialButton>(R.id.btnFw).setOnClickListener {
            Log.d(TAG, "Next button clicked")
            // Qui implementerai la navigazione al media successivo
            when (getCurrentMediaType()) {
                MEDIA_TYPE_IMAGE -> {
                    uiScope.launch {
                        imageViewModel.next(offset)
                    }
                }

                MEDIA_TYPE_VIDEO -> {
                    uiScope.launch {
                        pixbayVideoService.moveNext { hit ->
                            currentMedia = hit
                            currentMedia?.let { loadVideoPlayer(it.videos?.medium?.url) }
                        }
                        updateResultsInfo()
                    }
                }

                MEDIA_TYPE_AUDIO -> {
                    uiScope.launch {
                        radioBrowserService.moveNext { hit ->
                            currentRadio = hit
                            currentRadio?.let {
                                loadRadioPlayer(
                                    it.url,
                                    it.favicon.toString(),
                                    it.url.toString()
                                )
                            }
                        }
                        updateResultsInfo()
                    }
                }
            }
            vibrate()
            updateResultsInfo()
        } // [Source 37]
        findViewById<MaterialButton>(R.id.btnRew).setOnClickListener {
            Log.d(TAG, "Previous button clicked")
            // Qui implementerai la navigazione al media precedente
            when (getCurrentMediaType()) {
                MEDIA_TYPE_IMAGE -> {
                    uiScope.launch {
                        imageViewModel.previous(offset)
                    }
                }

                MEDIA_TYPE_VIDEO -> {
                    uiScope.launch {
                        pixbayVideoService.movePrevious { hit ->
                            currentMedia = hit
                            currentMedia?.let { loadVideoPlayer(it.videos?.medium?.url) }
                        }
                        updateResultsInfo()
                    }
                }

                MEDIA_TYPE_AUDIO -> {
                    uiScope.launch {
                        radioBrowserService.movePrevious { hit ->
                            currentRadio = hit
                            currentRadio?.let {
                                loadRadioPlayer(
                                    it.url,
                                    it.favicon.toString(),
                                    it.url.toString()
                                )
                            }
                        }
                        updateResultsInfo()
                    }
                }
            }
            vibrate()
            updateResultsInfo()
        } // [Source 29]

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val searchInput: EditText = findViewById(R.id.searchInput)
                Log.d(TAG, "Search initiated with query: ${searchInput.text}")
                // Qui andrebbe implementata la logica di ricerca
                query = searchInput.text.toString()
                when (getCurrentMediaType()) {
                    MEDIA_TYPE_IMAGE -> {
                        imageViewModel.search(query)
                    }

                    MEDIA_TYPE_VIDEO -> {
                        pixbayVideoService.searchMedia(query) { apiResponse ->
                            handleMediaResponse(apiResponse)
                            updateResultsInfo()
                        }
                    }

                    MEDIA_TYPE_AUDIO -> {
                        radioBrowserService.searchMedia(query) { apiResponse ->
                            handleRadioResponse(apiResponse)
                            updateResultsInfo()
                        }
                    }
                }
                return@setOnEditorActionListener true
            }
            false
        } // [Source 41]
        imageViewModel.currentImage.observe(this) { record ->
            {
                uiScope.launch {
                    loadImageWithGlide(record?.largeImageURL.toString())
                    updateResultsInfo()
                }
            }

        }


    }

    // Funzione per impostare lo stato iniziale (chiamata solo se non si ripristina)
    private fun setupInitialContent() {
        Log.d(TAG, "Setting up initial content...")
        query = "" // Imposta query iniziale
        searchInput.setText(query)
        selectMediaType(MEDIA_TYPE_IMAGE) // Seleziona immagine di default [Source 20]
        displayPlaceHolder() // Mostra GIF di caricamento [Source 20]
//        // Carica immagini iniziali
//        if (imageViewModel.getCurrentItem() == null)
//            imageViewModel.loadInitial()
//        else {
//            loadImageWithGlide(imageViewModel.getCurrentItem()?.webformatURL.toString())
//            updateResultsInfo()
//        }

    }

    @OptIn(UnstableApi::class)
    fun loadVideoPlayer(videoUrl: String?) {
        uiScope.launch {
            /*****************************************************************************************************/
            // Pulizia se necessario
            val mediaLayer = findViewById<FrameLayout>(R.id.mediaLayerCard)
            mediaLayer.removeAllViews()
            player?.release()
            this@MainActivity.playerView = null
            // Crea il player dinamico
            this@MainActivity.player = ExoPlayer.Builder(this@MainActivity).build()
            val inflater = LayoutInflater.from(this@MainActivity)
            val wrapperView =
                inflater.inflate(R.layout.player_video_wrapper, mediaLayer, true)
            val playerView = wrapperView.findViewById<PlayerView>(R.id.player_video_view)
            playerView.controllerShowTimeoutMs = 1000 // 0 = mai nascondere automaticamente
            playerView.useController = true
            playerView.player = this@MainActivity.player
            playerView.setBackgroundColor(Color.BLACK) // Sfondo nero
            playerView.setShutterBackgroundColor(Color.BLACK) // Sfondo nero durante il buffering/inizio
            playerView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            playerView.setKeepContentOnPlayerReset(true) // opzionale
            playerView.setControllerBackgroundFromUrl(this@MainActivity, "", wrapperView)
            this@MainActivity.playerView = playerView
            val fullButton = playerView.findViewById<MaterialButton>(R.id.video_fullscreen)
            fullButton?.setOnClickListener {
                toggleFullscreen()
            }
            val playButton = playerView.findViewById<MaterialButton>(R.id.video_play)
            playButton?.setOnClickListener {
                player?.play()
            }
            val pauseButton = playerView.findViewById<MaterialButton>(R.id.video_pause)
            pauseButton?.setOnClickListener {
                player?.pause()
            }
            val rewindButton = playerView.findViewById<MaterialButton>(R.id.video_rewind)
            rewindButton?.setOnClickListener {
                player?.seekTo(0)
            }
            // Imposta media
            val mediaItem = MediaItem.fromUri(videoUrl.toString())
            player?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val sharedPref =
                            getSharedPreferences("AppPreferences", MODE_PRIVATE) // [Source 7]
                        val videoPref = sharedPref.getString("selected_video", "false")
                        if (videoPref == "true") {

                            if (isPlaying && !isFullScreen) {
                                setFullscreen(true)
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val sharedPref =
                            getSharedPreferences("AppPreferences", MODE_PRIVATE) // [Source 7]
                        val videoPref = sharedPref.getString("selected_video", "false")
                        if (videoPref == "true") {
                            if (playbackState == Player.STATE_ENDED && isFullScreen) {
                                setFullscreen(false)
                            }
                        }
                    }
                })
            }
        }
        /********************************************************************************************************/
    }

    @OptIn(UnstableApi::class)
    fun loadRadioPlayer(name: String, icon: String, radioUrl: String?) {
        uiScope.launch {
            // Pulizia se necessario
//            val stopIntent = Intent(this@MainActivity, RadioPlayerService::class.java).apply {
//                action = RadioPlayerService.ACTION_STOP
//            }
//            startService(stopIntent)


            if (RadioPlayerService.isRunning) {
                val stopIntent = Intent(this@MainActivity, RadioPlayerService::class.java).apply {
                    action = RadioPlayerService.ACTION_STOP
                }
                startService(stopIntent)
                Log.d(TAG, "Service is started stopping")
            }

            val mediaLayer = findViewById<FrameLayout>(R.id.mediaLayerCard)
            mediaLayer.removeAllViews()
            player?.release()
            this@MainActivity.playerView = null
            // Crea il player dinamico
            this@MainActivity.player = ExoPlayer.Builder(this@MainActivity).build()
            val inflater = LayoutInflater.from(this@MainActivity)
            val wrapperView =
                inflater.inflate(R.layout.player_radio_wrapper, mediaLayer, true)
            val playerView = wrapperView.findViewById<PlayerView>(R.id.player_radio_view)
            playerView.controllerShowTimeoutMs = 0 // 0 = mai nascondere automaticamente
            playerView.useController = true
            playerView.player = this@MainActivity.player
            playerView.controllerShowTimeoutMs = 0 // non nasconde mai
            playerView.showController()
            playerView.setControllerBackgroundFromUrl(
                this@MainActivity,
                icon.toUri().toString(),
                wrapperView
            )

            playerView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(
                    mapOf("Icy-MetaData" to "1") // Richiesta esplicita metadati ICY
                )

            val player = ExoPlayer.Builder(this@MainActivity)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()


            player.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title
                    val artist = mediaMetadata.artist
                    Log.d("META", "🎶 MediaMetadata: $artist - $title")
                }
            })

            player.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    Log.d("META", "📝 MediaMetadata: ${mediaMetadata.title}")
                }
            })

            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onMediaItemTransition(
                    eventTime: AnalyticsListener.EventTime,
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    super.onMediaItemTransition(eventTime, mediaItem, reason)
                }

                override fun onPlaylistMetadataChanged(
                    eventTime: AnalyticsListener.EventTime,
                    playlistMetadata: MediaMetadata
                ) {
                    super.onPlaylistMetadataChanged(eventTime, playlistMetadata)
                }

                override fun onMetadata(
                    eventTime: AnalyticsListener.EventTime,
                    metadata: Metadata
                ) {
                    for (i in 0 until metadata.length()) {
                        val entry = metadata[i]
                        when (entry) {
                            is IcyInfo -> Log.d("META", "📻 ICY: ${entry.title}")
                            is TextInformationFrame -> Log.d("META", "🎙️ ID3: ${entry.value}")
                        }
                    }
                }
            })

            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onMetadata(
                    eventTime: AnalyticsListener.EventTime,
                    metadata: Metadata
                ) {
                    for (i in 0 until metadata.length()) {
                        val entry = metadata[i]
                        when (entry) {
                            is IcyInfo -> Log.d("META", "ICY: ${entry.title}")
                            is TextInformationFrame -> Log.d(
                                "META",
                                "ID3: ${entry.description} = ${entry.value}"
                            )

                            else -> Log.d("META", "Altro: $entry")
                        }
                    }
                }
            })

            player.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title
                    val artist = mediaMetadata.artist
                    Log.d("META", "🎶 MediaMetadata: $artist - $title")
                }
            })


            playerView.setKeepContentOnPlayerReset(true) // opzionale
            val pauseButton = playerView.findViewById<MaterialButton>(R.id.radio_pause)
            pauseButton?.setOnClickListener {
                if (RadioPlayerService.isRunning) {
                    val stopIntent =
                        Intent(this@MainActivity, RadioPlayerService::class.java).apply {
                            action = RadioPlayerService.ACTION_STOP
                        }
                    startService(stopIntent)
                    Log.d(TAG, "Service is started stopping")
                }
            }
            val playButton = playerView.findViewById<MaterialButton>(R.id.radio_play)
            playButton?.setOnClickListener {
                val intent = Intent(this@MainActivity, RadioPlayerService::class.java).apply {
                    action = RadioPlayerService.ACTION_START
                    putExtra("name", name)
                    putExtra("icon", icon)
                    putExtra("url", radioUrl.toString())
                }
                startService(intent)
            }

            // Prepara ExoPlayer solo se non già esistente
            if (PlayerHolder.exoPlayer == null) {
                PlayerHolder.exoPlayer = player
            }

            // Passa i parametri via Intent
            val intent = Intent(this@MainActivity, RadioPlayerService::class.java).apply {
                action = "ACTION_START_RADIO"
                putExtra("name", name)
                putExtra("icon", icon)
                putExtra("url", radioUrl.toString())
            }

            startService(intent)
            Log.d(TAG, "Start service")
        }

    }

    private fun toggleFullscreen() {
        setFullscreen(!isFullScreen)
    }

    fun PlayerView.setControllerBackgroundFromUrl(
        context: Context,
        url: String,
        wrapperView1: View
    ) {
        when (getCurrentMediaType()) {
            MEDIA_TYPE_VIDEO -> {
                val controllerLayout = this.findViewById<LinearLayout>(R.id.controller_video_layout)
                if (!url.isBlank()) {
                    Glide.with(context)
                        .load(url)
                        .into(object : CustomTarget<Drawable>() {
                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?
                            ) {
                                controllerLayout.background = resource
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                } else {
                    controllerLayout.setBackgroundColor(Color.TRANSPARENT)
                }
            }

            MEDIA_TYPE_AUDIO -> {
                val radioImageView = wrapperView1.findViewById<ImageView>(R.id.radio_imageView)
                if (!url.isBlank()) {
                    Glide.with(this@MainActivity)
                        .load(url)
                        .into(radioImageView)
                } else {
                    Glide.with(this@MainActivity)
                        .load(R.drawable.pngtreevector_radio_icon_4091198)
                        .into(radioImageView)
                }
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun setFullscreen(fullscreen: Boolean) {
        isFullScreen = fullscreen
        val playerViewLocal = playerView ?: return

        if (fullscreen) {
            // Entra in fullscreen
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
            supportActionBar?.hide()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // Sposta il player fuori dal container nel decorView
            (playerViewLocal.parent as? ViewGroup)?.removeView(playerViewLocal)
            (window.decorView as ViewGroup).addView(
                playerViewLocal,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            // Esce dal fullscreen
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            supportActionBar?.show()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            // Sposta di nuovo il player nel suo container originale
            (playerViewLocal.parent as? ViewGroup)?.removeView(playerViewLocal)
            playerContainer.addView(
                playerViewLocal,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // Altre funzioni helper come updateButtonBackgrounds, applyFullScreenVisuals rimangono simili a prima
    private fun vibrate() {
        val vibratorService = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (vibratorService.hasVibrator()) {
            val vibrationEffect =
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            vibratorService.vibrate(vibrationEffect)
        }
    }

    private fun handleMediaResponse(listResp: List<Hit>?) {
        if (listResp != null && listResp.isNotEmpty()) {
            currentMedia = listResp.firstOrNull()
            currentMedia?.let {
                Log.d("MainActivity", "Media caricato: ${it.id}")
                when (currentMediaType) {
                    MEDIA_TYPE_IMAGE -> loadImageWithGlide(it.largeImageURL.toString())
                    MEDIA_TYPE_VIDEO -> loadVideoPlayer(it.videos?.large?.url.toString())
                }
            }
        } else {
            Toast.makeText(this@MainActivity, "Nessun risultato trovato.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun handleRadioResponse(listResp: List<HitRadio>?) {
        if (listResp != null && listResp.isNotEmpty()) {
            currentRadio = listResp.firstOrNull()
            currentRadio?.let {
                Log.d("MainActivity", "Media caricato: ${it.name}")
                when (currentMediaType) {
                    MEDIA_TYPE_AUDIO -> loadRadioPlayer(
                        it.name.toString(),
                        it.favicon.toString(),
                        it.url.toString()
                    )
                }
            }
        } else {
            Toast.makeText(this@MainActivity, "Nessun risultato trovato.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun loadImageWithGlide(imageUrl: String?) {
        Glide.with(this@MainActivity)
            .load(imageUrl)
            .into(imageViewer)
    }

    private fun setWallpaperWithScaleType() {


        val drawable = imageViewer.drawable as BitmapDrawable
        val originalBitmap = drawable.bitmap ?: return

        val wallpaperManager = WallpaperManager.getInstance(applicationContext)
        val screenWidth = wallpaperManager.desiredMinimumWidth
        try {
            val screenHeight = wallpaperManager.desiredMinimumHeight
            val transformedBitmap = transformBitmapToScaleType(
                originalBitmap,
                screenWidth,
                screenHeight,
                imageViewer.scaleType
            )
            wallpaperManager.setBitmap(transformedBitmap)
            Toast.makeText(this@MainActivity, "Wallpaper impostato.", Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun transformBitmapToScaleType(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        scaleType: ImageView.ScaleType
    ): Bitmap {
        val matrix = Matrix()
        val scaleX = width.toFloat() / bitmap.width
        val scaleY = height.toFloat() / bitmap.height

        when (scaleType) {
            ImageView.ScaleType.FIT_CENTER -> {
                val scale = minOf(scaleX, scaleY)
                matrix.postScale(scale, scale)
                val newX = (width - bitmap.width * scale) / 2
                val newY = (height - bitmap.height * scale) / 2
                matrix.postTranslate(newX, newY)
            }

            ImageView.ScaleType.CENTER_CROP -> {
                val scale = maxOf(scaleX, scaleY)
                matrix.postScale(scale, scale)
                val newX = (width - bitmap.width * scale) / 2
                val newY = (height - bitmap.height * scale) / 2
                matrix.postTranslate(newX, newY)
            }

            ImageView.ScaleType.FIT_XY -> matrix.postScale(scaleX, scaleY)
            ImageView.ScaleType.CENTER_INSIDE -> {
                val scale = minOf(scaleX, scaleY, 1f)
                matrix.postScale(scale, scale)
                val newX = (width - bitmap.width * scale) / 2
                val newY = (height - bitmap.height * scale) / 2
                matrix.postTranslate(newX, newY)
            }

            ImageView.ScaleType.CENTER -> {
                val newX = (width - bitmap.width) / 2f
                val newY = (height - bitmap.height) / 2f
                matrix.postTranslate(newX, newY)
            }

            ImageView.ScaleType.MATRIX -> {
                matrix.postScale(0.75f, 0.75f)
                matrix.postRotate(45f, bitmap.width / 2f, bitmap.height / 2f)
                matrix.postTranslate((width - bitmap.width) / 2f, (height - bitmap.height) / 2f)
            }

            ImageView.ScaleType.FIT_START -> {
                val scale = minOf(scaleX, scaleY)
                matrix.postScale(scale, scale)
            }

            ImageView.ScaleType.FIT_END -> {
                val scale = minOf(scaleX, scaleY)
                matrix.postScale(scale, scale)
                val translateY = height - (bitmap.height * scale)
                matrix.postTranslate(0f, translateY)
            }

            //else -> return bitmap
        }

        val outputBitmap = createBitmap(width, height)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, matrix, null)
        return outputBitmap
    }


    /**
     * Seleziona il tipo di media e aggiorna l'interfaccia utente
     */
    private fun selectMediaType(mediaType: Int) {

        // Resetta lo sfondo di tutti i pulsanti
        btnImages.setBackgroundResource(android.R.color.transparent)
        btnVideos.setBackgroundResource(android.R.color.transparent)
        btnAudios.setBackgroundResource(android.R.color.transparent)

        // Imposta lo sfondo del pulsante selezionato
        when (mediaType) {
            MEDIA_TYPE_IMAGE -> {
                btnImages.setBackgroundResource(R.color.selected_button)
                Log.d(TAG, "Tipo media selezionato: Immagini")
            }

            MEDIA_TYPE_VIDEO -> {
                btnVideos.setBackgroundResource(R.color.selected_button)
                Log.d(TAG, "Tipo media selezionato: Video")
            }

            MEDIA_TYPE_AUDIO -> {
                btnAudios.setBackgroundResource(R.color.selected_button)
                Log.d(TAG, "Tipo media selezionato: Audio")
            }
        }
        // Crea e mostra il visualizzatore appropriato
        setupMediaLayer(mediaType)
        invalidateOptionsMenu()

        // Aggiorna la variabile globale
        currentMediaType = mediaType

    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playerView = null
    }

    /**
     * Configura il visualizzatore appropriato per il tipo di media selezionato
     */
    @OptIn(UnstableApi::class)
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMediaLayer(mediaType: Int) {
        playerContainer = findViewById(R.id.mediaLayerCard)
        if (currentMediaType != mediaType)
            playerContainer.removeAllViews()

        when (mediaType) {
            MEDIA_TYPE_IMAGE -> {
                // Crea e configura ImageView per la visualizzazione delle immagini
                imageViewer = PhotoView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                imageViewer.scaleType = scaleTypes[currentScaleTypeIndex]
                currentScaleTypeIndex = scaleTypes.indexOf(imageViewer.scaleType)
                imageViewer.isClickable = true // Assicurati che la ImageView sia cliccabile
                imageViewer.isZoomable =
                    true    // Assicurati che lo zoom sia abilitato (default: true)
                imageViewer.setOnLongClickListener {
                    Log.d("PhotoView", "Long press detected!")
                    setWallpaperWithScaleType()
                    vibrate()
                    true // restituisci true per indicare che l'evento è stato gestito
                }

                playerContainer.addView(imageViewer)
            }

            MEDIA_TYPE_VIDEO -> {
                player?.release()
                player = null
                playerView = null
                player = ExoPlayer.Builder(this).build()
                val inflater = LayoutInflater.from(this)
                val wrapperView =
                    inflater.inflate(
                        R.layout.player_video_wrapper,
                        playerContainer,
                        true
                    )
                val playerView = wrapperView.findViewById<PlayerView>(R.id.player_video_view)
                playerView.showController()
                playerView.controllerShowTimeoutMs = 0 // 0 = mai nascondere automaticamente
                playerView.useController = true
                playerView.player = this@MainActivity.player

                playerView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                playerView.setControllerVisibilityListener(object :
                    PlayerView.ControllerVisibilityListener {
                    override fun onVisibilityChanged(visibility: Int) {
                        if (visibility != View.VISIBLE) {
                            playerView.showController()
                        }
                    }
                })
            }

            MEDIA_TYPE_AUDIO -> {
                player?.release()
                player = null
                playerView = null
                player = ExoPlayer.Builder(this).build()
                val inflater = LayoutInflater.from(this)
                val wrapperView =
                    inflater.inflate(
                        R.layout.player_radio_wrapper,
                        playerContainer,
                        true
                    )
                val playerView = wrapperView.findViewById<PlayerView>(R.id.player_radio_view)
                playerView.showController()
                playerView.controllerShowTimeoutMs = 0 // 0 = mai nascondere automaticamente
                playerView.useController = true
                playerView.player = this@MainActivity.player

                playerView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                playerView.setControllerVisibilityListener(object :
                    PlayerView.ControllerVisibilityListener {
                    override fun onVisibilityChanged(visibility: Int) {
                        if (visibility != View.VISIBLE) {
                            playerView.showController()
                        }
                    }
                })
//                playerContainer.removeAllViews() // Rimuovi eventuali viste precedenti
            }
        }
    }

    private fun displayPlaceHolder() {
        // Ottieni il valore intero del colore dalla risorsa R.color.gray
        // 'this' si riferisce al Context dell'Activity
        val coloreGrigio: Int = ContextCompat.getColor(this, R.color.gray)

        // Imposta il colore di sfondo dell'ImageView
        imageViewer.setBackgroundColor(coloreGrigio)
        imageViewer.scaleType = ImageView.ScaleType.FIT_CENTER // O un'altra opzione a tua scelta
        Glide.with(this@MainActivity)
            .load(R.drawable.vecteezy_mona_lisa_portrait_without_face_in_vector_graphics_4686245) // Carica la risorsa GIF
            .error(R.drawable.error) // Puoi mantenere un'immagine di errore
            .into(imageViewer)
    }

    /**
     * Restituisce il tipo di media attualmente selezionato
     */
    fun getCurrentMediaType(): Int {
        return currentMediaType
    }


    /**
     * Converte il valore ordinale di un ImageView.ScaleType nel suo nome
     * formattato (minuscolo, con spazi al posto degli underscore).
     *
     * @param ordinalValue L'intero che rappresenta la posizione ordinale dell'enum ScaleType.
     * @return La stringa formattata del nome (es. "fit center"), oppure null se
     * l'ordinale fornito non è valido per ImageView.ScaleType.
     */
    fun getFormattedScaleTypeNameFromOrdinal(ordinalValue: Int): String {
        // Ottieni tutti i possibili valori di ScaleType
        val scaleTypes = ImageView.ScaleType.entries.toTypedArray()

        // Controlla se l'ordinale è valido (compreso tra 0 e la dimensione dell'array - 1)
        if (ordinalValue >= 0 && ordinalValue < scaleTypes.size) {
            // Ottieni la costante enum corrispondente all'ordinale
            val scaleTypeConstant = scaleTypes[ordinalValue]

            // Ottieni il nome della costante (es. "FIT_XY", "CENTER_CROP")
            val enumName = scaleTypeConstant.name

            // Converti in minuscolo e sostituisci '_' con ' '
            val formattedName = enumName.lowercase().replace('_', ' ')

            return formattedName
        } else {
            // L'ordinale fornito non è valido per questo enum
            println("Errore: Valore ordinale $ordinalValue non valido per ImageView.ScaleType")
            return "fit center" // Ritorna null per indicare un errore o un valore non trovato
        }
    }

    /**
     * Mostra il menu pop-up utilizzando il file XML definito in res/menu/my_menu.xml.
     */
    private fun showPopupMenu(anchor: View) {
        val wrapper = ContextThemeWrapper(this, R.style.CustomPopupMenu)
        val popup = PopupMenu(wrapper, anchor)
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val themePref = sharedPref.getString("selected_theme", "light")
        val videoPref = sharedPref.getString("selected_video", "false")
        val currentScaleTypeIndexPref = sharedPref.getString("currentScaleTypeIndex", "3")

        popup.menuInflater.inflate(R.menu.my_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            onOptionsItemSelected(item)
        }

        val label = getFormattedScaleTypeNameFromOrdinal(currentScaleTypeIndexPref?.toInt() ?: 3)
        popup.menu.findItem(R.id.menu_item_scaleType).subMenu?.forEach { menuItem ->
            if (label == menuItem.title) {
                menuItem.isChecked = true
            }
        }

        if (themePref == "dark") {
            popup.menu.findItem(R.id.theme_dark).isChecked = true
        } else {
            popup.menu.findItem(R.id.theme_light).isChecked = true
        }

        if (videoPref == "true") {
            popup.menu.findItem(R.id.fullScreen_true).isChecked = true
        } else {
            popup.menu.findItem(R.id.fullScreen_false).isChecked = true
        }
        popup.show()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "Options Menu item selected: ${item.title}")
        when (item.itemId) {

            // Selezione della voce Settings (eventuale log o azione speciale)
            R.id.menu_item_settings -> {
                return true
            }

            R.id.menu_item_pixabay_key -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }

            // Gestione delle opzioni per Scale Type (solo per immagini)
            R.id.scaleType_centerCrop -> {
                imageViewer.scaleType = ImageView.ScaleType.CENTER_CROP
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(currentScaleTypeIndex.toString())
                Log.d(TAG, "ScaleType selezionato: CENTER_CROP")
                return true
            }

            R.id.scaleType_fitCenter -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_CENTER
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(currentScaleTypeIndex.toString())
                Log.d(TAG, "ScaleType selezionato: FIT_CENTER")
                return true
            }

            R.id.scaleType_centerInside -> {
                imageViewer.scaleType = ImageView.ScaleType.CENTER_INSIDE
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(currentScaleTypeIndex.toString())
                Log.d(TAG, "ScaleType selezionato: CENTER_INSIDE")
                return true
            }

            R.id.scaleType_fitXY -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_XY
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(currentScaleTypeIndex.toString())
                Log.d(TAG, "ScaleType selezionato: FIT_XY")
                return true
            }

            R.id.scaleType_center -> {
                imageViewer.scaleType = ImageView.ScaleType.CENTER
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(currentScaleTypeIndex.toString())
                Log.d(TAG, "ScaleType selezionato: CENTER")
                return true
            }

            R.id.scaleType_fitStart -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_START
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(currentScaleTypeIndex.toString())
                Log.d(TAG, "ScaleType selezionato: FIT_START")
                return true
            }

            R.id.scaleType_fitEnd -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_END
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(currentScaleTypeIndex.toString())
                Log.d(TAG, "ScaleType selezionato: FIT_END")
                return true
            }

            R.id.fullScreen_true -> {
                saveVideoPreference("true")
                Log.d(TAG, "Tema selezionato: Light")
                recreate() // Ricrea l'Activity per applicare il nuovo tema
                return true
            }

            R.id.fullScreen_false -> {
                saveVideoPreference("false")
                Log.d(TAG, "Tema selezionato: Light")
                recreate() // Ricrea l'Activity per applicare il nuovo tema
                return true
            }

            // Gestione delle opzioni per il cambio del tema
            R.id.theme_light -> {
                saveThemePreference("light")
                Log.d(TAG, "Tema selezionato: Light")
                recreate() // Ricrea l'Activity per applicare il nuovo tema
                return true
            }

            R.id.theme_dark -> {
                saveThemePreference("dark")
                Log.d(TAG, "Tema selezionato: Dark")
                recreate() // Ricrea l'Activity per applicare il nuovo tema
                return true
            }

            // Altre voci del menu
            R.id.menu_item_about -> {
                Log.d(TAG, "About selected")
                return true
            }

            R.id.menu_item_help -> {
                Log.d(TAG, "Help selected")
                return true
            }

            R.id.menu_item_exit -> {
                Log.d(TAG, "Exit selected")
                finish()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun saveThemePreference(theme: String) {
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("selected_theme", theme)
            apply()
        }
    }

    private fun saveVideoPreference(theme: String) {
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("selected_video", theme)
            apply()
        }
    }

    private fun saveScalePreference(theme: String) {
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("currentScaleTypeIndex", theme)
            apply()
        }
    }

    private fun updateResultsInfo() {
        // Aggiorna il TextView con il numero totale di immagini e l'indice corrente (aggiungi 1 perché l'indice parte da 0)


        when (getCurrentMediaType()) {
            MEDIA_TYPE_IMAGE -> {
//                val totalResults = imageViewModel.getTotal()
//                val recordIndicator = findViewById<TextView>(R.id.recordIndicator)
//                if (recordIndicator != null) {
//                    recordIndicator.text = "$totalResults"
//                    Log.d(TAG, recordIndicator.text.toString())
//                }

//                val recordIndicator2 = findViewById<TextView>(R.id.recordIndicator2)
//                if (recordIndicator2 != null) {
//                    recordIndicator2.text = imageViewModel.getCurrentItem()?.id.toString()
//                    Log.d(TAG, recordIndicator2.text.toString())
//                }

//                val currentDisplayed =
//                    if (totalResults > 0) imageViewModel.getCurrentIndex() else 1
//                val recordIndicator3 = findViewById<TextView>(R.id.recordIndicator3)
//                if (recordIndicator3 != null) {
//                    recordIndicator3.text = "$currentDisplayed"
//                    Log.d(TAG, recordIndicator3.text.toString())
//                }

//                val topInfoText = findViewById<TextView>(R.id.topInfoText)
//                if (topInfoText != null) {
//                    topInfoText.text = imageViewModel.getCurrentItem()?.run {
//                        "By: $user - $tags - $views likes $likes ❤\uFE0F "
//                    } ?: ""
//                    Log.d(TAG, topInfoText.text.toString())
//                }

            }

            MEDIA_TYPE_VIDEO -> {
//                val totalResults = pixbayVideoService.getTotal()
//                val recordIndicator = findViewById<TextView>(R.id.recordIndicator)
//                if (recordIndicator != null) {
//                    recordIndicator.text = "$totalResults"
//                    Log.d(TAG, recordIndicator.text.toString())
//                }

//                val recordIndicator2 = findViewById<TextView>(R.id.recordIndicator2)
//                if (recordIndicator2 != null) {
//                    recordIndicator2.text = pixbayVideoService.getCurrentItem()?.id.toString()
//                    Log.d(TAG, recordIndicator2.text.toString())
//                }

//                val currentDisplayed =
//                    if (totalResults > 0) pixbayVideoService.getCurrentIndex() + 1 else 0
//                val recordIndicator3 = findViewById<TextView>(R.id.recordIndicator3)
//                if (recordIndicator3 != null) {
//                    recordIndicator3.text = "$currentDisplayed"
//                    Log.d(TAG, recordIndicator3.text.toString())
//                }

//                val topInfoText = findViewById<TextView>(R.id.topInfoText)
//                if (topInfoText != null) {
//                    topInfoText.text = pixbayVideoService.getCurrentItem()?.run {
//                        "By: $user - $tags - $views likes $likes ❤\uFE0F "
//                    } ?: ""
//                    Log.d(TAG, topInfoText.text.toString())
//                }
            }

            MEDIA_TYPE_AUDIO -> {
                val totalResults = radioBrowserService.getTotal()
//                val recordIndicator = findViewById<TextView>(R.id.recordIndicator)
//                if (recordIndicator != null) {
//                    recordIndicator.text = "$totalResults"
//                    Log.d(TAG, recordIndicator.text.toString())
//                }

//                val recordIndicator2 = findViewById<TextView>(R.id.recordIndicator2)
//                if (recordIndicator2 != null) {
//                    recordIndicator2.text = imageViewModel.getCurrentItem()?.id.toString()
//                    Log.d(TAG, recordIndicator2.text.toString())
//                }

//                val recordIndicator4 = findViewById<TextView>(R.id.topInfoText)
//                if (recordIndicator4 != null) {
//                    recordIndicator4.text = radioBrowserService.getCurrentItem()?.name.toString()
//                    Log.d(TAG, recordIndicator2.text.toString())
//                }

//                val currentDisplayed =
//                    if (totalResults > 0) radioBrowserService.getCurrentIndex() + 1 else 0
//                val recordIndicator3 = findViewById<TextView>(R.id.recordIndicator3)
//                if (recordIndicator3 != null) {
//                    recordIndicator3.text = "$currentDisplayed"
//                    Log.d(TAG, recordIndicator3.text.toString())
//                }
            }

        }
    }


}