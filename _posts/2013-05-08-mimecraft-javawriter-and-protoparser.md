---
title: MimeCraft, JavaWriter, and ProtoParser
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/mimecraft-javawriter-and-protoparser

categories: post
tags:
- Java
- HTTP
- Protocol Buffers
- Code Generation
---

In any complex project you’ll need a variety of tools to solve a variety of problems. The general rule that we use when developing libraries is “perform one task and perform it well”. Here are three purpose-built libraries which do just that:


### MimeCraft

Making requests with [OkHttp](http://square.github.io/okhttp/) or HttpUrlConnection requires that you encode the body of requests yourself. If you are using JSON or protocol buffers then there are multiple libraries which will take care of this for you. Other common request formats like form-encoding and multi-part are often left for the developer or through heavyweight HTTP abstractions.

MimeCraft aims to eliminate that by providing a terse, fluent API to construct both of these formats.

```java
FormEncoding fe = new FormEncoding.Builder()
    .add("name", "Jake Wharton")
    .add("occupation", "Byte code chef")
    .build();

Multipart m = new Multipart.Builder()
    .addPart(new Part.Builder()
        .contentType("image/gif")
        .body(new File("/Users/jake/images/bees.gif"))
        .build())
    .addPart(new Part.Builder()
        .contentType("text/plain")
        .body("The quick brown fox jumps over the lazy dog.")
        .build())
    .build();
```

The resulting FormEncoding and Multipart objects can write their content directly to an OutputStream in addition to providing header information such as MIME type and content length.

Check out [the project page on GitHub](https://github.com/square/mimecraft) for more information.


### ProtoParser

Protocol buffers are an efficient wire format for sending data and RPCs by using generated code based on a schema. While the generated code has reflection APIs that describe the underlying format, it requires generating the most verbose version of the code. ProtoParser quickly parses proto files into an object representation without the need of the protoc tool.

Having the proto represented in an object format is useful for a number of reasons:

* API documentation can be generated in alternate formats that are easier to understand. For example, visualizing dependency graphs and RPC call breakdowns in HTML and GraphViz.
* Validation tools that perform deep analysis which are easier to write. A Maven plugin or Ant task can fail the build if a complex constraint is not met.
* Code that indirectly supports the proto format can be generated. Mechanical code that deals with the generated protocol buffer can be generated rather than relying on generics and abstract implementations.
* The proto spec can be used to dictate other wire formats. At Square we’re using protocol buffers as a schema language for JSON and generating POJOs.

Check out [the project page on GitHub](https://github.com/square/protoparser) for more information.


### JavaWriter

Code that generates other code is an immensely powerful concept that saves developer time. JavaWriter is a simple class which assists in producing Java code. This library was developed in parallel with [Dagger](http://square.github.io/dagger/) and is responsible for all of its generated code.

Creating Java constructs becomes a much simpler and more declarative task:

```java
JavaWriter writer = new JavaWriter(fileWriter);
writer.beginType("Hello", "class", PUBLIC)
    .beginMethod("void", "main", PUBLIC | STATIC, "String...", "args")
    .emitStatement("System.out.println(\"Hello, World!\");")
    .endMethod()
    .endType();
```

With this we will have generated your standard “Hello, World” example.

```java
public class Hello {
  public static void main(String... args) {
    System.out.println("Hello, World!");
  }
}
```

JavaWriter intentionally lacks a lot of logic, delegating to the calling code for structure. This also makes it very powerful since no assumptions are made about how the resulting code will be organized.

Check out [the project page on GitHub](https://github.com/square/javawriter) for more information.


### Tiny Tools

Tools like the ones listed above are sprinkled throughout Square’s infrastructure in every language that we use. These projects are intentionally tiny in scope and purpose-built for a specific problem.

This post is part of Square’s “[Seven Days of Open Source](https://corner.squareup.com/2013/05/seven-days-of-open-source.html)” series.