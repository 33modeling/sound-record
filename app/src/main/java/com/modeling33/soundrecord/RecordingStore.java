package com.modeling33.soundrecord;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RecordingStore {
    private static final String RECORDINGS_RELATIVE_PATH = "Recordings/Voice Recorder";
    private static final String MIME_TYPE = "audio/mp4";
    private static final String EXTENSION = ".m4a";

    private RecordingStore() {
    }

    public static Uri save(Context context, File source) throws IOException {
        if (source == null || !source.exists() || source.length() == 0L) {
            throw new IOException("Recording file is missing");
        }

        String fileName = createFileName();
        Uri savedUri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? saveWithMediaStore(context, source, fileName)
                : saveWithFileApi(context, source, fileName);
        deleteQuietly(source);
        return savedUri;
    }

    public static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static Uri saveWithMediaStore(Context context, File source, String fileName) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Audio.Media.TITLE, titleFromFileName(fileName));
        values.put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, RECORDINGS_RELATIVE_PATH);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore insert failed");
        }

        boolean saved = false;
        try (InputStream input = new FileInputStream(source);
             OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("MediaStore output stream failed");
            }
            copy(input, output);
            saved = true;
        } finally {
            if (saved) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Audio.Media.IS_PENDING, 0);
                resolver.update(uri, done, null, null);
            } else {
                resolver.delete(uri, null, null);
            }
        }

        return uri;
    }

    private static Uri saveWithFileApi(Context context, File source, String fileName) throws IOException {
        File targetDir = new File(Environment.getExternalStorageDirectory(), RECORDINGS_RELATIVE_PATH);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Unable to create recording directory");
        }

        File target = uniqueFile(targetDir, fileName);
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            copy(input, output);
        }

        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                new String[]{target.getAbsolutePath()},
                new String[]{MIME_TYPE},
                null
        );
        return Uri.fromFile(target);
    }

    private static File uniqueFile(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) {
            return file;
        }

        String title = titleFromFileName(fileName);
        int suffix = 1;
        do {
            file = new File(dir, title + " (" + suffix + ")" + EXTENSION);
            suffix++;
        } while (file.exists());
        return file;
    }

    private static String createFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "Voice " + timestamp + EXTENSION;
    }

    private static String titleFromFileName(String fileName) {
        return fileName.endsWith(EXTENSION)
                ? fileName.substring(0, fileName.length() - EXTENSION.length())
                : fileName;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }
}
