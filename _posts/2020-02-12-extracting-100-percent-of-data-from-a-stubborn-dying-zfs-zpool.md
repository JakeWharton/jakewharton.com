---
title: 'Extracting 100% of Data From a Stubborn, Dying ZFS Pool'
layout: post

categories: post
tags:
- Linux
---

In 2010 I built a home server with five 2TB drives. It ran Solaris and ZFS for the redundancy and data checksumming to ensure no data could be lost or corrupted. Just 16 months later five 3TB drives were added to the pool. This computer took the 2600-mile trip to live in San Francisco with me. It then endured the 2600-mile return trip when I left.

Having sat unplugged for five years, I recently powered the server back on for new workloads. But relying on 10 ten-year-old hard drives in 2020 is asking for cascading failure. And not only were the drives old, they've experienced physical trauma. So instead I built a new server and endeavored to migrate the data.

During the transfer the drives exhibited consistent read failures as expected, but ZFS was able to transparently mitigate them. Occasionally, though, the pool would lock up in a way that could only be fixed with a hard reboot. These lock ups sent me on a weird journey of software and hardware orchestration to complete the data transfer.

### Symptoms

During transfer of the data, progress would stall randomly in a way that seemingly could not be killed. CTRL+C had no effect. No `kill` signal had an effect. Even last-resort `shutdown -r now`s did nothing.

The system was oddly otherwise responsive. You could SSH in from another tab and poke around. `ps` showed that the transfer process was in the "D+" state which was uninterruptible sleep in the foreground.

```
jake     21749  1.1  0.0   8400  2124 pts/0    D+   23:42   0:00 rsync ...
```

That explained why the process wouldn't die. The `dmesg` output also confirmed the problem happened deep in the I/O stack.

```
[ 3626.101527] INFO: task rsync:30680 blocked for more than 120 seconds.
[ 3626.101547]       Tainted: P           O      5.3.0-26-generic #28-Ubuntu
[ 3626.101563] "echo 0 > /proc/sys/kernel/hung_task_timeout_secs" disables this message.
[ 3626.101580] rsync           D    0 30680      1 0x00000000
[ 3626.101584] Call Trace:
[ 3626.101590]  __schedule+0x2b9/0x6c0
[ 3626.101596]  schedule+0x42/0xb0
[ 3626.101601]  schedule_timeout+0x152/0x2f0
[ 3626.101717]  ? __next_timer_interrupt+0xe0/0xe0
[ 3626.101730]  io_schedule_timeout+0x1e/0x50
[ 3626.101756]  __cv_timedwait_common+0x15e/0x1c0 [spl]
[ 3626.101767]  ? wait_woken+0x80/0x80
[ 3626.101790]  __cv_timedwait_io+0x19/0x20 [spl]
[ 3626.102007]  zio_wait+0x11b/0x230 [zfs]
[ 3626.102166]  dmu_buf_hold_array_by_dnode+0x1db/0x490 [zfs]
[ 3626.102322]  dmu_read_uio_dnode+0x49/0xf0 [zfs]
[ 3626.102523]  ? zrl_add_impl+0x31/0xb0 [zfs]
[ 3626.102680]  dmu_read_uio_dbuf+0x47/0x60 [zfs]
[ 3626.102880]  zfs_read+0x117/0x300 [zfs]
[ 3626.103086]  zpl_read_common_iovec+0x99/0xe0 [zfs]
[ 3626.103292]  zpl_iter_read_common+0xa8/0x100 [zfs]
[ 3626.103496]  zpl_iter_read+0x58/0x80 [zfs]
[ 3626.103509]  new_sync_read+0x122/0x1b0
[ 3626.103525]  __vfs_read+0x29/0x40
[ 3626.103536]  vfs_read+0xab/0x160
[ 3626.103547]  ksys_read+0x67/0xe0
[ 3626.103558]  __x64_sys_read+0x1a/0x20
[ 3626.103569]  do_syscall_64+0x5a/0x130
[ 3626.103581]  entry_SYSCALL_64_after_hwframe+0x44/0xa9
[ 3626.103594] RIP: 0033:0x7fe95027a272
[ 3626.103608] Code: Bad RIP value.
[ 3626.103616] RSP: 002b:00007ffd1cf2b8c8 EFLAGS: 00000246 ORIG_RAX: 0000000000000000
[ 3626.103629] RAX: ffffffffffffffda RBX: 00005649e1948980 RCX: 00007fe95027a272
[ 3626.103637] RDX: 0000000000040000 RSI: 00005649e1993120 RDI: 0000000000000004
[ 3626.103645] RBP: 0000000000040000 R08: 00000002a1df57af R09: 00000000108f57af
[ 3626.103654] R10: 00000000434ed0ba R11: 0000000000000246 R12: 0000000000000000
[ 3626.103662] R13: 0000000000040000 R14: 0000000000000000 R15: 0000000000000000
```

