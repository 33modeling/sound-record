package com.modeling33.soundrecord;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
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
import java.util.HashSet;
import java.util.Set;

public final class RecordingStore {
    private static final String RECORDINGS_RELATIVE_PATH =
            "Recordings" + File.separator + "Voice Recorder";
    private static final String COLUMN_IS_RECORDING = "is_recording";
    private static final String MIME_TYPE = "audio/mp4";
    private static final String EXTENSION = ".m4a";

    private RecordingStore() {
    }

    public static Uri save(Context context, File source) throws IOException {
        if (source == null || !source.exists() || source.length() == 0L) {
            throw new IOException("Recording file is missing");
        }

        String fileName = createFileName(context);
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

    public static void repairSavedRecordings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            repairMediaStoreRows(context);
        } else {
            scanRecordingDirectory(context);
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
                markAsVoiceRecording(resolver, uri);
                scanSavedFile(context, fileName);
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

    private static void markAsVoiceRecording(ContentResolver resolver, Uri uri) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_RECORDING, 1);
        try {
            resolver.update(uri, values, null, null);
        } catch (RuntimeException ignored) {
            // Save must still succeed on platform builds that reserve these columns for the scanner.
        }
    }

    private static void repairMediaStoreRows(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH
        };
        String selection = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?";
        String[] args = {"%.m4a"};

        try (Cursor cursor = resolver.query(collection, projection, selection, args, null)) {
            if (cursor == null) {
                return;
            }

            int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                String relativePath = pathColumn >= 0 ? cursor.getString(pathColumn) : null;
                if (!isVoiceRecorderPath(relativePath)) {
                    continue;
                }

                String displayName = nameColumn >= 0 ? cursor.getString(nameColumn) : null;
                if (displayName == null || !displayName.endsWith(EXTENSION)) {
                    continue;
                }

                long id = idColumn >= 0 ? cursor.getLong(idColumn) : -1L;
                if (id < 0L) {
                    continue;
                }

                Uri itemUri = ContentUris.withAppendedId(collection, id);
                markAsVoiceRecording(resolver, itemUri);
                scanSavedFile(context, displayName);
            }
        } catch (RuntimeException ignored) {
            scanRecordingDirectory(context);
        }
    }

    private static void scanRecordingDirectory(Context context) {
        File targetDir = new File(Environment.getExternalStorageDirectory(), RECORDINGS_RELATIVE_PATH);
        File[] files = targetDir.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (files == null || files.length == 0) {
            return;
        }

        String[] paths = new String[files.length];
        String[] mimeTypes = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            paths[i] = files[i].getAbsolutePath();
            mimeTypes[i] = MIME_TYPE;
        }
        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                paths,
                mimeTypes,
                (path, uri) -> {
                    if (uri != null) {
                        markAsVoiceRecording(context.getContentResolver(), uri);
                    }
                }
        );
    }

    private static void scanSavedFile(Context context, String fileName) {
        File target = new File(
                Environment.getExternalStorageDirectory(),
                RECORDINGS_RELATIVE_PATH + File.separator + fileName
        );
        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                new String[]{target.getAbsolutePath()},
                new String[]{MIME_TYPE},
                (path, uri) -> {
                    if (uri != null) {
                        markAsVoiceRecording(context.getContentResolver(), uri);
                    }
                }
        );
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

    private static String createFileName(Context context) {
        Set<String> existingNames = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? existingMediaStoreNames(context)
                : existingFileNames();

        int index = 1;
        String candidate;
        do {
            candidate = String.format(java.util.Locale.US, "Voice %03d%s", index, EXTENSION);
            index++;
        } while (existingNames.contains(candidate));
        return candidate;
    }

    private static Set<String> existingMediaStoreNames(Context context) {
        Set<String> names = new HashSet<>();
        ContentResolver resolver = context.getContentResolver();
        String[] projection = {
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH
        };
        String selection = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?";
        String[] args = {"Voice %.m4a"};

        try (Cursor cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                null
        )) {
            if (cursor == null) {
                return names;
            }

            int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                String relativePath = pathColumn >= 0 ? cursor.getString(pathColumn) : null;
                if (!isVoiceRecorderPath(relativePath)) {
                    continue;
                }

                String name = nameColumn >= 0 ? cursor.getString(nameColumn) : null;
                if (name != null) {
                    names.add(name);
                }
            }
        } catch (RuntimeException ignored) {
            // If media read permission is denied, MediaStore will still protect duplicates on insert.
        }
        return names;
    }

    private static Set<String> existingFileNames() {
        Set<String> names = new HashSet<>();
        File targetDir = new File(Environment.getExternalStorageDirectory(), RECORDINGS_RELATIVE_PATH);
        File[] files = targetDir.listFiles();
        if (files == null) {
            return names;
        }

        for (File file : files) {
            names.add(file.getName());
        }
        return names;
    }

    private static boolean isVoiceRecorderPath(String relativePath) {
        if (relativePath == null) {
            return false;
        }

        String normalized = relativePath.endsWith("/")
                ? relativePath.substring(0, relativePath.length() - 1)
                : relativePath;
        return RECORDINGS_RELATIVE_PATH.equals(normalized);
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
