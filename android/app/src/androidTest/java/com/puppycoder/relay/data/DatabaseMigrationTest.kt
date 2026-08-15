package com.puppycoder.relay.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PuppyCoderDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3AddsDurableMessageImages() {
        helper.createDatabase(DATABASE_NAME, 2).close()
        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            PuppyCoderDatabase.MIGRATION_2_3,
        ).close()
    }

    private companion object {
        const val DATABASE_NAME = "migration-image-attachments"
    }
}