Visual inspection of the computer also showed anywhere from one to three drive LEDs were solid. None of the drive arms were moving to actually access data (the platters were still spinning).

<img src="/static/post-image/transfer-leds.gif" height="512px" alt="Picture of stuck LEDs on hard drive cage"/>

If there was a way to recover from this state I could not find it.


###  Mitigation

The only way out of this state was a hard reboot which required holding down the power button. But after a reboot another lockup would occur randomly from within 10 seconds, to a few minutes in, to sometimes many hours later. Since I was only here to get data off of the machine, I did not want to spend a lot of time fixing the problem when a simple hard reboot was enough to unblock progress.

While seemingly random, I estimated a 100 hard reboots would be all that was needed. This meant progress could only be made during the day which increased transfer time from about 4 days to 10. Coupled with the restart time and then resuming transfer it looked more like 12 days would be required.

I decided this was unfortunate, but doable. I would work from my basement next to the machine which was hooked up to display on a TV. Whenever I noticed it was locked up, I would hard reboot the machine, wait for it to boot, and then resume the transfer by typing on its keyboard. In two weeks it would be over with.

After one day of working next to the machine I knew I needed to find a better solution. That day it had locked up 20-30 times which was triple what I had estimated for one day. Not only were the occurrences more frequent, but it took me a while to notice and using a keyboard attached to the server to restart the transfer was tedious.

In order for this transfer to complete with my sanity intact I needed to somehow automate the process. There were two problems to solve: figuring out when `rsync` was hung and performing a hard reboot.


### Automating Detection

Because `rsync` was stuck deep in the I/O stack there was no chance for timeouts to apply. Neither its transfer-based timeout or a timeout on the SSH connection would cause it to exit when hung. Even when I moved `rsync` to run from the new machine I could not get a timeout to trigger. Running on the new machine did allow killing the process, which was a start.

Since `rsync` wouldn't exit normally, I decided to automate hung detection the same way I checked manually: monitoring the output. If the last two lines of the output (which show the current file and transfer progress) haven't changed in 10 seconds we consider the transfer to be hung.

```bash
# Clear state from previous runs to ensure we can detect immediate hangs.
truncate -s 0 rsync.txt

rsync -ahHv --progress \
  theflame:/tanker/* \
  /tanker | tee rsync.txt &

LAST_OUTPUT=""
while sleep 10
do
  NEW_OUTPUT=$(tail -2 rsync.txt)
  if [[ "$LAST_OUTPUT" == "$NEW_OUTPUT" ]]; then
    break # Output has not changed, assume locked up.
  fi
  LAST_OUTPUT="$NEW_OUTPUT"
done
```

This script will now exit when the transfer is hung. I could now detect hangs by playing beeps or sending myself a push notification with `curl` on exit. Using this script on the second day meant that almost no time was wasted waiting for me to notice the transfer had stopped. I was still hard rebooting the machine and re-starting the script 20-30 times, though.


### Automating Reboot

Last year I got a TP-Link Kasa Smart Plug as a stocking stuffer. I found a few sites which detailed how to use their undocumented API but while I was able to authenticate I was unable to toggle the power. Thankfully they have integration with IFTTT. I linked the plug and set up two applets which were each triggered by webhook.

