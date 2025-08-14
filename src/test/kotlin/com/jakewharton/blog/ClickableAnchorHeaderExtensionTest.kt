package com.jakewharton.blog

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.junit.Test

class ClickableAnchorHeaderExtensionTest {
	@Test fun simple() {
		val html = ClickableHeadingAnchorExtension().render(
			"""
			|# Hey
			|## What up
			|Some text
			|## More
			|Also text
			|""".trimMargin()
		)
		assertThat(html).isEqualTo(
			"""
			|<h1 id="hey">Hey<a class="anchor" href="#hey"></a></h1>
			|<h2 id="what-up">What up<a class="anchor" href="#what-up"></a></h2>
			|<p>Some text</p>
			|<h2 id="more">More<a class="anchor" href="#more"></a></h2>
			|<p>Also text</p>
			|""".trimMargin(),
		)
	}

	@Test fun levels() {
		val html = ClickableHeadingAnchorExtension(
			supportedHeadingLevels = setOf(1, 3, 5),
		).render(
			"""
			|# One
			|## Two
			|### Three
			|#### Four
			|##### Five
			|###### Six
			|""".trimMargin()
		)
		assertThat(html).isEqualTo(
			"""
			|<h1 id="one">One<a class="anchor" href="#one"></a></h1>
			|<h2 id="two">Two</h2>
			|<h3 id="three">Three<a class="anchor" href="#three"></a></h3>
			|<h4 id="four">Four</h4>
			|<h5 id="five">Five<a class="anchor" href="#five"></a></h5>
			|<h6 id="six">Six</h6>
			|""".trimMargin(),
		)
	}

	private fun ClickableHeadingAnchorExtension.render(markdown: String): String {
		val extensions = listOf(
			HeadingAnchorExtension.create(),
			this,
		)
		val parser = Parser.builder()
			.extensions(extensions)
			.build()
		val renderer = HtmlRenderer.builder()
			.extensions(extensions)
			.build()
		return renderer.render(parser.parse(markdown))
	}
}
