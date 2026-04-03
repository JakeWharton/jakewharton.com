package com.jakewharton.website

import java.nio.file.Path
import java.time.LocalDate
import okhttp3.HttpUrl
import org.commonmark.node.Node

internal data class Site(
	val podcasts: List<PodcastAppearance>,
	val posts: List<BlogPost>,
	val presentations: List<Presentation>,
)

internal data class BlogPost(
	val path: Path,
	val date: LocalDate,
	val slug: String,
	val title: String,
	val externalLink: ExternalLink?,
	val tags: Set<String>,
	val content: Node,
) {
	data class ExternalLink(
		val blogName: String,
		val url: HttpUrl,
	)
}

internal data class PodcastAppearance(
	val path: Path,
	val date: LocalDate,
	val slug: String,
	val name: String,
	val episodeTitle: String,
	val episodeLink: HttpUrl,
)

internal data class Presentation(
	val path: Path,
	val date: LocalDate,
	val slug: String,
	val eventName: String,
	val eventLocation: String,
	/** Presentations without an event link will not be rendered. */
	val eventLink: HttpUrl?,
	val title: String,
	val slides: Slides?,
	val video: Video?,
	val abstract: Node,
) {
	sealed interface Slides {
		data class SpeakerDeck(val id: String) : Slides
	}
	sealed interface Video {
		data class Youtube(val id: String) : Video
		data class Vimeo(val id: Int) : Video
		data class Url(val url: HttpUrl) : Video
	}
}
