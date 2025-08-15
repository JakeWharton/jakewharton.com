@file:JvmName("Main")

package com.jakewharton.blog

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.defaultLazy
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField.HOUR_OF_DAY
import java.time.temporal.ChronoField.MINUTE_OF_HOUR
import java.time.temporal.ChronoField.SECOND_OF_MINUTE
import kotlin.collections.get
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
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.yaml.snakeyaml.Yaml

fun main(vararg args: String) {
	val systemFs = FileSystems.getDefault()!!
	val systemClock = Clock.systemUTC()!!
	MainCommand(systemFs, systemClock).main(args)
}

@OptIn(ExperimentalPathApi::class)
private class MainCommand(
	private val fs: FileSystem,
	private val clock: Clock,
) : CliktCommand(name = "jakewharton.com") {
	private val rootDir by argument("DIR")
		.path(mustExist = true, canBeFile = false, fileSystem = fs)
		.defaultLazy { fs.getPath(System.getProperty("user.dir")) }

	private val outputDir by argument("DIR")
		.path(canBeFile = false, fileSystem = fs)
		.defaultLazy { rootDir.resolve("out") }

	private val yaml = Yaml()

	private val mdExtensions = listOf(
		FootnotesExtension.create(),
		HeadingAnchorExtension.create(),
		ClickableHeadingAnchorExtension(
			supportedHeadingLevels = setOf(3, 4, 5, 6),
		),
		RougeHighlightingExtension,
		StrikethroughExtension.create(),
	)
	private val mdParser = Parser.Builder()
		.extensions(mdExtensions)
		.build()
	private val mdRenderer = HtmlRenderer.builder()
		.extensions(mdExtensions)
		.build()

	private val liquidParser = TemplateParser.Builder()
		.withFlavor(Flavor.JEKYLL)
		.withStrictVariables(true)
		.withFilter(
			object : Filter("date_to_xmlschema") {
				override fun apply(value: Any, context: TemplateContext, vararg params: Any?): Any {
					val string = super.asString(value, context)
					val dateTime = OffsetDateTime.parse(string, dateTimeFormat)
					return dateTime.format(ISO_OFFSET_DATE_TIME)
				}
			},
		)
		.withFilter(
			object : Filter("xml_escape") {
				override fun apply(value: Any, context: TemplateContext, vararg params: Any?): Any {
					val string = super.asString(value, context)
					return StringEscapeUtils.ESCAPE_XML11.translate(string)
				}
			},
		)
		.build()

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

	override fun run() {
		val templates =
			buildMap<String, Template> {
				val layoutsDir = rootDir.resolve("layouts")
				layoutsDir
					.walk(maxDepth = 1)
					.drop(1) // Starts with self.
					.forEach {
						val name = it.fileName.toString().substringBeforeLast(".")
						val template = liquidParser.parse(it.toFile())
						put(name, template)
					}
			}

		val podcasts = rootDir.resolve("podcasts")
			.asDatedCollection()
			.map(::parsePodcast)
			.toList()

		val postDir = rootDir.resolve("posts")
		val presentationDir = rootDir.resolve("presentations")

		val posts = parseCollection(postDir)
		val presentations = parseCollection(presentationDir)

		val siteData = mapOf(
			"url" to "https://jakewharton.com",
			"time" to OffsetDateTime.now(clock).format(dateTimeFormat),
			"podcasts" to podcasts.sortedByDescending { it["date"] as String },
			"posts" to posts.sortedByDescending { it["date"] as String },
			"presentations" to presentations.sortedByDescending { it["date"] as String },
		)

		outputDir.deleteRecursively()

		copyRecursively(rootDir, rootDir.resolve("static"), outputDir)
		copyRecursively(rootDir, rootDir.resolve("_redirects"), outputDir)

		renderHtml(rootDir.resolve("index.html"), templates, siteData, outputDir.resolve("index.html"))
		renderHtml(
			rootDir.resolve("blog.html"),
			templates,
			siteData,
			outputDir.resolve("blog/index.html"),
		)
		renderHtml(
			rootDir.resolve("presentations.html"),
			templates,
			siteData,
			outputDir.resolve("presentations/index.html"),
		)
		renderHtml(
			rootDir.resolve("podcasts.html"),
			templates,
			siteData,
			outputDir.resolve("podcasts/index.html"),
		)

		renderHtml(rootDir.resolve("atom.xml"), templates, siteData, outputDir.resolve("atom.xml"))

		for (post in posts) {
			renderPage(outputDir, post, templates, siteData)
		}
		for (presentation in presentations) {
			renderPage(outputDir, presentation, templates, siteData)
		}
	}

	private data class DatedEntry(
		val path: Path,
		val date: LocalDate,
		val slug: String,
		val frontMatter: Map<String, Any?>,
		val content: Node,
	)

	private fun Path.asDatedCollection(): Sequence<DatedEntry> {
		return walk(maxDepth = 1)
			.drop(1) // Starts with self.
			.map { file ->
				val (name, extension) = file.fileName.toString().splitAroundLast('.')
				check(extension == "md") { "Expected .md, found: $file" }

				val (rawDate, slug) = name.splitAround(10)
				val date = LocalDate.parse(rawDate)

				val (rawFrontMatter, rawMarkdown) = file.readText().splitFrontMatter()
				val frontMatter = (yaml.load(rawFrontMatter) as Map<String, Any?>)
				val markdown = mdParser.parse(rawMarkdown)

				DatedEntry(
					path = this,
					date = date,
					slug = slug,
					frontMatter = frontMatter,
					content = markdown,
				)
			}
	}

	private fun parsePodcast(entry: DatedEntry): Map<String, Any?> {
		val frontMatter = entry.frontMatter.toMutableMap()
		return buildMap {
			val title = frontMatter.remove("title") ?: error("Missing title: ${entry.path}")
			val name = frontMatter.remove("name") ?: error("Missing name: ${entry.path}")
			val link = frontMatter.remove("link") ?: error("Missing link: ${entry.path}")
			checkFrontMatterIsEmpty(frontMatter, entry)

			put("title", title)
			put("name", name)
			put("link", link) // TODO validate 200

			put("content", mdRenderer.render(entry.content))

			val slug = entry.slug
			put("url", "/$slug/")
			put("id", "/$slug")

			put(
				"date",
				entry.date
					.atStartOfDay(ZoneOffset.UTC)
					.toOffsetDateTime()
					.format(dateTimeFormat),
			)
		}
	}

	private fun checkFrontMatterIsEmpty(
		frontMatter: Map<String, Any?>,
		entry: DatedEntry,
	) {
		check(frontMatter.isEmpty()) {
			buildString {
				appendLine("Unhandled front matter in ${entry.path}:")
				frontMatter.keys.joinTo(this, prefix = " - ", separator = "\n - ")
			}
		}
	}

	private fun parseCollection(
		collectionDirectory: Path,
	): List<Map<String, Any?>> {
		return collectionDirectory
			.walk(maxDepth = 1)
			.drop(1) // Starts with self.
			.toList()
			.sorted()
			.map {
				print("Parsing $it…")

				val name = it.fileName.toString().substringBeforeLast('.')

				val (rawFrontMatter, markdown) = it.readText().splitFrontMatter()
				val frontMatter = (yaml.load(rawFrontMatter) as Map<String, Any?>).toMutableMap()
				print(" read…")
				val node = mdParser.parse(markdown)
				print(" parsed…")
				val html = mdRenderer.render(node)
				print(" rendered…")

				val model = buildMap {
					val title = frontMatter.remove("title") ?: error("Missing title")
					put("title", title)

					val date = name.take(10)
					val slug = name.substring(11)

					put("url", "/$slug/")
					put("id", "/$slug")

					put(
						"date",
						LocalDate.parse(date)
							.atStartOfDay(ZoneOffset.UTC)
							.toOffsetDateTime()
							.format(dateTimeFormat),
					)
					put("content", html)
					put("layout", frontMatter.remove("layout"))

					consumeAndPutOptionalFrontMatter(frontMatter, "redirect_from")

					// Posts
					consumeAndPutOptionalFrontMatter(frontMatter, "external")
					consumeAndPutOptionalFrontMatter(frontMatter, "blog")
					consumeAndPutOptionalFrontMatter(frontMatter, "blog_link") // TODO validate URL 200s
					frontMatter.remove("categories") // TODO
					frontMatter.remove("tags") // TODO
					frontMatter.remove("lead") // TODO delete all of these
					frontMatter.remove("image") // TODO delete all of these

					// Presentations
					consumeAndPutOptionalFrontMatter(frontMatter, "event")
					consumeAndPutOptionalFrontMatter(frontMatter, "location")
					consumeAndPutOptionalFrontMatter(frontMatter, "listing") // TODO validate URL 200s
					consumeAndPutOptionalFrontMatter(frontMatter, "type")
					consumeAndPutOptionalFrontMatter(frontMatter, "nolink")
					consumeAndPutOptionalFrontMatter(frontMatter, "homepage") // TODO validate URL 200s
					consumeAndPutOptionalFrontMatter(frontMatter, "vimeo") // TODO validate URL 200s
					consumeAndPutOptionalFrontMatter(frontMatter, "youtube") // TODO validate URL 200s
					consumeAndPutOptionalFrontMatter(frontMatter, "speakerdeck") // TODO validate URL 200s
					consumeAndPutOptionalFrontMatter(frontMatter, "video") // TODO validate URL 200s
					frontMatter.remove("additional_presenters") // TODO handle this
				}

				if (frontMatter.isNotEmpty()) {
					throw IllegalStateException(
						buildString {
							appendLine("Unhandled front matter in $name:")
							frontMatter.keys.joinTo(this, prefix = " - ", separator = "\n - ")
						},
					)
				}

				println(" Done")

				model
			}
	}

	private fun renderPage(
		outputDir: Path,
		pageData: Map<String, Any?>,
		templates: Map<String, Template>,
		siteData: Map<String, Any>,
	) {
		print("Rendering ${pageData["url"]}…")

		val layout = pageData["layout"] as String?
		val content = pageData.getValue("content") as String
		val rendered =
			if (layout == null) {
				content
			} else {
				val template = templates[layout] ?: error("Unknown layout $layout")
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

		pageData["redirect_from"]?.let { redirectFrom ->
			for (url in redirectFrom as List<String>) {
				val redirectToUrl = siteData.getValue("url") as String + pageData.getValue("url") as String
				val redirectFile = outputDir.resolve(urlPathToRelativeFilePath(url))
				redirectFile.parent.createDirectories()
				redirectFile.writeText(
					"""
            |<!DOCTYPE html>
            |<html lang="en-US">
            |  <meta charset="utf-8">
            |  <title>Redirecting&hellip;</title>
            |  <link rel="canonical" href="$redirectToUrl">
            |  <script>location="$redirectToUrl"</script>
            |  <meta http-equiv="refresh" content="0; url=$redirectToUrl">
            |  <meta name="robots" content="noindex">
            |  <h1>Redirecting&hellip;</h1>
            |  <a href="$redirectToUrl">Click here if you are not redirected.</a>
            |</html>
            |""".trimMargin(),
				)
			}
		}
		println(" Done")
	}

	private fun renderHtml(
		htmlFile: Path,
		templates: Map<String, Template>,
		siteData: Map<String, Any>,
		outputFile: Path,
	) {
		print("Rendering $htmlFile to HTML…")

		val (rawFrontMatter, content) = htmlFile.readText().splitFrontMatter()
		val frontMatter = (yaml.load(rawFrontMatter) as Map<String, Any?>).toMutableMap()
		val layout = frontMatter.remove("layout")
		val title = frontMatter.remove("title")

		if (frontMatter.isNotEmpty()) {
			throw IllegalStateException(
				buildString {
					appendLine("Unhandled front matter in ${htmlFile.fileName}:")
					frontMatter.keys.joinTo(this, prefix = " - ", separator = "\n - ")
				},
			)
		}

		val intermediateData = mapOf(
			"site" to siteData,
		)

		val intermediate = liquidParser.parse(content)
			.render(intermediateData)

		val rendered =
			if (layout == null) {
				intermediate
			} else {
				val template = templates[layout] ?: error("Unknown layout $layout")
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

private fun String.splitFrontMatter(): Pair<String?, String> {
	if (startsWith("---\n")) {
		val second = indexOf("---\n", startIndex = 4)
		if (second != -1) {
			return substring(4, second) to substring(second + 4)
		}
	}
	return null to this
}

private fun urlPathToRelativeFilePath(path: String): String {
	return path.trimStart('/') + if (path.endsWith("/")) "index.html" else ".html"
}

private fun MutableMap<String, Any?>.consumeAndPutOptionalFrontMatter(
	frontMatter: MutableMap<String, Any?>,
	key: String,
) {
	frontMatter.remove(key)?.let { put(key, it) }
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
