package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.SchedulePair
import com.example.ui.components.PairCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun pairCard_screenshot() {
    val samplePair = SchedulePair(
      id = 1,
      weekNumber = 0,
      dayIndex = 0,
      dayName = "Понеділок",
      dateStr = "31.08",
      pairNumber = 1,
      timeRange = "08:30-09:50",
      subject = "Комп'ютерна графіка",
      kind = "Лекція",
      room = "233",
      roomUrl = "",
      teacher = "Лобанчикова Надія Миколаївна",
      teacherUrl = "",
      subgroup = "підгр. 1"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        PairCard(pair = samplePair, isToday = false)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

