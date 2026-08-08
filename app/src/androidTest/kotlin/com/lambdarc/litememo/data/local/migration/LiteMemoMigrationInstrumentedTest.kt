package com.lambdarc.litememo.data.local.migration

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.data.local.LiteMemoDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiteMemoMigrationInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiteMemoDatabase::class.java
    )

    @Test
    fun version1SchemaCanBeCreatedAndQueried() {
        // Arrange
        helper.createDatabase(TEST_DATABASE_NAME, 1).apply {
            insertMemo()
            insertTag()
            insertTagRef()
            close()
        }

        // Act
        val database = helper.validate()
        val row = database.readMemoWithTagRef()
        database.close()

        // Assert
        assertEquals(
            StoredMemoWithTagRef(
                title = "Title",
                body = "Body",
                createdAt = 1_000L,
                updatedAt = 2_000L,
                isFavorite = 1,
                deletedAt = null,
                tagId = "tag-1",
                position = 0
            ),
            row
        )
    }

    @Test
    fun errorVersion1SchemaRejectsDuplicateTagName() {
        // Arrange
        helper.createDatabase(TEST_DATABASE_NAME, 1).apply {
            insertTag(id = "tag-1", name = "Work")
            close()
        }
        val database = helper.validate()

        // Act
        // Error: the unique index blocks a second tag id claiming the same name.
        val failure = runCatching {
            database.insertTag(id = "tag-2", name = "Work", createdAt = 2_000L)
        }.exceptionOrNull()
        database.close()

        // Assert
        assertEquals(true, failure is SQLiteConstraintException)
    }

    private fun MigrationTestHelper.validate(): SupportSQLiteDatabase =
        runMigrationsAndValidate(TEST_DATABASE_NAME, 1, true, *LiteMemoMigrations.ALL)

    private fun SupportSQLiteDatabase.insertMemo() {
        execSQL(
            """
            INSERT INTO memos (id, title, body, createdAt, updatedAt, isFavorite, deletedAt)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("memo-1", "Title", "Body", 1_000L, 2_000L, 1, null)
        )
    }

    private fun SupportSQLiteDatabase.insertTag(
        id: String = "tag-1",
        name: String = "Work",
        createdAt: Long = 1_000L
    ) {
        execSQL(
            "INSERT INTO tags (id, name, colorArgb, createdAt) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(id, name, 0xFF6750A4, createdAt)
        )
    }

    private fun SupportSQLiteDatabase.insertTagRef() {
        execSQL(
            "INSERT INTO memo_tag_refs (memoId, tagId, position) VALUES (?, ?, ?)",
            arrayOf<Any>("memo-1", "tag-1", 0)
        )
    }

    private fun SupportSQLiteDatabase.readMemoWithTagRef(): StoredMemoWithTagRef = query(
        """
        SELECT memos.title, memos.body, memos.createdAt, memos.updatedAt,
            memos.isFavorite, memos.deletedAt,
            memo_tag_refs.tagId, memo_tag_refs.position
        FROM memos
        INNER JOIN memo_tag_refs ON memo_tag_refs.memoId = memos.id
        WHERE memos.id = ?
        """.trimIndent(),
        arrayOf("memo-1")
    ).use { cursor ->
        check(cursor.moveToFirst()) { "Expected memo row." }
        StoredMemoWithTagRef(
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("isFavorite")),
            deletedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("deletedAt"))) {
                null
            } else {
                cursor.getLong(cursor.getColumnIndexOrThrow("deletedAt"))
            },
            tagId = cursor.getString(cursor.getColumnIndexOrThrow("tagId")),
            position = cursor.getInt(cursor.getColumnIndexOrThrow("position"))
        )
    }

    private data class StoredMemoWithTagRef(
        val title: String,
        val body: String,
        val createdAt: Long,
        val updatedAt: Long,
        val isFavorite: Int,
        val deletedAt: Long?,
        val tagId: String,
        val position: Int
    )

    private companion object {
        const val TEST_DATABASE_NAME = "lite_memo_migration_test.db"
    }
}
