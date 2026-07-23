package ua.ukrtv.app.ui.player

import org.json.JSONArray
import org.json.JSONObject
import ua.ukrtv.app.domain.model.Episode
import ua.ukrtv.app.domain.model.Season
import ua.ukrtv.app.domain.model.Voiceover

fun serializeSeasons(seasons: List<Season>): String {
    val arr = JSONArray()
    for (s in seasons) {
        val voiceovers = JSONArray()
        for (v in s.voiceovers) {
            val episodes = JSONArray()
            for (ep in v.episodes) {
                episodes.put(JSONObject().apply {
                    put("number", ep.number)
                    put("title", ep.title)
                    put("url", ep.url)
                    put("subtitles", ep.subtitles ?: JSONObject.NULL)
                    put("poster", ep.poster)
                })
            }
            voiceovers.put(JSONObject().apply {
                put("name", v.name)
                put("episodes", episodes)
            })
        }
        arr.put(JSONObject().apply {
            put("number", s.number)
            put("voiceovers", voiceovers)
        })
    }
    return arr.toString()
}

fun deserializeSeasons(json: String): List<Season> {
    return try {
        val arr = JSONArray(json)
        val result = mutableListOf<Season>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val voiceovers = mutableListOf<Voiceover>()
            val vArr = obj.getJSONArray("voiceovers")
            for (j in 0 until vArr.length()) {
                val vObj = vArr.getJSONObject(j)
                val episodes = mutableListOf<Episode>()
                val eArr = vObj.getJSONArray("episodes")
                for (k in 0 until eArr.length()) {
                    val eObj = eArr.getJSONObject(k)
                    episodes.add(Episode(
                        number = eObj.getInt("number"),
                        title = eObj.getString("title"),
                        url = eObj.getString("url"),
                        subtitles = if (eObj.isNull("subtitles")) null else eObj.getString("subtitles"),
                        poster = eObj.optString("poster", "")
                    ))
                }
                voiceovers.add(Voiceover(
                    name = vObj.getString("name"),
                    episodes = episodes
                ))
            }
            result.add(Season(
                number = obj.getInt("number"),
                voiceovers = voiceovers
            ))
        }
        result
    } catch (_: Exception) {
        emptyList()
    }
}
