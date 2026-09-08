package com.apextechlabs.wakeremote;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * QR scanning is the primary enrollment path. Manual key entry is a backup and lives on
 * its own screen behind a text link.
 */
public final class EnrollmentActivity extends Activity {
    private static final int REQUEST_CAMERA = 4101;

    private WakeKeyStore keyStore;
    private EnrollmentStore enrollmentStore;
    private StatusCard status;
    private TextView enrollmentSummary;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ApexUi.black(this));
        getWindow().setNavigationBarColor(ApexUi.black(this));
        keyStore = new WakeKeyStore();
        enrollmentStore = new EnrollmentStore(this);
        setContentView(buildScreen());
        updateSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSummary();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ApexUi.background(this));

        int gutter = ApexUi.dimen(this, R.dimen.gutter);
        int space1 = ApexUi.dimen(this, R.dimen.space_1);
        int space2 = ApexUi.dimen(this, R.dimen.space_2);
        int space3 = ApexUi.dimen(this, R.dimen.space_3);

        // Same header component, same slots, same positions as the home screen.
        LinearLayout header = ApexUi.header(this, true, this::finish, false, 0, R.string.settings, null);
        header.setPadding(space2, 0, space2, 0);
        root.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ApexUi.dimen(this, R.dimen.header_height)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(gutter, space3, gutter, space3);
        scroll.addView(column, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        int logo = ApexUi.dimen(this, R.dimen.logo_size);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.wake_remote_logo);
        mark.setContentDescription(getString(R.string.app_logo));
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        column.addView(mark, new LinearLayout.LayoutParams(logo, logo));

        TextView eyebrow = ApexUi.text(this, getString(R.string.enroll_eyebrow), 11, ApexUi.blue(this), Typeface.BOLD);
        eyebrow.setLetterSpacing(.16f);
        eyebrow.setGravity(Gravity.CENTER);
        add(column, eyebrow, space3);

        // The non-breaking space in the string keeps "Wake Remote" together, so it wraps
        // between the words or not at all, and never renders as "WakeRemote".
        TextView title = ApexUi.text(this, getString(R.string.enroll_title), 28, ApexUi.white(this), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        add(column, title, space1);

        TextView subtitle = ApexUi.text(this, getString(R.string.enroll_subtitle), 14, ApexUi.muted(this), Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0, 1.2f);
        add(column, subtitle, space1);

        status = new StatusCard(this);
        add(column, status, space3);

        LinearLayout scan = ApexUi.primaryButton(this, getString(R.string.scan_qr), R.drawable.ic_qr_scan, this::startScan);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ApexUi.dimen(this, R.dimen.button_height));
        scanParams.topMargin = space2;
        column.addView(scan, scanParams);

        View manual = ApexUi.linkButton(this, getString(R.string.enter_key_manually),
            () -> startActivity(new Intent(this, ManualKeyActivity.class)));
        add(column, manual, space1);

        // Read from the ACTUAL stored enrollment. This used to be two hardcoded lines in
        // the footer ("Fixed service", "Fixed target") that were wrong for every user.
        enrollmentSummary = ApexUi.text(this, "", 12, ApexUi.muted(this), Typeface.NORMAL);
        enrollmentSummary.setGravity(Gravity.CENTER);
        enrollmentSummary.setLineSpacing(ApexUi.dp(this, 3), 1f);
        add(column, enrollmentSummary, space3);

        // The footer is the attribution and nothing else.
        LinearLayout footer = ApexUi.footer(this);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = space2;
        footerParams.bottomMargin = space2;
        root.addView(footer, footerParams);
        return root;
    }

    private void add(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        parent.addView(view, params);
    }

    private void updateSummary() {
        boolean enrolled = enrollmentStore.isEnrolled();
        if (enrolled) {
            String label = enrollmentStore.selectedLabel();
            enrollmentSummary.setText(
                getString(R.string.service_label, hostOf(enrollmentStore.url()))
                    + "\n" + getString(R.string.target_label, label == null ? "" : label));
            status.set(getString(R.string.status_ready), getString(R.string.status_ready_detail), true);
        } else {
            enrollmentSummary.setText(R.string.not_enrolled);
            status.set(getString(R.string.status_setup_required), getString(R.string.status_setup_detail), false);
        }
    }

    private static String hostOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? url : host;
        } catch (Exception exception) {
            return url;
        }
    }

    // --- QR scanning -------------------------------------------------------------

    private void startScan() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            status.set(getString(R.string.status_failed), getString(R.string.no_camera), false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchScanner();
            return;
        }
        // Permission is requested at the point of use, not at launch, and the rationale
        // is shown first when the system says one is warranted.
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.camera_rationale_title)
                .setMessage(R.string.camera_rationale)
                .setPositiveButton(android.R.string.ok,
                    (dialog, which) -> requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA))
                .setNegativeButton(R.string.not_now, null)
                .show();
        } else {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_CAMERA) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchScanner();
            return;
        }
        // Graceful deny: say what happened, and offer both ways forward.
        status.set(getString(R.string.status_failed), getString(R.string.camera_denied), false);
        new AlertDialog.Builder(this)
            .setTitle(R.string.camera_rationale_title)
            .setMessage(R.string.camera_denied)
            .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            })
            .setNegativeButton(R.string.enter_key_manually,
                (dialog, which) -> startActivity(new Intent(this, ManualKeyActivity.class)))
            .show();
    }

    private void launchScanner() {
        new IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt(getString(R.string.enroll_subtitle))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;   // the user cancelled; leave the screen as it was
            }
            Uri uri = Uri.parse(result.getContents());
            if (!"wakeremote".equals(uri.getScheme()) || !"enroll".equals(uri.getHost())) {
                status.set(getString(R.string.status_failed), getString(R.string.enroll_invalid_qr), false);
            } else {
                enrollUri(uri);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** Also reachable from a wakeremote:// link, not only from the camera. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null) enrollUri(intent.getData());
    }

    private void enrollUri(Uri uri) {
        String server = uri.getQueryParameter("url");
        String token = uri.getQueryParameter("t");
        if (!"1".equals(uri.getQueryParameter("v")) || server == null || token == null
                || !server.startsWith("https://")) {
            status.set(getString(R.string.status_failed), getString(R.string.enroll_invalid_link), false);
            return;
        }
        status.working(getString(R.string.enroll_eyebrow), getString(R.string.enrolling));

        new Thread(() -> {
            try {
                // The URI carries the bare origin; the client appends the API path itself.
                URL endpoint = new URL(WakeApiClient.trimTrailingSlash(server) + WakeRequestSigner.ENROLL_PATH);
                HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(("{\"token\":\"" + token + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                if (code != 200) {
                    throw new IllegalStateException(code == 401
                        ? "This enrollment QR is expired or already used."
                        : "Enrollment failed (" + code + ").");
                }
                StringBuilder value = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) value.append(line);
                }
                JSONObject response = new JSONObject(value.toString());
                keyStore.importHexKey(response.getString("key"));
                enrollmentStore.save(response.getString("server_url"),
                    response.getString("key_id"), response.getJSONArray("targets"));
                handler.post(() -> {
                    setResult(RESULT_OK);
                    updateSummary();
                    status.set(getString(R.string.status_ready), getString(R.string.enroll_complete), true);
                    handler.postDelayed(this::finish, 700);
                });
            } catch (Exception exception) {
                String message = exception.getMessage();
                handler.post(() -> status.set(getString(R.string.status_failed),
                    message == null ? getString(R.string.status_failed) : message, false));
            }
        }).start();
    }
}
