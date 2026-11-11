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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.Presenter;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.prime.stream.StreamProgress;
import com.archos.mediacenter.video.utils.ThemeManager;

/**
 * Card for a streamed video you stopped part way ("Continue watching"), shown first in the
 * home screen's Recently added row. Same size and resume bar as NOVA's poster cards.
 * Holding OK on it calls {@link OnHold} (to remove it).
 */
public class PrimeContinuePresenter extends Presenter {

    public interface OnHold {
        void onHold(StreamProgress.Entry entry);
    }

    private final OnHold mOnHold;

    public PrimeContinuePresenter(OnHold onHold) {
        mOnHold = onHold;
    }

    private static final class Holder extends ViewHolder {
        final CustomImageCardview card;
        final View progress;
        StreamProgress.Entry entry;

        Holder(CustomImageCardview card, View progress) {
            super(card);
            this.card = card;
            this.progress = progress;
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context c = parent.getContext();
        CustomImageCardview card = new CustomImageCardview(c);
        int w = c.getResources().getDimensionPixelSize(R.dimen.poster_width);
        int h = c.getResources().getDimensionPixelSize(R.dimen.poster_height);
        card.setMainImageDimensions(w, h);
        card.getMainImageView().setBackgroundColor(ThemeManager.getInstance(c).getGridItemBackgroundColor());
        card.setMainImageScaleType(ImageView.ScaleType.CENTER);
        card.setMainImage(ContextCompat.getDrawable(c, R.drawable.filetype_new_video), false);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);

        View group = LayoutInflater.from(c).inflate(R.layout.leanback_resume, card, false);
        View bar = group.findViewById(R.id.resume_bar);
        bar.getLayoutParams().width = 0;
        BaseCardView.LayoutParams lp = new BaseCardView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_INFO;
        card.addView(group, card.getChildCount(), lp);

        Holder holder = new Holder(card, bar);
        card.setOnLongClickListener(v -> {
            if (holder.entry != null && mOnHold != null) mOnHold.onHold(holder.entry);
            return true;
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        Holder h = (Holder) viewHolder;
        StreamProgress.Entry e = (StreamProgress.Entry) item;
        h.entry = e;
        Context c = h.card.getContext();
        h.card.setTitleText(e.name);
        String watched = e.duration > 0
                ? c.getString(R.string.prime_stream_watched, StreamProgress.formatTime(e.position), StreamProgress.formatTime(e.duration))
                : c.getString(R.string.prime_stream_watched_no_total, StreamProgress.formatTime(e.position));
        h.card.setContentText(c.getString(R.string.prime_stream_continue) + " · " + watched);
        int width = c.getResources().getDimensionPixelSize(R.dimen.poster_width);
        h.progress.getLayoutParams().width = e.duration > 0
                ? (int) (width * Math.min(1f, e.position / (float) e.duration)) : 0;
        h.progress.requestLayout();
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        ((Holder) viewHolder).entry = null;
    }
}
