---
title: "Native UI and multiplatform Compose with Redwood"
layout: post

external: true
blog: Cash App Code Blog
blog_link: https://code.cash.app/native-ui-and-multiplatform-compose-with-redwood

categories: post
tags:
- Android
- Kotlin
---

[Redwood](https://github.com/cashapp/redwood) is Cash App’s take on multiplatform mobile client UI. Unlike many of the existing solutions to this problem, our values are slightly different:

1. Render using the native UI toolkit on each platform. Native UI is the best UI, and we want to ensure our engineers can continue to use all their skills when building UI components.
2. Retain the ability to reuse components from the rest of the application. The existing styles and custom controls used by the rest of the app are available without redefinition keeping a single source of truth.
3. Use a mobile language with great tooling. We want to tap into the existing skills of our engineers to build within this new system and not have to learn a new ecosystem.
4. Allow for incremental adoption in an existing app. This shouldn’t be an all-or-nothing framework but a library that you can use only where it’s needed.

We chose the [Kotlin programming language](https://kotlinlang.org/) because it can compile to Java bytecode, native (via LLVM), and Javascript. This supports use on Android, iOS, and the web using intrinsic execution on each platform.

On top of Kotlin we leverage Compose for creating UI nodes and managing state. Compose UI started as an Android-specific UI toolkit which was later ported to Kotlin multiplatform by JetBrains to run on Desktop and iOS. Compose, the underlying technology, can be used to manage state alongside any tree-like structure. Redwood’s Compose uses a custom tree which talks to the native UI toolkit on each platform.

In order to create a multiplatform set of composables that can interface with each platform’s native UI toolkit we need a common definition of UI widgets.

```kotlin
data class TextInput(
  val state: TextFieldState,
  val hint: String = "",
  val onChange: ((TextFieldState) -> Unit)? = null,
)
```

These definitions are called the schema. If your app has a formal design system this will be a programmatic representation of its contents.

From the schema Redwood generates a composable and an interface.

```kotlin
@Composable
fun TextInput(
  state: TextFieldState,
  hint: String = "",
  onChange: ((TextFieldState) -> Unit)? = null,
  modifier: Modifier = Modifier,
) { … }
```
```kotlin
interface TextInput<W : Any> : Widget<W> {
  fun state(state: TextFieldState)
  fun hint(hint: String)
  fun onChange(onChange: ((TextFieldState) -> Unit)?)
}
```

Each platform binds an implementation of the interface to an associate native UI component. To drive the UI we write regular Compose code, only with these generated composables instead of Compose UI. Redwood takes care of plumbing both halves together.

![Screenshot showing the iOS simulator, a web browser, and the Android emulator each running a version of the same app which is an input box containing the word "tree" and below it a list of five emoji images and their names which all contain the word "tree"](/static/post-image/redwood-1.png)

The Redwood repo is also home to Treehouse, a module which uses [Zipline](https://code.cash.app/zipline) to dynamically update the composable logic at runtime. This improves the development experience, but also allows updating our app logic in the wild between app upgrades.

Today we are releasing Redwood 0.5 which we’re calling “beta”. Breaking changes are still allowed, but all changes will be compatible with previous versions when running across the updatable Treehouse bridge. This means using a future Redwood 0.6 we can still target older apps only running Redwood 0.5.

Redwood, Zipline, and Treehouse are a big effort–much too large for one or two blog posts. If you want more information consider watching some of our recent conference talks:

- [“Playing in the Treehouse with Redwood and Zipline”](https://www.youtube.com/watch?v=G4LK_euTadU) @ KotlinConf 2023
- [“Dynamic code with Zipline”](https://www.droidcon.com/2022/09/29/dynamic-code-with-zipline/) @ Droidcon NYC 2022
- [“Native UI with multiplatform Compose”](https://www.droidcon.com/2022/09/29/native-ui-with-multiplatform-compose/) @ Droidcon NYC 2022

Our usage of Redwood to date has been very limited. With today’s beta we are excited to start rolling out experiences to all customers soon.

![Screenshot of Cash App running on iOS and Android showing the same "Money" screen with items like a cash balance and tiles for savings, taxes, and investing](/static/post-image/redwood-2.png)
