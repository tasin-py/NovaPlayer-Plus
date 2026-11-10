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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
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
 * Shows what a stream link points to before anything plays:
 * a folder (Search, its sub-folders and videos, then Bookmark) or
 * a video (title, size, Resume or Play, then Bookmark). Same GuidedStep look as the rest of NOVA's TV screens.
 */
public class StreamBrowseFragment extends GuidedStepSupportFragment {

    private static final Logger log = LoggerFactory.getLogger(StreamBrowseFragment.class);

    public static final int HINT_UNKNOWN = 0;
    public static final int HINT_FOLDER = 1;
    public static final int HINT_VIDEO = 2;

    private static final String ARG_URL = "url";
    private static final String ARG_NAME = "name";
    private static final String ARG_HINT = "hint";
    private static final String ARG_SIZE = "size";
    private static final String ARG_RECORD = "record";
    private static final String ARG_PARENT = "parent";

    private static final long ID_STATUS = 1;
    private static final long ID_BOOKMARK = 2;
    private static final long ID_PLAY = 3;
    private static final long ID_RETRY = 4;
    private static final long ID_PLAY_ANYWAY = 5;
    private static final long ID_RESUME = 6;
    private static final long ID_SEARCH = 7;
    private static final long ID_SEARCH_CLEAR = 8;
    private static final long ID_ITEM_BASE = 1000;

    private enum Mode { LOADING, FOLDER, VIDEO, ERROR }

    private static final ExecutorService sNet = Executors.newFixedThreadPool(2);

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Overlay mOverlay;

    private String mUrl;
    private String mName;
    private int mHint;
    private boolean mRecord;
    private String mParent;
    private StreamLink mLink;

    private Mode mMode = Mode.LOADING;
    private int mRequest = 0;
    private long mSize = -1;
    private String mFileName;
    private String mError;
    private final List<FolderParser.Item> mItems = new ArrayList<>();
    /** Folder search: only names containing every word are listed. Empty = everything. */
    private String mQuery = "";
    /** When the current list appeared: presses right after it are a remote's double press. */
    private long mShownAt;
    private static final long DOUBLE_PRESS_GUARD_MS = 700;

    /**
     * @param record add the link to "Recent streams" once it opens as a folder (videos are added when played)
     */
    public static StreamBrowseFragment newInstance(String url, String name, int hint, long size,
                                                   boolean record, String parentName) {
        StreamBrowseFragment f = new StreamBrowseFragment();
        Bundle b = new Bundle();
        b.putString(ARG_URL, url);
        b.putString(ARG_NAME, name);
        b.putInt(ARG_HINT, hint);
        b.putLong(ARG_SIZE, size);
        b.putBoolean(ARG_RECORD, record);
        b.putString(ARG_PARENT, parentName);
        f.setArguments(b);
        return f;
    }

    /** Opens a link on top of the current screen; Back returns to it. */
    public static void open(GuidedStepSupportFragment from, String url, String name, int hint, long size,
                            boolean record, String parentName) {
        FragmentManager fm = from.getParentFragmentManager();
        GuidedStepSupportFragment.add(fm, newInstance(url, name, hint, size, record, parentName));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle a = requireArguments();
        mUrl = a.getString(ARG_URL, "");
        mName = a.getString(ARG_NAME);
        mHint = a.getInt(ARG_HINT, HINT_UNKNOWN);
        mSize = a.getLong(ARG_SIZE, -1);
        mRecord = a.getBoolean(ARG_RECORD, false);
        mParent = a.getString(ARG_PARENT);
        mLink = StreamLink.parse(mUrl);
        if (mName == null || mName.isEmpty()) mName = mLink != null ? mLink.guessedName : mUrl;
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(mName, getString(R.string.prime_stream_loading),
                breadcrumb(), icon(mHint == HINT_FOLDER));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        buildActions(actions);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
        if (mMode == Mode.LOADING) load();
    }

