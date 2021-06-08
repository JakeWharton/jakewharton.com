---
title: "Gradle dependency license validation"
layout: post

external: true
blog: Cash App Code Blog
blog_link: https://code.cash.app/gradle-dependency-license-validation

categories: post
tags:
- Android
---

Six years ago we added a screen to the Android Cash App to display the open source libraries we use and their licenses. This screen had to be updated manually, which meant it occasionally was missing a library or displayed one no longer in use.

<style type="text/css">
video {
  border: 1px solid #ddd;
  display: block;
  margin-left: auto;
  margin-right: auto;
}
/*@media only screen and (min-width: 600px) {
  video {
    float: right;
    margin-left: 20px;
    margin-bottom: 10px;
    margin-right: 0;
  }
}
@media only screen and (min-width: 1000px) {
  video {
    position: relative;
    left: 125px;
    margin-left: -95px;
  }
}*/
</style>
<video autoplay muted loop height="540">
<source src="/static/post-image/casha-oss.mp4" type="video/mp4">
</video>

There are existing Gradle plugins for aggregating license info from your dependencies, but they usually produce an HTML page for display in a WebView which does not meet our UI requirements. In a recent hack week I sought to solve this in a way where we could retain the same UI but automate discovering the libraries and their licenses.

However, once you start tugging on this rope, other interesting problems start to fall out:

 1. We use short names like "Apache 2", but the raw license data uses long names ("The Apache Software License, Version 2.0") and URLs (http://www.apache.org/licenses/LICENSE-2.0.txt). How do we map between them?
 2. The license name and URL of each library will vary even when they refer to the same license (http://www.apache.org/licenses/LICENSE-2.0.txt vs. http://www.apache.org/licenses/LICENSE-2.0). How do we normalize these?
 3. As we're now parsing the license information of our dependencies, how do we ensure only accepted licenses are in use?

The [SPDX License List](https://spdx.org/licenses/) gives us the tools to solve both #1 and #2. By using its list of standard license URLs and adding additional variants from the wild, each license can be normalized to a SPDX identifier. This identifier is short (such as "Apache-2.0") and very similar to what we're already displaying.

With each license now mapped to a SPDX identifier, solving #3 is as simple as creating an allow-list of identifiers and failing the build if a disallowed license shows up. 

Finally, let's not forget our original goal was automating the data behind the open source screen. The normalized license data can be serialized as JSON and either be bundled directly or further manipulated before displaying.

All of this functionality is available in a new Gradle plugin called [Licensee](https://github.com/cashapp/licensee). Despite being driven by the needs of our Android app, the plugin should work for any Gradle-based project. And as its documentation details, there is support for a bunch of edge cases like internal dependencies, commercial SDKs, and non-standard licenses that show up in real-world projects.
