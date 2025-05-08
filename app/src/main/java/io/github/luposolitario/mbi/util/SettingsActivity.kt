package io.github.luposolitario.mbi.util

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.luposolitario.mbi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


/** Activity host della schermata */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Rimuovi il codice per impostare il tema XML qui
        // setTheme(...) non è necessario con un tema Compose nativo nel setContent

        super.onCreate(savedInstanceState)

        // Leggi la preferenza del tema per passarla al tema Compose
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val themePref = sharedPref.getString("selected_theme", "light")
        val useDarkTheme = themePref == "dark" // Booleano per il tema scuro

        setContent {
            // Applica il tema Material 3 nativo in Compose
            AppTheme(useDarkTheme = useDarkTheme) {
                SettingsScreen(
                    onSave = {
                        // Logica per salvare e chiudere l'Activity
                        val settingsViewModel =
                            SettingsViewModel(this) // Ottieni il ViewModel qui per salvare
                        settingsViewModel.save()
                        setResult(RESULT_OK)
                        finish()
                    },
                    onClose = {
                        // Logica per chiudere l'Activity senza salvare (es. al click sul tasto Indietro)
                        setResult(RESULT_CANCELED) // Opzionale: segnala cancellazione
                        finish()
                    }
                )
            }
        }
    }
}

/** ViewModel che incapsula l’accesso a SharedPreferences */
class SettingsViewModel(context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(prefs.getString("pixabay_api_key", "") ?: "")
    val apiKey = _apiKey.asStateFlow()

    fun onKeyChange(newKey: String) {
        _apiKey.value = newKey
    }

    fun save() {
        this.prefs.edit { putString("pixabay_api_key", _apiKey.value) }
    }
}

class SettingsViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(ctx) as T
}

/** Definizione di un tema Material 3 nativo in Compose */
@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(), // Default al tema di sistema
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) {
        darkColorScheme() // Tema scuro di Material 3
    } else {
        lightColorScheme() // Tema chiaro di Material 3
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography, // Usa la tipografia di default o definiscila
        shapes = MaterialTheme.shapes, // Usa le forme di default o definiscile
        content = content
    )
}

/** UI Compose */

// Rimuovi le anteprime basate su XmlTheme
/*
@Preview(
    name = "Settings • Theme.MBI (light)",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun SettingsPreviewLight() {
    XmlTheme(R.style.Theme_MBI) {
        PreviewContent()
    }
}

@Preview(
    name = "Settings • Dark",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showSystemUi = true
)
@Composable
fun SettingsPreviewDark() {
    XmlTheme(R.style.Theme_MBI_Dark) {
        PreviewContent()
    }
}
*/

// Nuove anteprime che usano il tema Compose nativo
@Preview(
    name = "Settings • Light Theme",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun SettingsPreviewLightCompose() {
    AppTheme(useDarkTheme = false) {
        PreviewContent()
    }
}

@Preview(
    name = "Settings • Dark Theme",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showSystemUi = true
)
@Composable
fun SettingsPreviewDarkCompose() {
    AppTheme(useDarkTheme = true) {
        PreviewContent()
    }
}


@Composable
private fun PreviewContent() {
    // ViewModel fittizio in memoria: NON tocca le SharedPreferences vere
    val ctx = LocalContext.current
    val fakeVm = remember {
        SettingsViewModel(ctx).apply {
            onKeyChange("1234567‑ABCD") // imposta una chiave d’esempio
        }
    }

    SettingsScreen(
        onSave = {}, // in anteprima non deve fare nulla
        onClose = {}, // in anteprima non deve fare nulla
        viewModel = fakeVm
    )
}


@OptIn(ExperimentalMaterial3Api::class) // Necessario per usare TopAppBar di Material 3
@Composable
fun SettingsScreen(
    onSave: () -> Unit, // Callback per l'azione Salva
    onClose: () -> Unit, // Callback per l'azione Chiudi/Indietro
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current))
) {
    val key by viewModel.apiKey.collectAsState()
    val uriHandler = LocalUriHandler.current // Ottieni l'handler per gestire gli URI
    val context = LocalContext.current // Ottieni il contesto per le stringhe

    Scaffold(
        topBar = {
            // Top App Bar di Material 3
            TopAppBar(
                title = {
                    Text(stringResource(id = R.string.settings_title)) // Usa una risorsa stringa per il titolo
                },
                navigationIcon = {
                    // Icona Indietro
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Icona Indietro
                            contentDescription = stringResource(id = R.string.back_button_desc) // Descrizione per accessibilità
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors( // Applica colori dalla Color Scheme del tema
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues -> // paddingValues contiene gli insetti della top bar e altri elementi di Scaffold
        Column(
            Modifier
                .fillMaxSize() // Usa fillMaxSize per riempire lo spazio disponibile
                .padding(paddingValues) // Applica il padding fornito da Scaffold
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ) // Aggiungi padding orizzontale e verticale al contenuto
        ) {

            Text(
                stringResource(id = R.string.pixabay_key_label),
                style = MaterialTheme.typography.titleLarge
            ) // Usa risorsa stringa
            Spacer(Modifier.height(8.dp))

            // Testo esplicativo per l'utente
            Text(
                text = stringResource(id = R.string.pixabay_key_explanation), // Usa risorsa stringa
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp)) // Piccolo spazio tra testo e link

            // Testo che funge da link
            val pixabayLink = "https://pixabay.com/it/"
            val annotatedLinkString = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(pixabayLink)
                }
            }

            Text(
                text = annotatedLinkString,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    // Gestisci il click per aprire il link nel browser
                    uriHandler.openUri(pixabayLink)
                }
            )

            Spacer(Modifier.height(8.dp)) // Spazio dopo il link e prima del campo di input

            OutlinedTextField(
                value = key,
                onValueChange = viewModel::onKeyChange,
                singleLine = true,
                label = { Text(stringResource(id = R.string.api_key_input_label)) }, // Usa risorsa stringa per la label
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            /* ---------- pulsanti affiancati ---------- */
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // gap fra i due
            ) {

                Button(
                    onClick = {
                        onSave() // Chiama il callback onSave quando "Salva" è cliccato
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.save_button)) // Usa risorsa stringa
                }
            }
        }
    }
}