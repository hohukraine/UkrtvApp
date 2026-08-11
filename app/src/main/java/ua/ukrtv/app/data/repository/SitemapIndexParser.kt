package ua.ukrtv.app.data.repository

import java.util.regex.Pattern

/**
 * Builds the same [SeriesIndexData] the build-time script produces (scripts/sync_catalog.py
 * --series-index), but at runtime from the live UAFIX sitemap so new episodes appear without a
 * rebuild. A single sitemap request yields the complete episode structure of the whole catalog.
 */
object SitemapIndexParser {

    private val EPISODE_URL = Pattern.compile(
        "^https://uafix\\.net/serials/([^/]+)/season-(\\d+)-episode-(\\d+)/$"
    )
    private val VARIANT_URL = Pattern.compile(
        "^https://uafix\\.net/serials/([^/]+)/season-(\\d+)-episode-(\\d+)/v\\d+/$"
    )

    fun parse(xml: String, updatedAt: Long = System.currentTimeMillis(), version: Int = 1): SeriesIndexData {
        val uaflix = LinkedHashMap<String, LinkedHashMap<String, LinkedHashSet<Int>>>()
        val variants = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>()

        val loc = Pattern.compile("<loc>(.*?)</loc>", Pattern.DOTALL).matcher(xml)
        while (loc.find()) {
            val url = loc.group(1).trim()
            val base = EPISODE_URL.matcher(url)
            if (base.matches()) {
                val slug = base.group(1)
                val season = base.group(2)?.toIntOrNull()?.toString() ?: continue
                val episode = base.group(3)?.toIntOrNull() ?: continue
                if (episode > 0) {
                    uaflix.getOrPut(slug) { LinkedHashMap() }
                        .getOrPut(season) { LinkedHashSet() }
                        .add(episode)
                }
                continue
            }
            val variant = VARIANT_URL.matcher(url)
            if (variant.matches()) {
                val slug = variant.group(1)
                val season = variant.group(2)?.toIntOrNull()?.toString() ?: continue
                val episode = variant.group(3)?.toIntOrNull() ?: continue
                if (episode > 0) {
                    uaflix.getOrPut(slug) { LinkedHashMap() }
                        .getOrPut(season) { LinkedHashSet() }
                        .add(episode)
                    variants.getOrPut(slug) { LinkedHashMap() }
                        .getOrPut(season) { LinkedHashMap() }
                        .putIfAbsent(episode.toString(), url)
                }
            }
        }

        return SeriesIndexData(
            version = version,
            updatedAt = updatedAt,
            uaflix = uaflix.mapValues { (_, seasons) ->
                seasons.mapValues { (_, eps) -> eps.sorted() }
            },
            uaflixVariants = variants.mapValues { (_, seasons) ->
                seasons.mapValues { (_, eps) -> eps }
            }
        )
    }
}
