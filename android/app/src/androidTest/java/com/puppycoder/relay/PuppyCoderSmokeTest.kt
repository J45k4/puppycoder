package com.puppycoder.relay

import android.view.ViewGroup
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuppyCoderSmokeTest {
    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun chatFirstShellIsAttached() {
        activity.scenario.onActivity { current ->
            val content = current.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(content.childCount > 0)
            assertEquals("PuppyCoder", current.applicationInfo.loadLabel(current.packageManager).toString())
        }
    }
}
