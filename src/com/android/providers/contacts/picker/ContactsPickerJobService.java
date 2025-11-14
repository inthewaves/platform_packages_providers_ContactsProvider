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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.provider.ContactsPickerSessionContract;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A {@link JobService} that cleans up stale contacts picker sessions. */
public class ContactsPickerJobService extends JobService {
    private static final String TAG = "ContactsPickerJob";
    private static final boolean VERBOSE_LOGGING = Log.isLoggable(TAG, Log.VERBOSE);
    private static final String CLEANUP_METHOD = "cleanupStaleSessions";
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        mExecutorService.execute(
                () -> {
                    try {
                        Log.i(TAG, "Starting cleanup of stale contacts picker sessions.");
                        getContentResolver()
                                .call(
                                        ContactsPickerSessionContract.AUTHORITY_URI,
                                        CLEANUP_METHOD,
                                        null,
                                        null);
                        if (VERBOSE_LOGGING) {
                            Log.d(TAG, "Cleanup task finished call to provider");
                        }
                        jobFinished(params, /* wantsReschedule= */ false);
                    } catch (Exception e) {
                        Log.e(TAG, "Exception during cleanup task", e);
                        jobFinished(params, /* wantsReschedule= */ true);
                    }
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdown();
    }
}
