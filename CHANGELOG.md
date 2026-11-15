# Arc changelog

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
