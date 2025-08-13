---
title: The Resurrection of Testing for Android
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/the-resurrection-of-testing-for-android

categories: post
tags:
- Android
- Testing
---

When testing Android applications, documentation and tools can be sparse. At Square, verifying the behavior of our applications is an essential part of our day-to-day development — and to make testing easier, we have created and refined a set of tools for three different aspects of testing; unit tests, instrumentation tests, and declaring assertions.


### Robolectric

The most fundamental problem which inhibits testing is that the API jar for each version of Android is empty. Rather than having actual code, the provided jars are filled with code that looks like this:

```java
public class View {
  public View(Context context) {
    throw new RuntimeException("Stub!");
  }
  ...
}
```

As you can imagine, having every constructor and every method in the entire platform immediately throw an exception prevents any real tests from being written.

[Robolectric](http://robolectric.org/) is a framework that has been designed to solve this problem by replacing these “stub” jars with the actual code. This means that any behavior you want to test will flow through the same code paths as if it were running on a real device.

Square continues to improve Robolectric and the forthcoming version 2 has significant ease of use and major performance improvements. Let’s write a quick test to demonstrate how easy it is to add to your project.

Given an ImageView that is designed to keep equal width and height, we can verify that it correctly exhibits this behavior.

```java
public class SquaredImageViewTest {
  @Test public void squaredImageViewStaysSquare() {
    Activity context = new Activity();
    SquaredImageView view = new SquaredImageView(context);

    // Manually measure the view at 200x100.
    view.measure(MeasureSpec.makeMeasureSpec(200, EXACTLY),
                 MeasureSpec.makeMeasureSpec(100, EXACTLY));
    
    // Verify that it correctly resized itself to 200x200.
    assertEquals(200, view.getMeasuredWidth());
    assertEquals(200, view.getMeasuredHeight());
  }
}
```

As mentioned before, if we were to execute this as-is we would immediately see a RuntimeException thrown. Let’s add Robolectric to our project and modify our code to use it:

```java
@RunWith(RobolectricTestRunner.class)
public class SquaredImageViewTest {
  ...
}
```

First we add an annotation on top of our test class that we previously created. This annotation tells JUnit that it should use Robolectric in order to execute the test. Behind the scenes Robolectric will load the appropriate Android runtime as well as all of the resources from your application.

Were you expecting a second step? Turns out that for most cases, the annotation is all that we need! We can now do all of the things that you would expect when writing tests: layout inflation, view and activity creation, and UI interaction.

For more information about Robolectric please visit its [website](http://robolectric.org/) and [blog](http://robolectric.blogspot.com/). Version 2.0 is currently in alpha but has been fundamental in enabling effective testing at Square.


### Spoon

While unit tests are useful for testing everything from UI behavior to complex, low-level internals of your application, sometimes it is useful to verify behavior at a higher level. Instrumentation tests were designed to address this in more of a black-box model.

Unfortunately instrumentation test execution and verification is still a very manual process. [Spoon](http://square.github.com/spoon/) was designed to address this by automating the test execution across multiple devices as well as aggregation of the results.

Plug in as many devices as adb will support and fire up Spoon using your existing application and instrumentation APKs. Here we see the sample app from Spoon — a variation of [Roman Nurik’s wizard flow](https://plus.google.com/113735310430199015092/posts/6cVymZvn3f4) — running on four devices at once. Each device has a different screen size and version of Android in order to get more useful results.

https://www.youtube.com/watch?v=UQZFhGPSPjM

Where Spoon really shines is in its aggregation of screenshots while your tests are running.

```java
username.setText("jake");
password.setText("omgsecret");
Spoon.screenshot(activity, "credentials_entered");
assertTrue(login.isEnabled());

login.performClick();
Spoon.screenshot(activity, "login_clicked");
```

The result of adding screenshot support to your tests is visual insight into your application’s behavior across multiple devices.

![](/post/static-image/resurrection-spoon.png)

In addition to static screenshots you can also view each test in [animated GIF form](https://corner.squareup.com/images/test-resurrection/spoon_result.gif).

You can learn more about Spoon from its [website](http://square.github.com/spoon/). Sample output for the provided demo tests is also [available to browse](http://square.github.com/spoon/sample/).


### FEST Android

Despite the undeniable usefulness of tests, they are not always fun to write. An even less enjoyable experience is debugging why a test is failing. Built upon FEST, [FEST Android](http://square.github.com/fest-android/) is a declarative, fluent API for verifying the state of Android objects.

In the examples above we wrote three assertions using the methods provided by JUnit:

```java
// In our unit test...
assertEquals(200, view.getMeasuredWidth());
assertEquals(200, view.getMeasuredHeight());
// In our instrumentation test...
assertTrue(login.isEnabled());
```

While these are relatively easy to read, the exceptions that they throw when failing are not very helpful.

```
Expected: <200> but was: <100>
```

While this message is not too bad, it does not describe exactly what is being verified. The exception message for the login assertion is even worse.

```
Expected: <true> but was: <false>
```

These messages report the problem in its raw form but provide no context for what we were verifying or what needs done. Let’s rewrite the assertions using FEST Android.

```java
// In our unit test...
assertThat(view).hasMeasuredWidth(200);
assertThat(view).hasMeasuredHeight(200);
// In our instrumentation test...
assertThat(login).isEnabled();
```

Immediately we are already given a much more declarative syntax that reads almost like English. Not only are these easier to write, but when looking at a test from another developer you can understand what is being tested much more quickly.

When failing, the exceptions provide much more context into the actual problem.

```
Expected measured width <200> but was <100>.

Expected view to be enabled but was disabled.
```

With these more informative messages it has been our experience that most times we can dive directly into the code to fix the problem rather than first having to figure out what broke.

More examples of usage — as well as download links — are available on the library’s [website](http://square.github.com/fest-android/).


### Triple Threat

The three aforementioned libraries enable us to efficiently write and execute tests for every aspect of our Android code. The result of which is increased confidence in our product and ability to iterate and ship more rapidly.

So while Android may not be the most forgiving framework when it comes to enabling you to write tests, our hope is that the libraries and tools we provide help break that trend.

```java
assertThat(this.blogPost).isFinished();
```

_P.S. There are also a few other great libraries for testing that augment everything described above. Some of our favorites are [Mockito](https://code.google.com/p/mockito/), [FEST](http://fest.easytesting.org/), [Robotium](https://code.google.com/p/robotium/), and [MockWebServer](https://code.google.com/p/mockwebserver/)._