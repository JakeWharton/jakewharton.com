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

internal fun String.splitAroundLast(delimiter: Char): Pair<String, String> {
	return splitAround(lastIndexOf(delimiter))
}
