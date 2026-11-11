package com.wangzan101.mika.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.settings.RecommendedTask
import com.wangzan101.mika.ui.theme.MikaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shortcutUsesSavedPromptAndHidesWhileBusy() {
        var selected = ""
        var mode by mutableStateOf(ComposerMode.Idle)
        compose.setContent {
            MikaTheme {
                Composer(
                    Modifier, "", {}, mode, hasHistory = false, onSend = {}, onStop = {},
                    shortcuts = listOf(RecommendedTask("1", "整理桌面", "请帮我整理桌面")),
                    onShortcut = { selected = it },
                )
            }
        }
        compose.onNodeWithTag("shortcut-row").assertIsDisplayed()
        compose.onNodeWithText("整理桌面").performClick()
        assertEquals("请帮我整理桌面", selected)
        compose.runOnUiThread { mode = ComposerMode.Busy }
        compose.onNodeWithTag("shortcut-row").assertDoesNotExist()
    }
}
