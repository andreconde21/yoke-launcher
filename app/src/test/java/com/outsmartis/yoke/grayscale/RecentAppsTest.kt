package com.outsmartis.yoke.grayscale

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {
    @Test fun newestGoesFirst() = assertEquals(listOf("b", "a"), GrayscalePolicy.pushRecent(listOf("a"), "b"))
    @Test fun repeatMovesToFrontWithoutDuplicates() = assertEquals(listOf("a", "b"), GrayscalePolicy.pushRecent(listOf("b", "a"), "a"))
    @Test fun keepsAtMostMax() = assertEquals(listOf("z", "a", "b"), GrayscalePolicy.pushRecent(listOf("a", "b", "c"), "z", max = 3))
}
