package com.apextechlabs.wakeremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.net.Uri;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

public final class EnrollmentActivity extends Activity {
    private WakeKeyStore keyStore;
    private EditText keyField;
    private TextView status;
    private Button removeButton;
    private EnrollmentStore enrollmentStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ApexUi.BLACK);
        getWindow().setNavigationBarColor(ApexUi.BLACK);
        keyStore = new WakeKeyStore();
        enrollmentStore = new EnrollmentStore(this);
        setContentView(buildScreen());
        updateState();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ApexUi.BACKGROUND);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        Button back = secondaryButton("‹  BACK");
        back.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-2, dp(40));
        backParams.gravity = Gravity.START;
        page.addView(back, backParams);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.apex_wol_logo);
        logo.setContentDescription(getString(R.string.apex_logo_description));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(116), dp(116));
        logoParams.topMargin = dp(8);
        page.addView(logo, logoParams);

        TextView eyebrow = ApexUi.text(this, "SECURE ENROLLMENT", 11, ApexUi.APEX_BLUE, Typeface.BOLD);
        eyebrow.setLetterSpacing(.16f);
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(-2, -2);
        eyebrowParams.topMargin = dp(10);
        page.addView(eyebrow, eyebrowParams);

        TextView title = ApexUi.text(this, "Connect WakeRemote", 27, ApexUi.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(6);
        page.addView(title, titleParams);

        TextView intro = ApexUi.text(this,
            "Paste the 64-character key created during the server setup. It is stored in Android's protected key store and is never displayed again.",
            14, ApexUi.MUTED, Typeface.NORMAL);
        intro.setGravity(Gravity.CENTER);
        intro.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(10);
        page.addView(intro, introParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(ApexUi.rounded(this, ApexUi.CARD, 20, ApexUi.BORDER, 1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(22);
        page.addView(card, cardParams);

        TextView label = ApexUi.text(this, "HMAC KEY", 11, ApexUi.MUTED, Typeface.BOLD);
        label.setLetterSpacing(.14f);
        card.addView(label);

        keyField = new EditText(this);
        keyField.setHint("64 hexadecimal characters");
        keyField.setHintTextColor(ApexUi.MUTED);
        keyField.setTextColor(ApexUi.WHITE);
        keyField.setTextSize(15);
        keyField.setSingleLine(true);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        keyField.setPadding(dp(14), 0, dp(14), 0);
        keyField.setBackground(ApexUi.rounded(this, ApexUi.FIELD, 12, ApexUi.BORDER, 1));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, dp(54));
        fieldParams.topMargin = dp(8);
        card.addView(keyField, fieldParams);

        status = ApexUi.text(this, "", 13, ApexUi.MUTED, Typeface.NORMAL);
        status.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(10);
        card.addView(status, statusParams);

        Button scan = primaryButton("SCAN ENROLLMENT QR");
        scan.setOnClickListener(view -> new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt("Scan the Wake Remote enrollment QR").setBeepEnabled(false).initiateScan());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, dp(56));
        scanParams.topMargin = dp(16);
        card.addView(scan, scanParams);

        Button save = primaryButton("SAVE SECURE KEY");
        save.setOnClickListener(view -> saveKey());
        keyField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveKey();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56));
        saveParams.topMargin = dp(16);
        card.addView(save, saveParams);

        removeButton = secondaryButton("REMOVE INSTALLED KEY");
        removeButton.setOnClickListener(view -> confirmRemove());
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-1, dp(48));
        removeParams.topMargin = dp(10);
        card.addView(removeButton, removeParams);

        TextView endpoint = ApexUi.text(this,
            "Fixed service  •  wol.apextechlabs.com\nFixed target  •  main-pc",
            12, ApexUi.MUTED, Typeface.NORMAL);
        endpoint.setGravity(Gravity.CENTER);
        endpoint.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams endpointParams = new LinearLayout.LayoutParams(-1, -2);
        endpointParams.topMargin = dp(20);
        page.addView(endpoint, endpointParams);
        return scroll;
    }

    private void saveKey() {
        boolean imported = false;
        try {
            keyStore.importHexKey(keyField.getText().toString());
            imported = true;
            WakeRequestSigner.create(keyStore.getKey(), "main-pc", 0L, new byte[16]);
            enrollmentStore.save("https://wol.apextechlabs.com", "main", new JSONArray("[{\"alias\":\"main-pc\",\"label\":\"Main PC\"}]"));
            keyField.setText("");
            setResult(RESULT_OK);
            status.setText(R.string.key_installed);
            status.setTextColor(ApexUi.APEX_BLUE);
            keyField.postDelayed(this::finish, 450);
        } catch (IllegalArgumentException exception) {
            status.setText(exception.getMessage());
            status.setTextColor(ApexUi.WHITE);
        } catch (Exception exception) {
            if (imported) {
                try {
                    keyStore.deleteKey();
                } catch (Exception ignored) {
                    // The enrollment still reports failure; Setup can retry removal.
                }
            }
            status.setText(R.string.key_protection_failed);
            status.setTextColor(ApexUi.WHITE);
        }
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this)
            .setTitle("Remove secure key?")
            .setMessage("WakeRemote will be unable to send requests until a key is enrolled again.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove", (dialog, which) -> removeKey())
            .show();
    }

    private void removeKey() {
        try {
            keyStore.deleteKey();
            enrollmentStore.clear();
            status.setText(R.string.key_removed);
            status.setTextColor(ApexUi.MUTED);
            updateState();
        } catch (Exception exception) {
            status.setText(R.string.key_removal_failed);
            status.setTextColor(ApexUi.WHITE);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            Uri uri = Uri.parse(result.getContents());
            if (!"wakeremote".equals(uri.getScheme()) || !"enroll".equals(uri.getHost())) {
                status.setText("This is not a valid Wake Remote enrollment QR.");
            } else {
                enrollUri(uri);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void enrollUri(Uri uri) {
        String server = uri.getQueryParameter("url"), token = uri.getQueryParameter("t");
        if (!"1".equals(uri.getQueryParameter("v")) || server == null || !server.startsWith("https://") || token == null) {
            status.setText("Enrollment requires a valid version 1 HTTPS link."); return;
        }
        status.setText("Enrolling securely…");
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(WakeApiClient.trimTrailingSlash(server) + WakeRequestSigner.ENROLL_PATH).openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) { out.write(("{\"token\":\"" + token + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                if (connection.getResponseCode() != 200) throw new IllegalStateException(connection.getResponseCode() == 401 ? "This enrollment QR is expired or already used." : "Enrollment failed (" + connection.getResponseCode() + ").");
                StringBuilder value = new StringBuilder(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) { String line; while ((line = reader.readLine()) != null) value.append(line); }
                JSONObject response = new JSONObject(value.toString()); keyStore.importHexKey(response.getString("key")); enrollmentStore.save(response.getString("server_url"), response.getString("key_id"), response.getJSONArray("targets"));
                runOnUiThread(() -> { setResult(RESULT_OK); status.setText("Enrollment complete."); status.setTextColor(ApexUi.APEX_BLUE); status.postDelayed(this::finish, 500); });
            } catch (Exception exception) { runOnUiThread(() -> { status.setText(exception.getMessage()); status.setTextColor(ApexUi.WHITE); }); }
        }).start();
    }

    private void updateState() {
        try {
            boolean installed = keyStore.hasKey();
            if (status.getText().length() == 0) {
                status.setText(installed ? "A secure key is installed. Saving a new key replaces it." : "No secure key is installed yet.");
            }
            ApexUi.setEnabledAppearance(removeButton, installed);
        } catch (Exception exception) {
            status.setText(R.string.key_status_unavailable);
            ApexUi.setEnabledAppearance(removeButton, false);
        }
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setLetterSpacing(.08f);
        button.setTextColor(ApexUi.buttonTextColors());
        button.setBackground(ApexUi.rounded(this, ApexUi.APEX_BLUE, 14, ApexUi.APEX_BLUE, 0));
        button.setStateListAnimator(null);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(ColorStateList.valueOf(ApexUi.APEX_BLUE));
        button.setBackground(ApexUi.rounded(this, ApexUi.CARD, 12, ApexUi.BORDER, 1));
        button.setStateListAnimator(null);
        return button;
    }

    private int dp(float value) {
        return ApexUi.dp(this, value);
    }
}
