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

package com.archos.mediacenter.video.prime.support;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.prime.PrimeConfig;
import com.archos.mediacenter.video.utils.WebUtils;

/**
 * Small "Support tasin-py" popup (socials + Buy me a coffee) and the QR code popup.
 * Built with the app's standard AlertDialog so it follows the current theme.
 */
public final class PrimeSupportDialogs {

    private PrimeSupportDialogs() {}

    public static void showSupport(Context context) {
        LinearLayout content = column(context);

        TextView intro = text(context, context.getString(R.string.prime_support_intro), 15);
        content.addView(intro);

        for (String[] social : PrimeConfig.SOCIALS) {
            final String url = social[1];
            content.addView(button(context, social[0] + "  ·  " + url.replaceFirst("^https?://(www\\.)?", ""),
                    v -> WebUtils.openWebLink(context, url)));
        }

        final AlertDialog[] holder = new AlertDialog[1];
        Button coffee = button(context, context.getString(R.string.prime_support_coffee), v -> {
            if (holder[0] != null) holder[0].dismiss();
            showCoffee(context);
        });
        content.addView(coffee);

        holder[0] = new AlertDialog.Builder(context)
                .setTitle(R.string.prime_support_title)
                .setView(scroll(context, content))
                .setNegativeButton(R.string.prime_close, null)
                .create();
        holder[0].setOnShowListener(d -> coffee.requestFocus());
        holder[0].show();
    }

    public static void showCoffee(Context context) {
        LinearLayout content = column(context);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView qr = new ImageView(context);
        qr.setImageResource(R.drawable.prime_support_qr);
        qr.setAdjustViewBounds(true);
        qr.setContentDescription(context.getString(R.string.prime_support_qr_description));
        int size = dp(context, 220);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(qr, lp);

        TextView hint = text(context, context.getString(R.string.prime_support_qr_hint), 14);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(hint);

        Button link = button(context, PrimeConfig.SUPPORT_URL.replaceFirst("^https?://(www\\.)?", ""),
                v -> WebUtils.openWebLink(context, PrimeConfig.SUPPORT_URL));
        content.addView(link);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.prime_support_coffee)
                .setView(scroll(context, content))
                .setNegativeButton(R.string.prime_close, null)
                .create();
        dialog.setOnShowListener(d -> link.requestFocus());
        dialog.show();
    }

    // ------------------------------------------------------------ tiny view helpers

    private static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 20);
        l.setPadding(p, dp(c, 8), p, 0);
        return l;
    }

    private static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.addView(child);
        return s;
    }

    private static TextView text(Context c, String s, int sp) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    private static Button button(Context c, String label, View.OnClickListener click) {
        Button b = new Button(c, null, android.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setFocusable(true);
        b.setOnClickListener(click);
        return b;
    }

    private static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
