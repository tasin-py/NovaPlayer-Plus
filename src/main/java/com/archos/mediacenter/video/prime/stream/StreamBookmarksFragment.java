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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Bookmarked" folder of the home screen's Network &amp; Files row: every bookmarked
 * stream folder and video. The same bookmarks also stay listed on the Stream screen.
 */
public class StreamBookmarksFragment extends GuidedStepSupportFragment {

    private static final long ID_EMPTY = 1;
    private static final long ID_SEARCH = 2;
    private static final long ID_SEARCH_CLEAR = 3;
    private static final long ID_NO_MATCH = 4;
    private static final long ID_BASE = 1000;

    private final List<StreamBookmarks.Entry> mEntries = new ArrayList<>();
    /** Only bookmarks whose name contains every word are listed. Empty = all. */
    private String mQuery = "";
    private Overlay mOverlay;
    /** When the list appeared: presses right after it are a remote's double press. */
    private long mShownAt;
    private static final long DOUBLE_PRESS_GUARD_MS = 700;

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.prime_bookmarked),
                getString(R.string.prime_bookmarked_description),
                getString(R.string.leanback_browsing),
                ContextCompat.getDrawable(requireContext(), R.drawable.filetype_new_folder));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        buildActions(actions);
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
        mShownAt = android.os.SystemClock.uptimeMillis();
        // A bookmark may have been added or removed in a screen opened from here
        int selected = getSelectedActionPosition();
        rebuild();
        if (selected >= 0 && selected < getActions().size()) setSelectedActionPosition(selected);
    }

    private void rebuild() {
        List<GuidedAction> actions = new ArrayList<>();
        buildActions(actions);
        setActions(actions);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOverlay != null) mOverlay.pause();
    }

    private void buildActions(List<GuidedAction> actions) {
        Context c = requireContext();
        mEntries.clear();
        mEntries.addAll(StreamBookmarks.load(c));
        if (mEntries.isEmpty()) {
            actions.add(new GuidedAction.Builder(c)
                    .id(ID_EMPTY)
                    .title(getString(R.string.prime_bookmarked_empty))
                    .description(getString(R.string.prime_bookmarked_empty_hint))
                    .infoOnly(true)
                    .focusable(false)
                    .multilineDescription(true)
                    .build());
            return;
        }
        actions.add(new GuidedAction.Builder(c)
                .id(ID_SEARCH)
                .title(getString(R.string.prime_stream_search_bookmarks))
                .description(mQuery.isEmpty() ? null : getString(R.string.prime_stream_search_showing, mQuery))
                .build());
        if (!mQuery.isEmpty()) {
            actions.add(new GuidedAction.Builder(c)
                    .id(ID_SEARCH_CLEAR)
                    .title(getString(R.string.prime_stream_search_clear))
                    .build());
        }
        int shown = 0;
        for (int i = 0; i < mEntries.size(); i++) {
            StreamBookmarks.Entry e = mEntries.get(i);
            if (!StreamSearch.matches(e.name, mQuery)) continue;
            shown++;
            String host = Uri.parse(e.url).getHost();
            String kind = getString(e.folder ? R.string.prime_stream_folder_label : R.string.prime_stream_video_label);
            actions.add(new GuidedAction.Builder(c)
                    .id(ID_BASE + i)
                    .title(e.name)
                    .description(host == null || host.isEmpty() ? kind : kind + "  ·  " + host)
                    .multilineDescription(false)
                    .build());
        }
        if (shown == 0) {
            actions.add(new GuidedAction.Builder(c)
                    .id(ID_NO_MATCH)
                    .title(getString(R.string.prime_stream_search_none, mQuery))
                    .infoOnly(true)
                    .focusable(false)
                    .build());
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (android.os.SystemClock.uptimeMillis() - mShownAt < DOUBLE_PRESS_GUARD_MS) return; // remote double press
        if (action.getId() == ID_SEARCH) {
            StreamSearch.ask(requireContext(), mQuery, query -> {
                if (!isAdded()) return;
                mQuery = query;
                rebuild();
                List<GuidedAction> list = getActions();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getId() >= ID_BASE) {
                        setSelectedActionPosition(i);
                        break;
                    }
                }
            });
            return;
        }
        if (action.getId() == ID_SEARCH_CLEAR) {
            mQuery = "";
            rebuild();
            return;
        }
        int index = (int) (action.getId() - ID_BASE);
        if (index < 0 || index >= mEntries.size()) return;
        StreamBookmarks.Entry e = mEntries.get(index);
        StreamBrowseFragment.open(this, e.url, e.name,
                e.folder ? StreamBrowseFragment.HINT_FOLDER : StreamBrowseFragment.HINT_VIDEO, -1, false,
                getString(R.string.prime_bookmarked));
    }
}
