---
event: KotlinConf
location: Munich, Germany
homepage: https://kotlinconf.com/

title: "Talking to terminals (and how they talk back)"
speakerdeck: fde74abc3525461291d2d09284d913fb
youtube: QYlzKV0Oe1A
---

Have you ever wondered how Gradle renders multiple progress bars below the scrolling output? Or how a Linux installer renders full screen in the terminal? What about how curl somehow displays a progress bar despite having its output redirected to a file?

This talk will explore how command-line applications communicate with the terminal. From basic elements such as colors and the terminal size to advanced behavior like frame synchronization, images, and keyboard events.

Our journey will be in Kotlin, of course, and cover talking to OS-specific APIs, the challenges of JVM vs. Kotlin/Native, and finally some reusable libraries to make your life easier.

Modern terminals are powerful systems which can do a lot more than just display text. Let's use them to their full potential!
