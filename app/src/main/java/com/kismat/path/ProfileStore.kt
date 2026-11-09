package com.kismat.path

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class UserProfile(
    val name: String = "",
    val age: String = "",
    val heightCm: String = "",
    val weightKg: String = ""
)

private class ProfileDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "kismat_profile.db", null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE profile (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                name TEXT NOT NULL DEFAULT '',
                age TEXT NOT NULL DEFAULT '',
                height_cm TEXT NOT NULL DEFAULT '',
                weight_kg TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        private const val DATABASE_VERSION = 1
    }
}

internal object ProfileStore {
    fun displayName(context: Context): String = load(context).name.trim().take(32)

    fun load(context: Context): UserProfile {
        val helper = ProfileDatabase(context)
        return try {
            helper.readableDatabase.query(
                "profile", arrayOf("name", "age", "height_cm", "weight_kg"), "id = ?", arrayOf("1"), null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    UserProfile(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))
                } else UserProfile()
            }
        } finally {
            helper.close()
        }
    }

    fun save(context: Context, profile: UserProfile) {
        val helper = ProfileDatabase(context)
        try {
            val values = ContentValues().apply {
                put("id", 1)
                put("name", profile.name.trim())
                put("age", profile.age.trim())
                put("height_cm", profile.heightCm.trim())
                put("weight_kg", profile.weightKg.trim())
            }
            helper.writableDatabase.insertWithOnConflict("profile", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } finally {
            helper.close()
        }
    }
}
