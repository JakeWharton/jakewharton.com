---
title: 'Peeking at command-line ANSI escape sequences'
layout: post

categories: post
---

Command-line programs use color to convey additional information and to look pretty. For example, compare the output of `ls` with and without the `--color` flag:

<a href="/static/post-image/ansi-ls.svg"><img src="/static/post-image/ansi-ls.svg" alt="The output of 'ls' and 'ls --color' in a folder with three entries, the latter command using color to distinguish an executable and folder from a regular file."></a>

The color helps convey information in this compact output that would otherwise only be available in more verbose forms (`-l`).

In addition to color, a program may update existing output. You can see this when updating images with `docker-compose`:

<a href="/static/post-image/ansi-docker.svg"><img src="/static/post-image/ansi-docker.svg" alt="The output of 'docker-compose' showing three lines of progress bars updating individually."></a>

Both of these effects are created using something called ANSI escape sequences.


### ANSI escape crash course

Reading [the Wikipedia entry on ANSI escapes](https://en.wikipedia.org/wiki/ANSI_escape_code) is a great starting point for learning how to recreate these examples. Each escape sequence starts with a 0x1B (escape) character followed usually by `[` and then one or more commands using letters or numbers.

The `ls` example above uses green and blue text as well as making the colored entries bold which we can recreate.

```
echo -e "\e[1;32mbinary\e[0m  file  \e[1;34mfolder\e[0m"
```

<a href="/static/post-image/ansi-ls-echo.svg"><img src="/static/post-image/ansi-ls-echo.svg" alt="The output of the 'echo' command replicating the output of 'ls'."></a>

Let's break down the interesting parts:

 * `echo -e` – Adding the `-e` flag to `echo` instructs it to enable backslash escapes.
 * `\e[1;32m` – `\e` is a backslash escape for the 0x1B escape character and the `[` starts a sequence. `1` enables bold and `32` is the color green. Numbers are separated by `;` and terminated by `m`. Anything that follows will now be displayed as bold and green.
 * `\e[0m` – Once again `\e[` starts a sequence and `m` terminates it. The `0` clears all previous formatting.
 * `\e[1;34m` – Nearly identical to the sequence from before except it uses `34` for a blue color.

The `docker-compose` example moves the cursor to rewrite previous output which we can begin to recreate.

```
echo "Pulling zulu-jdk-15 ... downloading" && \
echo "Pulling zulu-jdk-11 ... downloading" && \
echo "Pulling zulu-jdk-8  ... downloading" && \
sleep 2 && \
echo -e "\e[2A\e[24C\e[32mdone\e[0m\e[K" && \
sleep 1 && \
echo -e "\e[24C\e[32mdone\e[0m\e[K" && \
sleep 1 && \
echo -e "\e[3A\e[24C\e[32mdone\e[0m\e[K\n\n"
```

<a href="/static/post-image/ansi-docker-echo.svg"><img src="/static/post-image/ansi-docker-echo.svg" alt="The output of the 'echo' commands replicating the output of 'docker-compose'."></a>

Let's break down the interesting parts for this example:

 * `\e[2A` – Each `echo` emits a trailing newline, so after the third `echo` our cursor is below the third line at column 0. This command moves the cursor up (`A`) by two lines placing it on the "zulu-jdk-11" line still at column 0.
 * `\e[24C` – Move the cursor to the right (`C`) by 24 columns. This places the cursor directly before the "d" in "downloading".
 * `\e[32m` – Set the color to green. Remember this from the last section?
 * `\e[K` – After writing "done", the "loading" part of "downloading" is still visible. This command clears the current line from the cursor position to the line end.

With these ANSI escape sequences we can recreate existing programs and being to create our own. But how do we know whether we're using the same techniques as these programs? And if we don't know how to produce a particular output how can we discover how it was created?


### Displaying ANSI sequences

Given that ANSI sequences start with the 0x1B character and then `[` we can replace that escape with something else to disable it.

```
ls --color | sed -r 's/\x1b\[/\\e\[/g'
```
<a href="/static/post-image/ansi-ls-sed.svg"><img src="/static/post-image/ansi-ls-sed.svg" alt="The output of 'ls' piped through 'sed' replacing ANSI escapes with printable characters"></a>

The `sed` command[^1] matches 0x1B and `[` and replaces it with `\e[` which is shown as normal text. This particular replacement is convenient because you can copy the output into an `echo` and see the rendered form.

 [^1]: If you are on Mac OS, you'll need GNU `sed` for the `-r` flag which can be installed via `brew install gnu-sed` and then used as `gsed` or by `alias sed=gsed`.

In this output we can see `ls` is using almost exactly the same ANSI sequences as we were. The only addition is that they start with `\e[0m` in order to clear any existing formatting.

You may also notice that the output has changed to list each entry on its own line rather than on a single line. This is because `ls` detects that its output is going into a pipe rather than to a terminal display. Programs may also choose to omit color when piped which defeats the whole purpose of adding the `sed` command. To solve both cases, run the program using `unbuffer` before piping.

```
unbuffer ls --color | sed -r 's/\x1b\[/\\e\[/g'
```
<a href="/static/post-image/ansi-ls-sed-unbuffer.svg"><img src="/static/post-image/ansi-ls-sed-unbuffer.svg" alt="The output of 'ls' with 'unbuffer' piped through 'sed'"></a>

With the pipe usage hidden by `unbuffer`, the output of `ls` is back to being a single line.

If you run `docker-compose` with `unbuffer` and piping to `sed` the result is clearly not correct:
```
unbuffer docker-compose pull | sed -r 's/\x1b\[/\\e\[/g'
```
<a href="/static/post-image/ansi-docker-sed.svg"><img src="/static/post-image/ansi-docker-sed.svg" alt="The output of 'docker-compose' with 'unbuffer' piped through 'sed'"></a>

This is because `docker-compose` is using carriage returns (`\r`) to move the cursor back to column 0 on a line. We can update our `sed` to include a command to escape carriage returns too.

```
unbuffer docker-compose pull | sed -r -e 's/\x0d/\\r/g' -e 's/\x1b\[/\\e\[/g'
```
<a href="/static/post-image/ansi-docker-sed-2.svg"><img src="/static/post-image/ansi-docker-sed-2.svg" alt="The output of 'docker-compose' now with carriage return escaping"></a>

Now we can see all the commands. There is a lot of output here because `docker-compose` is updating the display very rapidly. Unlike our toy version above, each line is fully rewritten for each update. At the very end, though, you can see the `\e[32mdone\e[0m` sequence as part of updating the "zulu-jdk-15" line.


### Bonus technique: Asciinema

[Asciinema](https://asciinema.org/) can also be used to inspect ANSI sequences, carriage returns, and everything else that a program outputs. Every terminal image and animation captured in this post was captured using Asciinema before being fed to [`svg-term`](https://github.com/marionebl/svg-term-cli).

For example, the `docker-compose` output can be captured like this:
```
asciinema rec -c "docker-compose pull" docker.json
```
<a href="/static/post-image/ansi-asciinema.svg"><img src="/static/post-image/ansi-asciinema.svg" alt="Using 'asciinema' to capture the output of the 'docker-compose' command"></a>

_(Yes, I captured the above example of using Asciinema inside Asciinema!)_

The resulting `docker.json` contains a series of JSON objects which describe the output commands.
```json
{"version": 2, "width": 122, "height": 48, "timestamp": 1603858671, "env": {"SHELL": "/bin/bash", "TERM": "xterm-256color"}}
[0.412745, "o", "Pulling zulu-jdk-15 ... \r\r\nPulling zulu-jdk-11 ... \r\r\nPulling zulu-jdk-8  ... \r\r\n"]
[0.671883, "o", "\u001b[1A\u001b[2K\rPulling zulu-jdk-8  ... pulling from azul/zulu-openjdk\r\u001b[1B"]
[0.672048, "o", "\u001b[1A\u001b[2K\rPulling zulu-jdk-8  ... digest: sha256:13d16ca0335fbe1df3...\r\u001b[1B"]
[0.672159, "o", "\u001b[1A\u001b[2K\rPulling zulu-jdk-8  ... status: image is up to date for a...\r\u001b[1B"]
[0.672478, "o", "\u001b[1A\u001b[2K\r"]
[0.672507, "o", "Pulling zulu-jdk-8  ... \u001b[32mdone\u001b[0m\r\u001b[1B"]
[0.782864, "o", "\u001b[2A\u001b[2K\rPulling zulu-jdk-11 ... pulling from azul/zulu-openjdk\r\u001b[2B"]
[0.782985, "o", "\u001b[2A\u001b[2K\r"]
[0.78307, "o", "Pulling zulu-jdk-11 ... digest: sha256:315e0a2a7b6bcc2343...\r\u001b[2B"]
[0.783146, "o", "\u001b[2A\u001b[2K\rPulling zulu-jdk-11 ... status: image is up to date for a...\r\u001b[2B"]
[0.783372, "o", "\u001b[2A\u001b[2K\r"]
[0.783428, "o", "Pulling zulu-jdk-11 ... \u001b[32mdone\u001b[0m\r\u001b[2B"]
[1.091186, "o", "\u001b[3A\u001b[2K\rPulling zulu-jdk-15 ... pulling from azul/zulu-openjdk\r\u001b[3B"]
[1.09136, "o", "\u001b[3A\u001b[2K\rPulling zulu-jdk-15 ... digest: sha256:bf2d25e46d2c9fc373...\r\u001b[3B"]
[1.091511, "o", "\u001b[3A\u001b[2K\r"]
[1.091571, "o", "Pulling zulu-jdk-15 ... status: image is up to date for a...\r\u001b[3B"]
[1.091859, "o", "\u001b[3A\u001b[2K\rPulling zulu-jdk-15 ... \u001b[32mdone\u001b[0m\r"]
[1.091919, "o", "\u001b[3B"]
```

For a complex output like `docker-compose` the JSON form can be easier to understand. One other advantage is that each individual write to standard out gets its own line whereas with the `sed` escape technique we don't differentiate individual writes.

---

If you use tools like Docker, Gradle, Bazel, and even just `ls` you may be familiar with seeing colored and updating output daily. By using tools like `sed` and `asciinema` you can learn how those tools render their output. Should you find yourself building a command-line tool in the future, knowledge of how to use these ANSI sequences can help delight your users–even if it's only yourself!