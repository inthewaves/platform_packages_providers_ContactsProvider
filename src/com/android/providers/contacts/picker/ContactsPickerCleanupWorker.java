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
import android.provider.ContactsPickerSessionContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** A {@link Worker} that cleans up stale contacts picker sessions. */
public class ContactsPickerCleanupWorker extends Worker {

    private static final String TAG = "ContactsPickerWork";
    private static final String CLEANUP_METHOD = "cleanupStaleSessions";
    private static final boolean VERBOSE_LOGGING = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int MAX_RETRY_COUNT = 3;

    public ContactsPickerCleanupWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.i(TAG, "Starting cleanup of stale contacts picker sessions.");
            getApplicationContext()
                    .getContentResolver()
                    .call(ContactsPickerSessionContract.AUTHORITY_URI, CLEANUP_METHOD, null, null);

            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Cleanup task finished call to provider");
            }
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "Exception during cleanup task", e);
            if (getRunAttemptCount() < MAX_RETRY_COUNT) {
                return Result.retry();
            } else {
                Log.e(TAG, "Max retries reached for cleanup task");
                return Result.failure();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception during cleanup task", e);
            return Result.failure();
        }
    }
}
