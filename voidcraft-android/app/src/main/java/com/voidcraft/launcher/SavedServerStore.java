package com.voidcraft.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Small local repository for user-managed server name/address pairs.
 *
 * Server reachability is intentionally not stored here: saved entries are
 * offline configuration, not evidence that a server is currently online.
 */
final class SavedServerStore {
    private static final String PREFS = "voidcraft_saved_servers";
    private static final String KEY_ENTRIES = "entries";

    static final class Entry {
        final String id;
        final String name;
        final String address;

        Entry(String id, String name, String address) {
            this.id = id;
            this.name = name;
            this.address = address;
        }
    }

    private final SharedPreferences preferences;

    SavedServerStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Entry> load() {
        List<Entry> entries = new ArrayList<>();
        String encoded = preferences.getString(KEY_ENTRIES, "[]");
        try {
            JSONArray array = new JSONArray(encoded);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) {
                    continue;
                }
                String id = object.optString("id", "");
                String name = object.optString("name", "").trim();
                String address = object.optString("address", "").trim();
                if (!id.isEmpty() && !name.isEmpty() && !address.isEmpty()) {
                    entries.add(new Entry(id, name, address));
                }
            }
        } catch (JSONException malformedData) {
            // Keep the app usable if old or damaged local data cannot be read.
            // The next explicit save replaces the malformed payload.
        }
        return entries;
    }

    Entry add(String name, String address) {
        Entry entry = new Entry(UUID.randomUUID().toString(), name.trim(), address.trim());
        List<Entry> entries = load();
        entries.add(entry);
        save(entries);
        return entry;
    }

    boolean update(String id, String name, String address) {
        List<Entry> entries = load();
        for (int index = 0; index < entries.size(); index++) {
            Entry current = entries.get(index);
            if (current.id.equals(id)) {
                entries.set(index, new Entry(id, name.trim(), address.trim()));
                save(entries);
                return true;
            }
        }
        return false;
    }

    boolean remove(String id) {
        List<Entry> entries = load();
        boolean removed = false;
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (entries.get(index).id.equals(id)) {
                entries.remove(index);
                removed = true;
            }
        }
        if (removed) {
            save(entries);
        }
        return removed;
    }

    private void save(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", entry.id);
                object.put("name", entry.name);
                object.put("address", entry.address);
                array.put(object);
            } catch (JSONException impossibleForStringValues) {
                // String values are JSON-compatible; omit only an invalid item.
            }
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply();
    }
}
