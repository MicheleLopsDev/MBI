package io.github.luposolitario.mbi

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import io.github.luposolitario.mbi.model.Country
import io.github.luposolitario.mbi.model.Hit
import io.github.luposolitario.mbi.model.HitRadio
import io.github.luposolitario.mbi.model.RadioStation
import io.github.luposolitario.mbi.model.RadioStationDao
import io.github.luposolitario.mbi.model.toHitRadio
import io.github.luposolitario.mbi.service.PixbayImageService
import io.github.luposolitario.mbi.service.PixbayVideoService
import io.github.luposolitario.mbi.service.RadioBrowserService
import io.github.luposolitario.mbi.util.SettingsActivity
import io.github.luposolitario.mbi.viewmodel.ImageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        const val MIN_RADIO_STATIONS = 10
        const val PREF_SELECTED_COUNTRY = "selected_country_code"
        const val PREF_FAV_NAME = "fav_name"
        const val PREF_FAV_ICON = "fav_icon"
        const val PREF_FAV_URL = "fav_url"
        const val PREF_FAV_UUID= "fav_uuid"
    }


    // Variabile per tenere traccia del tipo di media attualmente selezionato
    private var currentMediaType = MEDIA_TYPE_IMAGE

    // Riferimenti alle View per un accesso più semplice
    private lateinit var btnImages: MaterialButton
    private lateinit var btnVideos: MaterialButton
    private lateinit var btnAudios: MaterialButton
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var searchInputLayout: TextInputLayout
    private lateinit var btnPrevious: MaterialButton
    private lateinit var btnFirst: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnFw: MaterialButton
    private lateinit var btnRev: MaterialButton
    private lateinit var btnFavoriteRadio: MaterialButton

    private lateinit var topInfoText: TextView
    private lateinit var textViewsCount: TextView
    private lateinit var textLikesCount: TextView

    private lateinit var countrySelector: AutoCompleteTextView
    private lateinit var countrySelectorLayout: TextInputLayout

    private lateinit var defaultTint: ColorStateList
    private lateinit var goldColor: ColorStateList


    // Riferimenti ai visualizzatori di media
    private lateinit var playerContainer: MaterialCardView
    private lateinit var imageViewer: PhotoView
    private var playerView: PlayerView? = null
    private var player: ExoPlayer? = null
    private lateinit var searchInput: EditText

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var countryChanged = false            //  true quando l’utente cambia Paese
    private lateinit var sharedPref: SharedPreferences //  reference alle SharedPreferences
    private lateinit var countries: List<Country>     // lista completa da tenere in memoria

    private var isFullScreen = false
    private var query = ""
    private var currentScaleTypeIndex = 0
    private var offset = 10
    @Inject
    lateinit var pixbayImageService: PixbayImageService
    private lateinit var pixbayVideoService: PixbayVideoService

    @Inject
    lateinit var radioBrowserService: RadioBrowserService

    @Inject
    lateinit var radioStationDao: RadioStationDao //  Inject the DAO via Hilt
    private var currentMedia: Hit? = null
    private var currentRadio: HitRadio? = null

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
        sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE) // [Source 7]
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

