package me.yzhl.imagepatcher;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int OPEN_IMAGE_REQUEST = 1001;
    private static final String[] KMIS = {
            "android12-5.10",
            "android13-5.10",
            "android13-5.15",
            "android14-5.15",
            "android14-6.1",
            "android15-6.6",
            "android16-6.12",
            "android17-6.18"
    };

    private Spinner kmiSpinner;
    private Button chooseButton;
    private Button patchButton;
    private TextView selectedImageView;
    private TextView logView;
    private Uri selectedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        appendLog("请选择内核版本和原始 boot/init_boot 镜像。\n");
        appendLog("输出位置：内部存储/Download/yzhlSU\n");
    }

    private View createContentView() {
        int padding = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(246, 247, 251));

        TextView title = new TextView(this);
        title.setText("yzhlSU 镜像修补器");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(28, 30, 36));
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title, fullWidthWrap());

        TextView kmiLabel = label("内核版本");
        root.addView(kmiLabel, fullWidthWrap());

        kmiSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, KMIS);
        kmiSpinner.setAdapter(adapter);
        kmiSpinner.setSelection(6);
        root.addView(kmiSpinner, fullWidth(dp(54)));

        chooseButton = new Button(this);
        chooseButton.setText("选择镜像");
        chooseButton.setAllCaps(false);
        chooseButton.setOnClickListener(v -> openImagePicker());
        LinearLayout.LayoutParams chooseParams = fullWidth(dp(54));
        chooseParams.topMargin = dp(14);
        root.addView(chooseButton, chooseParams);

        selectedImageView = new TextView(this);
        selectedImageView.setText("尚未选择镜像");
        selectedImageView.setTextColor(Color.DKGRAY);
        selectedImageView.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(selectedImageView, fullWidthWrap());

        patchButton = new Button(this);
        patchButton.setText("开始修补");
        patchButton.setAllCaps(false);
        patchButton.setEnabled(false);
        patchButton.setOnClickListener(v -> startPatch());
        root.addView(patchButton, fullWidth(dp(56)));

        TextView logLabel = label("日志");
        LinearLayout.LayoutParams logLabelParams = fullWidthWrap();
        logLabelParams.topMargin = dp(18);
        root.addView(logLabel, logLabelParams);

        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setTextColor(Color.rgb(225, 230, 238));
        logView.setBackgroundColor(Color.rgb(27, 30, 36));
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setTextIsSelectable(true);

        ScrollView logScroll = new ScrollView(this);
        logScroll.addView(logView, fullWidthWrap());
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        logParams.topMargin = dp(6);
        root.addView(logScroll, logParams);
        return root;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.rgb(50, 52, 58));
        return view;
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_IMAGE_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        selectedImage = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    selectedImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The temporary read permission remains valid for this activity.
        }
        selectedImageView.setText(getDisplayName(selectedImage));
        patchButton.setEnabled(true);
        appendLog("已选择：" + selectedImage + "\n");
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return uri.getLastPathSegment() == null ? "已选择镜像" : uri.getLastPathSegment();
    }

    private void startPatch() {
        if (selectedImage == null) {
            Toast.makeText(this, "请先选择镜像", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true);
        String kmi = KMIS[kmiSpinner.getSelectedItemPosition()];
        appendLog("\n开始修补，KMI：" + kmi + "\n");
        new Thread(() -> patchInBackground(kmi), "yzhlSU-image-patch").start();
    }

    private void patchInBackground(String kmi) {
        try {
            File jobDir = new File(getCacheDir(), "patch-" + System.currentTimeMillis());
            if (!jobDir.mkdirs() && !jobDir.isDirectory()) {
                throw new IOException("无法创建临时目录");
            }
            File input = new File(jobDir, "source.img");
            File module = new File(jobDir, kmi + "_kernelsu.ko");
            copyUri(selectedImage, input);
            copyAsset("kmi/" + module.getName(), module);

            String outputName = "yzhlSU_patched_" + new SimpleDateFormat(
                    "yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".img";
            File nativeTool = new File(getApplicationInfo().nativeLibraryDir,
                    "libyzhlsupatcher.so");
            if (!nativeTool.isFile()) {
                throw new IOException("APK 中缺少修补引擎");
            }

            List<String> command = Arrays.asList(
                    nativeTool.getAbsolutePath(),
                    "boot-patch",
                    "--boot", input.getAbsolutePath(),
                    "--module", module.getAbsolutePath(),
                    "--out", jobDir.getAbsolutePath(),
                    "--out-name", outputName
            );
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(jobDir);
            builder.redirectErrorStream(true);
            builder.environment().put("TMPDIR", jobDir.getAbsolutePath());

            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog(line + "\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("修补引擎退出，代码 " + exitCode);
            }

            File output = new File(jobDir, outputName);
            if (!output.isFile() || output.length() == 0) {
                throw new IOException("没有生成有效的修补镜像");
            }
            Uri saved = saveToDownloads(output, outputName);
            String visiblePath = "/storage/emulated/0/Download/yzhlSU/" + outputName;
            appendLog("\n修补成功\n输出路径：" + visiblePath + "\nURI：" + saved + "\n");
            runOnUiThread(() -> Toast.makeText(
                    this, "已保存到 Download/yzhlSU", Toast.LENGTH_LONG).show());
        } catch (Exception error) {
            appendLog("\n修补失败：" + error.getMessage() + "\n");
        } finally {
            runOnUiThread(() -> setBusy(false));
        }
    }

    private void copyUri(Uri uri, File destination) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(destination)) {
            if (input == null) {
                throw new IOException("无法打开选择的镜像");
            }
            copyStream(input, output);
        }
    }

    private void copyAsset(String assetName, File destination) throws IOException {
        try (InputStream input = getAssets().open(assetName);
             OutputStream output = new FileOutputStream(destination)) {
            copyStream(input, output);
        }
    }

    private Uri saveToDownloads(File source, String name) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/yzhlSU");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri target = getContentResolver().insert(collection, values);
        if (target == null) {
            throw new IOException("无法在 Download 中创建输出文件");
        }
        try {
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                if (output == null) {
                    throw new IOException("无法写入 Download 输出文件");
                }
                copyStream(input, output);
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(target, values, null, null);
            return target;
        } catch (IOException error) {
            getContentResolver().delete(target, null, null);
            throw error;
        }
    }

    private void setBusy(boolean busy) {
        chooseButton.setEnabled(!busy);
        patchButton.setEnabled(!busy && selectedImage != null);
        kmiSpinner.setEnabled(!busy);
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
    }

    private void appendLog(String text) {
        runOnUiThread(() -> {
            logView.append(text);
            View parent = (View) logView.getParent();
            if (parent instanceof ScrollView scrollView) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        });
    }
}
