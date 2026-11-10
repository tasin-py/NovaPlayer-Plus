// Copyright 2026 tasin-py (Nova Player Plus)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.prime.stream;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.archos.mediacenter.video.R;

import java.util.Locale;

/** The Search button of the stream folder and Bookmarks screens: ask for a word, then filter by name. */
final class StreamSearch {

    interface Listener {
        void onSearch(String query);
    }

    private StreamSearch() {}

    /**
     * True when every word of the query appears in the name, ignoring case and order:
     * "s02 friends" matches "Friends.S02E05.1080p".
     */
    static boolean matches(String name, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!n.contains(word)) return false;
        }
        return true;
    }

    /** Opens the TV keyboard (with voice typing) in a small dialog. */
    static void ask(Context context, String current, Listener listener) {
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setHint(R.string.prime_stream_search_hint);
        if (current != null) {
            input.setText(current);
            input.setSelection(current.length());
        }
        FrameLayout box = new FrameLayout(context);
        int pad = (int) (24 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.prime_stream_search)
                .setView(box)
                .setPositiveButton(R.string.prime_stream_search, (d, w) -> listener.onSearch(input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                dialog.dismiss();
                listener.onSearch(input.getText().toString().trim());
                return true;
            }
            return false;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }
}
