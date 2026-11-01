package com.sfm.app.live

import com.sfm.app.data.Catalog
import com.sfm.app.data.Duration
import com.sfm.app.data.FeedKind
import com.sfm.app.data.FeedSource
import com.sfm.app.data.Net
import com.sfm.app.data.Repo
import com.sfm.app.data.Term
import com.sfm.app.data.TermType
import com.sfm.app.data.Video
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** End-to-end checks against the real site. Run with SFM_LIVE=1 (skipped otherwise). */
class RepoLiveTest {
    private lateinit var repo: Repo

    @Before
    fun setUp() {
        assumeTrue(System.getenv("SFM_LIVE") == "1")
        val net = Net(null)
        repo = Repo(net, Catalog(null, net))
    }

    private fun check(name: String, items: List<Video>, min: Int = 5) {
        val withUrl = items.count { it.videoUrl != null }
        val withThumb = items.count { it.thumb != null }
        println("$name: ${items.size} items, url=$withUrl thumb=$withThumb cats=${items.count { it.cats.isNotEmpty() }} views=${items.count { it.views != null }} first='${items.firstOrNull()?.title}'")
        assertTrue("$name: too few items", items.size >= min)
        assertEquals("$name: duplicate ids", items.size, items.map { it.id }.distinct().size)
        assertTrue("$name: missing video urls", withUrl >= items.size * 0.9)
        assertTrue("$name: missing thumbs", withThumb >= items.size * 0.9)
        assertTrue("$name: missing titles", items.all { it.title.isNotBlank() && it.link.startsWith("https://") })
    }

    @Test
    fun feeds() = runBlocking {
        val tifa = Term(338, "Tifa", "tifa", link = "https://sfmcompile.club/category/final-fantasy/tifa/", parent = 80, type = TermType.CHARACTER)
        val sound = Term(52, "sound", "sound", link = "https://sfmcompile.club/tag/sound/", type = TermType.TAG)
        for (kind in listOf(FeedKind.LATEST, FeedKind.TRENDING, FeedKind.HOT, FeedKind.TOP, FeedKind.LONG)) {
            val src = FeedSource(kind)
            val p1 = repo.feed(src, 1)
            check("$kind p1", p1.items)
            assertTrue("$kind hasMore", p1.hasMore)
            val p2 = repo.feed(src, 2)
            check("$kind p2", p2.items)
            assertTrue("$kind p2 must differ from p1 (filter kept across pages)", p2.items.map { it.id }.intersect(p1.items.map { it.id }.toSet()).size < p2.items.size / 2)
        }
        check("TERM_NEW tifa", repo.feed(FeedSource(FeedKind.TERM_NEW, term = tifa), 1).items)
        val top = repo.feed(FeedSource(FeedKind.TERM_TOP, term = tifa), 1).items
        check("TERM_TOP tifa", top)
        assertTrue("TERM_TOP sorted by views", (top.first().views ?: 0) >= (top.last().views ?: 0))
        check("TERM_NEW tag", repo.feed(FeedSource(FeedKind.TERM_NEW, term = sound), 1).items)
        check("SEARCH tifa", repo.feed(FeedSource(FeedKind.SEARCH, query = "tifa"), 1).items)
        check("SEARCH 1min+", repo.feed(FeedSource(FeedKind.SEARCH, query = "tifa", duration = Duration.M1), 1).items)
        check("SUBS", repo.feed(FeedSource(FeedKind.SUBS, terms = listOf(tifa, sound)), 1).items)
        val long = repo.feed(FeedSource(FeedKind.LATEST, duration = Duration.M2), 1).items
        check("LATEST 2min+", long)
        assertTrue("duration filter", long.all { (it.tagDurationSec ?: 0) >= 120 })
    }

    @Test
    fun singles() = runBlocking {
        val r = repo.random()
        println("random: ${r?.title} ${r?.videoUrl}")
        assertNotNull(r?.videoUrl)
        val byUrl = repo.byUrl("https://sfmcompile.club/tifa-lockhart-maid-service/")
        println("byUrl: ${byUrl?.title} ${byUrl?.videoUrl} thumb=${byUrl?.thumb} artist=${byUrl?.artist}")
        assertNotNull(byUrl?.videoUrl)
        val p = repo.post(byUrl!!.id)
        assertEquals(byUrl.id, p?.id)
        val tags = repo.tagsOf(p!!)
        println("tags: ${tags.map { it.name }}")
        assertTrue(tags.isNotEmpty())
        val size = repo.fileSize(p.videoUrl!!)
        println("size: $size")
        assertTrue((size ?: 0) > 100_000)
        repo.catalog.ensureAll()
        val d = repo.catalog.data.value
        println("catalog: games=${d.games.size} chars=${d.characters.size} artists=${d.artists.size} tags=${d.plainTags.size}")
        assertTrue(d.games.size > 20 && d.characters.size > 100 && d.artists.size > 100 && d.plainTags.size > 100)
        println("search 'tifa': ${repo.catalog.search("tifa").map { it.name }}; 'overwach' typo: ${repo.catalog.search("overwach").map { it.name }}")
        assertTrue(repo.catalog.search("tifa").any { it.name == "Tifa" })
    }
}
