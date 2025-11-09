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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.provider.ContactsContract.Data;
import android.provider.ContactsPickerSessionContract;
import android.text.TextUtils;
import android.util.Log;

import com.android.providers.contacts.picker.ContactsPickerDatabaseHelper.SessionColumns;
import com.android.providers.contacts.picker.ContactsPickerDatabaseHelper.Tables;
import com.android.providers.contacts.picker.ContactsPickerWorkScheduler;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ContentProvider} for creating and managing contacts picker sessions.
 *
 * <p>This provider allows callers to create a session by inserting a set of data row IDs. A unique
 * session URI is returned, which can then be used to query the corresponding contact data.
 *
 * <p>Sessions older than 24 hours are automatically cleaned up by a daily job.
 */
public final class ContactsPickerSessionProvider extends ContentProvider {

    private static final String TAG = "ContactsPickerSession";
    private static final boolean VERBOSE_LOGGING = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int URI_MATCH_SESSIONS_BASE = 1;
    private static final int URI_MATCH_SESSION_ID = 2;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(
                ContactsPickerSessionContract.AUTHORITY, "sessions", URI_MATCH_SESSIONS_BASE);
        sUriMatcher.addURI(
                ContactsPickerSessionContract.AUTHORITY, "sessions/*", URI_MATCH_SESSION_ID);
    }

    private ContactsPickerDatabaseHelper mDatabaseHelper;

    @Override
    public boolean onCreate() {
        mDatabaseHelper = ContactsPickerDatabaseHelper.getInstance(getContext());
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "insert: uri=" + uri);
        }

        final int match = sUriMatcher.match(uri);
        if (match != URI_MATCH_SESSIONS_BASE) {
            throw new IllegalArgumentException("Unknown/Invalid URI: " + uri);
        }

        validateContentValues(values);

        final String sessionUid = UUID.randomUUID().toString();

        ContentValues valuesToInsert = new ContentValues();
        valuesToInsert.put(SessionColumns.DATA_ROW_IDS,
                values.getAsString(SessionColumns.DATA_ROW_IDS));
        valuesToInsert.put(SessionColumns.SESSION_UID, sessionUid);
        valuesToInsert.put(SessionColumns.CALLER_UID,
                values.getAsInteger(SessionColumns.CALLER_UID));
        valuesToInsert.put(SessionColumns.CREATED_AT, System.currentTimeMillis());

        final SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        final long rowId = db.insert(Tables.SESSIONS, null, valuesToInsert);
        if (rowId <= 0) {
            Log.e(TAG, "Failed to insert session into database.");
            return null;
        }

        final Uri sessionUri =
                Uri.withAppendedPath(ContactsPickerSessionContract.Session.CONTENT_URI, sessionUid);

        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Successfully inserted session: " + sessionUri);
        }

        return sessionUri;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "query: uri=" + uri);
        }

        return switch (sUriMatcher.match(uri)) {
            case URI_MATCH_SESSIONS_BASE -> {
                if (VERBOSE_LOGGING) {
                    Log.w(TAG, "Query on base URI not supported, no session ID provided: " + uri);
                }
                yield null; // No session ID provided
            }
            case URI_MATCH_SESSION_ID -> handleSessionIdQuery(uri, projection, selection,
                    selectionArgs, sortOrder);
            default -> throw new IllegalArgumentException("Unknown URI: " + uri);
        };
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        final int match = sUriMatcher.match(uri);
        return switch (match) {
            case URI_MATCH_SESSION_ID -> ContactsPickerSessionContract.Session.CONTENT_TYPE;
            case URI_MATCH_SESSIONS_BASE -> null; // Base URI not supported for type
            default -> null;
        };
    }

    /**
     * The call() method is exposed for invoking provider-defined methods.
     * Currently, the only supported method is "cleanupStaleSessions".
     *
     * <p>The "cleanupStaleSessions" method is used to trigger the cleanup of stale sessions.
     * This method can only be called by the same process that is running this provider.
     * Calls from other processes will result in a {@link SecurityException}.
     *
     * @param method The name of the method to call. Currently only "cleanupStaleSessions" is
     *               supported.
     * @param arg    Optional string argument.
     * @param extras Optional Bundle of extra data.
     * @return Always returns null.
     * @throws SecurityException if "cleanupStaleSessions" is called from a different process.
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("cleanupStaleSessions".equals(method)) {
            if (Binder.getCallingUid() == android.os.Process.myUid()) {
                cleanupStaleSessions();
            } else {
                throw new SecurityException(
                        "cleanupStaleSessions can only be called by the provider's own process.");
            }
        }
        return null;
    }

    private record SessionData(String dataRowIds, int callerUid) {
    }

    private SessionData getSessionData(String sessionUid) {
        final SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();

        try (Cursor sessionCursor =
                     db.query(
                             Tables.SESSIONS,
                             new String[]{SessionColumns.DATA_ROW_IDS, SessionColumns.CALLER_UID},
                             SessionColumns.SESSION_UID + " = ?",
                             new String[]{sessionUid},
                             null,
                             null,
                             null)) {
            if (!sessionCursor.moveToFirst()) {
                return null; // Session not found
            }

            final String dataRowIdsStr = sessionCursor.getString(0);
            final int callerUid = sessionCursor.getInt(1);
            return new SessionData(dataRowIdsStr, callerUid);
        }
    }

    private Cursor handleSessionIdQuery(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        final String sessionUid = uri.getLastPathSegment();
        final SessionData sessionData = getSessionData(sessionUid);
        if (sessionData == null) {
            if (VERBOSE_LOGGING) {
                Log.w(TAG, "No session found for UID: " + sessionUid);
            }
            return null;
        }

        final int callingUid = Binder.getCallingUid();
        if (sessionData.callerUid != callingUid) {
            throw new SecurityException(
                    "Calling UID " + callingUid + " does not match session owner "
                            + sessionData.callerUid + " for session UID: " + sessionUid);
        }

        DataQuery dataQuery = buildDataQuerySelection(sessionData.dataRowIds, selection,
                selectionArgs);
        return getContext()
                .getContentResolver()
                .query(Data.CONTENT_URI, projection, dataQuery.selection, dataQuery.selectionArgs,
                        sortOrder);
    }

    private static class DataQuery {
        final String selection;
        final String[] selectionArgs;

        DataQuery(String selection, String[] selectionArgs) {
            this.selection = selection;
            this.selectionArgs = selectionArgs;
        }
    }

    private DataQuery buildDataQuerySelection(String dataRowIds, String callerSelection,
            String[] callerSelectionArgs) {
        String[] dataIds = dataRowIds.split(",");
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < dataIds.length; i++) {
            inClause.append(i == 0 ? "?" : ",?");
        }

        String finalSelection = Data._ID + " IN (" + inClause + ")";
        if (!TextUtils.isEmpty(callerSelection)) {
            finalSelection += " AND (" + callerSelection + ")";
        }

        String[] finalSelectionArgs;
        if (callerSelectionArgs != null) {
            finalSelectionArgs = new String[dataIds.length + callerSelectionArgs.length];
            System.arraycopy(dataIds, 0, finalSelectionArgs, 0, dataIds.length);
            System.arraycopy(callerSelectionArgs, 0, finalSelectionArgs, dataIds.length,
                    callerSelectionArgs.length);
        } else {
            finalSelectionArgs = dataIds;
        }
        return new DataQuery(finalSelection, finalSelectionArgs);
    }

    // TODO(b/456723413): Finalize validation logic.
    private void validateContentValues(ContentValues values) {
        if (values == null) {
            throw new IllegalArgumentException("Insert operation failed: ContentValues is null.");
        }

        String dataRowIds = values.getAsString(SessionColumns.DATA_ROW_IDS);
        if (TextUtils.isEmpty(dataRowIds)) {
            throw new IllegalArgumentException(
                    "Insert operation failed: DATA_ROW_IDS is missing or empty.");
        }

        // Validate that dataRowIds are comma-separated long integers
        String[] ids = dataRowIds.split(",");
        for (String id : ids) {
            try {
                Long.parseLong(id.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid dataRowIds format: " + dataRowIds
                        + ". Must be comma-separated longs.", e);
            }
        }

        if (!values.containsKey(SessionColumns.CALLER_UID)) {
            throw new IllegalArgumentException("Insert operation failed: CALLER_UID is missing.");
        }

        Integer callerUid = values.getAsInteger(SessionColumns.CALLER_UID);
        if (callerUid == null) {
            throw new IllegalArgumentException(
                    "Insert operation failed: CALLER_UID cannot be null.");
        }
    }

    /**
     * Deletes sessions that are older than 24 hours from the database.
     */
    private void cleanupStaleSessions() {
        long expirationTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(
                ContactsPickerWorkScheduler.CLEANUP_INTERVAL_DAYS);
        String selection = SessionColumns.CREATED_AT + " <= ?";
        String[] selectionArgs = {String.valueOf(expirationTimestamp)};
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        int rowsDeleted = db.delete(Tables.SESSIONS, selection, selectionArgs);
        Log.i(TAG, "Cleaned up " + rowsDeleted + " stale session(s).");
    }
}
