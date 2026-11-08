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

package com.archos.mediacenter.video.prime;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.archos.mediacenter.video.BuildConfig;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.prime.support.PrimeSupportDialogs;
import com.archos.mediacenter.video.prime.update.PrimeUpdater;
import com.archos.mediacenter.video.utils.WebUtils;

/**
 * Wires the "NovaPlayer Prime" settings category (declared in preferences_video.xml):
 * update checker, credits to the original NOVA developers, and support links.
 */
public final class PrimePreferences {

    private PrimePreferences() {}

    public static void attach(final PreferenceFragmentCompat fragment) {
        final Context context = fragment.getContext();
        if (context == null) return;

        Preference credits = fragment.findPreference("prime_credits");
        if (credits != null) {
            credits.setOnPreferenceClickListener(p -> {
                WebUtils.openWebLink(fragment.requireActivity(), PrimeConfig.GITHUB_PROFILE_URL);
                return true;
            });
        }

        Preference supportMe = fragment.findPreference("prime_support_me");
        if (supportMe != null) {
            supportMe.setOnPreferenceClickListener(p -> {
                PrimeSupportDialogs.showSupport(fragment.requireActivity());
                return true;
            });
        }

        Preference supportNova = fragment.findPreference("prime_support_nova");
        if (supportNova != null) {
            supportNova.setOnPreferenceClickListener(p -> {
                WebUtils.openWebLink(fragment.requireActivity(), PrimeConfig.NOVA_DONATE_URL);
                return true;
            });
        }

        final Preference update = fragment.findPreference("prime_update");
        if (update != null) {
            final PrimeUpdater updater = PrimeUpdater.get(context);
            final PrimeUpdater.Listener listener = u -> refreshUpdatePreference(fragment, update, u);
            update.setOnPreferenceClickListener(p -> {
                if (updater.isUpdateAvailable()) updater.install();
                else updater.check();
                return true;
            });
            refreshUpdatePreference(fragment, update, updater);
            updater.addListener(listener);
            fragment.getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    updater.removeListener(listener);
                }
            });
        }
    }

    private static void refreshUpdatePreference(PreferenceFragmentCompat fragment, Preference pref, PrimeUpdater u) {
        Context c = fragment.getContext();
        if (c == null) return;
        boolean available = u.isUpdateAvailable();
        pref.setTitle(available ? R.string.prime_update_now : R.string.prime_update_check);
        String current = c.getString(R.string.prime_update_current, BuildConfig.VERSION_NAME);
        String summary;
        switch (u.getState()) {
            case CHECKING:
                summary = c.getString(R.string.prime_update_checking);
                break;
            case DOWNLOADING:
                summary = c.getString(R.string.prime_update_downloading, u.getProgress());
                break;
            case INSTALLING:
                summary = c.getString(R.string.prime_update_installing);
                break;
            case UP_TO_DATE:
                summary = c.getString(R.string.prime_update_up_to_date) + "\n" + current;
                break;
            case ERROR:
                summary = c.getString(available ? R.string.prime_update_failed_retry : R.string.prime_update_failed, u.getError());
                break;
            default:
                summary = available
                        ? c.getString(R.string.prime_update_available, u.getAvailableVersionName())
                        : current;
                break;
        }
        if (available && u.getState() == PrimeUpdater.State.UP_TO_DATE) {
            summary = c.getString(R.string.prime_update_available, u.getAvailableVersionName());
        }
        pref.setSummary(summary);
    }
}
