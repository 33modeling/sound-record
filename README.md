# SR

Minimal Android voice recorder.

- Lower-right blank buttons: start recording and stop recording.
- Stop opens a discard/save confirmation bar with discard on the left and save on the right.
- Recording runs in a microphone foreground service so it can continue in the background.
- Saved files are AAC in an MPEG-4 container with a `.m4a` extension.
- Saved file names use the Samsung-style sequence, for example `Voice 001.m4a`.
- On Android 10 and newer, files are inserted into `Recordings/Voice Recorder` through `MediaStore`.
