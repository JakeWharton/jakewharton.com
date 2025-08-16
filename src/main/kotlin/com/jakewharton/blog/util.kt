package com.jakewharton.blog

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

internal fun Path.walk(
    maxDepth: Int = Int.MAX_VALUE,
    vararg option: FileVisitOption
): Sequence<Path> {
  return Files.walk(this, maxDepth, *option).asSequence()
}

internal fun String.splitAround(index: Int): Pair<String, String> {
	return take(index) to substring(index + 1)
}

internal fun <T> Iterable<T>.checkEmptyOrSingleOrThrow(message: () -> String): T? {
	val iterator = iterator()
	if (!iterator.hasNext()) return null
	val item = iterator.next()
	if (!iterator.hasNext()) return item
	error(message())
}
