package com.apextechlabs.wakeremote;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-secret enrollment metadata: the service origin, key id, and the target list the
 * server returned. The signing key itself lives in {@link WakeKeyStore}.
 */
final class EnrollmentStore {
    static final class Target {
        final String alias;
        final String label;

        Target(String alias, String label) {
            this.alias = alias;
            this.label = label;
        }

        /** The spinner renders this, so it must be the human label, not the alias. */
        @Override
        public String toString() {
            return label;
        }
    }

    private final SharedPreferences prefs;

    EnrollmentStore(Context context) {
        prefs = context.getSharedPreferences("enrollment_v1", Context.MODE_PRIVATE);
    }

    void save(String url, String keyId, JSONArray targets) throws Exception {
        prefs.edit()
            .putString("url", url)
            .putString("key_id", keyId)
            .putString("targets", targets.toString())
            .putString("selected", targets.getJSONObject(0).getString("alias"))
            .apply();
    }

    boolean isEnrolled() {
        return prefs.contains("url");
    }

    /** Empty rather than a baked-in production host: the UI must show what is stored. */
    String url() {
        return prefs.getString("url", "");
    }

    String keyId() {
        return prefs.getString("key_id", "main");
    }

    String selected() {
        return prefs.getString("selected", "");
    }

    /** The display label for the selected target, or null when nothing is enrolled. */
    String selectedLabel() {
        String alias = selected();
        for (Target target : targets()) {
            if (target.alias.equals(alias)) return target.label;
        }
        return alias.isEmpty() ? null : alias;
    }

    void select(String alias) {
        prefs.edit().putString("selected", alias).apply();
    }

    void clear() {
        prefs.edit().clear().apply();
    }

    List<Target> targets() {
        List<Target> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("targets", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Target(item.getString("alias"), item.getString("label")));
            }
        } catch (Exception ignored) {
            // A corrupt list is not worth crashing over; the caller sees no targets.
        }
        return result;
    }
}
