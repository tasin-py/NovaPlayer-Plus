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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Receives the PackageInstaller result for a self-update. */
public class PrimeInstallReceiver extends BroadcastReceiver {

    private static final Logger log = LoggerFactory.getLogger(PrimeInstallReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (log.isDebugEnabled()) log.debug("install status {} {}", status, message);
        PrimeUpdater updater = PrimeUpdater.get(context);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // Android requires a one-time confirmation (first self-update, the "allow installs
                // from this app" switch, or Android 11 and older). This is the system screen, not ours.
                Intent confirm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                        : intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(confirm);
                    } catch (Exception e) {
                        log.error("cannot show install confirmation", e);
                        updater.onInstallFinished(false, e.getMessage());
                    }
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                updater.onInstallFinished(true, null);
                break;
            default:
                updater.onInstallFinished(false, message);
                break;
        }
    }
}
