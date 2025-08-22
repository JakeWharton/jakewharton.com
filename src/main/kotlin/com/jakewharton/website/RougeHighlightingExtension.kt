package com.jakewharton.website

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.html.HtmlRenderer.HtmlRendererExtension

internal object RougeHighlightingExtension : HtmlRendererExtension {
	override fun extend(rendererBuilder: HtmlRenderer.Builder) {
		rendererBuilder.nodeRendererFactory(::RougeHighlightingNodeRenderer)
	}
}

private class RougeHighlightingNodeRenderer(
	private val context: HtmlNodeRendererContext,
) : NodeRenderer {
	override fun getNodeTypes(): Set<Class<out Node>> {
		return setOf(FencedCodeBlock::class.java)
	}

	override fun render(node: Node) {
		val fencedCodeBlock = node as FencedCodeBlock
		val html = context.writer
		html.line()

		val language = fencedCodeBlock.info
		if (language.isNotEmpty()) {
			val process =
				ProcessBuilder("rougify", "highlight", "-f", "html", "-l", language, "-i", "-")
					.start()
			process.outputWriter().use { it.write(fencedCodeBlock.literal) }
			val highlighted = process.inputReader().readText()

			html.tag("div", mapOf("class" to "language-$language highlighter-rouge"))
			html.tag("div", mapOf("class" to "highlight"))
			html.tag("pre", mapOf("class" to "highlight"))
			html.tag("code")
			html.raw(highlighted)
		} else {
			html.tag("div", mapOf("class" to "highlighter-rouge"))
			html.tag("div", mapOf("class" to "highlight"))
			html.tag("pre", mapOf("class" to "highlight"))
			html.tag("code")
			html.text(fencedCodeBlock.literal)
		}
		html.tag("/code")
		html.tag("/pre")
		html.tag("/div")
		html.tag("/div")
		html.line()
	}
}