<img alt="IFTTT Applets for powering on and off the plug" src="/static/post-image/transfer-applets.png" height="141"/>

I hooked the old server's power through the plug and I could now control its power with two `curl` commands!

<img alt="IFTTT Applets for powering on and off the plug" src="/static/post-image/transfer-plug.jpg" height="300"/>

Integrating this into the script was a bit more complicated than expected. I started with a simple infinite loop running the above sync and then doing a power cycle with a delay.

```bash
while :
do
  # truncate, rsync, while loop from above...

  echo "POWER: off"
  curl -X POST -s https://maker.ifttt.com/trigger/power_off/with/key/... > /dev/null
  sleep 5

  echo "POWER: on"
  curl -X POST -s https://maker.ifttt.com/trigger/power_on/with/key/... > /dev/null
  echo "Waiting 50s for startup..."
  sleep 50
done
```

After a few successful reboots the script would endlessly power-cycle the server or it would simply remain off. This was because IFTTT has no guarantees on latency or order of events. Instead of using `sleep` to time things, I switched to monitoring the actual machine for its state through SSH.

```bash
while :
do
  # Loop until SSH appears to confirm power on.
  ssh -q -o ConnectTimeout=1 theflame exit
  if [[ $? != 0 ]]; then
    echo "SSH is not available. waiting 5s..."
    sleep 5
    continue
  fi

  # truncate, rsync, while loop from above...

  echo "POWER: off"
  curl -X POST -s https://maker.ifttt.com/trigger/power_off/with/key/... > /dev/null

  while :
  do
    sleep 5
    ssh -q -o ConnectTimeout=1 theflame exit
    if [[ $? != 0 ]]; then
      break # SSH is down!
    fi
    echo "POWER: SSH is still available. Waiting 5s..."
  done

  echo "POWER: on"
  curl -X POST -s https://maker.ifttt.com/trigger/power_on/with/key/... > /dev/null
  echo "Waiting 50s for startup..."
  sleep 50
done
```

Now when IFTTT was delayed in delivering the `power_off` event the script would wait to confirm the machine powered off. This would sometimes spike as high as 10 minutes. But whenever it eventually triggered, 5 seconds later the `power_on` event would be sent and the machine would start coming back up. After 50 seconds it confirms SSH availability before restarting the `rsync`.

Sadly I didn't capture a video of this in action. I did capture some of the output to excitedly share with some friends as they watched me go down this rabbit hole though.

```
Other/backup/presentations/...
          1.20G  79%   79.29MB/s    0:00:25
POWER: off
POWER: on
Waiting 50s for startup...
Starting rsync...
receiving incremental file list
Other/
Other/backup/presentations/...
          1.45G  95%   81.64MB/s    0:00:05
```

I let this script run for two days and it managed to complete the transfer of all files. IFTTT reports that the `power_on` and `power_off` events were each triggered 151 times! A few hours of scripting and a $10 gift saved me from two weeks of doing this myself.


### Final Thoughts

Had I known the drives would be so much trouble I would have taken a totally different approach. A better option would have been to run `dd` on the drives to copy their raw content to 2TB and 3TB files which would do a single pass across the platters. I think this would have been less likely to cause a freeze than the random access that `rsync` was doing. Then I could mount these files as storage on the new machine, import them as a ZFS pool, and do a local `zfs send | zfs recv` to get the data out.

I chose to use `rsync` over `zfs send | zfs recv` because I was unable to get a snapshot to complete before locking up. Once the initial transfer completed, I did a second pass using this script where `rsync` did a checksum of the file content on both ends (normally it only compares size and date). This found a few inconsistencies and re-transfered about 100GB of data.

Here's the full script in its entirety: [gist.github.com/JakeWharton/968859c48fd1bd7e85a0f78a164253b9](https://gist.github.com/JakeWharton/968859c48fd1bd7e85a0f78a164253b9). There are some additional features such as mounting the old ZFS pool as readonly on boot and using a third IFTTT trigger to update a push notification on my phone for the current item.

More on what this new machine is for in future posts.

<img src="/static/post-image/transfer-new.jpg" alt="Picture of new server"/>