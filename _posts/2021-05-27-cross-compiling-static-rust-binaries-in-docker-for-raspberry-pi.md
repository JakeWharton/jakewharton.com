---
title: 'Cross-compiling static Rust binaries in Docker for Raspberry Pi'
layout: post

categories: post
---

Earlier this year I built a web-based garage door controller using Rust for the Raspberry Pi called [Normally Closed][nc]. My deployment includes a Pi 3b and a Pi Zero which are ARMv7 and ARMv6 devices, respectively. I deploy services with Docker and wanted to continue using it here for simplicity.

[nc]: https://github.com/JakeWharton/NormallyClosed

Getting all of this set up and working together was not easy. There's also a lot of words in that title which might not mean much to you. That's okay! This is the blog post that I needed two weeks ago, so let's take a look at the steps required to accomplish this task.

### Cross-compiling and static linking

Rust has excellent facilities for cross-compiling and static linking through Cargo. I got started following [this guide](https://medium.com/swlh/compiling-rust-for-raspberry-pi-arm-922b55dbb050#2b8a) on cross-compiling Rust for the Raspberry Pi.

The guide recommends using the `armv7-unknown-linux-gnueabihf` Rust target which would support my Pi 3b. For the Pi Zero we can infer from [Rust's platform support list](https://doc.rust-lang.org/nightly/rustc/platform-support.html) that we need `arm-unknown-linux-gnueabihf`. However, these targets dynamically link against GNU libc whereas I wanted to statically link with musl. Referencing the platform support list again we can find the `armv7-unknown-linux-musleabihf` and `arm-unknown-linux-musleabihf` targets to use instead.

```
$ rustup target add armv7-unknown-linux-musleabihf
$ rustup target add arm-unknown-linux-musleabihf
```

In addition to the target, the linker needs to be changed since it otherwise will use the one from your machine and its architecture. This can be specified in `.cargo/config`:

```toml
[target.armv7-unknown-linux-musleabihf]
linker = "arm-linux-gnueabihf-ld"

[target.arm-unknown-linux-musleabihf]
linker = "arm-linux-gnueabihf-ld"
```

On Ubuntu you can install this linker by running `sudo apt install gcc-arm-linux-gnueabihf`. On my Mac I was able to install it with `brew install arm-linux-gnueabihf-binutils`. (Despite using the musl compilation target, the GNU linker will still work.)

This is enough to compile working binaries!

```
$ cargo build --target armv7-unknown-linux-musleabihf --release
$ cargo build --target arm-unknown-linux-musleabihf --release
```

#### Bonus: Smaller binaries

I really like to make my binaries and Docker containers as small as possible. Once again Rust's Cargo gives us a simple mechanism to achieve this in our `Cargo.toml`:

```toml
[profile.release]
opt-level = 'z'
lto = true
codegen-units = 1
```

Setting `opt-level` to `z` instructs the compiler to favor a smaller binary size over performance. This will not be appropriate for anything CPU-intensive, but for a web server which will only see a few interactions _per year_ we don't require maximum performance.

LTO is short for "link-time optimization" which performs optimization on the whole program rather than locally on individual functions. By virtue of analyzing the whole program it also improves the ability to remove dead code.

Finally, the `codegen-units` setting reduces the parallelism of Cargo to allow compilation to occur in a single unit and be optimized as a single unit. This allows compilation and optimization to have the maximum impact by always seeing the entire program.


### Building inside Docker

Having starting with only the Pi 3b and needing ARMv7, getting the build going in Docker was not too difficult. We basically just run the commands from above to produce the binary and then copy that into an Alpine container.

```dockerfile
FROM rust:1.52.1 AS rust
RUN rustup target add armv7-unknown-linux-musleabihf
RUN apt-get update && apt-get -y install binutils-arm-linux-gnueabihf
WORKDIR /app
COPY .cargo ./.cargo
COPY Cargo.toml Cargo.lock .rustfmt.toml ./
COPY src ./src
RUN cargo build --release --target armv7-unknown-linux-musleabihf

FROM --platform linux/arm alpine:3.12
WORKDIR /app
COPY --from=rust /app/target/armv7-unknown-linux-musleabihf/release/normally-closed ./
# ENTRYPOINT setup...
```

This container can be built with the regular `docker build .` command.

Adding a second architecture complicates things significantly. Docker does support containers which are built for multiple architectures through [Docker buildx](https://docs.docker.com/buildx/working-with-buildx/#build-multi-platform-images).

However, unlike the buildx examples, we cannot naively run `docker buildx build --platform linux/arm/v7,linux/arm/v6 .` and have it just work. For one, the Rust container is not available for those architectures. But even if it were, we still need to specify the custom compilation target per architecture due to our desire to use musl.

The first step towards making this work is having the Rust container always use the architecture of the machine on which it is running. This is similar to having run Cargo directly on our machine before, and it works because Rust is already allowing us to cross-compile to ARM.

```diff
-FROM rust:1.52.1 AS rust
+FROM --platform=$BUILDPLATFORM rust:1.52.1 AS rust
 RUN rustup target add armv7-unknown-linux-musleabihf
  ⋮
```

The `BUILDPLATFORM` argument [is documented](https://docs.docker.com/engine/reference/builder/#automatic-platform-args-in-the-global-scope) as one which is available by default in the global scope of a Dockerfile.

With Rust always running on our host architecture we still need to vary the Rust target which is used for cross-compilation. Also present in the list of default arguments in Docker is `TARGETPLATFORM` which will contain either `linux/arm/v7` or `linux/arm/v6` in our case. We can use a `case` statement to determine the associated Rust target.

```diff
 FROM --platform=$BUILDPLATFORM rust:1.52.1 AS rust
+ARG TARGETPLATFORM
+RUN case "$TARGETPLATFORM" in \
+  "linux/arm/v7") echo armv7-unknown-linux-musleabihf > /rust_target.txt ;; \
+  "linux/arm/v6") echo arm-unknown-linux-musleabihf > /rust_target.txt ;; \
+  *) exit 1 ;; \
+esac
 RUN rustup target add armv7-unknown-linux-musleabihf
  ⋮
```

We write the value to a file since `export` does not work and there's not really another mechanism for passing data between build steps. A read of that file replaces the hard-coded targets in the steps that follow.

```diff
  ⋮
 esac
-RUN rustup target add armv7-unknown-linux-musleabihf
+RUN rustup target add $(cat /rust_target.txt)
 RUN apt-get update && apt-get -y install binutils-arm-linux-gnueabihf
  ⋮
 COPY src ./src
-RUN cargo build --release --target armv7-unknown-linux-musleabihf
+RUN cargo build --release --target $(cat /rust_target.txt)
```

The Alpine build stage also references the target in the source folder of the binary. Rather than worry about passing along the file which holds this value, an easy workaround is to copy the binary to a location which does not contain the target name.

```diff
  ⋮
 RUN cargo build --release --target $(cat /rust_target.txt)
+# Move the binary to a location free of the target since that is not available in the next stage.
+RUN cp target/$(cat /rust_target.txt)/release/normally-closed .
  ⋮
```

The Alpine build stage can now remove the target platform and copy from the new location.

```diff
  ⋮
-FROM --platform linux/arm alpine:3.12
+FROM alpine:3.12
 WORKDIR /app
-COPY --from=rust /app/target/armv7-unknown-linux-musleabihf/release/normally-closed ./
+COPY --from=rust /app/normally-closed ./
 # ENTRYPOINT setup...
```

At this point we have a fully-working, cross-compiling, static-linking, multi-architecture Docker container built from Rust!

```
$ docker buildx build --platform linux/arm/v7,linux/arm/v6 .
[+] Building 147.9s (34/34) FINISHED
```

You can see the result reflected on [the Docker Hub listing](https://hub.docker.com/r/jakewharton/normally-closed/tags) as compared to the latest release:

[![Screenshot of Docker Hub showing the container has two architectures](/static/post-image/normally-closed-docker-hub.png)](/static/post-image/normally-closed-docker-hub.png)

The full and final `Dockerfile` can be found [here](https://github.com/JakeWharton/NormallyClosed/blob/a21e4de89ef90417a99cadf75c2b6297eda35735/Dockerfile) for reference. The repository also contains GitHub Actions setup for building the standalone binaries as well as the multi-architecture Docker container.

Hopefully this helps someone! It was a couple nights of piecing together all the steps for me. And hey if you have a garage door and a spare Pi lying around maybe try out [Normally Closed](https://github.com/JakeWharton/NormallyClosed)!
