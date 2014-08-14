package com.pauselabs.pause.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import com.pauselabs.pause.models.PauseBounceBackMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tyndallm on 6/30/14.
 */
public class SavedPauseDataSource {
    // Database fields
    private SQLiteDatabase database;
    private MySQLiteHelper dbHelper;
    private String[] allColumns = {
            MySQLiteHelper.COLUMN_ID, // 0
            MySQLiteHelper.COLUMN_MESSAGE, // 1
            MySQLiteHelper.COLUMN_CREATED_ON, // 2
            MySQLiteHelper.COLUMN_PATH_TO_IMAGE, // 3
            MySQLiteHelper.COLUMN_PATH_TO_ORIGINAL, // 4
            MySQLiteHelper.COLUMN_LOCATION, // 5
            MySQLiteHelper.COLUMN_DURATION}; // 6


    public SavedPauseDataSource(Context context) {
        dbHelper = new MySQLiteHelper(context);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public PauseBounceBackMessage createSavedPause(PauseBounceBackMessage pauseMessage) {

        ContentValues values = new ContentValues();
        values.put(MySQLiteHelper.COLUMN_MESSAGE, pauseMessage.getMessage());
        values.put(MySQLiteHelper.COLUMN_CREATED_ON, pauseMessage.getCreatedOn());
        values.put(MySQLiteHelper.COLUMN_PATH_TO_IMAGE, pauseMessage.getPathToImage());
        values.put(MySQLiteHelper.COLUMN_PATH_TO_ORIGINAL, pauseMessage.getPathToOriginal());
        values.put(MySQLiteHelper.COLUMN_LOCATION, pauseMessage.getLocation());
        values.put(MySQLiteHelper.COLUMN_DURATION, pauseMessage.getEndTime());

        long insertId = database.insert(MySQLiteHelper.TABLE_SAVED_PAUSES, null,values);

        Cursor cursor = database.query(MySQLiteHelper.TABLE_SAVED_PAUSES,
                allColumns, MySQLiteHelper.COLUMN_ID + " = " + insertId, null,
                null, null, null);
        cursor.moveToFirst();
        PauseBounceBackMessage savedPause = cursorToPause(cursor);
        cursor.close();
        return savedPause;
    }

    public PauseBounceBackMessage getSavedPauseById(long id) {
        Cursor cursor = database.query(MySQLiteHelper.TABLE_SAVED_PAUSES,
                allColumns, MySQLiteHelper.COLUMN_ID + " = " + id, null,
                null, null, null);
        cursor.moveToFirst();
        PauseBounceBackMessage savedPause = cursorToPause(cursor);
        return savedPause;
    }

    public void deleteComment(PauseBounceBackMessage savedPause) {
        long id = savedPause.getId();
        System.out.println("Comment deleted with id: " + id);
        database.delete(MySQLiteHelper.TABLE_SAVED_PAUSES, MySQLiteHelper.COLUMN_ID
                + " = " + id, null);
    }

    public void deleteAllSavedPauseMessages() {
        System.out.println("deleting all saved Pauses");
        database.delete(MySQLiteHelper.TABLE_SAVED_PAUSES, null, null);

    }

    public List<PauseBounceBackMessage> getAllSavedPauses() {
        List<PauseBounceBackMessage> savesPauseMessages = new ArrayList<PauseBounceBackMessage>();

        Cursor cursor = database.query(MySQLiteHelper.TABLE_SAVED_PAUSES,
                allColumns, null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            PauseBounceBackMessage savedPause = cursorToPause(cursor);
            savesPauseMessages.add(savedPause);
            cursor.moveToNext();
        }
        // make sure to close the cursor
        cursor.close();
        return savesPauseMessages;
    }

    private PauseBounceBackMessage cursorToPause(Cursor cursor) {
        String message = cursor.getString(1);
        // TODO Update this jankiness
        PauseBounceBackMessage savedPause = new PauseBounceBackMessage(message, message);
        savedPause.setId(cursor.getLong(0));
        savedPause.setCreatedOn(cursor.getLong(2));
        savedPause.setPathToImage(cursor.getString(3));
        savedPause.setPathToOriginal(cursor.getString(4));
        savedPause.setLocation(cursor.getString(5));
        savedPause.setEndTime(cursor.getLong(6));
        return savedPause;
    }

}
