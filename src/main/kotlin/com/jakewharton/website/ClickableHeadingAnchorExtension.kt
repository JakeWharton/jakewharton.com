package com.jakewharton.website

import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.html.HtmlRenderer.HtmlRendererExtension

internal class ClickableHeadingAnchorExtension(
	private val supportedHeadingLevels: Set<Int> = setOf(1, 2, 3, 4, 5, 6),
) : HtmlRendererExtension {
	override fun extend(rendererBuilder: HtmlRenderer.Builder) {
		rendererBuilder.nodeRendererFactory { context ->
			ClickableHeadingAnchorNodeRenderer(context, supportedHeadingLevels)
		}
	}
}

private class ClickableHeadingAnchorNodeRenderer(
	private val context: HtmlNodeRendererContext,
	private val supportedHeadingLevels: Set<Int>,
) : NodeRenderer {
	override fun getNodeTypes() = setOf(Heading::class.java)

	override fun render(node: Node) {
		val heading = node as Heading
		val tag = "h${heading.level}"

		val attributes = context.extendAttributes(heading, tag, emptyMap())
		val id = attributes["id"] ?: error("No 'id' attribute created for heading. Is the heading anchor extension installed?")

		val html = context.writer

		html.line()
		html.tag(tag, attributes)

		var renderNode = heading.firstChild
		while (true) {
			context.render(renderNode)
			if (renderNode === heading.lastChild) {
				break
			}
			renderNode = renderNode.next
		}

		if (heading.level in supportedHeadingLevels) {
			html.tag(
				"a",
				mapOf(
					"class" to "anchor",
					"href" to "#$id",
				),
			)
			html.tag("/a")
		}

		html.tag("/$tag")
		html.line()
	}
}
