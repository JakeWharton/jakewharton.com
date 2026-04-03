package com.jakewharton.website

import com.jakewharton.website.Presentation.Slides.SpeakerDeck
import com.jakewharton.website.Presentation.Video.Url
import com.jakewharton.website.Presentation.Video.Vimeo
import com.jakewharton.website.Presentation.Video.Youtube
import com.jakewharton.website.ValidationProblem.UnreachableLink
import java.io.Closeable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Link

sealed interface ValidationProblem {
	val message: String

	data class UnreachableLink(override val message: String) : ValidationProblem
}

internal class SiteValidator : Closeable {
	private val httpClient = OkHttpClient.Builder()
		.build()

	fun validate(site: Site): List<ValidationProblem> = buildList {
		for (podcast in site.podcasts) {
			if (!isValidUrl(podcast.episodeLink)) {
				this += UnreachableLink("Podcast '${podcast.slug}' episode link unreachable")
			}
		}

		for (post in site.posts) {
			if (post.externalLink != null) {
				if (!isValidUrl(post.externalLink.url)) {
					this += UnreachableLink("Post '${post.slug}' external link unreachable")
				}
			}
			post.content.accept(object : AbstractVisitor() {
				override fun visit(link: Link) {
					val destination = link.destination
					if (destination.startsWith("/")) {
						// TODO validate relative link
					} else {
						val url = destination.toHttpUrlOrNull()
						if (url == null) {
							this@buildList += UnreachableLink("Post '${post.slug}' link '${link.title}' malformed/invalid: $url")
						} else if (!isValidUrl(url)) {
							this@buildList += UnreachableLink("Post '${post.slug}' link '$url' unreachable")
						}
					}
				}
			})
		}

		for (presentation in site.presentations) {
			if (presentation.eventLink != null) {
				if (!isValidUrl(presentation.eventLink)) {
					this += UnreachableLink("Presentation '${presentation.slug}' event link unreachable")
				}
			}
			if (presentation.video != null) {
				val url = when (presentation.video) {
					is Url -> presentation.video.url
					is Vimeo -> "https://vimeo.com/api/v2/video/${presentation.video.id}.xml".toHttpUrl()
					is Youtube -> "http://img.youtube.com/vi/${presentation.video.id}/maxresdefault.jpg".toHttpUrl()
				}
				if (!isValidUrl(url)) {
					this += UnreachableLink("Presentation '${presentation.slug}' video unreachable")
				}
			}
			if (presentation.slides != null) {
				val url = when (presentation.slides) {
					is SpeakerDeck -> "https://speakerd.s3.amazonaws.com/presentations/${presentation.slides.id}/slide_0.jpg".toHttpUrl()
				}
				if (!isValidUrl(url)) {
					this += UnreachableLink("Presentation '${presentation.slug}' slides unreachable")
				}
			}
		}
	}

	private fun isValidUrl(url: HttpUrl): Boolean {
		return runCatching {
			httpClient.newCall(Request(url, method = "HEAD")).execute().use(Response::isSuccessful)
		}.getOrElse {
			false
		}
	}

	override fun close() {
		httpClient.dispatcher.executorService.shutdown()
		httpClient.connectionPool.evictAll()
	}
}
