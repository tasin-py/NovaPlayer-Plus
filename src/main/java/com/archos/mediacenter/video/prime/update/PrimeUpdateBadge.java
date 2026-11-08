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

package com.archos.mediacenter.video.prime.update;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/** Red notification dot drawn over the top-right corner of a home-screen icon. */
public final class PrimeUpdateBadge {

    private PrimeUpdateBadge() {}

    public static View addTo(RelativeLayout shell, ImageView icon) {
        float d = shell.getResources().getDisplayMetrics().density;
        if (icon.getId() == View.NO_ID) icon.setId(View.generateViewId());

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFFE53935);
        dot.setStroke(Math.round(2 * d), Color.WHITE);

        View badge = new View(shell.getContext());
        badge.setBackground(dot);
        badge.setVisibility(View.GONE);
        badge.setFocusable(false);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        int size = Math.round(16 * d);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(size, size);
        lp.addRule(RelativeLayout.ALIGN_TOP, icon.getId());
        lp.addRule(RelativeLayout.ALIGN_END, icon.getId());
        // icon has 12dp padding: place the dot near the glyph's corner
        lp.topMargin = Math.round(14 * d);
        lp.setMarginEnd(Math.round(26 * d));
        shell.addView(badge, lp);
        return badge;
    }
}
