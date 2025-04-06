package eu.kanade.tachiyomi.animeextension.en.animekai

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class AnimeKai : ParsedAnimeHttpSource() {
    override fun popularAnimeNextPageSelector(): String? = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector(): String? = "a.next.page-numbers"
    override fun searchAnimeNextPageSelector(): String? = "a.next.page-numbers"
    override val name = "AnimeKai"
    override val baseUrl = "https://animekai.to"
    override val lang = "en"
    override val supportsLatest = true

    // Note: 'cloudflareClient' is deprecated; the regular client handles Cloudflare by default.
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Helper GET function if not provided by the base class
    private fun GET(url: String): Request {
        return Request.Builder().url(url).get().build()
    }

    // --- Popular Anime ---

    override fun popularAnimeSelector(): String = "div.loop-anime"

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/popular?page=$page")

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("h3").text()
        thumbnail_url = element.select("img").attr("data-src")
    }

    override fun popularAnimeNextPageSelector(): String? = null

    // --- Latest Updates ---
    override fun latestUpdatesSelector(): String = "div.loop-anime"

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest?page=$page")

    override fun latestUpdatesFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // --- Search Anime ---
    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/search?keyword=$query&page=$page")

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = null

    // --- Anime Details ---
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.select("h1.title").text()
        genre = document.select("div.genres a").joinToString { it.text() }
        description = document.select("div.description").text()
        status = when (document.select("div.status").text()) {
            "Ongoing" -> SAnime.ONGOING
            "Completed" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
        thumbnail_url = document.select("div.poster img").attr("data-src")
    }

    // --- Episodes ---
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodeListSelector()).map { episodeFromElement(it) }
    }

    override fun episodeListSelector(): String = "div.episodes a"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val epNum = element.attr("data-number").toFloatOrNull() ?: 0F
        name = "Episode $epNum"
        episode_number = epNum
        date_upload = parseDate(element.select("span.date").text())
        url = element.attr("href")
    }
    private fun parseDate(dateStr: String): Long {
        return try {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    // --- Videos ---
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return document.select(videoListSelector()).map { videoFromElement(it) }
    }

    override fun videoListSelector(): String = "source"

    override fun videoFromElement(element: Element): Video {
        val quality = element.attr("size").takeIf { it.isNotEmpty() } ?: "Unknown"
        return Video(element.attr("src"), quality, element.attr("src"))
    }

    override fun videoUrlParse(document: Document): String =
        document.select("source").attr("src")
}

// Extension function to convert an OkHttp Response to a Jsoup Document
fun Response.asJsoup(): Document {
    val bodyString = this.body?.string() ?: ""
    return Jsoup.parse(bodyString)
}
