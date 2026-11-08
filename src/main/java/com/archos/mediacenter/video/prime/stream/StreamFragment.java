// Copyright 2026 tasin-py (NovaPlayer Prime)
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

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.player.PlayerActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Stream" screen: a link box, a Paste button, a Play button and the list of
 * links played in the last 30 days. Uses the same GuidedStep look as NOVA's other TV screens.
 */
public class StreamFragment extends GuidedStepSupportFragment {

    private static final Logger log = LoggerFactory.getLogger(StreamFragment.class);

    private static final long ID_URL = 1;
    private static final long ID_PASTE = 2;
    private static final long ID_PLAY = 3;
    private static final long ID_HISTORY_HEADER = 4;
    private static final long ID_CLEAR_HISTORY = 5;
    private static final long ID_HISTORY_BASE = 1000;

    private static final ExecutorService sNameLookup = Executors.newSingleThreadExecutor();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<StreamHistory.Entry> mHistory = new ArrayList<>();
    private String mUrl = "";
    private Overlay mOverlay;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.prime_stream),
                getString(R.string.prime_stream_description),
                getString(R.string.network_storage),
                ContextCompat.getDrawable(requireActivity(), R.drawable.filetype_new_video));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) mUrl = savedInstanceState.getString("url", "");
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("url", mUrl);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView() {
        if (mOverlay != null) mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOverlay != null) mOverlay.resume();
        // Coming back from the player: show the updated history
        rebuildActions();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOverlay != null) mOverlay.pause();
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        buildActions(actions);
    }

    private void buildActions(List<GuidedAction> actions) {
        Context c = requireActivity();
        actions.add(new GuidedAction.Builder(c)
                .id(ID_URL)
                .title(mUrl.isEmpty() ? getString(R.string.prime_stream_hint) : mUrl)
                .editTitle(mUrl)
                .description(getString(R.string.prime_stream_link))
                .editable(true)
                .editInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI)
                .build());
        actions.add(new GuidedAction.Builder(c)
                .id(ID_PASTE)
                .title(getString(R.string.prime_stream_paste))
                .description(getString(R.string.prime_stream_paste_description))
                .build());
        actions.add(new GuidedAction.Builder(c)
                .id(ID_PLAY)
                .title(getString(R.string.prime_stream_play))
                .build());

        mHistory.clear();
        mHistory.addAll(StreamHistory.load(c));
        if (!mHistory.isEmpty()) {
            actions.add(new GuidedAction.Builder(c)
                    .id(ID_HISTORY_HEADER)
                    .title(getString(R.string.prime_stream_history))
                    .description(getString(R.string.prime_stream_history_description))
                    .infoOnly(true)
                    .focusable(false)
                    .build());
            for (int i = 0; i < mHistory.size(); i++) {
                StreamHistory.Entry e = mHistory.get(i);
                actions.add(new GuidedAction.Builder(c)
                        .id(ID_HISTORY_BASE + i)
                        .title(e.name)
                        .description(DateUtils.getRelativeTimeSpanString(e.lastPlayed,
                                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) + "  ·  " + e.url)
                        .multilineDescription(false)
                        .build());
            }
            actions.add(new GuidedAction.Builder(c)
                    .id(ID_CLEAR_HISTORY)
                    .title(getString(R.string.prime_stream_clear_history))
                    .build());
        }
    }

    private void rebuildActions() {
        if (!isAdded()) return;
        int selected = getSelectedActionPosition();
        List<GuidedAction> actions = new ArrayList<>();
        buildActions(actions);
        setActions(actions);
        if (selected >= 0 && selected < actions.size()) setSelectedActionPosition(selected);
    }

    private void setUrl(String url) {
        mUrl = url == null ? "" : url.trim();
        GuidedAction a = findActionById(ID_URL);
        if (a != null) {
            a.setTitle(mUrl.isEmpty() ? getString(R.string.prime_stream_hint) : mUrl);
            a.setEditTitle(mUrl);
            notifyActionChanged(findActionPositionById(ID_URL));
        }
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        if (action.getId() == ID_URL) {
            setUrl(action.getEditTitle() == null ? "" : action.getEditTitle().toString());
            return ID_PLAY; // jump to Play after typing
        }
        return GuidedAction.ACTION_ID_CURRENT;
    }

    @Override
    public void onGuidedActionEditCanceled(GuidedAction action) {
        if (action.getId() == ID_URL) {
            // keep whatever was typed, even if the user pressed Back
            setUrl(action.getEditTitle() == null ? mUrl : action.getEditTitle().toString());
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        long id = action.getId();
        if (id == ID_PASTE) {
            paste();
        } else if (id == ID_PLAY) {
            play(mUrl, null);
        } else if (id == ID_CLEAR_HISTORY) {
            StreamHistory.clear(requireActivity());
            rebuildActions();
            setSelectedActionPosition(findActionPositionById(ID_URL));
        } else if (id >= ID_HISTORY_BASE) {
            int index = (int) (id - ID_HISTORY_BASE);
            if (index >= 0 && index < mHistory.size()) {
                StreamHistory.Entry e = mHistory.get(index);
                setUrl(e.url);
                play(e.url, e.name);
            }
        }
    }

    private void paste() {
        Context c = requireActivity();
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence text = null;
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) text = clip.getItemAt(0).coerceToText(c);
        }
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(c, R.string.prime_stream_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        StreamLink link = StreamLink.parse(text.toString());
        setUrl(link != null ? link.original : text.toString());
        setSelectedActionPosition(findActionPositionById(ID_PLAY));
    }

    private void play(String text, String knownName) {
        Context c = requireActivity();
        StreamLink link = StreamLink.parse(text);
        if (link == null) {
            Toast.makeText(c, R.string.prime_stream_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        if (link.looksLikeFolder()) {
            Toast.makeText(c, R.string.prime_stream_folder, Toast.LENGTH_LONG).show();
            return;
        }

        StreamHistory.add(c, link.original, knownName != null ? knownName : link.guessedName);
        if (knownName == null && link.needsNameLookup()) {
            final Context app = c.getApplicationContext();
            sNameLookup.execute(() -> {
                String name = link.fetchServerFileName();
                if (name != null && !name.isEmpty()) {
                    StreamHistory.rename(app, link.original, name);
                    mHandler.post(this::rebuildActions);
                }
            });
        }

        if (log.isDebugEnabled()) log.debug("play: {} -> {} ({})", link.original, link.playUri, link.mimeType);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(c, PlayerActivity.class);
        intent.setDataAndType(link.playUri, link.mimeType);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            log.error("play: cannot start player", e);
            Toast.makeText(c, R.string.prime_stream_invalid, Toast.LENGTH_SHORT).show();
        }
    }
}
