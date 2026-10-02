# Phone Settings (issue #2, 2026-10-02)

Settings used to be the TV app's: `*SettingsPresenter` classes in `common/` building
`OptionCategory` lists that `MobileAppDialogActivity` drew full-screen, every choice spelled out as
an inline radio list. Issue #2 ("way too convoluted, lots of options per section") was the most
repeated complaint after launch. The phone now has its own Settings, modelled on YouTube's
(a top level of sections with icons; each section one short page; a choice shows its value and
opens a dialog) with LibreTube's grouping and wording habits.

## Before and after

Measured on API 35 emulators at the same density (420 dpi, 1080 px wide), signed out, by walking
every page, old and new, with uiautomator dumps and scrolling to the end.

| | Before (1.14.1) | After |
|---|---|---|
| Screens | 25 (incl. 9 identical SponsorBlock sub-pages and two Auto backup pages with no title) | 16 |
| Rows drawn | 1,015 + 56 headers, 800 of them inline radio options | 167 + 25 headers |
| Scrolling, all pages | about 79 screens | about 21 screens |
| Longest page | Player: 487 rows, 37.7 screens | Video menu: 39 switches, 3.2 screens (then Advanced, 2.2) |
| Choices | a header plus every option, no current value on the parent | one row with the value under it; a tap opens a radio dialog |
| TV-only rows shown | dozens (clock, screensaver, OK-button long press, ATV launcher, TV layouts…) | none |

## The tree

```
Settings
  Account                       name / email, or "Not signed in"; opens the accounts sheet
  App
    General                     Theme · Language · Location · Start screen · Interface size ·
                                Search by voice right away · Use 24-hour time
    Tabs and feeds              Tabs › · Hidden videos › · Video menu › · Thumbnails ·
                                Original titles · Order of Channels in You
    History and privacy         Watch history · Clear watch history · Don't keep search history ·
                                Clear search history (+ two turn-off-only rows, below)
  Video and audio
    Playback                    When a video ends · When you leave the app · speed · sleep timer ·
                                audio focus · Resume · Under the video
    Video quality               Default quality (Auto or a cap: 2160p…360p) · loudness · volume
    Captions                    Style · Size · Distance from the bottom · per channel · Android's settings
    SponsorBlock                on/off · what to do per kind of part · Marks on the seek bar › · More
    DeArrow                     Better titles · Better thumbnails
  Other
    Backup and restore          Back up now · Restore · Automatic backup
    Advanced                    Streaming · Live streams · Player · Network · Tabs and feeds
    About                       updates · diagnostic log · star / share · source · license
```

Things that are per video stay in the player, not here: quality for this video, audio track,
speed, captions on/off, zoom (gear › More › Zoom / aspect ratio, or pinch).

## How it is built

`smarttubetv/src/stmobile/java/com/newtube/mobile/ui/settings/`:

- `MobileSettingsActivity`: hosts one `SettingsPageFragment` per page on a fragment back stack with
  `MaterialSharedAxis.X`. Not exported; opened from You › Settings (`MobileBrowseActivity.openSettings`).
  It is registered in `ViewManager` with Home as its parent: without the mapping, `addTop()` of a
  screen with no parent clears ViewManager's stack, and Back from Settings left the app.
- `SettingsPages` (ids, the root page, About), `AppPages` (General, Tabs and feeds and its three
  sub-pages, History and privacy, Backup), `PlayerPages` (Playback, Video quality, Captions,
  SponsorBlock and its marks page, DeArrow, Advanced). Each page is a static method that reads the
  prefs and returns rows. Page ids are strings, so a recreated activity (the theme switch recreates
  it) rebuilds the same stack.
- `SettingsRow`: HEADER, LINK (opens a page or runs an action), SWITCH, CHOICE, NOTE, DIVIDER.
  Values are suppliers, so `rebuild()` after a change re-reads everything (a change can show, hide
  or enable other rows). `fromRadio(OptionCategory, title)` reuses an existing radio factory
  (language, country, caption style…) as one CHOICE row.
