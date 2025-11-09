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

package com.android.providers.contacts.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;
import androidx.work.WorkManager;

public class WorkManagerInitializer {
    private static final String TAG = "WorkManagerInitializer";

    /**
     * Initialize the {@link WorkManager} if it is not initialized already.
     *
     * @return a {@link WorkManager} object that can be used to run work requests.
     */
    @NonNull
    public static WorkManager getWorkManager(Context mContext) {
        if (!WorkManager.isInitialized()) {
            Log.i(TAG, "Work manager not initialised. Attempting to initialise.");
            WorkManager.initialize(mContext, getWorkManagerConfiguration());
        }
        return WorkManager.getInstance(mContext);
    }

    @NonNull
    private static Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setDefaultProcessName("android.process.acore")
                .build();
    }
}
