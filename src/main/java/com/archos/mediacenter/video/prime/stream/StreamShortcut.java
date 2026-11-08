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

import android.net.Uri;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.Shortcut;

import java.io.Serializable;

/** "Stream" tile shown next to "Browse a network share" in the Network screen. */
public class StreamShortcut extends Shortcut implements Serializable {

    public StreamShortcut(String name) {
        super(-2, "", "", name);
    }

    @Override
    public Uri getUri() {
        return null;
    }

    @Override
    public int getImage() {
        return R.drawable.filetype_new_video;
    }
}
