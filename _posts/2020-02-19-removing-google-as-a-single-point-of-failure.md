---
title: 'Removing Google as a Single Point of Failure'
layout: post

categories: post
tags:
- Linux
---

I want to remove Google as a single point of failure in my life. They have two decades of my email. They have two decades of my photos. They have the only copy of thousands of documents, projects, and other random files from the last two decades.

Now I trust Google _completely_ in their ability to correctly retain my data. But I think it's clear that over the last 5 years the company has lost something intrinsically important in the way it operates. I no longer trust them not to permanently lock me out of my account. And I say this as a current Google employee.

This year I've embarked on a mission to reclaim ownership of my data. This does not mean that I'm going to stop using Google products. Quite the opposite. Gmail, Google Photos, and Google Drive will remain the source-of-truth for all of the things I listed above. What's different is that should Google disappear tomorrow (or just my account) I would lose no data.


### Get Your Data

#### Step 1: Takeout

The first thing you need to do **today** is visit [takeout.google.com](https://takeout.google.com/) and export your Gmail, Photos, and Drive data (and anything else you want). This will send you links to a set of 50GB `.tar.gz` files of your data that you can download.

That is, provided it works. It took me 5 attempts of exporting just my Photos data to have one succeed. Persistence pays off, though, so don't give up even though this is a slow process. Get. Your. Data.

Google providing the Takeout service is amazing, but as far as a backup solutions go it is woefully inadequate. It's an extremely manual, slow, and non-incremental process. However, it's also comprehensive in ways that no other solution can match. Because of that, I have a monthly recurring task to perform a Takeout. Do it during a boring meeting so it feels less of a chore and more of a welcome distraction.

Seriously, do this today!


#### Step 2: Drive Sync

The [rclone][1] tool can incrementally sync your Google Drive contents. It will also take Google's proprietary document formats and convert them into well-defined standard formats (which usually means Microsoft Office formats).

 [1]: https://rclone.org/

I run `rclone` hourly using the [bcardiff/docker-rclone][2] Docker container onto a large, redundant storage array (more on this array later). This container is nice because it pings [healthchecks.io](https://healthchecks.io/) after each hourly sync. The check is set up to expect an hourly ping with a grace period of two hours.

 [2]: https://github.com/bcardiff/docker-rclone


#### Step 3: Photo Sync

While Google Photos does have an API, it [does not provide access to the original image](https://issuetracker.google.com/issues/112096115). This is some bullshit. I pay for Google Drive and Google Photos storage but I can only access original files for Drive. Some bullshit.

Thankfully, after [tweeting about said bullshit][3] I was pointed at the [gphotos-cdp][4] tool (built by some _very_ smart people). This uses the Chrome DevTools protocol to drive the Google Photos website and download the original photos one-by-one. This is awful and awesome and scary and it totally works!

 [3]: https://twitter.com/JakeWharton/status/1222017202662125568
 [4]: http://github.com/perkeep/gphotos-cdp

In an effort to automate this, I wrapped the tool up in a Docker container as [jakewharton/gphotos-sync][5] which can run it on a periodic schedule and uses the same [healthchecks.io](https://healthchecks.io/) service as the rclone container. The initial setup is a little rough, but I've been running two instances hourly for two weeks without incident. Try it out!

 [5]: https://github.com/JakeWharton/docker-gphotos-sync


#### Step 4: Gmail Sync

I looked into a bunch of tools to do backup Gmail but I couldn't find one that was still maintained and still worked. This is bad. Takeout is a start for this, but I want something more real-time.

Anyone have a solution here? Please let me know!


### Data Storage

I recently built a brand new home server with the intent of using it for storing backups of my Google data (among other things). It has four 8TB drives in a ZFS pool to ensure data is written to more than one physical drive. ZFS is an incredible storage technology that can ensure data is written in a way that is resilient to both drive failures and bitrot on both the writing and reading side.

ZFS supports creating separate filesystems as easily as you would normally create folders. Each filesystem can manage things like storage quotas and their own snapshots of content. Each of the Docker containers running rclone or gphotos-cdp writes into its own ZFS filesystem.

```
$ zfs list
NAME                          USED  AVAIL     REFER  MOUNTPOINT
tanker                       17.8T  2.71T      151K  /tanker
tanker/backup                 417G  2.71T      151K  /tanker/backup
tanker/backup/angela          170G  2.71T      140K  /tanker/backup/angela
tanker/backup/angela/photos   170G  2.71T      170G  /tanker/backup/angela/photos
tanker/backup/jake            227G  2.71T      151K  /tanker/backup/jake
tanker/backup/jake/drive     78.9G  2.71T     78.9G  /tanker/backup/jake/drive
tanker/backup/jake/photos     148G  2.71T      148G  /tanker/backup/jake/photos
```

I currently use [znapzhot][6] to recursively create automatic snapshots of all these filesytems under "tanker/backup". My current policy is:

 [6]: https://www.znapzend.org/

 * Hourly snapshots retained for one day.
 * Daily snapshots retained for one month.
 * Monthly snapshots retained for one year.

This policy is a hedge against any deleted or changed file. The simplicity of `cd`-ing into the hidden `.zfs` directory means these older copies are easily browsed, if ever needed.

### Data Replication

The frequently-repeated, best-practice rule for data storage is the "3–2–1 rule". That is: three copies of the data, across two storage mediums, with one off-site location. In this framework, Google serves as one copy, one storage medium, and one off-site location. The local backups that we're synchronizing serve as a second copy and a second storage medium (HDDs vs. the cloud).

For the third copy, I chose [rsync.net](https://rsync.net/) which is quite the nerdy backup solution. Normally turning back to rclone for synchronizing the data to Dropbox or Backblaze would be an obvious solution. But rsync.net is unique in that they give you direct access to a ZFS zpool over SSH as root. This means that I can not only synchronize the latest data, but I can also synchronize the historical snapshots of it from the last year. The znapzend tool that I am already using handles sending the incremental snapshots as they're taken. While rsync.net is a slightly more expensive alternative for cloud storage, the raw ZFS access and ability to store historical snapshots makes it worthwhile.

### Self Hosting

In the unlikely event that Google implodes (or the far-more-likely scenario that they lock you out of your account) your data may be backed up but is otherwise relatively inaccessible. This is not very useful.

So far I have been serving read-only copies of my "tanker/backup" folder using NextCloud via the [linuxserver/nextcloud](https://github.com/linuxserver/docker-nextcloud) Docker container. This not only affords me access on the go, but I can also easily share content with others.

NextCloud is a generic file host that offers document editing, photo viewing, and video playback in addition to just serving raw files. It offers many similar features to Google Drive. For example, if you do not want to set up the gphotos-cdp tool to back up your photos, you can run the NextCloud app on your phone which can automatically synchronize new photos to your server.

In order to expose NextCloud to the internet, you need, at minimum, knowledge of your IP address. While I do have business internet at home, I don't have a static IP. Instead, I use the [oznu/cloudflare-ddns](https://github.com/oznu/docker-cloudflare-ddns) Docker container to update a Cloudflare DNS A record on one of my domains.

Instead of exposing NextCloud directly to the internet, I use the [traefik](https://containo.us/traefik/) Docker container as a reverse proxy. It takes care of talking to Let's Encrypt to keep a valid SSL certificate in rotation as well as routing traffic for the domain to the NextCloud container.

### Docker

The NextCloud, Traefik, Cloudflare DDNS, rclone, and gphotos-cdp containers are all managed by Docker Compose. This makes it easy to update and manage their configuration.

In order to monitor the host I also run [Netdata](https://netdata.cloud/) and [Portainer](https://www.portainer.io).

Here's my `docker-compose.yml`:
```yaml
version: "3.6"

services:
  portainer:
    container_name: portainer
    image: portainer/portainer
    command: -H unix:///var/run/docker.sock
    restart: always
    ports:
      - "11080:9000"
    volumes:
      - ${USERDIR}/docker/portainer/data:/data
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      - TZ=${TZ}

  netdata:
    container_name: netdata
    image: netdata/netdata
    restart: unless-stopped
    hostname: netdata
    ports:
      - 19999:19999
    environment:
      - PGID=998 #docker group
    cap_add:
      - SYS_PTRACE
    security_opt:
      - apparmor:unconfined
    volumes:
      - ${USERDIR}/docker/netdata:/etc/netdata:ro
      # For monitoring:
      - /etc/passwd:/host/etc/passwd:ro
      - /etc/group:/host/etc/group:ro
      - /etc/os-release:/etc/os-release:ro
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /var/log/smartd:/var/log/smartd:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro

  traefik:
    container_name: traefik
    image: traefik
    command:
      - "--api.insecure=true"
      - "--providers.docker=true"
      - "--providers.docker.exposedbydefault=false"
      - "--entrypoints.http.address=:80"
      - "--entrypoints.https.address=:443"
      - "--certificatesresolvers.letsencrypttls.acme.tlschallenge=true"
      - "--certificatesresolvers.letsencrypttls.acme.email=example@example.com"
      - "--certificatesresolvers.letsencrypttls.acme.storage=/letsencrypt/acme.json"
    ports:
      - "80:80"
      - "443:443"
      - "8080:8080"
    volumes:
      - "${USERDIR}/docker/traefik/letsencrypt:/letsencrypt"
      - "/var/run/docker.sock:/var/run/docker.sock:ro"
    labels:
      - "traefik.enable=true"
      # HTTP-to-HTTPS Redirect
      - "traefik.http.routers.http-catchall.entrypoints=http"
      - "traefik.http.routers.http-catchall.rule=HostRegexp(`{host:.+}`)"
      - "traefik.http.routers.http-catchall.middlewares=redirect-to-https"
      - "traefik.http.middlewares.redirect-to-https.redirectscheme.scheme=https"

  cloudflare-ddns:
    container_name: cloudflare-ddns
    image: oznu/cloudflare-ddns
    restart: unless-stopped
    environment:
      - API_KEY=apikey
      - ZONE=example.com
      - SUBDOMAIN=*

  nextcloud:
    container_name: nextcloud
    image: linuxserver/nextcloud
    restart: unless-stopped
    environment:
      - TZ=${TZ}
      - PUID=${PUID}
      - PGID=${PGID}
    volumes:
      - ${USERDIR}/docker/nextcloud:/config
      - /tanker/nextcloud:/data
      - /tanker/backup:/backup:ro
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.nextcloud.rule=Host(`files.example.com`)"
      - "traefik.http.routers.nextcloud.entrypoints=https"
      - "traefik.http.routers.nextcloud.tls.certresolver=letsencrypttls"

  rclone-drive-jake:
    container_name: rclone-drive-jake
    image: pfidr/rclone
    restart: unless-stopped
    volumes:
      - ${USERDIR}/docker/rclone-drive-jake:/config
      - /tanker/backup/jake/drive:/gdrive
    environment:
      - "UID=${PUID}"
      - "GID=${PGID}"
      - "TZ=${TZ}"
      - "SYNC_SRC=gdrive:"
      - "SYNC_DEST=/gdrive"
      - "CHECK_URL=https://hc-ping.com/..."
      # Hourly
      - "CRON=0 * * * *"
      # TODO update to https://github.com/rclone/rclone/issues/2893 when released
      - "SYNC_OPTS=-v --drive-alternate-export"

  gphotos-sync-jake:
    container_name: gphotos-sync-jake
    image: jakewharton/gphotos-sync:latest
    restart: unless-stopped
    volumes:
      - ${USERDIR}/docker/gphotos-sync-jake:/tmp/gphotos-cdp
      - /tanker/backup/jake/photos:/download
    environment:
      - TZ=${TZ}
      # Hourly
      - "CRON=0 * * * *"
      - "CHECK_URL=https://hc-ping.com/..."

  gphotos-sync-angela:
    container_name: gphotos-sync-angela
    image: jakewharton/gphotos-sync:latest
    restart: unless-stopped
    volumes:
      - ${USERDIR}/docker/gphotos-sync-angela:/tmp/gphotos-cdp
      - /tanker/backup/angela/photos:/download
    environment:
      - TZ=${TZ}
      # Hourly
      - "CRON=0 * * * *"
      - "CHECK_URL=https://hc-ping.com/..."
```

All of the containers store their configuration in `${USERDIR}/docker` which is in my home directory. This folder is mounted as a ZFS filesystem on a partition of the OS drive. It has a znapzend snapshot policy, is replicated into `/tanker/backup/home`, and is synchronized to rsync.net. In the event of this machine failing or being destroyed it should be fairly easy to set up a replacement.

---

So far I'm pretty happy with this setup for backing up my Google Drive and Photos content. The apps for Drive and Photos are best-in-class and so I prefer to keep using them as the source of truth as long as possible. It's nice to know that NextCloud could step in here if needed, but hopefully it never comes to that.

Gmail backups remain a problem to be solved. It's also a _huge_ problem that I cannot take control of my email address if it were needed. The Gmail webapp and mobile app also haven't seen innovation in a decade and increasingly feel like legacy software. The thought of migrating my email is daunting, but it feels like it's looming.

I continue to beleive that trusting Google with your data is a safe bet, but it is not a sufficient backup strategy by itself. Take control of your data.
