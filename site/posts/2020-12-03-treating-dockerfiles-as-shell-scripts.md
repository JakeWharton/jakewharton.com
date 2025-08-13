---
title: 'Treating Dockerfiles as shell scripts'
layout: post

categories: post
---

I use Docker to run a lot of tools. With the tools all wrapped up in containers, my computers are
free of Python and Go and the various other dependencies needed for their use. While this is a nice
win for isolation and reproducibility, the user experience is sub-par.

To run a tool, I just use `docker run`:
```
$ docker run --rm tool arguments...
```

But the editing workflow is something like:
```
$ nano tool.dockerfile
# hack hack hack...
$ docker build -t tool - < tool.dockerfile
$ docker run --rm tool arguments...
```

I also use a lot of bash scripts with easy-to-remember names. To run a script I just type its name:
`./update_containers.sh`. To edit, I open it in an editor, save, and then run. The user experience
of this is top-notch!

Can we combine the two?

### Executable Dockerfiles

If the first line of an executable starts with `#!`, unix-y systems will treat what follows on that
line as an executable for interpreting the rest of the file. This is called the
[shebang](https://en.wikipedia.org/wiki/Shebang_(Unix)), and the bash scripts I use start with one:
`#!/usr/bin/env bash`.

In order to do this with a Dockerfile, though, we need a program which will conditionally run
`docker build` and then `docker run` the resulting image. Thankfully `docker build` is already
conditional and won't rebuild anything unless necessary, so we can always run it.

```shell
#!/usr/bin/env bash
NAME=$(basename "$1")
docker build -t "$NAME" - < "$1" > /dev/null
shift # Remove script name from arguments
docker run --rm --name "$NAME" "$NAME" "$@"
```

With this saved as `dockerfile-shebang.sh`, we can add it as the shebang in a Dockerfile.

```docker
#!/path/to/dockerfile-shebang.sh

FROM alpine:latest
ENTRYPOINT ["echo"]
```

Saving this as `echo.dockerfile` and running `chmod +x echo.dockerfile` provides the user experience
we're after:

```
$ ./echo.dockerfile Hello, world!
Hello, world!
```

### It's Dockerfile-Shebang!

I have wrapped up this utility into an executable, `dockerfile-shabang`.
You can find it at
[github.com/JakeWharton/dockerfile-shebang](https://github.com/JakeWharton/dockerfile-shebang).

The implementation is a bit more complicated than above for a few usability and correctness
concerns:

 1. Builds can be slow, so a message will be displayed if the container is currently being built. 
 2. If the build step fails, its entire output will be displayed to aid in debugging.
 3. Most importantly, there's a mechanism for passing arguments to the `docker run` command for
   mounting volumes, setting environment variables, and any other container-level flags.

A real-world invocation looks something like:
```
$ ./tool.dockerfile -v /tanker/backups:/backups -e UID=1000 -- /backups/path/to/file.txt
```

While I've been using Docker to wrap tools for a while, I've only been using this shebang for a
week. If you're feeling similar usability pain around Dockerfiles, try it out, let me know if it
works, and let me know of any use cases you have which aren't covered.
