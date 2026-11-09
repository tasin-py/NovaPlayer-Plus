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

import android.os.Bundle;

import androidx.leanback.app.GuidedStepSupportFragment;

import com.archos.mediacenter.video.leanback.LeanbackActivity;

/**
 * Hosts {@link StreamFragment} (Network &gt; Stream), or {@link StreamBookmarksFragment} for the
 * home screen's "Bookmarked" folder. Same structure as NOVA's Rescan screen.
 */
public class StreamActivity extends LeanbackActivity {

    public static final String EXTRA_BOOKMARKS = "prime_bookmarks";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            boolean bookmarks = getIntent().getBooleanExtra(EXTRA_BOOKMARKS, false);
            GuidedStepSupportFragment.addAsRoot(this,
                    bookmarks ? new StreamBookmarksFragment() : new StreamFragment(), android.R.id.content);
        }
    }
}
