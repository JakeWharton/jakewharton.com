package com.jakewharton.blog

import java.nio.file.Path
import java.time.LocalDate
import okhttp3.HttpUrl
import org.commonmark.node.Node

data class BlogPost(
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
