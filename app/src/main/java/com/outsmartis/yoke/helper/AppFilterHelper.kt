package com.outsmartis.yoke.helper

import com.outsmartis.yoke.data.AppModel

interface AppFilterHelper {
    fun onAppFiltered(items:List<AppModel>)
}