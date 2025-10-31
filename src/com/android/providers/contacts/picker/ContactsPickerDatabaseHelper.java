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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

class ContactsPickerDatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "contact_picker_sessions.db";
    private static final Object SINGLETON_LOCK = new Object();

    public interface Tables {
        String SESSIONS = "sessions";
    }

    public interface SessionColumns extends BaseColumns {
        String DATA_ROW_IDS = "data_row_ids";
        String SESSION_UID = "session_uid";
        String CALLER_UID = "caller_uid";
        String CREATED_AT = "created_at";
    }

    private static volatile ContactsPickerDatabaseHelper sInstance;

    public static ContactsPickerDatabaseHelper getInstance(Context context) {
        ContactsPickerDatabaseHelper singleReadResult = sInstance;
        if (singleReadResult != null) {
            return singleReadResult;
        }
        synchronized (SINGLETON_LOCK) {
            if (sInstance == null) {
                sInstance = new ContactsPickerDatabaseHelper(context);
            }
        }
        return sInstance;
    }

    ContactsPickerDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Tables.SESSIONS + " ("
                + SessionColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SessionColumns.SESSION_UID + " TEXT UNIQUE NOT NULL,"
                + SessionColumns.DATA_ROW_IDS + " TEXT,"
                + SessionColumns.CALLER_UID + " INTEGER NOT NULL,"
                + SessionColumns.CREATED_AT + " INTEGER NOT NULL"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nothing to do here, still on version 1.
    }
}
