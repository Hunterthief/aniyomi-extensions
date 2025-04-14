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

    override val name = "AnimeKai"
    override val baseUrl = "https://animekai.to"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Helper request function (renamed)
    private fun getRequest(url: String): Request {
        return Request.Builder().url(url).get().build()
    }

    override fun popularAnimeSelector(): String = "div.loop-anime"

    override fun popularAnimeRequest(page: Int): Request =
        getRequest("$baseUrl/popular?page=$page")

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("h3").text()
        thumbnail_url = element.select("img").attr("data-src")
    }

    override fun popularAnimeNextPageSelector(): String? = null

    override fun latestUpdatesSelector(): String = "div.loop-anime"

    override fun latestUpdatesRequest(page: Int): Request =
        getRequest("$baseUrl/latest?page=$page")

    override fun latestUpdatesFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        getRequest("$baseUrl/search?keyword=$query&page=$page")

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = null

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

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodeListSelector()).map { episodeFromElement(it) }
    }

    override fun episodeListSelector(): String = "div.episodes a"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        name = "Episode ${element.attr("data-number")}"
        episode_number = element.attr("data-number").toFloatOrNull() ?: 0f
        date_upload = System.currentTimeMillis()
        url = element.attr("href")
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        return document.select(videoListSelector()).map { videoFromElement(it) }
    }

    override fun videoListSelector(): String = "source"

    override fun videoFromElement(element: Element): Video =
        Video(element.attr("src"), "Direct Video", element.attr("src"))

    override fun videoUrlParse(document: Document): String =
        document.select("source").attr("src")
}

fun Response.asJsoup(): Document {
    val bodyString = this.body?.string() ?: ""
    return Jsoup.parse(bodyString)
}
