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

package com.archos.mediacenter.video.leanback;

import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

/**
 * Shows two adapters one after the other in a single row: Nova Player Plus puts its
 * "Continue watching" cards in front of NOVA's own Recently added cards. Each item keeps the
 * presenter of the adapter it comes from; any change in either one refreshes the row.
 */
public class PrimeJoinedAdapter extends ObjectAdapter {

    private final ObjectAdapter mFirst;
    private final ObjectAdapter mSecond;

    public PrimeJoinedAdapter(ObjectAdapter first, ObjectAdapter second) {
        mFirst = first;
        mSecond = second;
        setPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object item) {
                for (int i = 0; i < mFirst.size(); i++) {
                    if (mFirst.get(i) == item) return mFirst.getPresenter(item);
                }
                return mSecond.getPresenter(item);
            }

            @Override
            public Presenter[] getPresenters() {
                Presenter[] a = mFirst.getPresenterSelector().getPresenters();
                Presenter[] b = mSecond.getPresenterSelector().getPresenters();
                if (a == null) return b;
                if (b == null) return a;
                Presenter[] all = new Presenter[a.length + b.length];
                System.arraycopy(a, 0, all, 0, a.length);
                System.arraycopy(b, 0, all, a.length, b.length);
                return all;
            }
        });
        DataObserver changed = new DataObserver() {
            @Override
            public void onChanged() {
                notifyChanged();
            }
        };
        mFirst.registerObserver(changed);
        mSecond.registerObserver(changed);
    }

    @Override
    public int size() {
        return mFirst.size() + mSecond.size();
    }

    @Override
    public Object get(int position) {
        int n = mFirst.size();
        return position < n ? mFirst.get(position) : mSecond.get(position - n);
    }
}