- `SettingsAdapter`: same row shape → rebinds in place (`PAYLOAD_VALUES`); otherwise a full refresh.
- `SettingsPageFragment`: a CHOICE opens a `MaterialAlertDialog` radio list; a tap applies and
  closes (YouTube's behaviour; Cancel is the only button). `needsRestart()` rows offer a Restart
  snackbar after the change.

Adding a setting is one line in the right page, for example:

```java
rows.add(SettingsRow.toggle(context.getString(R.string.…), context.getString(R.string.…_summary),
        playerData::isFooEnabled, playerData::setFooEnabled));
```

Write the side effects the old presenter ran next to the setter (the audits list them per row); a
row that only writes the pref is the main way this screen can be wrong.

## Rules learned while building it

- **Rows go in before the first layout** (`onCreateView`). A layout pass with 0 items makes
  `LinearLayoutManager` drop its pending saved state, so the page lost its scroll position every
  time you came back to it.
- **`android:tint`, not `app:tint`,** on a plain `ImageView`: these activities are not AppCompat, and
  `app:tint` is ignored there (the root icons were invisible in the light theme).
- **`MobileAlertDialog` sets `elevationOverlayEnabled=false`.** Without it the dialog's 24 dp
  elevation blends white into the surface: #1E1E1E became #3F3F3F. This applies to every dialog in
  the app.
- The activity is not exported: `am start` cannot open it in a test. Go through You › Settings.

## What left the screen, and why

Per-row evidence (what each pref does on the phone, with file:line) is in
[AUDIT-app.md](AUDIT-app.md) and [AUDIT-player.md](AUDIT-player.md), both reviewed by codex.

- **Rows the phone never reads** (TV layouts, the clock, screensaver, OK-button and D-pad
  behaviour, Android TV channels and launcher, TV player buttons, decoder and frame-drop fixes for TV
  boxes, the network-engine picker that media3 ignores, audio delay, and so on). Their prefs are untouched.
- **Rows the phone reads only to do harm or to do something other than the label**: the "Oculus
  fix" (landscape-locks every screen), "Ambilight"/TextureView (stops SponsorBlock skipping short
  parts), the auto-hide timeout, the likes counter (it only gated the dislike fetch), the TV
  layouts of Channels, pinned channels and Playlists, "Fullscreen mode" (unticked, it adds a TV inset
  theme). A one-shot migration in `MobileMainApplication` (`settings_redesign_defaults`) puts these
  back to their defaults once, so nobody is stuck with a value there is no row to change.
- **Removed features:** Google Drive backup (broken on the phone: its sign-in step opens the YouTube
  sign-in screen and never reaches Google), the
  GrayJay/PocketTube/NewPipe import, "Protect all settings with password" (nothing enforced it),
  turning on child mode and the start-up password (child mode does not block search or lock Settings
  as it claimed; anyone in Settings could clear the password). People who already turned either on
  get a turn-off row in History and privacy.
- **Duplicates merged:** history (General radio, Search switch, menu items) is one History and privacy
  page; "separate settings per account" and the account password stay in the accounts sheet
  (Account › Account settings) only.
- **Video menu:** the reorder UI is gone (the order is fixed and tested); items that never show on
  the phone (exit PiP, move section up, open playlist/comments, pause history, update check, select
  account) have no switch.
- **Shorts** rows are gone with Shorts (1.12.0); **Hide Mixes** too (its filter only runs on the old v1
  lists; every phone feed is v2).

Labels: every row has its own phone string in `strings_settings.xml` (English and Spanish). Other
locales fall back to English until Weblate catches up; the upstream TV strings were not reused
where their wording was the problem ("Use Web Proxy", "Enable Conscrypt", the SponsorBlock
descriptions written for a remote).
