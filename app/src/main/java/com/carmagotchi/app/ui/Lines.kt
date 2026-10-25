package com.carmagotchi.app.ui

import android.content.Context
import com.carmagotchi.app.domain.Dialogue
import com.carmagotchi.app.domain.Line

/** assets/dialogue.tsv의 시스템 문장을 ID로 읽는다(UI가 글을 코드에 박지 않게). 못 찾으면 fallback. */
object Lines {
    @Volatile private var all: List<Line>? = null
    fun text(ctx: Context, id: String, fallback: String): String {
        val l = all ?: runCatching { ctx.applicationContext.assets.open("dialogue.tsv").use(Dialogue::parse) }.getOrDefault(emptyList()).also { all = it }
        return l.firstOrNull { it.id == id }?.text ?: fallback
    }
}
