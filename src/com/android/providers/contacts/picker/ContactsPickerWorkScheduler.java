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

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.android.providers.contacts.work.WorkManagerInitializer;

import java.util.concurrent.TimeUnit;

/** Schedules the periodic cleanup work for the contacts picker sessions. */
public class ContactsPickerWorkScheduler {

    private static final String TAG = "ContactsPickerWork";
    private static final String CLEANUP_WORK_NAME = "ContactsPickerCleanup";
    static final long CLEANUP_INTERVAL_DAYS = 1;
    private static final boolean VERBOSE_LOGGING = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * Schedules a periodic work to clean up stale contacts picker sessions.
     *
     * <p>The work is scheduled to run once a day when the device is idle. If the work is already
     * scheduled, this method will keep the existing work.
     *
     * @param context The {@link Context} to use for scheduling the work.
     */
    public static void schedulePeriodicCleanupWork(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot schedule cleanup work.");
            throw new IllegalArgumentException("Context cannot be null");
        }

        WorkManager workManager = WorkManagerInitializer.getWorkManager(context);

        Constraints constraints =
                new Constraints.Builder()
                        .setRequiresDeviceIdle(true)
                        .setRequiresBatteryNotLow(true)
                        .build();

        PeriodicWorkRequest cleanupRequest =
                new PeriodicWorkRequest.Builder(
                                ContactsPickerCleanupWorker.class,
                                CLEANUP_INTERVAL_DAYS,
                                TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .build();

        workManager.enqueueUniquePeriodicWork(
                CLEANUP_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, cleanupRequest);

        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Successfully enqueued periodic cleanup work (" + CLEANUP_WORK_NAME + ").");
        }
    }
}
