package com.jakewharton.blog

import org.commonmark.renderer.html.HtmlRenderer
import org.redundent.kotlin.xml.Namespace
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.XmlVersion
import org.redundent.kotlin.xml.xml

fun renderAtom(
	mdRender: HtmlRenderer,
	posts: List<BlogPost>,
): String {
	return xml(
		root = "feed",
		version = XmlVersion.V10,
		encoding = "utf-8",
		namespace = Namespace("http://www.w3.org/2005/Atom"),
	) {
		"title" {
			-"Jake Wharton"
		}
		"link" {
			attribute("href", "https://jakewharton.com/atom.xml")
			attribute("rel", "self")
		}
		"link" {
			attribute("href", "https://jakewharton.com/")
		}
		"updated" {
			-"SUP"
		}
		"id" {
			-"https://jakewharton.com/"
		}
		"author" {
			"name" {
				-"Jake Wharton"
			}
		}

		for (post in posts) {
			"entry" {
				"title" {
					-post.title
				}
				"link" {
					attribute("href", post.externalLink?.url?.toString() ?: "https://jakewharton.com/${post.slug}/")
				}
				"updated" {
					-post.date.toString() // TODO
				}
				"id" {
					-"https://jakewharton.com/${post.slug}"
				}
				"content" {
					attribute("type", "html")
					if (post.externalLink != null) {
						-"This post was published externally on ${post.externalLink.blogName}. Read it at "
						"a" {
							attribute("href", post.externalLink.url.toString())
							post.externalLink.url.toString()
						}
						-"."
					} else {
						-mdRender.render(post.content)
					}
				}
			}
		}
	}.toString(
		PrintOptions(
			pretty = true,
			singleLineTextElements = true,
			indent = "  ",
		)
	)
}
