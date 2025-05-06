# MBI – Multimedia Buddy Interface

[![Licenza MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Collaborazione AI](https://img.shields.io/badge/AI-Collaborative%20Development-blue)](https://example.com)

**Autore: Michele Lops(<sentieroluminoso@gmail.com>)**

MBI è un’applicazione **Android** open‑source che permette di **cercare, visualizzare e ascoltare**
contenuti multimediali provenienti da diversi servizi web. L’interfaccia è pensata per essere
semplice, fluida e ricca di scorciatoie, sia per l’uso quotidiano sia per esperimenti didattici con
librerie e framework moderni.

---

## Funzionalità principali

|  ✔️  | Cosa puoi fare con MBI                       |  Dettagli                                                                                              |
|------|----------------------------------------------|--------------------------------------------------------------------------------------------------------|
| 🔍   | **Ricercare immagini**                       | integrazione con l’API Pixabay (endpoint `/api/`)                                                      |
| 📹   | **Ricercare video**                          | integrazione con l’API Pixabay Video (endpoint `/api/videos/`)                                         |
| 📻   | **Cercare e ascoltare stazioni radio**       | query sull’API [Radio‑Browser](https://www.radio-browser.info/) e riproduzione streaming               |
| 🎞   | **Riprodurre video**                         | player basato su Media3 ExoPlayer con controlli personalizzati, modalità fullscreen ed autohide        |
| 🎧   | **Riprodurre radio in background**           | servizio in primo piano con MediaSession, notifiche e metadati ICY/ID3                                 |
| 🖼   | **Zoom, pan & wallpaper**                    | PhotoView per pinch‑to‑zoom; lunga pressione per impostare l’immagine come sfondo con vari `ScaleType` |
| ⬅️➡️ | **Navigazione veloce**                       | pulsanti «prev/next», salti ±10 elementi, «first» e indicatori di posizione / totale                   |
| 🎨   | **Tema chiaro / scuro runtime**              | preferenza salvata in SharedPreferences                                                                |
| ⚙️   | **Schermata impostazioni (Jetpack Compose)** | modifica chiave API Pixabay e preferenze video                                                         |
| 🗣   | **Interfaccia in italiano**                  | stringhe localizzate, icone Material                                                                   |

---

## Servizi implementati

| Classe / Service      | API o libreria esterna                           | Responsabilità                                      |
|-----------------------|--------------------------------------------------|-----------------------------------------------------|
| `PixbayImageService`  | **Pixabay REST** (`/api/`)                       | download e paging delle immagini                    |
| `PixbayVideoService`  | **Pixabay Video REST** (`/api/videos/`)          | download e paging dei video                         |
| `RadioBrowserService` | **Radio‑Browser JSON** (`/json/stations/search`) | ricerca stazioni radio (con fallback offline)       |
| `RadioPlayerService`  | **Media3 ExoPlayer + Notification**              | streaming audio, MediaSession, lock‑screen controls |

---

## Principali classi

| Classe                         | Posizione*                        | Ruolo / Responsabilità chiave                                                                                                             |
|--------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `MainActivity`                 | `MainActivity.kt`                 | Contiene la UI principale, gestisce il `NavController` tra immagini, video e radio, e delega gli eventi di riproduzione a `PlayerHolder`. |
| `MediaService`                 | `MediaService.kt`                 | Servizio astratto base per il playback; espone una `MediaSession` configurata e funge da superclass per `RadioPlayerService`.             |
| `RadioPlayerService`           | `RadioPlayerService.kt`           | Estende `MediaService` per lo streaming radio: configura ExoPlayer audio‑only, notifica foreground e metadati *now‑playing*.              |
| `RadioInterfaceService`        | `RadioInterfaceService.kt`        | Wrapper Retrofit verso Radio‑Browser; mappa le risposte JSON in `HitRadio`.                                                               |
| `PixabayInterfaceImageService` | `PixabayInterfaceImageService.kt` | Definisce le chiamate REST (`/api/`) per le immagini Pixabay.                                                                             |
| `PixabayInterfaceVideoService` | `PixabayInterfaceVideoService.kt` | Endpoint Retrofit per i video Pixabay (`/api/videos/`).                                                                                   |
| `PixbayImageService`           | `PixbayImageService.kt`           | Repository che orchestra `PixabayInterfaceImageService`, caching locale e mapping verso il dominio.                                       |
| `PixbayVideoService`           | `PixbayVideoService.kt`           | Analogo a `PixbayImageService` ma per contenuti video.                                                                                    |
| `ImageViewModel`               | `ImageViewModel.kt`               | ViewModel condiviso da fragment immagini e video; mantiene stato di ricerca, paging e selezione correnti, esponendo `LiveData`/`Flow`.    |
| `PlayerHolder`                 | `PlayerHolder.kt`                 | Wrapper singleton di ExoPlayer che fornisce metodi `play/pause/seek` comuni e pubblica lo stato tramite `Flow<PlayerState>`.              |
| `SettingsActivity`             | `SettingsActivity.kt`             | Activity basata su Jetpack Compose che gestisce preferenze (tema, chiave API, modalità video).                                            |

<sub>*I percorsi dei file si riferiscono alla root `app/src/main/java/...`</sub>

---

## Stack tecnologico

* **Kotlin 1.9** + **Coroutines / Flow**
* **Android Jetpack**
    * ViewModel, LiveData, SavedStateHandle (architettura **MVVM**)
    * **Jetpack Compose Material 3** (Settings)
    * ConstraintLayout & ViewBinding per la UI classica
* **Media3 ExoPlayer 1.3.x** – riproduzione audio/video e integrazione MediaSession
* **Retrofit 2 + OkHttp 4 + Gson** – network layer e parsing JSON
* **Glide** – caricamento e caching immagini
* **PhotoView** – gesti pinch‑to‑zoom sulle immagini
* **Material Components** – temi chiaro/scuro, badge, stili moderni
* **Accompanist ThemeAdapter Material3** – bridge XML ↔ Compose
* **SharedPreferences** per configurazioni leggere (API‑key, preferenze tema, fullscreen,
  scale‑type)

---

## Avvio rapido

1. **Clona** il repository:
   ```bash
   git clone https://github.com/MicheleLopsDev/mbi.git
   ```
2. **Importa** in *Android Studio* 
3. **Inserisci la tua chiave Pixabay**:
    * apri l’app → **Menu ▸ Impostazioni ▸ Chiave Pixabay**
4. **Esegui** l’app su un device/emulatore con **Android 8.0(Oreo)** o superiore.

> ℹ️ La ricerca radio non richiede chiavi API.

---

## Contribuire

Le pull‑request sono benvenute!Leggi **`CONTRIBUTING.md`** e **`CODE_OF_CONDUCT.md`** prima di
iniziare.

* Apri una *issue* se trovi bug o hai suggerimenti.
* Implementa la tua feature in un branch dedicato e invia una PR descrittiva.

---

## Licenza

Distribuito con licenza [MIT](https://opensource.org/licenses/MIT).
