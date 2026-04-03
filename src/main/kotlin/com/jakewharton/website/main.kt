@file:JvmName("Main")

package com.jakewharton.website

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.time.Clock
import kotlin.system.exitProcess
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension

fun main(vararg args: String) {
	val systemFs = FileSystems.getDefault()!!
	val systemClock = Clock.systemUTC()!!
	MainCommand(systemFs, systemClock).main(args)
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

	private val validateLinks by option().flag()

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
		println("${site.podcasts.size} podcasts")
		println("${site.posts.size} posts")
		println("${site.presentations.size} presentations")

		println("\nValidating!\n")
		val problems = SiteValidator(validateLinks).use { it.validate(site) }
		if (problems.isNotEmpty()) {
			for (problem in problems.sortedBy { it.level }) {
				System.err.println(problem::class.simpleName + ": " + problem.message)
			}
			if (problems.any { it.level == ValidationProblem.Level.Error }) {
				exitProcess(1)
			}
			System.err.flush()
		} else {
			println("All good!")
		}

		println("\nRendering!\n")
		SiteRenderer(clock, mdExtensions).render(site, rootDir, outputDir)
	}
}
