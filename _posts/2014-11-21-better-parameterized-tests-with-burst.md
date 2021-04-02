---
title: Better Parameterized Tests with Burst
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/better-parameterized-tests-with-burst

categories: post
tags:
- Testing
- Java
- Android
- Burst
---

_Written with [Daniel Lubarov](https://twitter.com/dlubarov) and [Dimitris Koutsogiorgas](https://twitter.com/dnkoutso)_

At Square, we invest a lot of effort in testing to ensure that our software is reliable. Not only do we unit test our business logic, we also use automated UI tests to detect bugs and prevent regressions in our applications. We also use manual testing and phased rollouts as additional safeguards.

Sometimes we want to repeat a test several times with different parameters. If we’re testing a web server for example, we might want to repeat a request using different versions of the HTTP protocol.

Test authors often use foreach loops to enumerate test parameters. When such a test fails, the investigation usually starts with determining the parameter or parameters which triggered the failure. In the interest of making tests easier to maintain, it is helpful to have a framework which can immediately show you the parameters with which a test failed.


### JUnit's Parameterized

JUnit 4 ships with a test runner called Parameterized for this purpose. We could write a web server test like this:

```java
@RunWith(Parameterized.class)
public class WebServerTest {
  enum Protocol { HTTP_1_0, HTTP_1_1, HTTP_2 }
  @Parameters(name = "protocol={0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[] { Protocol.HTTP_1_0 },
        new Object[] { Protocol.HTTP_1_1 },
        new Object[] { Protocol.HTTP_2 }
    );
  }

  private final Protocol protocol;

  public WebServerTest(Protocol protocol) {
    this.protocol = protocol;
  }

  @Test public void testGetRequest() {
    dispatchGetRequest(protocol);
  }

  @Test public void testPostRequest() {
    dispatchPostRequest(protocol);
  }
}
```

Parameterized will generate the following hierarchy of tests, making it easy to see where the failure occurred:

![](/static/post-image/burst-0.png)

Parameterized is useful, but declaring each combination of test parameters can be cumbersome. They are declared using object arrays, which are awkward to use. Additionally, there is no way to parameterize a single test method (apart from moving it into a separate class).

Finally, since Parameterized is part of the JUnit 4 framework, it is not available in JUnit 3. While most modern projects have adopted JUnit 4, Android’s test framework still requires JUnit 3.


### Introducing Burst

Burst uses enums to provide test variations in a clean and type-safe manner. If you add one or more enum parameters in a test’s constructor, Burst will automatically generate a test for each combination of parameters; there’s no need to list them out. Here’s an example:

```java
@RunWith(BurstJUnit4.class)
public class WebServerTest {
  enum Protocol { HTTP_1_0, HTTP_1_1, HTTP_2 }

  private final Protocol protocol;

  public WebServerTest(Protocol protocol) {
    this.protocol = protocol;
  }

  @Test public void testGetRequest() {
    dispatchGetRequest(protocol);
  }

  @Test public void testPostRequest() {
    dispatchPostRequest(protocol);
  }
}
```

Running this will produce a hierarchy of tests similar to the one produced by Parameterized:

![](/static/post-image/burst-1.png)

In addition to class-level parameters, Burst also supports parameters at the method level. This can be used standalone or in tandem with class parameters. Here’s an example with both class and method parameters:

```java
@RunWith(BurstJUnit4.class)
public class WebServerTest {
  enum Protocol { HTTP_1_0, HTTP_1_1, HTTP_2 }
  enum Method { GET, PUT, POST }

  private final Protocol protocol;

  public WebServerTest(Protocol protocol) {
    this.protocol = protocol;
  }

  @Test public void testConnect() {
    connect(protocol);
  }

  @Test public void testRequest(Method method) {
    dispatchRequest(protocol, method);
  }
}
```

And the resulting test hierarchy:

![](/static/post-image/burst-2.png)

If you declare more than one enum parameter in a constructor or method, Burst will generate a test for each unique combination of parameters.

Sometimes your test parameter isn’t a single value that can trivially be represented by an enum. But keep in mind that enums are flexible — you can embed whatever data you need in them. If you’re testing some logic that involves credit cards and want to repeat a test using several cards, you could declare:

```java
enum CreditCard {
  VISA("4111111111111111", "1804", "123"),
  MASTERCARD("5500005555555559", "1804", "456");

  final String accountNumber;
  final String expirationYYMM;
  final String securityCode;

  CreditCard(String accountNumber, String expirationYYMM, String securityCode) {
    this.accountNumber = accountNumber;
    this.expirationYYMM = expirationYYMM;
    this.securityCode = securityCode;
  }
}
```


### Android

Android’s test framework is built on the older JUnit 3 API, which has no built-in support for test parameterization. We provide a test runner called BurstAndroid for use in Android tests.

Like our JUnit 4 runner, BurstAndroid lets you add enum parameters to test constructors. Method-level parameters are unfortunately not supported, as Android’s test framework only includes zero-parameter methods when constructing a test suite.


### Filtering Tests

JUnit 4 has a concept of [assumptions](http://junit.sourceforge.net/javadoc/org/junit/Assume.html), which allow a test to indicate that it isn’t applicable to the current test environment and should be skipped. JUnit 3 lacks this feature, but our BurstAndroid runner provides similar functionality. By overriding isClassApplicable or isMethodApplicable, you can filter out tests based on the current environment.

Our apps have certain features which only exist on phones or tablets, so we annotate associated tests with @PhoneOnly or @TabletOnly. BurstAndroid lets us skip these tests based on the device being tested.


### Downloading

Burst is available in Maven Central; see our [GitHub project](https://github.com/square/burst) for details. Please give the library a try and if you find any issues, let us know in the comments or on GitHub.

For more details on how we approach UI testing at Square, check out Dimitris’ [Droidcon slides](https://speakerdeck.com/dnkoutso/automated-testing-at-square-droidcon-nyc-2014).