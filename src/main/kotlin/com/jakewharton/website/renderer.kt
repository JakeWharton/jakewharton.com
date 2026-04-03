package com.jakewharton.website

import com.jakewharton.website.Presentation.Slides.SpeakerDeck
import com.jakewharton.website.Presentation.Video.Url
import com.jakewharton.website.Presentation.Video.Vimeo
import com.jakewharton.website.Presentation.Video.Youtube
import java.nio.file.Path
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField.HOUR_OF_DAY
import java.time.temporal.ChronoField.MINUTE_OF_HOUR
import java.time.temporal.ChronoField.SECOND_OF_MINUTE
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import liqp.Template
import liqp.TemplateContext
import liqp.TemplateParser
import liqp.filters.Filter
import liqp.parser.Flavor
import org.apache.commons.text.StringEscapeUtils
import org.commonmark.Extension
import org.commonmark.renderer.html.HtmlRenderer

internal class SiteRenderer(
	private val clock: Clock,
	markdownExtensions: List<Extension>,
) {
	private val mdRenderer = HtmlRenderer.builder()
		.extensions(markdownExtensions)
		.build()

	private val liquidParser = TemplateParser.Builder()
		.withFlavor(Flavor.JEKYLL)
		.withStrictVariables(true)
		.withFilter(
			object : Filter("date_to_xmlschema") {
				override fun apply(value: Any?, context: TemplateContext, vararg params: Any?): Any {
					val string = super.asString(value, context)
					val dateTime = OffsetDateTime.parse(string, dateTimeFormat)
					return dateTime.format(ISO_OFFSET_DATE_TIME)
				}
			},
		)
		.withFilter(
			object : Filter("xml_escape") {
				override fun apply(value: Any?, context: TemplateContext, vararg params: Any?): Any {
					val string = super.asString(value, context)
					return StringEscapeUtils.ESCAPE_XML11.translate(string)
				}
			},
		)
		.build()

	@OptIn(ExperimentalPathApi::class)
	fun render(site: Site, rootDir: Path, outputDir: Path) {
		val podcasts = site.podcasts.associateWith { it.toData() }
		val posts = site.posts.associateWith { it.toData() }
		val presentations = site.presentations.associateWith { it.toData() }

		val siteData = mapOf(
			"url" to "https://jakewharton.com",
			"time" to OffsetDateTime.now(clock).format(dateTimeFormat),
			"podcasts" to podcasts.values,
			"posts" to posts.values,
			"presentations" to presentations.values,
		)

		val layoutsDir = rootDir.resolve("layouts")
		val defaultTemplate = liquidParser.parse(layoutsDir.resolve("default.html"))
		val postTemplate = liquidParser.parse(layoutsDir.resolve("post.html"))
		val presentationTemplate = liquidParser.parse(layoutsDir.resolve("presentation.html"))

		outputDir.deleteRecursively()

		copyRecursively(rootDir, rootDir.resolve("static"), outputDir)
		copyRecursively(rootDir, rootDir.resolve("_redirects"), outputDir)

		renderHtml(rootDir.resolve("index.html"), null, null, siteData, outputDir.resolve("index.html"))
		renderHtml(rootDir.resolve("atom.xml"), null, null, siteData, outputDir.resolve("atom.xml"))

		renderHtml(
			rootDir.resolve("blog.html"),
			defaultTemplate,
			"Posts",
			siteData,
			outputDir.resolve("blog/index.html"),
		)
		renderHtml(
			rootDir.resolve("résumé.html"),
			defaultTemplate,
			"Résumé",
			siteData,
			outputDir.resolve("résumé/index.html"),
		)
		renderHtml(
			rootDir.resolve("podcasts.html"),
			defaultTemplate,
			"Podcasts",
			siteData,
			outputDir.resolve("podcasts/index.html"),
		)
		renderHtml(
			rootDir.resolve("presentations.html"),
			defaultTemplate,
			"Presentations",
			siteData,
			outputDir.resolve("presentations/index.html"),
		)

		for ((post, data) in posts) {
			if (post.externalLink == null) {
				renderPage(outputDir, data, postTemplate, siteData)
			}
		}

		for ((presentation, data) in presentations) {
			if (presentation.eventLink != null) {
				renderPage(outputDir, data, presentationTemplate, siteData)
			}
		}
	}

	private fun PodcastAppearance.toData() = buildMap {
		put("name", name)
		put("title", episodeTitle)
		put("link", episodeLink.toString())
		put("date", date
			.atStartOfDay(ZoneOffset.UTC)
			.toOffsetDateTime()
			.format(dateTimeFormat))
		put("url", "/$slug/")
	}

	private fun Presentation.toData(): Map<String, Any> = buildMap {
		put("title", title)
		put("url", "/$slug/")
		put("date", date
			.atStartOfDay(ZoneOffset.UTC)
			.toOffsetDateTime()
			.format(dateTimeFormat))
		put("event", eventName)
		put("location", eventLocation)
		eventLink?.let { put("homepage", it) }
		when (slides) {
			is SpeakerDeck -> put("speakerdeck", slides.id)
			null -> {}
		}
		when (video) {
			is Url -> put("video", video.url.toString())
			is Vimeo -> put("vimeo", video.id)
			is Youtube -> put("youtube", video.id)
			null -> {}
		}
		put("content", mdRenderer.render(abstract))
	}

	fun BlogPost.toData(): Map<String, Any> = buildMap {
		put("title", title)
		put("id", "/$slug")
		put("url", "/$slug/")
		put("date", date
			.atStartOfDay(ZoneOffset.UTC)
			.toOffsetDateTime()
			.format(dateTimeFormat))
		if (externalLink != null) {
			put("external", true)
			put("blog", externalLink.blogName)
			put("blog_link", externalLink.url.toString())
		}
		put("content", mdRenderer.render(content))
	}

	private fun renderPage(
		outputDir: Path,
		pageData: Map<String, Any?>,
		template: Template?,
		siteData: Map<String, Any>,
	) {
		print("Rendering ${pageData["url"]}…")

		val content = pageData.getValue("content") as String
		val rendered =
			if (template == null) {
				content
			} else {
				template.render(
					mapOf(
						"content" to content,
						"page" to pageData,
						"site" to siteData,
					),
				)
			}

		val urlPath = pageData["url"] as String
		val outputFile = outputDir.resolve(urlPathToRelativeFilePath(urlPath))
		outputFile.parent.createDirectories()
		outputFile.writeText(rendered)

		println(" Done")
	}

	private fun renderHtml(
		htmlFile: Path,
		template: Template?,
		title: String?,
		siteData: Map<String, Any>,
		outputFile: Path,
	) {
		print("Rendering $htmlFile to HTML…")

		val content = htmlFile.readText()

		val intermediateData = mapOf(
			"site" to siteData,
		)
		val intermediate = liquidParser.parse(content)
			.render(intermediateData)

		val rendered =
			if (template == null) {
				intermediate
			} else {
				template.render(
					mapOf(
						"content" to intermediate,
						"page" to mapOf("title" to title),
						"site" to siteData,
					),
				)
			}

		outputFile.parent.createDirectories()
		outputFile.writeText(rendered)
		println(" Done")
	}
}

private val dateTimeFormat = DateTimeFormatterBuilder()
	.parseCaseInsensitive()
	.append(ISO_LOCAL_DATE)
	.appendLiteral(' ')
	.appendValue(HOUR_OF_DAY, 2)
	.appendLiteral(':')
	.appendValue(MINUTE_OF_HOUR, 2)
	.appendLiteral(':')
	.appendValue(SECOND_OF_MINUTE, 2)
	.appendLiteral(' ')
	.appendOffset("+HHMM", "Z")
	.toFormatter()

private fun urlPathToRelativeFilePath(path: String): String {
	return path.trimStart('/') + if (path.endsWith("/")) "index.html" else ".html"
}

private fun copyRecursively(rootDir: Path, source: Path, destination: Path) {
	source.walk().forEach { sourcePath ->
		val destPath = destination.resolve(sourcePath.relativeTo(rootDir).toString())
		if (sourcePath.isDirectory()) {
			destPath.createDirectories()
		} else {
			print("Copying $sourcePath to $destPath…")
			sourcePath.copyTo(destPath, overwrite = true)
			println(" Done")
		}
	}
}