// --- Inizializzazione DB ---
        // Lancia la coroutine sul dispatcher IO per il controllo iniziale del DB
        CoroutineScope(Dispatchers.IO).launch { // <-- CORREZIONE: Usa Dispatchers.IO
            try {
                // Ora questa chiamata viene eseguita su un thread in background
                val needsFetching = radioStationDao.getRadioStationCount() < MIN_RADIO_STATIONS

                // Le funzioni chiamate qui (fetchAndSaveRadioStations e loadRadioStationsFromDb)
                // sono 'suspend' e gestiscono già il cambio di contesto se necessario
                // per aggiornare la UI sul Main thread.
                if (needsFetching) {
                    Log.d(TAG, "Numero stazioni radio basso, recupero dalla rete...")
                    fetchAndSaveRadioStations()
                } else {
                    Log.d(TAG, "Caricamento stazioni radio dal DB...")
                    loadRadioStationsFromDb()
                }
            } catch (e: Exception) {
                // È buona norma gestire potenziali eccezioni qui
                Log.e(TAG, "Errore durante l'inizializzazione del DB: ${e.message}", e)
                // Potresti voler mostrare un messaggio all'utente qui,
                // ma ricorda di farlo tornando sul Main thread:
                // withContext(Dispatchers.Main) {
                //    Toast.makeText(this@MainActivity, "Errore caricamento dati", Toast.LENGTH_LONG).show()
                // }
            }
        }

        // --- 1. Inizializza SEMPRE le view principali ---
        initializeCoreViews() // Prende riferimenti a mediaViewer, bottoni, searchInput

        // --- IMPOSTA LA TOOLBAR COME ACTION BAR DELL'ACTIVITY ---
        setSupportActionBar(topAppBar) // <--- AGGIUNGI QUESTA RIGA

        setupCountrySpinner()

        // --- 2. Configura i listener che devono essere sempre attivi ---
        setupListeners(savedInstanceState) // Imposta onClickListeners per bottoni, menu, search


        // --- 3. Imposta lo stato iniziale ---
        Log.d(TAG, "onCreate: Setting up initial state.")
        setupInitialContent() // Imposta mediaType=IMAGE, carica gif, ecc. chiama setupMediaViewer per immagine

    }


    private fun fetchAndSaveRadioStations() {
        //CoroutineScope(Dispatchers.IO).launch {
        try {
            radioBrowserService.searchMedia("", "") { apiResponse ->
                CoroutineScope(Dispatchers.IO).launch { // Launch another coroutine to handle DB operation
                    apiResponse?.forEach { hit ->
                        if (hit is HitRadio) {
                            val radioStation = RadioStation(hit)
                            radioStationDao.insertOrIgnore(radioStation)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        loadRadioStationsFromDb()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching/saving radio stations: ${e.message}")
            //  Handle error (e.g., show a message, load fallback data)
        }
        //}
    }

    private suspend fun loadRadioStationsFromDb() {
        val stations = radioStationDao.getAllRadioStations()
        withContext(Dispatchers.Main) {
            //  Update UI with stations from DB (e.g., populate a RecyclerView)
            Log.d(TAG, "Loaded ${stations.size} stations from DB")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Gonfia il menu; questo aggiunge voci alla action bar se è presente.
        menuInflater.inflate(R.menu.my_menu, menu)
        return true // true per mostrare il menu, false per non mostrarlo
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        // Viene chiamato appena prima che il menu venga mostrato.
        // Puoi modificare gli elementi del menu qui.

        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val themePref = sharedPref.getString("selected_theme", "light")
        val videoPref = sharedPref.getString("selected_video", "false")
        val currentScaleTypeIndexPref = sharedPref.getString("currentScaleTypeIndex", "3")

        // Esempio: Mostra/nascondi le opzioni di Scale Type solo per le immagini
        val scaleTypeMenuItem =
            menu?.findItem(R.id.menu_item_scaleType) // Assicurati che l'ID corrisponda
        val themeMenuItem = menu?.findItem(R.id.menu_item_theme) // Assicurati che l'ID corrisponda
        val fullscreenOptionsMenuItem =
            menu?.findItem(R.id.menu_item_video) // Assicurati di avere un ID per le opzioni fullscreen

        if (getCurrentMediaType() == MEDIA_TYPE_IMAGE) {
            scaleTypeMenuItem?.isVisible = true
            // Potresti anche voler mostrare/nascondere i sottomenu se necessario
            scaleTypeMenuItem?.subMenu?.setGroupVisible(
                R.id.menu_item_scaleType,
                true
            ) // Se hai un gruppo per scale types

//            val label =
//                getFormattedScaleTypeNameFromOrdinal(currentScaleTypeIndexPref?.toInt() ?: 3)

            scaleTypeMenuItem?.subMenu?.forEach({ menuItem ->
                if (currentScaleTypeIndexPref?.toInt() == menuItem.itemId) {
                    menuItem.isChecked = true
                }
            })
        } else {
            scaleTypeMenuItem?.isVisible = false
            scaleTypeMenuItem?.subMenu?.setGroupVisible(
                R.id.menu_item_scaleType,
                false
            ) // Nascondi il gruppo scale types
        }

        // Esempio: Mostra/nascondi le opzioni di fullscreen per video
        if (getCurrentMediaType() == MEDIA_TYPE_VIDEO) {
            fullscreenOptionsMenuItem?.isVisible = true
            fullscreenOptionsMenuItem?.subMenu?.forEach({ menuItem ->
                if (videoPref == menuItem.title) {
                    menuItem.isChecked = true
                }
            })
        } else {
            fullscreenOptionsMenuItem?.isVisible = false
        }

        themeMenuItem?.subMenu?.forEach({ menuItem ->
            if (themePref == menuItem.title) {
                menuItem.isChecked = true
            }
        })


        // Puoi fare lo stesso per altre voci di menu che dipendono dal tipo di media.

        // Chiama super per far continuare la preparazione standard (es. icone)
        return super.onPrepareOptionsMenu(menu)
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
        searchInputLayout = findViewById(R.id.searchInputLayout)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnFirst = findViewById(R.id.btnFirst)
        btnNext = findViewById(R.id.btnNext)
        btnFw = findViewById(R.id.btnFw)
        btnRev = findViewById<MaterialButton>(R.id.btnRew)
        btnFavoriteRadio = findViewById<MaterialButton>(R.id.btnFavoriteRadio)
        topInfoText = findViewById(R.id.topInfoText)
        countrySelector = findViewById(R.id.countrySelector)  // subito dopo gli altri findViewById
        countrySelectorLayout = findViewById(R.id.countrySelectorLayout)


    }

    // ---- LEGGE IL JSON DAGLI assets ----
    private fun loadCountriesFromAssets(): List<Country> {
        return try {
            val jsonString = assets.open("country.json")
                .bufferedReader()
                .use { it.readText() }
            val listType = object : TypeToken<List<Country>>() {}.type
            Gson().fromJson(jsonString, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun createStatsOverlay() {
        // Usa LayoutInflater per caricare il layout XML
        val statsOverlayLayout = LayoutInflater.from(this).inflate(
            R.layout.stats_overlay_layout,
            playerContainer,
            false
        ) as MaterialCardView // Cast necessario

        // Aggiungi statsOverlayLayout al playerContainer
        playerContainer.addView(statsOverlayLayout)

    }

    /**
     * Restituisce il country‑code (es. "IT") a partire dal testo mostrato
     * nell’AutoCompleteTextView countrySelector.
     * Se il testo non corrisponde a nessun Paese noto, torna null.
     */
    private fun getSelectedCountryCode(): String? {
        val currentText = countrySelector.text?.toString()?.trim() ?: return null

        // 1) Estrai la parte prima dei due punti (es. "Italia" da "Italia: (42)")
        val countryName = currentText.substringBefore(':').trim()

        // 2) Cerca nella lista caricata in precedenza
        return countries.firstOrNull { it.name.equals(countryName, ignoreCase = true) }?.code
    }


    private fun setupCountrySpinner() {

        countries = loadCountriesFromAssets()

        // Entry formattate "Nome: (xx)"
        val entries = countries.map { "${it.name}: (${it.stationCount})" }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries)
        countrySelector.setAdapter(adapter)

        /* ---------- DEFAULT: leggiamo quello salvato o Italia ---------- */
        val savedCode = sharedPref.getString(PREF_SELECTED_COUNTRY, "IT") ?: "IT"
        val defaultIdx = countries.indexOfFirst { it.code.equals(savedCode, true) }
            .takeIf { it >= 0 }          // ‑1 → null
            ?: countries.indexOfFirst {  // se non c’è, ripieghiamo su Italia
                it.code.equals("IT", true) || it.name.equals("Italia", true)
            }

        countrySelector.post {
            countrySelector.setText(entries[defaultIdx], /*filter=*/false)
        }
        /* ---------------------------------------------------------------- */

        /* --- Pulisci al primo tap --- */
        var firstUserEdit = true
        countrySelector.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && firstUserEdit) {
                firstUserEdit = false
                countrySelector.text?.clear()
                countrySelector.showDropDown()
            } else if (!hasFocus) {          // uscita dal campo → valida
                validateCountry(entries, countries, defaultIdx)
            }
        }

        /* --- Click su un elemento --- */
        countrySelector.setOnItemClickListener { _, _, position, _ ->
            saveCountryIfChanged(countries[position])
        }
    }

    /* ---------- FUNZIONI DI SUPPORTO ---------- */

    private fun saveCountryIfChanged(newCountry: Country) {
        val oldCode = sharedPref.getString(PREF_SELECTED_COUNTRY, "IT")
        if (!newCountry.code.equals(oldCode, true)) {
            sharedPref.edit().putString(PREF_SELECTED_COUNTRY, newCountry.code).apply()
            countryChanged =
                true                   //  ora chi usa l’audio sa che deve rifare la query
            Log.d(TAG, "Country cambiato in ${newCountry.code}")
        }
    }

    private fun validateCountry(
        entries: List<String>,
        countries: List<Country>,
        defaultIdx: Int
    ) {
        val txt = countrySelector.text?.toString() ?: ""
        val idx = entries.indexOf(txt)
        if (idx == -1) {                     // testo libero NON valido → torna al default
            countrySelector.setText(entries[defaultIdx], /*filter=*/false)
            saveCountryIfChanged(countries[defaultIdx])
        } else {
            saveCountryIfChanged(countries[idx])
        }
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
        btnPrevious.setOnClickListener {
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
                                    hit
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
        btnFirst.setOnClickListener {
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
                                    hit
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
        btnNext.setOnClickListener {
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
                                    hit
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

        searchInputLayout.setStartIconOnClickListener {
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
                            // Lanciamo una coroutine per eseguire l'operazione di DB e rete in background
                            uiScope.launch {
                                // 1. Ricerca nel database usando il metodo esistente searchByName
                                val stationsFromDb = withContext(Dispatchers.IO) {
                                    // Esegui la ricerca nel DB in un thread di background (Dispatchers.IO)
                                    radioStationDao.searchByName(query)
                                }

                                if (stationsFromDb.isNotEmpty() && !countryChanged) {
                                    // 2. Se trovati risultati nel DB, usali
                                    Log.d(
                                        TAG,
                                        "Trovate ${stationsFromDb.size} stazioni nel DB per la query: $query"
                                    )
                                    // Converti la lista di RadioStation in List<HitRadio> usando l'extension function esistente
                                    val hitRadioListFromDb = stationsFromDb.map { it.toHitRadio() }
                                    handleRadioResponse(hitRadioListFromDb)
                                    radioBrowserService.setQuery(query)
                                    radioBrowserService.setradioList(hitRadioListFromDb)
                                    radioBrowserService.setCurrentIndex(0)
                                    updateResultsInfo()
                                } else {
                                    // 3. Se nessun risultato nel DB, cerca tramite il servizio di rete
                                    Log.d(
                                        TAG,
                                        "Nessuna stazione trovata nel DB, cerco sul servizio per la query: $query"
                                    )
                                    val code = getSelectedCountryCode()
                                        ?: "IT"   // fallback su Italia se null
                                    radioBrowserService.searchMedia(
                                        query,
                                        countryCode = code
                                    ) { apiResponse ->
                                        // Gestisci la risposta dal servizio di rete
                                        handleRadioResponse(apiResponse)
                                        // Opzionale: Salva i risultati trovati online nel DB per future ricerche
                                        CoroutineScope(Dispatchers.IO).launch {
                                            apiResponse?.forEach { hit ->
                                                if (hit is HitRadio) {
                                                    val radioStation =
                                                        RadioStation(hit) // Usa il costruttore che prende HitRadio
                                                    radioStationDao.insertOrIgnore(radioStation)
                                                }
                                            }
                                        }
                                    }
                                }
                                // Aggiorna l'UI principale (potrebbe essere chiamata anche dentro i callback
                                // se vuoi aggiornare l'UI solo quando i dati sono pronti)
                                //updateResultsInfo()
                            }
                        }
                    }
        }

        btnFw.setOnClickListener {
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
                                    hit
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
        btnRev.setOnClickListener { //TODO DA FARE
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
                                    hit
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
        btnFavoriteRadio.setOnClickListener { //TODO DA FARE
            Log.d(TAG, "Star button clicked")
            // Qui implementerai la navigazione al media precedente
            when (getCurrentMediaType()) {
                MEDIA_TYPE_AUDIO -> {
                    toggleRadioPref()
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
                        // Lanciamo una coroutine per eseguire l'operazione di DB e rete in background
                        uiScope.launch {
                            // 1. Ricerca nel database usando il metodo esistente searchByName
                            val stationsFromDb = withContext(Dispatchers.IO) {
                                // Esegui la ricerca nel DB in un thread di background (Dispatchers.IO)
                                radioStationDao.searchByName(query)
                            }

                            if (stationsFromDb.isNotEmpty() && !countryChanged) {
                                // 2. Se trovati risultati nel DB, usali
                                Log.d(
                                    TAG,
                                    "Trovate ${stationsFromDb.size} stazioni nel DB per la query: $query"
                                )
                                // Converti la lista di RadioStation in List<HitRadio> usando l'extension function esistente
                                val hitRadioListFromDb = stationsFromDb.map { it.toHitRadio() }
                                handleRadioResponse(hitRadioListFromDb)
                                radioBrowserService.setradioList(hitRadioListFromDb)
                                radioBrowserService.setCurrentIndex(0)
                            } else {
                                // 3. Se nessun risultato nel DB, cerca tramite il servizio di rete
                                Log.d(
                                    TAG,
                                    "Nessuna stazione trovata nel DB, cerco sul servizio per la query: $query"
                                )
                                val code =
                                    getSelectedCountryCode() ?: "IT"   // fallback su Italia se null
                                radioBrowserService.searchMedia(
                                    query,
                                    countryCode = code
                                ) { apiResponse ->
                                    // Gestisci la risposta dal servizio di rete
                                    handleRadioResponse(apiResponse)
                                    // Opzionale: Salva i risultati trovati online nel DB per future ricerche
                                    CoroutineScope(Dispatchers.IO).launch {
                                        apiResponse?.forEach { hit ->
                                            if (hit is HitRadio) {
                                                val radioStation =
                                                    RadioStation(hit) // Usa il costruttore che prende HitRadio
                                                radioStationDao.insertOrIgnore(radioStation)
                                            }
                                        }

                                    }
                                }
                            }
                            // Aggiorna l'UI principale (potrebbe essere chiamata anche dentro i callback
                            // se vuoi aggiornare l'UI solo quando i dati sono pronti)
                            //updateResultsInfo()
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

    private fun toggleRadioPref() {
        if (RadioPlayerService.isRunning) {
            if (btnFavoriteRadio.iconTint == defaultTint) {

                sharedPref.edit().putString(PREF_FAV_NAME, currentRadio?.name).apply()
                sharedPref.edit().putString(PREF_FAV_ICON, currentRadio?.favicon).apply()
                sharedPref.edit().putString(PREF_FAV_URL, currentRadio?.url).apply()
                sharedPref.edit().putString(PREF_FAV_UUID, currentRadio?.serveruuid).apply()

                btnFavoriteRadio.iconTint = goldColor
                btnFavoriteRadio.iconTintMode = PorterDuff.Mode.SRC_ATOP

            } else {

                sharedPref.edit().putString(PREF_FAV_NAME, "").apply()
                sharedPref.edit().putString(PREF_FAV_ICON, "").apply()
                sharedPref.edit().putString(PREF_FAV_URL, "").apply()
                sharedPref.edit().putString(PREF_FAV_UUID, "").apply()

                btnFavoriteRadio.iconTint = defaultTint
                btnFavoriteRadio.iconTintMode = PorterDuff.Mode.SRC_ATOP
            }
        }
    }

    // Funzione per impostare lo stato iniziale (chiamata solo se non si ripristina)
    private fun setupInitialContent() {
        Log.d(TAG, "Setting up initial content...")

        defaultTint = btnFavoriteRadio.iconTint
        goldColor = ContextCompat.getColorStateList(this, R.color.gold)!!

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
    fun loadRadioPlayer(radio: HitRadio?) {
        uiScope.launch {
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
                radio?.favicon?.toUri().toString(),
                wrapperView
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
                if (!RadioPlayerService.isRunning) {
                    val intent = Intent(this@MainActivity, RadioPlayerService::class.java).apply {
                        action = RadioPlayerService.ACTION_START
                        putExtra("hitRadio", radio)
                    }
                    startService(intent)
                }
            }

            // Prepara ExoPlayer solo se non già esistente
            if (PlayerHolder.exoPlayer == null) {
                PlayerHolder.exoPlayer = player
            }

            // Passa i parametri via Intent
            val intent = Intent(this@MainActivity, RadioPlayerService::class.java).apply {
                action = "ACTION_START_RADIO"
                putExtra("hitRadio", radio)
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
            currentRadio = listResp.firstOrNull() // currentRadio viene aggiornato qui
            currentRadio?.let {
                Log.d("MainActivity", "Media caricato: ${it.name}")
                when (currentMediaType) {
                    MEDIA_TYPE_AUDIO -> loadRadioPlayer(
                        it
                    )
                }
            }
        } else {
            Toast.makeText(this@MainActivity, "Nessun risultato trovato.", Toast.LENGTH_SHORT)
                .show()
            currentRadio = null // **Imposta currentRadio a null se non ci sono risultati**
        }
        // Chiama updateResultsInfo() dopo che currentRadio è stato aggiornato
        updateResultsInfo()
    }

    private fun loadImageWithGlide(imageUrl: String?) {
        Glide.with(this@MainActivity)
            .load(imageUrl)
            .into(imageViewer)
    }

    // Funzione di estensione per convertire Bitmap in InputStream
// Puoi definire questa funzione fuori dal metodo setWallpaperWithScaleType, magari
// insieme alle altre funzioni helper nella tua classe.
    fun Bitmap.toInputStream(format: Bitmap.CompressFormat, quality: Int): InputStream {
        val byteStream = ByteArrayOutputStream()
        this.compress(format, quality, byteStream)
        return ByteArrayInputStream(byteStream.toByteArray())
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

            // --- Inizia codice per debug: Salva il bitmap ---
            // Richiede permessi di scrittura su storage esterno se Target API < 29
            // (ma per Target API >= 29, si usano le Scoped Storage API, il che è più complesso)
            // Per l'emulatore, potresti provare un path semplice, ma la robustezza
            // richiede l'uso corretto delle API di storage in base alla versione Android.
            // Un modo più semplice per il debug rapido su emulatore (se i permessi sono gestiti):
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "wallpaper_debug_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                transformedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d(TAG, "Bitmap di debug salvato in: ${file.absolutePath}")

            // --- Usa setStream() anziché setBitmap() ---
            // Ottieni un InputStream dal bitmap trasformato
            val bitmapInputStream = transformedBitmap.toInputStream(
                Bitmap.CompressFormat.PNG,
                100
            ) // Puoi provare anche JPEG con qualità diversa

            // Imposta il wallpaper utilizzando setStream
            wallpaperManager.setStream(
                bitmapInputStream, // L'InputStream del bitmap
                null, // rect per definire quale parte del bitmap usare (null per l'intero bitmap)
                true // true per consentire lo scrolling del wallpaper
            )

            // Chiudi l'InputStream dopo l'uso
            bitmapInputStream.close()

            Toast.makeText(
                this@MainActivity,
                "Wallpaper impostato (via stream).",
                Toast.LENGTH_SHORT
            )
                .show()


//            wallpaperManager.setBitmap(transformedBitmap)
//            Toast.makeText(this@MainActivity, "Wallpaper impostato.", Toast.LENGTH_SHORT)
//                .show()
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

        // Aggiorna la variabile globale
        currentMediaType = mediaType

        // Crea e mostra il visualizzatore appropriato
        setupMediaLayer(mediaType)

        // Forza la ricreazione del menu della Top App Bar
        invalidateOptionsMenu() // Aggiungi questa riga

        // Mostra il filtro Paese solo per l’audio
        countrySelectorLayout.visibility =
            if (mediaType == MEDIA_TYPE_AUDIO) View.VISIBLE else View.GONE

        // La chiamata a updateResultsInfo() può rimanere qui o essere gestita altrove se preferisci
        updateResultsInfo()

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
        radioBrowserService.cancelJobs()   // libera le coroutine, non serve onDestroy interno
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

                playerContainer.addView(imageViewer) //OK
                createStatsOverlay()
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

                playerView.setControllerVisibilityListener(object :
                    PlayerView.ControllerVisibilityListener {
                    override fun onVisibilityChanged(visibility: Int) {
                        if (visibility != View.VISIBLE) {
                            playerView.showController()
                        }
                    }
                })

                // --- NUOVA LOGICA PER AVVIARE LA RADIO PREFERITA ---
                val favRadioUrl = sharedPref.getString(PREF_FAV_URL, "")
                val favRadioUid = sharedPref.getString(PREF_FAV_UUID, "")
                val favRadioIcon = sharedPref.getString(PREF_FAV_ICON, "")
                val favRadioName = sharedPref.getString(PREF_FAV_NAME, getString(R.string.radio_station_default_name)) // Recupera anche il nome, con un default

                if (!favRadioUrl.isNullOrEmpty()) {
                    Log.i(TAG, "Stazione radio preferita trovata: '$favRadioUrl' ($favRadioName). Tentativo di avvio tramite Service.")

                    val serviceIntent = Intent(this@MainActivity, RadioPlayerService::class.java).apply {
                        action = RadioPlayerService.ACTION_START // Usa la costante corretta dal tuo RadioPlayerService
                        var radio = HitRadio(favRadioUid.toString(),favRadioName.toString(),favRadioIcon,favRadioUrl)
                        putExtra("hitRadio", radio)


                        val playerView = wrapperView.findViewById<PlayerView>(R.id.player_radio_view)
                        playerView.setControllerBackgroundFromUrl(
                            this@MainActivity,
                            favRadioIcon.toString(),
                            wrapperView
                        )

                        btnFavoriteRadio.iconTint = goldColor
                        btnFavoriteRadio.iconTintMode = PorterDuff.Mode.SRC_ATOP

                    }
                    startService(serviceIntent) // Avvia il servizio per la riproduzione

                    // Nota: La ProgressBar dovrebbe essere nascosta quando il RadioPlayerService
                    // segnala l'inizio effettivo della riproduzione o un errore.
                    // Questo richiede una comunicazione Service -> Activity (es. LocalBroadcastManager o LiveData).
                } else {
                    Log.i(TAG, "Nessuna stazione radio preferita trovata nelle SharedPreferences.")

                }
                // --- FINE NUOVA LOGICA ---
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
                saveScalePreference(item.itemId.toString())
                invalidateOptionsMenu()
                Log.d(TAG, "ScaleType selezionato: CENTER_CROP")
                return true
            }

            R.id.scaleType_fitCenter -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_CENTER
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(item.itemId.toString())
                invalidateOptionsMenu()
                Log.d(TAG, "ScaleType selezionato: FIT_CENTER")
                return true
            }

            R.id.scaleType_centerInside -> {
                imageViewer.scaleType = ImageView.ScaleType.CENTER_INSIDE
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(item.itemId.toString())
                invalidateOptionsMenu()
                Log.d(TAG, "ScaleType selezionato: CENTER_INSIDE")
                return true
            }

            R.id.scaleType_fitXY -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_XY
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(item.itemId.toString())
                invalidateOptionsMenu()
                Log.d(TAG, "ScaleType selezionato: FIT_XY")
                return true
            }

            R.id.scaleType_center -> {
                imageViewer.scaleType = ImageView.ScaleType.CENTER
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(item.itemId.toString())
                invalidateOptionsMenu()
                Log.d(TAG, "ScaleType selezionato: CENTER")
                return true
            }

            R.id.scaleType_fitStart -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_START
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(item.itemId.toString())
                invalidateOptionsMenu()
                Log.d(TAG, "ScaleType selezionato: FIT_START")
                return true
            }

            R.id.scaleType_fitEnd -> {
                imageViewer.scaleType = ImageView.ScaleType.FIT_END
                currentScaleTypeIndex = imageViewer.scaleType.ordinal
                saveScalePreference(item.itemId.toString())
                invalidateOptionsMenu()
                Log.d(TAG, "ScaleType selezionato: FIT_END")
                return true
            }

            R.id.fullScreen_true -> {
                saveVideoPreference(getString(R.string.full_true))
                Log.d(TAG, "Fullscreen non selezionato")
                invalidateOptionsMenu()
                recreate() // Ricrea l'Activity per applicare il nuovo tema
                return true
            }

            R.id.fullScreen_false -> {
                saveVideoPreference(getString(R.string.full_false))
                Log.d(TAG, "Fullscreen non selezionato")
                invalidateOptionsMenu()
                recreate() // Ricrea l'Activity per applicare il nuovo tema
                return true
            }

            // Gestione delle opzioni per il cambio del tema
            R.id.theme_light -> {
                saveThemePreference(getString(R.string.light))
                Log.d(TAG, "Tema selezionato: Light")
                invalidateOptionsMenu()
                recreate() // Ricrea l'Activity per applicare il nuovo tema
                return true
            }

            R.id.theme_dark -> {
                saveThemePreference(getString(R.string.dark))
                Log.d(TAG, "Tema selezionato: Dark")
                invalidateOptionsMenu()
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

    /**
     * Aggiorna le informazioni mostrate nella top app bar e nel TextView topInfoText
     * in base al tipo di media e ai dati correnti.
     */
    private fun updateResultsInfo() {
        val infoTextTopBar: String = when (getCurrentMediaType()) {
            MEDIA_TYPE_IMAGE -> {
                val totalResults = imageViewModel.getTotal()
                val currentIndex =
                    imageViewModel.getCurrentIndex() + 1 // +1 perché l'indice parte da 0
                val currentImage = imageViewModel.getCurrentItem()
                topInfoText.text = getTopInfoText(currentMediaType, currentImage, query)
                textViewsCount = findViewById(R.id.textViewsCount)
                textLikesCount = findViewById(R.id.textLikesCount)
                if (currentImage != null) {
                    textLikesCount.text = currentImage.likes.toString() ?: "N/A"
                    textViewsCount.text = currentImage.views.toString() ?: "N/A"
                }

                // Restituisci la stringa per la Top App Bar (solo indice/totale)
                if (totalResults > 0) {
                    "Image: ${currentIndex} - ${totalResults}"
                } else if (query.isNotBlank()) {
                    "Image: Nessun risultato per \"$query\""
                } else {
                    "Image: Nessun risultato"
                }
            }

            MEDIA_TYPE_VIDEO -> {
                val totalResults = pixbayVideoService.getTotal()
                val currentIndex =
                    pixbayVideoService.getCurrentIndex() + 1 // +1 perché l'indice parte da 0
                val currentVideo = pixbayVideoService.getCurrentItem()

                topInfoText.text = getTopInfoText(currentMediaType, currentVideo, query)

                // Restituisci la stringa per la Top App Bar (solo indice/totale)
                if (totalResults > 0) {
                    "Video: ${currentIndex} - ${totalResults}"
                } else if (query.isNotBlank()) {
                    "Video: Nessun risultato per \"$query\""
                } else {
                    "Video: Nessun risultato"
                }
            }

            MEDIA_TYPE_AUDIO -> {
                val totalResults = radioBrowserService.getTotal()
                val currentIndex =
                    radioBrowserService.getCurrentIndex() + 1 // +1 perché l'indice parte da 0
                val currentAudio = radioBrowserService.getCurrentItem()

                topInfoText.text = getTopInfoText(currentMediaType, currentAudio, query)

                // Restituisci la stringa per la Top App Bar (solo indice/totale)
                if (totalResults > 0) {
                    "Radio: ${currentIndex} - ${totalResults}"
                } else if (query.isNotBlank()) {
                    "Radio: Nessun risultato per \"$query\""
                } else {
                    "Radio: Nessun risultato"
                }
            }

            else -> {
                // Caso di default o stato iniziale
                topInfoText.text =
                    "Seleziona un tipo di media e cerca!" // Messaggio di default per topInfoText
                "MBI" // Titolo di default per la Top App Bar
            }
        }
        // Imposta il titolo della Top App Bar con la stringa creata
        topAppBar.title = infoTextTopBar
        Log.d(TAG, "Top AppBar Title: $infoTextTopBar")

        // Imposta il sottotitolo della top bar come vuoto
        topAppBar.subtitle = "Multimedia Buddy :-) "
        Log.d(TAG, "Top AppBar Subtitle: ${topAppBar.subtitle}")
    }

    private fun getTopInfoText(mediaType: Int, currentMedia: Any?, query: String): String {
        return when (mediaType) {
            MEDIA_TYPE_IMAGE -> {
                if (currentMedia is io.github.luposolitario.mbi.model.Hit) {
                    val currentImage = currentMedia as io.github.luposolitario.mbi.model.Hit
                    "By: ${currentImage.user} - Tags: ${currentImage.tags} "
                } else if (query.isNotBlank()) {
                    "Nessun dettaglio disponibile per \"$query\""
                } else {
                    "Nessun dettaglio immagine"
                }
            }

            MEDIA_TYPE_VIDEO -> {
                if (currentMedia is io.github.luposolitario.mbi.model.Hit) {
                    val currentVideo = currentMedia as io.github.luposolitario.mbi.model.Hit
                    "By: ${currentVideo.user} - Tags: ${currentVideo.tags} - Views: ${currentVideo.views} Likes: ${currentVideo.likes} ❤\uFE0F"
                } else if (query.isNotBlank()) {
                    "Nessun dettaglio disponibile per \"$query\""
                } else {
                    "Nessun dettaglio video"
                }
            }

            MEDIA_TYPE_AUDIO -> {
                if (currentMedia is HitRadio) "Radio: ${currentMedia.name}"
                else if (query.isNotBlank()) "Nessun dettaglio disponibile per \"$query\""
                else "Nessun dettaglio audio"
            }

            else -> "Seleziona un tipo di media e cerca!"
        }
    }
}