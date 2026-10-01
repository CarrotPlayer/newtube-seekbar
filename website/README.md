# NewTube website

The landing page for NewTube, served at <https://newtube.org/> (custom domain on GitHub Pages; `website/CNAME` must stay, or the deploy drops the domain).
`.github/workflows/pages.yml` copies this folder to the
[newtube-app/newtube-app.github.io](https://github.com/newtube-app/newtube-app.github.io) repo,
which GitHub Pages serves, and turns the old address
(aleixrodriala.github.io/newtube) into a redirect. Edit the site here, never in that repo.

It is a plain static site: `index.html`, `styles.css`, a small `main.js` and
`assets/`. There is no framework, no build step, no web fonts and no analytics.
The page makes no third-party requests except one optional call to the GitHub
API (`/releases?per_page=5`, which unlike `/releases/latest` answers 200 rather
than 404 while there are no releases) to show the latest version and link its
`*_arm64-v8a.apk` directly. If that call fails for any reason, the download
buttons keep pointing at the releases page.

The copy follows the repository's `README.md`, which is the source of truth for
features, numbers and wording. Change the README first, then mirror it here.

## Preview locally

```sh
cd website
python3 -m http.server 8000
# open http://localhost:8000/
```

All paths are relative, so the page works both at the root of a local server
and under `/newtube/` on GitHub Pages.

## Deploy

`.github/workflows/pages.yml` publishes this folder on every push to `main`
that touches `website/**` (or the workflow itself), and can be run by hand from
the Actions tab. In the repository settings, Pages must use the source
"GitHub Actions".

## Assets

- `assets/icon.svg`: the app icon, also used as the favicon.
- `assets/apple-touch-icon.png`: 180 px, full-bleed for iOS.
- `assets/icon-512.png`: structured-data image.
- `assets/og-image.jpg`: 1280x640 social preview.
- `assets/screens/*.webp`: phone screenshots at 360 and 720 px wide, and the
  landscape player at 800 and 1600 px. The videos shown are Blender Foundation
  open movies (Creative Commons).

The canonical URL, Open Graph and Twitter tags, `robots.txt` and `sitemap.xml`
use the absolute address above. Update them together if the site moves to its
own domain.

## Search

The page targets the searches "SmartTube for phone(s)", "SmartTube mobile" and
"SmartTube android phone": the title, the H1, the `#smarttube-for-phones`
section and the FAQ say it, always with "unofficial" and "built on SmartTube by
@yuliskov" next to it. The FAQ is marked up twice, as visible `<details>` and as
`FAQPage` JSON-LD; both are generated from one list, so edit them together.

### Google Search Console (not set up yet)

A `*.github.io` address can't use a Domain property (that needs DNS), so use a
URL-prefix property:

1. Sign in at <https://search.google.com/search-console>, choose **Add property**,
   then **URL prefix**, and enter `https://newtube.org/`.
2. Pick one verification method:
   - **HTML file:** download the `google<token>.html` file Google offers, put it
     in this folder unchanged (`website/google<token>.html`), commit and push to
     `main`, wait for the "Deploy website" workflow, then press **Verify**. Keep
     the file for as long as the property should stay verified.
   - **HTML tag:** copy the `<meta name="google-site-verification" ...>` tag,
     replace the commented placeholder in the `<head>` of `index.html` with it,
     push, wait for the deploy, then press **Verify**.
3. Once verified, open **Sitemaps** and submit `sitemap.xml`, then use **URL
   inspection** on the home page and **Request indexing**.
4. Optional: Bing Webmaster Tools can import the verified property from Search
   Console in one step.

The Content-Security-Policy meta tag doesn't affect either method.
