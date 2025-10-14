---
title: 'Custom short-link redirector'

tags:
- Programming
---

I used to use Bit.ly to put links into my presentations.
Their service allowed you to customize the path portion of the link, so I was able to create links like [bit.ly/ok-libs](https://bit.ly/ok-libs).
If you were attending the talks live, watching the recording, or browsing the slides, the short URL was easy to type into a browser.

Unfortunately, the path customization of Bit.ly is a global namespace.
Short and memorable paths became increasingly hard to find.

Ten years ago I bought the jakes.link URL to solve this problem.
Bit.ly let you point custom domains at their service, and each then gets its own path namespace without risk of collision.

A few months ago bit.ly announced that they would show a preview page _with ads_ before redirecting.
Gross.
This happens for links on their domain and on custom domains for all free users.
I'd be happy to pay a few bucks a year to avoid this, but the cheapest plan which supports custom domains is $350/year (paid annually).
That's nothing short of ridiculous for the one or two links per year which I create.


### Netlify

I use Netlify for hosting this site because one of its features is server-side redirects.
This ensures that I can keep old URLs working, because a good URL is forever.
But it also makes Netlify a great candidate for a build-your-own short-link redirector.

Here's how I migrated jakes.link in three steps:

 1. Create a git repo with a `_redirects` file following [Netlify's redirect documentation](https://docs.netlify.com/manage/routing/redirects/overview/).

    ```
    /        https://jakewharton.com  302
    /how-to  https://jakewharton.com/custom-short-link-redirector/ 302
    ```

    (I use HTTP 302 redirects so that if third-party content moves over time I can at least update my redirects.)

 2. Create a project on Netlify and link it to the git repo (on GitHub or wherever else).
    It will automatically deploy your redirects to a subdomain on their domain which you can use to test (e.g., [jakes-link.netlify.app](https://jakes-link.netlify.app)).
    Pushes to the git repo will now be automatically deployed.

 3. In the "Domain management" section on the Netlify project, add your custom domain as an alias.
    You will have to either point your nameservers at Netlify to have it manage DNS automatically, or add the necessary DNS records to whatever service manages your domain.
    After a day or so, the new nameservers and DNS will propagate and everything should be working.

Try it out: [jakes.link/how-to](https://jakes.link/how-to) (link to this post).

As a nice bonus, this can all be accomplished on Netlify's free tier.
Their paid plans aren't expensive, but probably too expensive for _just_ a link redirector.


### Alternatives

I am very satisfied with the Netlify-based solution.
It's free, entirely server-side, and driven by data that I control.
Bit.ly worked for ten years, and I'll be happy if I get ten years out of Netlify, too.

The two big features lost in the move is analytics and the experience of adding a link.
I don't need analytics, and I'm very comfortable editing a file in a git repo to create new links, but perhaps you're not!
Thankfully there are plenty of alternative approaches to consider.

I briefly looked at [short.io](https://short.io) which is more of a direct competitor to bit.ly.
Their features look comprehensive, and the free and paid tiers look like good value.
If I wasn't already paying Netlify I would have probably used this service.

There are a few link-shortening apps which you could host yourself, like [yourls.org](https://yourls.org).
I do self-host a few services, but given the overwhelming simplicity of a link shortener I don't expect hosted services to churn that much.
There's just not enough value for me to host my own given how infrequently I shorten links.

Finally, you could write your own simple server-side URL shortener in about 15 lines of code and a full-featured one in about 50.
If you choose the right language, these can be deployed on various service providers under the name "workers", "functions", "lambdas", etc.
In the old, dark days I used to run [an express.js-based one](https://github.com/JakeWharton/abs.io/blob/master/web.js) for ActionBarSherlock.
With the Netlify setup having data-driven routes in a git repo I control, this is probably my fallback option.
