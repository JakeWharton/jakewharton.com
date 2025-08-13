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
