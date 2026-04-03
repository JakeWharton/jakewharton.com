package com.jakewharton.website

import com.jakewharton.website.Presentation.Slides.SpeakerDeck
import com.jakewharton.website.Presentation.Video.Url
import com.jakewharton.website.Presentation.Video.Vimeo
import com.jakewharton.website.Presentation.Video.Youtube
import com.jakewharton.website.ValidationProblem.MalformedLink
import com.jakewharton.website.ValidationProblem.UnreachableLink
import java.io.Closeable
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Link

sealed interface ValidationProblem {
	enum class Level {
		Error,
		Warning,
	}

	val message: String
	val level: Level

	data class MalformedLink(override val message: String) : ValidationProblem {
		override val level get() = Level.Error
	}
	data class UnreachableLink(override val message: String) : ValidationProblem {
		override val level get() = Level.Warning
	}
}

internal class SiteValidator(
	private val validateLinks: Boolean,
) : Closeable {
	private val httpClient = OkHttpClient.Builder()
		.build()

	fun validate(site: Site): List<ValidationProblem> = buildList {
		for (podcast in site.podcasts) {
			checkUrl(podcast.episodeLink) { failure ->
				this += UnreachableLink("Podcast '${podcast.slug}' episode link: $failure")
			}
		}

		for (post in site.posts) {
			if (post.externalLink != null) {
				checkUrl(post.externalLink.url) { failure ->
					this += UnreachableLink("Post '${post.slug}' external link: $failure")
				}
			}
			post.content.accept(object : AbstractVisitor() {
				override fun visit(link: Link) {
					super.visit(link)

					val destination = link.destination
					if (destination.startsWith("/")) {
						// TODO validate relative link
					} else if (destination.startsWith("#")) {
						// TODO validate anchor link
					} else if (destination.startsWith("mailto:")) {
						// TODO validate email address?
					} else {
						val url = destination.toHttpUrlOrNull()
						if (url == null) {
							val linkText = link.firstChild.toString()
							this@buildList += MalformedLink("Post '${post.slug}' link '$linkText' malformed/invalid: $destination")
						} else {
							checkUrl(url) { failure ->
								this@buildList += UnreachableLink("Post '${post.slug}' link '$url': $failure")
							}
						}
					}
				}
			})
		}

		for (presentation in site.presentations) {
			if (presentation.eventLink != null) {
				checkUrl(presentation.eventLink) { failure ->
					this += UnreachableLink("Presentation '${presentation.slug}' event link: $failure")
				}
			}
			if (presentation.video != null) {
				val url = when (presentation.video) {
					is Url -> presentation.video.url
					is Vimeo -> "https://vimeo.com/api/v2/video/${presentation.video.id}.xml".toHttpUrl()
					is Youtube -> "http://img.youtube.com/vi/${presentation.video.id}/maxresdefault.jpg".toHttpUrl()
				}
				checkUrl(url) { failure ->
					this += UnreachableLink("Presentation '${presentation.slug}' video: $failure")
				}
			}
			if (presentation.slides != null) {
				val url = when (presentation.slides) {
					is SpeakerDeck -> "https://speakerd.s3.amazonaws.com/presentations/${presentation.slides.id}/slide_0.jpg".toHttpUrl()
				}
				checkUrl(url) { failure ->
					this += UnreachableLink("Presentation '${presentation.slug}' slides: $failure")
				}
			}
		}
	}

	private fun checkUrl(url: HttpUrl, onFailure: (failure: String) -> Unit) {
		if (!validateLinks) return

		val response = try {
			httpClient.newCall(Request(url, method = "HEAD")).execute()
		} catch (e: IOException) {
			onFailure(e.message!!)
			return
		}
		response.use {
			if (!it.isSuccessful) {
				onFailure("HTTP ${response.code}")
			}
		}
	}

	override fun close() {
		httpClient.dispatcher.executorService.shutdown()
		httpClient.connectionPool.evictAll()
	}
}
