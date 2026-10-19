package com.outsmartis.yoke.helper

import android.content.Intent

/** Marks a launch as one of Yoke's own helper jumps: coming back keeps the screen instead of landing on home. */
fun Intent.keepScreen(): Intent = putExtra(HomeReturn.EXTRA_HOLD, true)
