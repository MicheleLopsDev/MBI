package io.github.luposolitario.mbi.util

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StyleRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import io.github.luposolitario.mbi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Incapsula il contenuto in un Context con il tema XML indicato */

/** Activity host della schermata */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE) // [Source 7]
        val themePref = sharedPref.getString("selected_theme", "light")
        var darkMode = false

        if (themePref == "dark") {
            setTheme(R.style.Theme_MBI_Dark)
            darkMode = true
        } else {
            setTheme(R.style.Theme_MBI) // [Source 8]
        }

        super.onCreate(savedInstanceState)
        setContent {
            MBITheme(useDarkTheme = darkMode) {       // composable custom
                SettingsScreen(
                    onSave = {            // ⬅️ definisci cosa succede dopo il Salva
                        setResult(RESULT_OK)   // facoltativo: segnala “operazione riuscita”
                        finish()               // chiudi l’Activity → torni alla Main
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
        this.prefs.edit() { putString("pixabay_api_key", _apiKey.value) }
    }
}

class SettingsViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(ctx) as T
}

/** UI Compose */

@Preview(
    name = "Settings • Theme.MBI (light)",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Composable
fun SettingsPreviewLight() {
    XmlTheme(R.style.Theme_MBI) {
        PreviewContent()         // composable wrapper senza parametri (vedi sotto)
    }
}

@Preview(
    name = "Settings • Dark",
    showBackground = true,
    backgroundColor = 0xFF000000,      // ← FF (opaco) + 000000 (nero)
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showSystemUi = true
)
@Composable
fun SettingsPreviewDark() {
    XmlTheme(R.style.Theme_MBI_Dark) {
        PreviewContent()
    }
}


@Composable
private fun PreviewContent() {
    // ViewModel fittizio in memoria: NON tocca le SharedPreferences vere
    val ctx = LocalContext.current
    val fakeVm = remember {
        SettingsViewModel(ctx).apply {
            onKeyChange("1234567‑ABCD")      // imposta una chiave d’esempio
        }
    }

    SettingsScreen(
        onSave = {},        // in anteprima non deve fare nulla
        viewModel = fakeVm
    )
}


@Composable
fun SettingsScreen(
    onSave: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current))
) {
    val key by viewModel.apiKey.collectAsState()
    // val activity = LocalActivity.current as? Activity     // ⬅️ reference
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
    ) {

        Text("Chiave Pixabay", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = key,
            onValueChange = viewModel::onKeyChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        /* ---------- pulsanti affiancati ---------- */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)   // gap fra i due
        ) {

            Button(
                onClick = {
                    onSave()           // 2️⃣ delega l’azione di uscita al caller
                },
                modifier = Modifier
                    .weight(1f)              // 50 % larghezza
                    .fillMaxWidth()
            ) {
                Text("Chiudi")
            }


            Button(
                onClick = {
                    viewModel.save()   // 1️⃣ salva la preferenza
                    onSave()           // 2️⃣ delega l’azione di uscita al caller
                },
                modifier = Modifier
                    .weight(1f)              // 50 % larghezza
                    .fillMaxWidth()
            ) {
                Text("Salva")
            }

        }

    }


}


@Composable
private fun XmlTheme(
    @StyleRes themeId: Int,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themedCtx = remember(themeId, context) {
        ContextThemeWrapper(context, themeId)
    }

    CompositionLocalProvider(LocalContext provides themedCtx) {
        Mdc3Theme { content() }   // ⬅️ adapter per Material 3
    }
}