    @Override
    public void onDestroyView() {
        mRequest++; // ignore results that arrive after the screen is gone
        if (mOverlay != null) mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOverlay != null) mOverlay.resume();
        mShownAt = SystemClock.uptimeMillis();
        // Bookmarks and watched positions may have changed in a screen opened from here
        if (mMode == Mode.FOLDER || mMode == Mode.VIDEO) {
            int selected = getSelectedActionPosition();
            refreshActions();
            if (selected >= 0 && selected < getActions().size()) setSelectedActionPosition(selected);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOverlay != null) mOverlay.pause();
    }

    // ---------------------------------------------------------------- loading

    private void load() {
        if (mLink == null) {
            showError(getString(R.string.prime_stream_invalid), false);
            return;
        }
        mMode = Mode.LOADING;
        mError = null;
        refreshActions();
        final int request = ++mRequest;
        final boolean http = StreamNet.isHttp(mLink.playUri.toString());

        if (!http) {
            showVideo(null); // rtsp, udp…: nothing to check, show the details right away
            return;
        }
        if (mHint == HINT_VIDEO && mSize > 0) {
            showVideo(null); // size already known from the folder listing
            return;
        }
        final boolean folder = mHint == HINT_FOLDER || mLink.looksLikeFolder();
        sNet.execute(() -> {
            StreamNet.Result r = folder ? StreamNet.listFolder(mLink.playUri.toString()) : StreamNet.probe(mLink);
            mHandler.post(() -> {
                if (request != mRequest || !isAdded()) return;
                if (r.folder) {
                    if (r.error != null) showError(r.error, false);
                    else showFolder(r);
                } else {
                    if (r.size > 0) mSize = r.size;
                    if (r.fileName != null && !r.fileName.isEmpty()) mFileName = r.fileName;
                    showVideo(r.error);
                }
            });
        });
    }

    private void showFolder(StreamNet.Result r) {
        mMode = Mode.FOLDER;
        mItems.clear();
        if (r.items != null) mItems.addAll(r.items);
        if (mItems.isEmpty() && mHint != HINT_FOLDER && !mLink.looksLikeFolder()) {
            // A web page, not a folder listing
            showError(getString(R.string.prime_stream_not_folder), true);
            return;
        }
        if (mRecord) StreamHistory.add(requireContext(), mUrl, mName);
        int folders = 0, videos = 0;
        for (FolderParser.Item i : mItems) {
            if (i.folder) folders++;
            else videos++;
        }
        setGuidance(mName, getString(R.string.prime_stream_folder_summary,
                getResources().getQuantityString(R.plurals.prime_stream_folders, folders, folders),
                getResources().getQuantityString(R.plurals.prime_stream_videos, videos, videos),
                host()), true);
        refreshActions();
    }

    private void showVideo(String error) {
        mMode = Mode.VIDEO;
        StringBuilder d = new StringBuilder();
        if (mFileName != null) d.append(mFileName).append('\n');
        else {
            String last = mLink.playUri.getLastPathSegment();
            if (last != null && !last.isEmpty() && mLink.kind == StreamLink.Kind.DIRECT) d.append(last).append('\n');
        }
        String size = FolderParser.formatSize(mSize);
        if (!size.isEmpty()) d.append(size).append("  ·  ");
        d.append(host());
        if (error != null) d.append('\n').append(getString(R.string.prime_stream_check_failed, error));
        setGuidance(displayName(), d.toString(), false);
        refreshActions();
    }

    private void showError(String error, boolean canPlayAnyway) {
        mMode = Mode.ERROR;
        mError = error;
        mHint = canPlayAnyway ? HINT_UNKNOWN : mHint;
        setGuidance(mName, getString(R.string.prime_stream_error, error), mHint == HINT_FOLDER);
        refreshActions();
    }

    // ---------------------------------------------------------------- actions

    private void buildActions(List<GuidedAction> actions) {
        Context c = requireContext();
        switch (mMode) {
            case LOADING:
                actions.add(info(c, getString(R.string.prime_stream_loading), null));
                break;
            case FOLDER:
                if (mItems.isEmpty()) {
                    actions.add(info(c, getString(R.string.prime_stream_folder_empty), null));
                } else {
                    actions.add(new GuidedAction.Builder(c)
                            .id(ID_SEARCH)
                            .title(getString(R.string.prime_stream_search_folder))
                            .description(mQuery.isEmpty() ? null : getString(R.string.prime_stream_search_showing, mQuery))
                            .build());
                    if (!mQuery.isEmpty()) {
                        actions.add(new GuidedAction.Builder(c)
                                .id(ID_SEARCH_CLEAR)
                                .title(getString(R.string.prime_stream_search_clear))
                                .build());
                    }
                }
                int shown = 0;
                for (int i = 0; i < mItems.size(); i++) {
                    FolderParser.Item item = mItems.get(i);
                    if (!StreamSearch.matches(item.name, mQuery)) continue;
                    shown++;
                    String desc = item.folder ? getString(R.string.prime_stream_folder_label)
                            : join(join(getString(R.string.prime_stream_video_label), FolderParser.formatSize(item.size)),
                                    watched(StreamProgress.find(c, item.url)));
                    actions.add(new GuidedAction.Builder(c)
                            .id(ID_ITEM_BASE + i)
                            .title(item.folder ? item.name : stripExtension(item.name))
                            .description(desc)
                            .multilineDescription(false)
                            .build());
                }
                if (shown == 0 && !mItems.isEmpty()) {
                    actions.add(info(c, getString(R.string.prime_stream_search_none, mQuery), null));
                }
                // Last in the list, so a remote's double press never lands on it
                actions.add(bookmarkAction(c, true));
                break;
            case VIDEO:
                StreamProgress.Entry progress = StreamProgress.find(c, mUrl);
                if (progress != null && progress.canResume()) {
                    actions.add(new GuidedAction.Builder(c)
                            .id(ID_RESUME)
                            .title(getString(R.string.prime_stream_resume, StreamProgress.formatTime(progress.position)))
                            .description(watched(progress))
                            .build());
                    actions.add(new GuidedAction.Builder(c)
                            .id(ID_PLAY)
                            .title(getString(R.string.prime_stream_play_from_start))
                            .build());
                } else {
                    actions.add(new GuidedAction.Builder(c)
                            .id(ID_PLAY)
                            .title(getString(R.string.prime_stream_play))
                            .build());
                }
                actions.add(bookmarkAction(c, false));
                break;
            case ERROR:
                actions.add(info(c, mError, null));
                actions.add(new GuidedAction.Builder(c)
                        .id(ID_RETRY)
                        .title(getString(R.string.prime_stream_retry))
                        .build());
                if (mLink != null && mHint != HINT_FOLDER) {
                    actions.add(new GuidedAction.Builder(c)
                            .id(ID_PLAY_ANYWAY)
                            .title(getString(R.string.prime_stream_play_anyway))
                            .build());
                }
                break;
        }
    }

    private GuidedAction bookmarkAction(Context c, boolean folder) {
        boolean saved = StreamBookmarks.contains(c, mUrl);
        String title = saved ? getString(R.string.prime_stream_bookmark_remove)
                : getString(folder ? R.string.prime_stream_bookmark_folder : R.string.prime_stream_bookmark_video);
        return new GuidedAction.Builder(c)
                .id(ID_BOOKMARK)
                .title(title)
                .description(getString(saved ? R.string.prime_stream_bookmarked : R.string.prime_stream_bookmark_hint))
                .build();
    }

    /** "Watched 23:10 of 45:00", or null when there is nothing worth showing. */
    private String watched(StreamProgress.Entry p) {
        if (p == null || !p.canResume()) return null;
        return p.duration > 0
                ? getString(R.string.prime_stream_watched, StreamProgress.formatTime(p.position), StreamProgress.formatTime(p.duration))
                : getString(R.string.prime_stream_watched_no_total, StreamProgress.formatTime(p.position));
    }

    private GuidedAction info(Context c, String title, String description) {
        return new GuidedAction.Builder(c)
                .id(ID_STATUS)
                .title(title)
                .description(description)
                .infoOnly(true)
                .focusable(false)
                .multilineDescription(true)
                .build();
    }

    private void refreshActions() {
        if (!isAdded()) return;
        List<GuidedAction> actions = new ArrayList<>();
        buildActions(actions);
        setActions(actions);
        mShownAt = SystemClock.uptimeMillis();
        // Land on the first thing that can be clicked
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).isFocusable() && !actions.get(i).infoOnly()) {
                setSelectedActionPosition(i);
                break;
            }
        }
    }

    private void updateBookmarkAction() {
        int pos = findActionPositionById(ID_BOOKMARK);
        if (pos < 0) return;
        GuidedAction fresh = bookmarkAction(requireContext(), mMode == Mode.FOLDER);
        GuidedAction a = getActions().get(pos);
        a.setTitle(fresh.getTitle());
        a.setDescription(fresh.getDescription());
        notifyActionChanged(pos);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (SystemClock.uptimeMillis() - mShownAt < DOUBLE_PRESS_GUARD_MS) return; // remote double press
        long id = action.getId();
        Context c = requireContext();
        if (id == ID_BOOKMARK) {
            boolean folder = mMode == Mode.FOLDER;
            if (StreamBookmarks.contains(c, mUrl)) {
                StreamBookmarks.remove(c, mUrl);
                Toast.makeText(c, R.string.prime_stream_bookmark_removed, Toast.LENGTH_SHORT).show();
            } else {
                StreamBookmarks.add(c, mUrl, folder ? mName : displayName(), folder);
                Toast.makeText(c, R.string.prime_stream_bookmark_added, Toast.LENGTH_SHORT).show();
            }
            updateBookmarkAction();
        } else if (id == ID_RESUME) {
            StreamProgress.Entry p = StreamProgress.find(c, mUrl);
            play(p != null ? p.position : 0);
        } else if (id == ID_PLAY || id == ID_PLAY_ANYWAY) {
            play(0);
        } else if (id == ID_SEARCH) {
            StreamSearch.ask(c, mQuery, query -> {
                if (!isAdded()) return;
                mQuery = query;
                refreshActions();
                // land on the first match
                List<GuidedAction> list = getActions();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getId() >= ID_ITEM_BASE) {
                        setSelectedActionPosition(i);
                        break;
                    }
                }
            });
        } else if (id == ID_SEARCH_CLEAR) {
            mQuery = "";
            refreshActions();
        } else if (id == ID_RETRY) {
            load();
        } else if (id >= ID_ITEM_BASE) {
            int index = (int) (id - ID_ITEM_BASE);
            if (index < 0 || index >= mItems.size()) return;
            FolderParser.Item item = mItems.get(index);
            if (item.folder) {
                open(this, item.url, item.name, HINT_FOLDER, -1, false, mName);
            } else {
                open(this, item.url, stripExtension(item.name), HINT_VIDEO, item.size, false, mName);
            }
        }
    }

    private void play(int positionMs) {
        if (mLink == null) return;
        startPlayer(requireContext(), mLink, mUrl, displayName(), positionMs);
    }

    /**
     * Starts NOVA's player on a stream link, from {@code positionMs} when it is above zero.
     * The link is added to the recent streams and its watched position is tracked.
     */
    static void startPlayer(Context c, StreamLink link, String url, String name, int positionMs) {
        StreamHistory.add(c, url, name);
        StreamProgress.start(c, url, name, link.playUri.toString());
        if (log.isDebugEnabled()) log.debug("play: {} -> {} ({}) from {}", url, link.playUri, link.mimeType, positionMs);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(c, PlayerActivity.class);
        intent.setDataAndType(link.playUri, link.mimeType);
        if (positionMs > 0) intent.putExtra("position", positionMs); // NOVA's start position extra
        try {
            c.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            log.error("play: cannot start player", e);
            Toast.makeText(c, R.string.prime_stream_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- helpers

    private String displayName() {
        if (mFileName != null && (mLink == null || mLink.kind != StreamLink.Kind.DIRECT || mName == null)) {
            return stripExtension(mFileName);
        }
        return mName;
    }

    private String breadcrumb() {
        return mParent != null && !mParent.isEmpty() ? mParent : getString(R.string.prime_stream);
    }

    private String host() {
        if (mLink == null) return "";
        Uri u = mLink.playUri;
        return u.getHost() == null ? "" : u.getHost();
    }

    private void setGuidance(String title, String description, boolean folder) {
        GuidanceStylist s = getGuidanceStylist();
        TextView t = s.getTitleView();
        if (t != null) t.setText(title);
        TextView d = s.getDescriptionView();
        if (d != null) d.setText(description);
        ImageView i = s.getIconView();
        if (i != null) i.setImageDrawable(icon(folder));
    }

    private android.graphics.drawable.Drawable icon(boolean folder) {
        return ContextCompat.getDrawable(requireContext(),
                folder ? R.drawable.filetype_new_folder : R.drawable.filetype_new_video);
    }

    private static String join(String a, String b) {
        return b == null || b.isEmpty() ? a : a + "  ·  " + b;
    }

    static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return name;
        String ext = name.substring(dot + 1);
        return ext.length() <= 5 && ext.matches("[A-Za-z0-9]+") ? name.substring(0, dot) : name;
    }
}
