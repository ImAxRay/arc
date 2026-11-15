# Changelog

## 0.9.3

Everything since 0.9.0. Some features are marked Beta: they can break and each has its own off switch.

### Home

- **Home is built from modules now**, stacked in one list: Speed dial, New releases, Recents, Quick picks, Forgotten favourites and Your playlists (when you're signed in). Modules skip whatever the speed dial already shows.
- **The day and a greeting sit at the top.** The greeting follows the clock and fades away as you scroll.
- **The speed dial got reworked:**
  - Page one opens with a big Continue tile for the song you stopped on. Tap it to pause or resume, and it shows how much of the song is left.
  - One tile per song, no video duplicates. Empty slots get filled from what you play most.
  - Only whole pages, and each page gets one big 2×2 tile.
  - Tiles stay put until you pull to refresh, so a tapped tile no longer shuffles the grid.
  - It paints right away on a cold start instead of showing a skeleton, and still changes on every launch.
  - Page dots replace the old line: the current page is a pill that flows into the next as you swipe.
  - A size setting, and tiles always fit above the mini player.
- **Glow on press.** Pressed Home tiles bloom in their cover's colour, with a band of flickering dots inside the rim. The mini player rests with dim dots along its lit side, and the play button gets a soft glow disc. Appearance > Glow on press turns it off. It also switches off in battery saver and with animations off.
- **Tilt covers.** Covers look sunk into the screen and shift as you tilt the phone. Off by default.
- **New releases:** the Single / EP / Album tag now sits on the cover's corner in the cover's own colours.
- **On a cold start Home lands in**, the greeting and modules arriving one after another. Off in battery saver.
- Mood and genre pages show YouTube's own sections: songs and videos as side-scrolling rows, playlists and albums as rows of covers.
- The Your playlists header opens Library on Playlists.
- The Speed dial label is gone, so New releases sits higher and clear of the Arc line.

### Player

- **Music videos (Beta).**
  - When a song has a music video, it can play in the cover spot over the song's own audio. Album tracks get swapped for their video at the same position.
  - A full-screen watch view with gestures.
  - Separate quality for Wi-Fi and mobile data. Hold the watch button, or tap the quality pill in full screen, to pick from what that video actually has. Wide films count as 1080p, not 800p.
  - Videos keep the screen on while playing.
  - Radio and autoplay stay songs-only.
- **Cover-coloured ground.**
  - Under the controls, the cover's reflection fades into one colour from the cover's lower edge.
  - The play button, line and end dot take a pale tint of the cover.
  - The open player always uses the dark version of your theme.
- **A minimise chevron** in the top-left corner rolls the player down.
- **The player layout switch** (Thumb arc / Mini) lives in the player menu.
- **Skip non-music parts with SponsorBlock (Beta).** Talk, skits and long intros marked as off-topic get jumped over. Only a 4-character hash prefix of the video id is sent. On by default; the switch is in Sound and playback.
- **The player opens in its plain view**; vinyl is one switch away.
- Switching between song and video no longer flashes the status bar back in.

### Lyrics

- **Shout lines** grow as they're sung and send up balloons.
- Scrolling follows seeks and resyncs, and ad-libs fade in place.

### Library, search and playlists

- **Hold to select in more places:** search results, library search, charts and now your own playlists. Hold a song, then drag up or down to pick more. The selection bar sits on the mini player with Play, Shuffle, Next, Queue, Like all and More. In playlists, More has Remove from playlist.
- **Painting hearts** (hold a heart and drag) scrolls the list when you reach its edge.
- **Arc's own slider everywhere**: settings, lyrics offset, sleep timer, volume, EQ, length rules and the snippet editor. It swells under your thumb, ticks on steps and jumps to where you tap.

### Google Drive

- **Pick folders or songs in Google's own picker.** Arc only sees what you picked.
- Whole folders for testers next to picked songs, with a reminder before sign-in runs out.
- Songs from folders Drive refuses to open are never dropped.

### Widgets

- Arc vinyl widget with a spinning record and shuffle.
- Speed dial widget with centred controls, dynamic colours and current song info.

### Look and settings

- **First start asks "smoother or prettier?"** and sets the effects to match.
- **Beta labels** on Snippets, Spotify import, music videos and SponsorBlock.
- Settings search points at the rows that exist now, alarms included (they live on the Queue page).
- The old full Player settings page, which nothing opened anymore, is gone.

### Battery and smoothness

- **A still Home doesn't redraw anymore.** It draws about 17 frames a second for the background's own slow animation, instead of 60.
  - The speed dial was rebuilding itself every frame. Fixed.
  - Long names on Home stay still until you touch Home, then roll once. A name that's already rolling isn't restarted.
  - The dice tile no longer redraws while idle.
- Long names elsewhere roll three times, then rest.
- Release builds no longer write debug logs.

### Fixes

- No crash when Home shows up before the music service has finished starting (seen on a phone's first launch after boot).
- No crash when Home asks to read songs ahead before the service is up.
- No crash when a song is queued twice and the cover carousel scrolls.
- Playback stats no longer crash the app on a song swap.
- Crash reports are named after Arc.
- The speed dial keeps its tiles when Home scrolls away or you switch tabs.
- Hearts no longer buzz when a finger just lands on them.
- The cover glow ends unevenly per cover instead of along a straight line, and songs without a cover fade into the player's ground.

### Repo

- The README is in my own words now, with credits to Metrolist, Dial, Mochy and BitChord.
- Metrolist's workflows and store files are gone. Commits use a private email address.
