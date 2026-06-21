package com.modeling33.soundrecord;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
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
        RecordingStore.repairSavedRecordings(this);
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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        startButton = new Button(this);
        startButton.setText("");
        startButton.setContentDescription("Start recording");
        startButton.setBackgroundColor(0xFF444444);
        startButton.setMinWidth(0);
        startButton.setMinHeight(0);
        startButton.setMinimumWidth(0);
        startButton.setMinimumHeight(0);
        startButton.setOnClickListener(view -> startRecording());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(84), dp(84));
        startParams.setMarginEnd(dp(12));
        controls.addView(startButton, startParams);

        stopButton = new Button(this);
        stopButton.setText("");
        stopButton.setContentDescription("Stop and save");
        stopButton.setBackgroundColor(0xFF222222);
        stopButton.setMinWidth(0);
        stopButton.setMinHeight(0);
        stopButton.setMinimumWidth(0);
        stopButton.setMinimumHeight(0);
        stopButton.setOnClickListener(view -> stopForDecision());
        controls.addView(stopButton, new LinearLayout.LayoutParams(dp(84), dp(84)));

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END
        );
        controlParams.setMargins(0, 0, dp(20), dp(28));
        root.addView(controls, controlParams);

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
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setBackgroundColor(Color.BLACK);

        Button discardButton = new Button(this);
        discardButton.setText("버리기");
        discardButton.setAllCaps(false);
        discardButton.setOnClickListener(view -> {
            RecordingStore.deleteQuietly(tempFile);
            dialog.dismiss();
        });
        actions.addView(discardButton, new LinearLayout.LayoutParams(dp(132), dp(64)));

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        Button saveButton = new Button(this);
        saveButton.setText("저장하기");
        saveButton.setAllCaps(false);
        saveButton.setOnClickListener(view -> {
            saveRecording(tempFile);
            dialog.dismiss();
        });
        actions.addView(saveButton, new LinearLayout.LayoutParams(dp(132), dp(64)));

        dialog.setContentView(actions);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
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
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void requestMissingPermissions() {
        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
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
            RecordingStore.repairSavedRecordings(this);
            updateButtons();
        }
    }
}
