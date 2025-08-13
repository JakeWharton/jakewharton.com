---
title: 'Removing Google as a Single Point of Failure Part 2: Gmail'
layout: post

categories: post
tags:
- Linux
---

I want to remove Google as a single point of failure in my life. In [the first blog post][1] on this subject I detailed my setup for backing up Google Photos and Google Drive contents onto my home server and remotely to [rsync.net](https://rsync.net). Left out of that post was a solution for Gmail because I hadn't found one yet. Now I have.

 [1]: /removing-google-as-a-single-point-of-failure/

### Source of truth

That first post started with an important qualification:

> This does not mean that I'm going to stop using Google products. Quite the opposite. Gmail, Google Photos, and Google Drive will remain the source-of-truth for all of the things I listed above. What's different is that should Google disappear tomorrow (or just my account) I would lose no data.

This was easy to achieve with Photos and Drive because the data is all there is. With email that's unfortunately not true.

Incrementally backing up the email data is pretty straightforward–we'll get into that shortly. But with Gmail your email address is still tied to the `@gmail.com` domain. So if my account or all of Google disappears, I won't be able to receive any more email.

Of course the "easy" fix here is to just use a domain that I control. Obviously I own `jakewharton.com`, and I intend to set that up, but I wanted something shorter. I've owned `cob.io` for many years with the intention of setting up `j@cob.io`, but I go by "Jake". Luckily the last few years have seen an influx of new TLDs so I managed to grab `ke.fyi`. Say hello to `j@ke.fyi`!

Having an email on my own domain doesn't address the problem that there's still hundreds or thousands of services that I've given the Gmail address to. While I can migrate many, there are inevitably those which I can't or that I simply don't know exist. The old address needs to remain working.


### Fastmail

After browsing a few hosted email solutions, I settled on [Fastmail](https://ref.fm/u23361320) _(Note: referral link)_. In addition to a positive recommendation from a friend, there were a few key motivating factors.

#### Domain catch-all

A popular feature of Gmail is the ability to append a `+` to your user followed by any text and mail will still be sent to you. This can be used for filters or to see who is selling your email address to others.

A domain catch-all is the same thing but you can change the entire username. Now I can use addresses like `southwest@ke.fyi` without needing to set anything up first. Aside from knowing if they sell my email it also slightly improves security. While the format is human guessable, any automated attack using emails from a data breach simply don't exist on other services.

Fastmail supports replying to these catch-all emails using the same address to which it was sent. This is critical to maintain the illusion, especially when dealing with people rather than automated systems.

#### Multiple domains

Aside from `ke.com` I also set up `jakewharton.com` and a few other domains. Fastmail sends all emails to my configured domains to a unified inbox rather than forcing me to switch accounts. Instead, my replies will match the incoming address the same as it did for the catch-all.

Additionally, when composing emails I can choose the domain from which it will be sent. And for those with catch-all set up, I can even pick arbitrary usernames on those domains. Neat!

#### Gmail support

Since my Gmail address will receive _some_ mail for the foreseeable future it's important to use a service that supports more than just a one-time import. Fastmail performs near-realtime incremental syncs to pull in any new email or calendar events from Gmail. Not only is it very fast, but they seem to be able to bypass the rate limits that otherwise exist when downloading your email over IMAP.

I can compose email using the Gmail address. I don't know why I would ever need this, but it's nice to have.

In replies to any email Fastmail lets me change the address from which I'm replying. When a person sends an email to my old address, I can use this feature to gradually migrate them over to the new one.

#### IMAP availability

Remember, it's not enough to migrate from Gmail to Fastmail for an address on our own domain. We still need to ensure a backup solution. Thankfully Fastmail supports any and every protocol you'd need.

As a nice bonus, the Gmail to Fastmail sync bypasses Google's rate limit meaning you can also sync your entire Gmail within minutes through Fastmail rather than having to spread it out over multiple days when accessing it directly.


### Backup

Almost immediately after the previous blog post people were sending me a myriad of tools for Gmail backup. Thank you for that!

#### mbsync

After trying a few tools I settled on `mbsync` which is part of the [isync][isync] project. The tool is very generic but can be used to synchronize emails to the [Maildir][maildir] format over IMAP.

 [isync]: http://isync.sourceforge.net/
 [maildir]: https://en.wikipedia.org/wiki/Maildir

Maildir is a standard format that can be read by many tools. Unlike mbox, the format used in Google Takeout for Gmail, Maildir uses individual files for each email. This lends itself to incremental updates, tools like `grep`, and compression.

Few clients operate on Maildir directly, unfortunately. Definitely none which I'm comfortable using (sorry, Mutt).

It's quite easy to push Maildir back into any IMAP-supported host with `mbsync` should you need to restore from a backup. And if you really need an always-on, self-hosted client you can push into one as part of your sync.


#### Docker

In order to automate this procedure I wrapped `mbsync` up in a Docker container as [jakewharton/mbsync][mbsync] which can run it on a periodic schedule.

 [mbsync]: https://github.com/JakeWharton/docker-mbsync/

It uses same [healthchecks.io][hc] service as the [rclone][rc] and [gphotos-sync][gps] containers from the last post for monitoring. I personally send this to my own Slack workspace which gives me simple history and easy notifications on all my devices.

 [hc]: https://healthchecks.io/
 [rc]: https://github.com/bcardiff/docker-rclone
 [gps]: https://github.com/JakeWharton/docker-gphotos-sync

Here's its entry in my `docker-compose.yml`:

```yaml
version: "3.6"

services:
  # Services from previous blog post...

  mbsync-jake:
    container_name: mbsync-jake
    image: jakewharton/mbsync:latest
    restart: unless-stopped
    volumes:
      - /tanker/backup/jake/mail:/mail
      - ${USERDIR}/docker/mbsync-jake:/config
    environment:
      # Hourly
      - "CRON=0 * * * *"
      - "CHECK_URL=https://hc-ping.com/..."
```

For information on how to set up the container please see [the repo's README][readme].

 [readme]: https://github.com/JakeWharton/docker-mbsync/#readme

#### Storage

Just like the ["Data Storage" and "Data Replication"][ds] sections from the last post, the backup goes to a dedicated ZFS filesystem. This filesystem is regularly snapshotted to provide local history. The data and all its snapshots are also synchronized to [rsync.net][rsync] for an off-site copy.

 [ds]: /removing-google-as-a-single-point-of-failure/#data-storage
 [rsync]: https://rsync.net

```
$ zfs list
NAME                          USED  AVAIL     REFER  MOUNTPOINT
tanker                       18.4T  2.08T      151K  /tanker
tanker/backup                 529G  2.08T      151K  /tanker/backup
tanker/backup/angela          172G  2.08T      140K  /tanker/backup/angela
tanker/backup/angela/photos   172G  2.08T      172G  /tanker/backup/angela/photos
tanker/backup/jake            337G  2.08T      151K  /tanker/backup/jake
tanker/backup/jake/drive     78.9G  2.08T     78.9G  /tanker/backup/jake/drive
tanker/backup/jake/mail      12.1G  2.08T     12.1G  /tanker/backup/jake/mail
tanker/backup/jake/photos     246G  2.08T      148G  /tanker/backup/jake/photos
```

I didn't bother enabling compression on the filesystem because it's only 12GiB. I suspect it would compress very well and it's something that I can always turn on later.

---

I did this migration one week after the previous post so I've been on Fastmail for about three weeks now. In general it's been a positive experience. The Android app is hybrid so sometimes it feels a bit weird, but otherwise the clients have some nice features. My favorite so far is how it deals with quoted sections in long threads:

<img src="/static/post-image/fastmail-quote.png" alt="Screenshot of Fastmail showing a large quoted section collapsed" style="border: 1px solid #ddd;"/>

Having much more control over my email, photos, and files is comforting but I sincerely hope I never need to rely on these backups.

Once configured the Docker containers have been almost entirely maintenance free. I haven't touched the photos or files sync for over a month now. Sometimes it hiccups and notifies me, but it's always recovered on its own.

<img src="/static/post-image/healthchecks.png" alt="Screenshot of Slack channel showing healthchecks.io notifications of sync being down and then recovering an hour later" style="border: 1px solid #222;"/>

Now that Google is mostly removed as a single point of failure (I'm still relying on them for Keep and employment for now), it seems like getting automated backups rolling for all my GitHub projects is the next most pressing matter.
