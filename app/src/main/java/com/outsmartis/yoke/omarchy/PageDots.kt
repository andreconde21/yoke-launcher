package com.outsmartis.yoke.omarchy

import com.outsmartis.yoke.gestures.GestureAction

/** Top or bottom of the screen, for the pieces that can sit at either. */
enum class Edge(val id: String) {
    TOP("top"), BOTTOM("bottom");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: TOP
    }
}

/** The launcher's "workspaces": home, the widget page and the Today sheet. */
enum class Page { HOME, WIDGETS, TODAY }

enum class DotStyle(val id: String) {
    NUMBERS("numbers"), DOTS("dots");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: NUMBERS
    }
}

/** Which pages get an indicator and how each one reads, like Omarchy's workspace indicator. Pure, so it is unit tested. */
object PageDots {

    /**
     * Home, plus the widget page when a gesture opens it and Today when a gesture opens it
     * or the agenda line (whose tap opens it) is on.
     */
    fun pages(actions: Collection<GestureAction>, agendaLine: Boolean): List<Page> = buildList {
        add(Page.HOME)
        if (GestureAction.WidgetPage in actions) add(Page.WIDGETS)
        if (GestureAction.CockpitToday in actions || agendaLine) add(Page.TODAY)
    }

    /** A single page needs no indicator. */
    fun visible(pages: List<Page>): Boolean = pages.size > 1

    /** Numbers count the shown pages from 1; dots are filled for the current page. */
    fun label(index: Int, current: Boolean, style: DotStyle): String = when (style) {
        DotStyle.NUMBERS -> "${index + 1}"
        DotStyle.DOTS -> if (current) "●" else "○"
    }
}
