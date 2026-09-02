package com.puppycoder.relay.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun migrate3To4MovesTunnelCredentialsIntoHops() {
        helper.createDatabase(DATABASE_NAME_V4, 3).use { database ->
            database.execSQL(
                "INSERT INTO tunnel_profiles (id, name, host, port, username, encryptedPassword, " +
                    "encryptedPrivateKey, encryptedPrivateKeyPassphrase, hostKeyFingerprint, priority, enabled) " +
                    "VALUES ('tunnel-1', 'Workstation', 'gateway.example', 22, 'puppy', 'enc-pass', " +
                    "'enc-key', 'enc-passphrase', 'SHA256:abc', 120, 1)",
            )
        }
        helper.runMigrationsAndValidate(
            DATABASE_NAME_V4,
            4,
            true,
            PuppyCoderDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query("SELECT host, port, username, encryptedPassword, hopIndex FROM tunnel_hops").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("gateway.example", cursor.getString(0))
                assertEquals(22, cursor.getInt(1))
                assertEquals("puppy", cursor.getString(2))
                assertEquals("enc-pass", cursor.getString(3))
                assertEquals(0, cursor.getInt(4))
                assertFalse(cursor.moveToNext())
            }
            database.query("SELECT name, priority, enabled FROM tunnel_profiles").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Workstation", cursor.getString(0))
                assertEquals(120, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
            }
        }
    }

    @Test
    fun migrate4To5AddsIdentitiesAndHopIdentityReferences() {
        helper.createDatabase(DATABASE_NAME_V5, 4).use { database ->
            database.execSQL(
                "INSERT INTO tunnel_profiles (id, name, priority, enabled) VALUES ('tunnel-1', 'Workstation', 120, 1)",
            )
            database.execSQL(
                "INSERT INTO tunnel_hops (id, profileId, hopIndex, host, port, username, encryptedPassword, " +
                    "encryptedPrivateKey, encryptedPrivateKeyPassphrase, hostKeyFingerprint) " +
                    "VALUES ('tunnel-1-hop0', 'tunnel-1', 0, 'gateway.example', 22, 'puppy', 'enc-pass', " +
                    "'enc-key', 'enc-passphrase', 'SHA256:abc')",
            )
        }
        helper.runMigrationsAndValidate(
            DATABASE_NAME_V5,
            5,
            true,
            PuppyCoderDatabase.MIGRATION_4_5,
        ).use { database ->
            database.query("SELECT identityId FROM tunnel_hops WHERE id = 'tunnel-1-hop0'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM tunnel_identities").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate5To6CreatesReusableSshConnectionsForExistingHops() {
        helper.createDatabase(DATABASE_NAME_V6, 5).use { database ->
            database.execSQL("INSERT INTO tunnel_profiles (id, name, priority, enabled) VALUES ('tunnel-1', 'Workstation', 120, 1)")
            database.execSQL(
                "INSERT INTO tunnel_hops (id, profileId, hopIndex, host, port, username, encryptedPassword, " +
                    "encryptedPrivateKey, encryptedPrivateKeyPassphrase, hostKeyFingerprint, identityId) " +
                    "VALUES ('tunnel-1-hop0', 'tunnel-1', 0, 'gateway.example', 22, 'puppy', 'enc-pass', " +
                    "'enc-key', 'enc-passphrase', 'SHA256:abc', NULL)",
            )
        }
        helper.runMigrationsAndValidate(
            DATABASE_NAME_V6,
            6,
            true,
            PuppyCoderDatabase.MIGRATION_5_6,
        ).use { database ->
            database.query("SELECT name, host, port FROM ssh_connections").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Workstation · Hop 1", cursor.getString(0))
                assertEquals("gateway.example", cursor.getString(1))
                assertEquals(22, cursor.getInt(2))
            }
            database.query("SELECT connectionId FROM tunnel_hops WHERE id = 'tunnel-1-hop0'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("tunnel-1-connection-0", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-image-attachments"
        const val DATABASE_NAME_V4 = "migration-tunnel-hops"
        const val DATABASE_NAME_V5 = "migration-tunnel-identities"
        const val DATABASE_NAME_V6 = "migration-ssh-connections"
    }
}
