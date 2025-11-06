/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.contacts.picker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A {@link BroadcastReceiver} that ensures the contacts picker cleanup work is actively scheduled
 * with WorkManager, especially after system events like boot or package updates.
 *
 * <p>While WorkManager persists work requests across reboots and app updates, this receiver
 * acts as a trigger to ensure WorkManager re-evaluates and re-synchronizes these tasks
 * with the underlying system schedulers at the correct times.
 *
 * <ul>
 *   <li>{@link Intent#ACTION_BOOT_COMPLETED}: After a device reboot, this prompts WorkManager
 *       to re-register its persisted tasks with the system's job scheduler.
 *   <li>{@link Intent#ACTION_MY_PACKAGE_REPLACED}: When the ContactsProvider package is updated,
 *       explicitly rescheduling ensures the new version of the app correctly registers
 *       the cleanup task, applying any changes in the work definition and ensuring
 *       the WorkManager instance in the new version is fully initialized and synchronized.
 * </ul>
 *
 * <p>This is a best practice to guarantee the reliability and consistency of periodic work.
 */
public class ContactsPickerSchedulingReceiver extends BroadcastReceiver {
    private static final String TAG = "ContactsPickerWork";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.i(TAG, "Received action: " + action + ", scheduling cleanup work.");
            ContactsPickerWorkScheduler.schedulePeriodicCleanupWork(context);
        } else {
            Log.w(TAG, "Received unexpected action: " + action);
        }
    }
}
