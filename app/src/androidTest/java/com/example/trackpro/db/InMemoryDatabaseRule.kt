package com.example.trackpro.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackpro.managerClasses.ESPDatabase
import org.junit.rules.ExternalResource

/**
 * A fresh, empty, in-memory [ESPDatabase] for every test.
 *
 * Built directly rather than through [ESPDatabase.getInstance] so the tests never touch the
 * app's real database file, and in memory so nothing survives from one test to the next.
 * The migrations are deliberately not registered here: an in-memory database is always
 * created at the current version, and the migration path has its own file-backed tests in
 * [EspDatabaseMigrationTest].
 */
class InMemoryDatabaseRule : ExternalResource() {

    lateinit var db: ESPDatabase
        private set

    override fun before() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ESPDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    override fun after() {
        db.close()
    }
}
