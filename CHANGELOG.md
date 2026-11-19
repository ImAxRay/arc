# Arc changelog

## 0.9.5

### SETTINGS OVERHAULED

- **Visual mode** at the top: Saver, Normal or Full. Saver turns off every heavy effect, caps the screen at 60 Hz and follows the phone's Battery Saver.
- **One Appearance page** with Home's background live behind it, and what each effect costs.
- Every page regrouped: values on the rows, fewer rows, fewer taps.
- **Group bar on the edge**, niagara style. Hold the sidebar to scroll thru settings.
- Storage shows what Arc keeps on your phone.
- **NEW tags** and a "New in" list point at new settings.
- New option: either the Song Repeat resets on new tracks or it does not, u choose
- Android Auto: choose and order the tabs in the car.

### Import

- **Spotify data export**: whole playlists and Liked Songs.
- Better song matching for Spotify, CSV and M3U imports.
- One Playlist file row for M3U and CSV.

### Fixes

- Songs play after a reboot on Android 17.
- No crash when the phone starts locked.
- Settings search lands on rows further down the page.
- Restoring a backup refreshes the speed dial.

## 0.9.4a

- Improved telemetry (it never leaves your phone).

## 0.9.4: Welcome home. Part 1

Everything since 0.9.3.

### Home screen got a big update!

- Changed visuals of Speed Dial
- Added modules for home screen. You can add them, rearrange and hide!
- Speed dial now can be modified as well.
- Added some extra panels to Speed Dial that you can select from editing mode
- Improved the dice button...

### Player

- **Tap the next song** to open the queue.
- Strongly coloured covers on a pale ground keep their own colour.

### Library and search

- One selected song gets **Radio** in place of Shuffle.

### Android Auto

- Covers of your own files show up.

### General

- **Updates download and install inside Arc.** Changelogs look like they do on GitHub.
- Settings search finds Tilt covers by "parallax".

## 0.9.3

Everything since 0.9.0. Some features are marked Beta: they can break and each has its own off switch.

### Home

- **Home is built from modules now**, stacked in one list: Speed dial, New releases, Recents, Quick picks, Forgotten favourites and Your playlists (when you're signed in).
- **The day and a greeting sit at the top.**
- **The speed dial got reworked:** it has its own panels now, and panels can be 2 by 2 or 3 by 3.
- **Glow animations.**
- **Tilt covers.** Parallax option for covers.
- **New releases:** the Single / EP / Album tag now sits on the cover's corner in the cover's own colours.

### Player

- **Music videos (Beta).**
  - When a song has a music video, it can play in the cover spot over the song's own audio. Album tracks get swapped for their video at the same position.
  - A full-screen watch view with gestures.
  - Separate quality for Wi-Fi and mobile data. Hold the watch button, or tap the quality pill in full screen, to pick from what that video actually has.
- **Cover colours got reworked a bit.**
- **A minimise arrow** in the top-left corner rolls the player down.
- **The player layout switch** (Thumb arc / Mini) lives in the player menu.
- **Skip non-music parts with SponsorBlock (Beta).** Talk, skits and long intros marked as off-topic get jumped over. Only a 4-character hash prefix of the video id is sent. On by default; the switch is in Sound and playback.

### Lyrics

- Scrolling follows seeks and resyncs, and ad-libs fade in place.

### Library, search and playlists

- **Hold to select in more places:** search results, library search, charts and now your own playlists. Hold a song, then drag up or down to pick more.
- **Painting hearts** (hold a heart and drag) scrolls the list when you reach its edge.
- **Arc's own slider everywhere**: settings, lyrics offset, sleep timer, volume, EQ, length rules and the snippet editor. It swells under your thumb, ticks on steps and jumps to where you tap.

### Google Drive

- **Pick folders or songs in Google's own picker.**
- Whole folders for testers next to picked songs, with a reminder before sign-in runs out.

### Look and settings

- **First start asks "smoother or prettier?"** and sets the effects to match.
- **Beta labels** on Snippets and Spotify import.
- Settings search points at the rows that exist now, alarms included (they live on the Queue page).

### Fixes

- No crash when Home shows up before the music service has finished starting (seen on a phone's first launch after boot).
- No crash when Home asks to read songs ahead before the service is up.
- No crash when a song is queued twice and the cover carousel scrolls.
- Playback stats no longer crash the app on a song swap.
- Hearts no longer buzz when a finger just lands on them.
- The cover glow ends unevenly per cover instead of along a straight line.
