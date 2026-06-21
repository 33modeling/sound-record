package com.modeling33.soundrecord;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 100;

    private Button startButton;
    private Button stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        buildLayout();
        requestMissingPermissions();
        updateButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtons();
    }

    private void configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);

        startButton = new Button(this);
        startButton.setText("");
        startButton.setContentDescription("Start recording");
        startButton.setBackgroundColor(0xFF444444);
        startButton.setOnClickListener(view -> startRecording());
        root.addView(startButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        stopButton = new Button(this);
        stopButton.setText("");
        stopButton.setContentDescription("Stop and save");
        stopButton.setBackgroundColor(0xFF222222);
        stopButton.setOnClickListener(view -> stopForDecision());
        root.addView(stopButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private void startRecording() {
        if (!hasAudioPermission()) {
            requestMissingPermissions();
            return;
        }

        RecordingService.start(this);
        updateButtons(true);
    }

    private void stopForDecision() {
        if (!RecordingService.isRecording()) {
            updateButtons(false);
            return;
        }

        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                updateButtons(false);
                if (resultCode != RecordingService.RESULT_STOPPED || resultData == null) {
                    return;
                }

                String path = resultData.getString(RecordingService.EXTRA_TEMP_PATH);
                if (path != null) {
                    showSaveDialog(new File(path));
                }
            }
        };
        RecordingService.stopForDecision(this, receiver);
    }

    private void showSaveDialog(File tempFile) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setNegativeButton("버리기", (dialog, which) -> RecordingStore.deleteQuietly(tempFile))
                .setPositiveButton("저장하기", (dialog, which) -> saveRecording(tempFile))
                .show();
    }

    private void saveRecording(File tempFile) {
        try {
            RecordingStore.save(this, tempFile);
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (IOException exception) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateButtons() {
        updateButtons(RecordingService.isRecording());
    }

    private void updateButtons(boolean recording) {
        startButton.setEnabled(!recording && hasAudioPermission());
        stopButton.setEnabled(recording);
        startButton.setAlpha(startButton.isEnabled() ? 1f : 0.35f);
        stopButton.setAlpha(stopButton.isEnabled() ? 1f : 0.35f);
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMissingPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            updateButtons();
        }
    }
}
