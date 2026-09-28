# NewTube website

The landing page for NewTube, served at <https://newtube-app.github.io/>.
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
