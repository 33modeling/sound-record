# Sound Record

Minimal Android voice recorder.

- Top blank button: start recording.
- Bottom blank button: stop recording, then choose discard or save.
- Recording runs in a microphone foreground service so it can continue in the background.
- Saved files are AAC in an MPEG-4 container with a `.m4a` extension.
- Saved file names use the Samsung-style sequence, for example `Voice 001.m4a`.
- On Android 10 and newer, files are inserted into `Recordings/Voice Recorder` through `MediaStore`.
