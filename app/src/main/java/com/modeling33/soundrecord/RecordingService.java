package com.modeling33.soundrecord;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.ResultReceiver;

import java.io.File;
import java.io.IOException;

public class RecordingService extends Service {
    public static final String ACTION_START = "com.modeling33.soundrecord.action.START";
    public static final String ACTION_STOP_FOR_DECISION = "com.modeling33.soundrecord.action.STOP_FOR_DECISION";
    public static final String EXTRA_RESULT_RECEIVER = "result_receiver";
    public static final String EXTRA_TEMP_PATH = "temp_path";
    public static final int RESULT_STOPPED = 1;
    public static final int RESULT_ERROR = 2;

    private static final String CHANNEL_ID = "recording";
    private static final int NOTIFICATION_ID = 33;
    private static volatile boolean active;

    private MediaRecorder recorder;
    private File tempFile;
    private PowerManager.WakeLock wakeLock;

    public static void start(Context context) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopForDecision(Context context, ResultReceiver receiver) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction(ACTION_STOP_FOR_DECISION);
        intent.putExtra(EXTRA_RESULT_RECEIVER, receiver);
        context.startService(intent);
    }

    public static boolean isRecording() {
        return active;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            handleStart();
            return START_STICKY;
        }

        if (ACTION_STOP_FOR_DECISION.equals(action)) {
            handleStopForDecision(intent);
            return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseRecorder(false);
        releaseWakeLock();
        super.onDestroy();
    }

    private void handleStart() {
        if (!hasAudioPermission()) {
            stopSelf();
            return;
        }
        if (recorder != null) {
            return;
        }

        try {
            startAsForeground();
            startRecorder();
        } catch (RuntimeException | IOException exception) {
            releaseRecorder(true);
            releaseWakeLock();
            stopForegroundCompat();
            stopSelf();
        }
    }

    private void handleStopForDecision(Intent intent) {
        ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
        try {
            File stoppedFile = stopRecorder();
            stopForegroundCompat();
            stopSelf();
            if (receiver != null && stoppedFile != null) {
                Bundle data = new Bundle();
                data.putString(EXTRA_TEMP_PATH, stoppedFile.getAbsolutePath());
                receiver.send(RESULT_STOPPED, data);
            }
        } catch (RuntimeException exception) {
            stopForegroundCompat();
            stopSelf();
            if (receiver != null) {
                receiver.send(RESULT_ERROR, Bundle.EMPTY);
            }
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startAsForeground() {
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_stat_record)
                .setContentTitle("Recording")
                .setContentText("Microphone is active")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setSound(null, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void startRecorder() throws IOException {
        File tempDir = new File(getCacheDir(), "recordings");
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new IOException("Unable to create recording cache");
        }

        tempFile = File.createTempFile("voice_", ".m4a", tempDir);
        recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new MediaRecorder(this)
                : new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioChannels(1);
        recorder.setAudioEncodingBitRate(128000);
        recorder.setAudioSamplingRate(48000);
        recorder.setOutputFile(tempFile.getAbsolutePath());
        recorder.prepare();
        acquireWakeLock();
        recorder.start();
        active = true;
    }

    private File stopRecorder() {
        if (recorder == null) {
            return null;
        }

        File stoppedFile = tempFile;
        try {
            recorder.stop();
        } finally {
            releaseRecorder(false);
            releaseWakeLock();
        }

        if (stoppedFile == null || !stoppedFile.exists() || stoppedFile.length() == 0L) {
            RecordingStore.deleteQuietly(stoppedFile);
            throw new IllegalStateException("Recording is empty");
        }
        return stoppedFile;
    }

    private void releaseRecorder(boolean deleteTempFile) {
        if (recorder != null) {
            try {
                recorder.reset();
            } catch (RuntimeException ignored) {
                // MediaRecorder can already be in an unusable state after stop failures.
            }
            recorder.release();
            recorder = null;
        }

        active = false;
        if (deleteTempFile) {
            RecordingStore.deleteQuietly(tempFile);
        }
        tempFile = null;
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            return;
        }

        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":recording"
        );
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }
}
