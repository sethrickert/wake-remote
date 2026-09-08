package com.apextechlabs.wakeremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

/**
 * The manual-key backup path, on its own screen so the QR flow stays uncluttered.
 *
 * Unlike the previous version, saving a manual key does NOT overwrite server and target
 * metadata that a QR enrollment already stored: those placeholders are only written when
 * nothing is enrolled yet.
 */
public final class ManualKeyActivity extends Activity {
    private WakeKeyStore keyStore;
    private EnrollmentStore enrollmentStore;
    private EditText keyField;
    private EditText serverField;
    private TextView message;
    private Button removeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ApexUi.black(this));
        getWindow().setNavigationBarColor(ApexUi.black(this));
        keyStore = new WakeKeyStore();
        enrollmentStore = new EnrollmentStore(this);
        setContentView(buildScreen());
        updateState();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ApexUi.background(this));

        int gutter = ApexUi.dimen(this, R.dimen.gutter);
        int space1 = ApexUi.dimen(this, R.dimen.space_1);
        int space2 = ApexUi.dimen(this, R.dimen.space_2);
        int space3 = ApexUi.dimen(this, R.dimen.space_3);

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
        column.setPadding(gutter, space3, gutter, space3);
        scroll.addView(column, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = ApexUi.text(this, getString(R.string.manual_title), 26, ApexUi.white(this), Typeface.BOLD);
        column.addView(title);

        TextView subtitle = ApexUi.text(this, getString(R.string.manual_subtitle), 14, ApexUi.muted(this), Typeface.NORMAL);
        subtitle.setLineSpacing(0, 1.2f);
        add(column, subtitle, space1);

        LinearLayout card = ApexUi.card(this);
        add(column, card, space3);

        TextView serverLabel = ApexUi.text(this, getString(R.string.server_url_label), 11, ApexUi.muted(this), Typeface.BOLD);
        serverLabel.setLetterSpacing(.14f);
        card.addView(serverLabel);

        serverField = field(getString(R.string.server_url_hint), InputType.TYPE_TEXT_VARIATION_URI);
        addTo(card, serverField, space1);

        TextView keyLabel = ApexUi.text(this, getString(R.string.hmac_key), 11, ApexUi.muted(this), Typeface.BOLD);
        keyLabel.setLetterSpacing(.14f);
        addTo(card, keyLabel, space2);

        keyField = field(getString(R.string.key_hint), InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        keyField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveKey();
                return true;
            }
            return false;
        });
        addTo(card, keyField, space1);

        message = ApexUi.text(this, "", 13, ApexUi.muted(this), Typeface.NORMAL);
        message.setLineSpacing(0, 1.12f);
        addTo(card, message, space1);

        LinearLayout save = ApexUi.primaryButton(this, getString(R.string.save_secure_key), null, this::saveKey);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ApexUi.dimen(this, R.dimen.button_height));
        saveParams.topMargin = space2;
        card.addView(save, saveParams);

        removeButton = ApexUi.secondaryButton(this, getString(R.string.remove_installed_key), this::confirmRemove);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ApexUi.dimen(this, R.dimen.touch_target));
        removeParams.topMargin = space1;
        card.addView(removeButton, removeParams);

        LinearLayout footer = ApexUi.footer(this);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = space2;
        footerParams.bottomMargin = space2;
        root.addView(footer, footerParams);
        return root;
    }

    private EditText field(String hint, int variation) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(ApexUi.muted(this));
        input.setTextColor(ApexUi.white(this));
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | variation);
        int pad = ApexUi.dp(this, 14);
        input.setPadding(pad, 0, pad, 0);
        input.setBackground(ApexUi.rounded(this, ApexUi.surface(this), 12, ApexUi.border(this), 1));
        return input;
    }

    private void add(LinearLayout parent, View view, int topMargin) {
        addTo(parent, view, topMargin);
    }

    private void addTo(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            view instanceof EditText ? ApexUi.dimen(this, R.dimen.touch_target) + ApexUi.dp(this, 6)
                                     : LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        parent.addView(view, params);
    }

    private void updateState() {
        boolean hasKey;
        try {
            hasKey = keyStore.hasKey();
        } catch (Exception exception) {
            message.setText(R.string.key_status_unavailable);
            return;
        }
        removeButton.setVisibility(hasKey ? View.VISIBLE : View.GONE);
        if (enrollmentStore.isEnrolled()) serverField.setText(enrollmentStore.url());
        if (!hasKey) message.setText(R.string.no_key_installed);
    }

    private void saveKey() {
        boolean imported = false;
        try {
            keyStore.importHexKey(keyField.getText().toString());
            imported = true;

            String server = serverField.getText().toString().trim();
            if (server.isEmpty() && enrollmentStore.isEnrolled()) server = enrollmentStore.url();
            if (!server.startsWith("https://") && !server.startsWith("http://")) {
                throw new IllegalArgumentException(getString(R.string.server_url_hint));
            }

            // Only seed placeholder metadata when there is none. A prior QR enrollment
            // carries the real key id and target list, and must not be clobbered here.
            if (!enrollmentStore.isEnrolled()) {
                enrollmentStore.save(server, "main",
                    new JSONArray("[{\"alias\":\"main-pc\",\"label\":\"Main PC\"}]"));
            } else if (!server.equals(enrollmentStore.url())) {
                enrollmentStore.save(server, enrollmentStore.keyId(), targetsJson());
            }

            keyField.setText("");
            setResult(RESULT_OK);
            message.setText(R.string.key_installed);
            message.setTextColor(ApexUi.blue(this));
            updateState();
        } catch (IllegalArgumentException invalid) {
            message.setText(invalid.getMessage());
            message.setTextColor(ApexUi.red(this));
        } catch (Exception exception) {
            message.setText(imported ? R.string.key_protection_failed : R.string.key_protection_failed);
            message.setTextColor(ApexUi.red(this));
        }
    }

    private JSONArray targetsJson() throws Exception {
        JSONArray array = new JSONArray();
        for (EnrollmentStore.Target target : enrollmentStore.targets()) {
            array.put(new org.json.JSONObject().put("alias", target.alias).put("label", target.label));
        }
        if (array.length() == 0) array = new JSONArray("[{\"alias\":\"main-pc\",\"label\":\"Main PC\"}]");
        return array;
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.remove_key_title)
            .setMessage(R.string.remove_key_message)
            .setPositiveButton(R.string.remove, (dialog, which) -> removeKey())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void removeKey() {
        try {
            keyStore.deleteKey();
            enrollmentStore.clear();
            message.setText(R.string.key_removed);
            message.setTextColor(ApexUi.muted(this));
            setResult(RESULT_OK);
            updateState();
        } catch (Exception exception) {
            message.setText(R.string.key_removal_failed);
            message.setTextColor(ApexUi.red(this));
        }
    }
}
