package com.outsmartis.yoke.gestures

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

class GestureInitOrderTest {

    /** Touching an action object before its parent class must not leave a null in [GestureAction.simple]. */
    @Test
    fun simpleHasNoNullWhenASubclassLoadsFirst() {
        val urls = System.getProperty("java.class.path").split(File.pathSeparator)
            .map { File(it).toURI().toURL() }.toTypedArray()
        URLClassLoader(urls, null).use { cl ->
            Class.forName("com.outsmartis.yoke.gestures.GestureAction\$None", true, cl)
            val companion = Class.forName("com.outsmartis.yoke.gestures.GestureAction", true, cl)
                .getField("Companion").get(null)
            val simple = companion.javaClass.getMethod("getSimple").invoke(companion) as List<*>
            assertFalse(simple.contains(null))
        }
    }
}
