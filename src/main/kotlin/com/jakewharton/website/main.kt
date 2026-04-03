@file:JvmName("Main")

package com.jakewharton.website

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.defaultLazy
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.time.Clock
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension

fun main(vararg args: String) {
	val systemFs = FileSystems.getDefault()!!
	val systemClock = Clock.systemUTC()!!
	MainCommand(systemFs, systemClock).main(args)
}

private enum class LinkValidationMode {
	Ignore,
	Warn,
	Error,
}

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

	private val linkValidation by option()
		.enum<LinkValidationMode>()
		.default(LinkValidationMode.Ignore)

	private val mdExtensions = listOf(
		FootnotesExtension.create(),
		HeadingAnchorExtension.create(),
		ClickableHeadingAnchorExtension(
			supportedHeadingLevels = setOf(3, 4, 5, 6),
		),
		RougeHighlightingExtension,
		StrikethroughExtension.create(),
		TablesExtension.create(),
	)

	override fun run() {
		println("Parsing!\n")
		val site = SiteParser(mdExtensions).parse(rootDir)

		if (linkValidation != LinkValidationMode.Ignore) {
			println("\nValidating!\n")

			val problems = SiteValidator().use { it.validate(site) }
			if (problems.isNotEmpty()) {
				for (problem in problems) {
					System.err.println(problem.message)
				}
				if (linkValidation == LinkValidationMode.Error) {
					throw IllegalStateException("${problems.size} problems found")
				}
				System.err.flush()
			}
		}

		println("\nRendering!\n")
		SiteRenderer(clock, mdExtensions).render(site, rootDir, outputDir)
	}
}
