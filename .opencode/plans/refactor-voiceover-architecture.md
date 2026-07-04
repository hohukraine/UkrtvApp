# Refactor: Proper 3-level Voiceover Architecture

## The Problem

Current model flattens `Season → Voiceover → Episode` into `Season → List<Episode>`:
- Duplicate episode numbers from different voiceovers cause `Key "2"` crash
- `voiceoverUrls: Map<String, String>` stored per-episode (over-engineered, duplicated)
- UI doesn't filter episodes by selected voiceover
- Band-aid `.distinctBy { it.number }` added in 3 places

## The Solution

### 1. Domain Model - `MovieDetail.kt`

```kotlin
@Parcelize
data class Voiceover(
    val name: String,
    val episodes: List<Episode>
) : Parcelable

@Parcelize
data class Season(
    val number: Int,
    val voiceovers: List<Voiceover>,
    val defaultEpisodes: List<Episode> = voiceovers.firstOrNull()?.episodes ?: emptyList()
) : Parcelable

@Parcelize
data class Episode(
    val number: Int,
    val title: String,
    val url: String,
    val subtitles: String? = null
) : Parcelable {
    val id: String get() = url
    val pageUrl: String get() = url
}
```

### 2. Data Model - `StreamProvider.kt`

```kotlin
data class ProviderVoiceover(
    val name: String,
    val episodes: List<ProviderEpisode>
)

data class ProviderSeason(
    val number: Int,
    val voiceovers: List<ProviderVoiceover>,
    val episodes: List<ProviderEpisode>  // first voiceover's episodes
)

data class ProviderEpisode(
    val number: Int,
    val title: String,
    val url: String,
    val subtitles: String? = null
)
```

### 3. Mapping - `StreamProvider.toDomainSeason()`

```kotlin
fun ProviderSeason.toDomainSeason(): Season = Season(
    number = this.number,
    voiceovers = this.voiceovers.map { v ->
        Voiceover(
            name = v.name,
            episodes = v.episodes.map { ep ->
                Episode(
                    number = ep.number,
                    title = ep.title,
                    url = ep.url,
                    subtitles = ep.subtitles
                )
            }
        )
    }
)
```

### 4. Resolution Strategies - `DleStrategies.kt` and `DleResolutionUtils.kt`

The AJAX playlist parsing already uses `Map<Int, MutableMap<String, String>>` keyed by episode → voiceover → URL. Need to invert this to `Map<String, MutableList<ProviderEpisode>>` keyed by voiceover name → episodes.

Instead of:
```kotlin
val eps = dEpsByNum.map { (epNum, urls) ->
    ProviderEpisode(epNum, "Серія $epNum", urls.values.first(), voiceoverUrls = urls)
}
ProviderSeason(num, eps.sortedBy { it.number }, voiceoverOptions = dCleanNames)
```

Return:
```kotlin
val voiceovers = dCleanNames.map { vName ->
    val eps = dEpsByNum.entries.mapNotNull { (epNum, urls) ->
        val url = urls[vName] ?: return@mapNotNull null
        ProviderEpisode(epNum, "Серія $epNum", url)
    }.sortedBy { it.number }
    ProviderVoiceover(vName, eps)
}
ProviderSeason(num, voiceovers)
```

### 5. Deep Resolution - `DleStrategies.kt` line 201-208

Replace flatMap group with voiceover-aware merge:
```kotlin
val merged = allSeasons.groupBy { it.number }
    .map { (num, list) ->
        val allVoiceovers = list.flatMap { it.voiceovers }
        val mergedVoiceovers = allVoiceovers.groupBy { it.name }
            .map { (name, vList) ->
                ProviderVoiceover(
                    name = name,
                    episodes = vList.flatMap { it.episodes }.distinctBy { it.number }.sortedBy { it.number }
                )
            }
        ProviderSeason(num, mergedVoiceovers)
    }.sortedBy { it.number }
```

### 6. `StreamResolver.kt` - voiceover switching (line 105-115)

Replace `ep.voiceoverUrls[voiceover]` with voiceover-aware lookup:
```kotlin
if (source is ProviderMediaSource.Series && season != null) {
    val s = source.seasons.find { it.number == season }
    if (s != null) {
        val defaultEp = s.episodes.find { it.number == episode } ?: s.episodes.firstOrNull()
        val targetUrl = if (voiceover != null) {
            s.voiceovers.find { it.name == voiceover }
                ?.episodes?.find { it.number == (episode ?: 1) }?.url
        } else null
        streamUrl = targetUrl ?: defaultEp?.url ?: streamUrl
        fallbackUrls = (s.episodes.map { it.url } - streamUrl) + fallbackUrls
    }
}
```

### 7. UI - `SeasonEpisodePicker.kt`

Add voiceover selector row between season tabs and episode list (similar to EpisodePickerOverlay lines 131-171). Filter episodes by selected voiceover:
```kotlin
val selectedVoiceover by remember { mutableStateOf(selectedSeason?.voiceovers?.firstOrNull()?.name) }
val episodes = selectedSeason?.voiceovers?.find { it.name == selectedVoiceover }?.episodes ?: emptyList()
```

### 8. UI - `EpisodePickerOverlay.kt`

Already has voiceover selector (lines 131-171). Just need to filter episodes by selected voiceover instead of using `selectedSeason?.episodes`:
```kotlin
val episodes = selectedSeason?.voiceovers?.find { it.name == selectedVoiceover }?.episodes ?: emptyList()
```

### 9. Remove band-aids

- `PlayerViewModel.kt` line 475: `context.voiceover = voiceover ?: context.voiceover` — keep (valid)
- `ContentRow.kt`: keep `key = { it.pageUrl }` — valid fix (id hash collision was real)
- Remove all `distinctBy { it.number }` band-aids from:
  - `DleStrategies.kt:204`
  - `DleResolutionUtils.kt:170`
  - `StreamProvider.kt:117`
