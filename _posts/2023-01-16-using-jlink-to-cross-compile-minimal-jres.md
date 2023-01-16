---
title: 'Using jlink to cross-compile minimal JREs'
layout: post

categories: post
tags:
- Java
- Kotlin
---

`jlink` is a JDK tool to create bespoke, minimal JREs for your applications.
Let's try it with a "Hello, world!" program:
```java
class Main {
  public static void main(String... args) {
    System.out.println("Hello, world!");
  }
}
```

My laptop is an M1 Mac and I have downloaded the Azul Zulu JDK 19 build for it.
With the JDK I can both compile Java and then run the resulting program.
```
$ mkdir out
$ zulu19.30.11-ca-jdk19.0.1-macosx_aarch64/bin/javac -d out in/Main.java
$ zulu19.30.11-ca-jdk19.0.1-macosx_aarch64/bin/java -cp out Main
Hello, world!
```

Azul Zulu also provides a JRE that I can use to run compiled programs.
```
$ zulu19.30.11-ca-jre19.0.1-macosx_aarch64/bin/java -cp out Main
Hello, world!
```
Note the slight change in folder name ("jdk" → "jre").

If we were shipping this to end-users it would be an easy win for binary size.
```
$ du -hs zulu*
329M    zulu19.30.11-ca-jdk19.0.1-macosx_aarch64
136M    zulu19.30.11-ca-jre19.0.1-macosx_aarch64
```
But 136MiB just for "Hello, world"? Don't tell Reddit or Hacker News!

Thankfully, `jlink` is here to help us build a minimal JRE with only what we need.
Given our program, a sibling tool, `jdeps`, lists the Java modules which are required.
```
$ zulu19.30.11-ca-jdk19.0.1-macosx_aarch64/bin/jdeps \
      --print-module-deps \
      out/Main.class
java.base
```

Our program is so simple that it only needs the "base" module.
Now with `jlink` we can produce a minimal JRE.
```
$ zulu19.30.11-ca-jdk19.0.1-macosx_aarch64/bin/jlink \
      --compress 2 \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --output zulu-hello-jre \
      --add-modules java.base

$ du -hs zulu*
 28M    zulu-hello-jre
329M    zulu19.30.11-ca-jdk19.0.1-macosx_aarch64
136M    zulu19.30.11-ca-jre19.0.1-macosx_aarch64
```

28MiB won't win any language wars, but it's a massive 80% savings over the full JRE.

```
$ zulu-hello-jre/bin/java -cp out Main
Hello, world!
```

We can ship it to our client and call it a day, right?
```
$ tar -czf hello.tgz zulu-hello-jre out

$ scp hello.tgz jw@server:
hello.tgz            100%   14MB   2.0MB/s   00:07

$ ssh jw@server "tar xzf hello.tgz && zulu-hello-jre/bin/java -cp out Main"
bash: zulu-hello-jre/bin/java: cannot execute binary file: Exec format error
```

Nope!
While the Java bytecode we compiled is platform independent, the JRE is specific to each platform and my server runs Linux x64.

Thankfully, `jlink` can operate on JDKs for different platforms.
Let's download the Linux x64 JDK and point `jlink` at its Java modules using `--module-path`.

```
$ zulu19.30.11-ca-jdk19.0.1-macosx_aarch64/bin/jlink \
      --compress 2 \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --output zulu-hello-jre-linux-x64 \
      --module-path zulu19.30.11-ca-jdk19.0.1-linux_x64/jmods
      --add-modules java.base

$ du -hs zulu*
 28M    zulu-hello-jre
 36M    zulu-hello-jre-linux-x64
338M    zulu19.30.11-ca-jdk19.0.1-linux_x64
329M    zulu19.30.11-ca-jdk19.0.1-macosx_aarch64
136M    zulu19.30.11-ca-jre19.0.1-macosx_aarch64
```

The Linux x64 JRE is a little larger than the one for my ARM Mac, but it's still small compared to the full-size JRE.
Does it work on the client?

```
$ tar -czf hello-linux.tgz zulu-hello-jre-linux-x64 out

$ scp hello-linux.tgz jw@server:
hello.tgz            100%   16MB   2.1MB/s   00:08

$ ssh jw@server "tar xzf hello-linux.tgz && zulu-hello-jre-linux-x64/bin/java -cp out Main"
Hello, world!
```

It works! Now we can grab JDKs for any architecture for any platform and use our host `jlink` to effectively cross-compile minimal JREs for each target.

This is a great solution for multi-architecture Docker containers, desktop clients like JetBrains Compose UI, shipping to devices where you can't fit a full JDK, and more.
Be sure to explore all the options on `jdeps` and `jlink` for ways to keep your runtimes small.
