package com.example.rehabilitationapp.data;

import android.content.Context;

public class DatabaseProvider {

    private static AppDatabase currentDb = null;
    private static String currentUserId = null;

    public static synchronized AppDatabase getDatabase(Context context, String userId) {

        if (currentDb == null || !userId.equals(currentUserId)) {

            if (currentDb != null) currentDb.close();

            currentUserId = userId;

            String dbName = "rehab_db_" + userId + ".db";

            currentDb = AppDatabase.buildDatabase(context, dbName);
        }

        return currentDb;
    }

    public static synchronized void close() {
        if (currentDb != null) currentDb.close();
        currentDb = null;
        currentUserId = null;
    }
}
