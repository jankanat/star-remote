package kz.alnazar.starremote.control

import kz.alnazar.starremote.data.AppSettings
import java.time.LocalTime

/** Часы, когда Mac не спит (настраиваются на Mac скриптом mac/schedule.sh). */
object Schedule {
    fun parse(s: String): LocalTime? = runCatching {
        val t = s.trim()
        LocalTime.parse(if (t.length == 4) "0$t" else t)
    }.getOrNull()

    fun inWindow(from: LocalTime, to: LocalTime, now: LocalTime = LocalTime.now()): Boolean =
        if (!to.isAfter(from)) !now.isBefore(from) || now.isBefore(to)
        else !now.isBefore(from) && now.isBefore(to)

    /** true, если сейчас Mac по расписанию должен работать (или расписание не задано). */
    fun shouldBeAwake(s: AppSettings): Boolean {
        val f = parse(s.awakeFrom) ?: return true
        val t = parse(s.awakeTo) ?: return true
        return inWindow(f, t)
    }

    /** Текст для случая, когда Mac не ответил. */
    fun asleepText(s: AppSettings): String {
        val f = parse(s.awakeFrom); val t = parse(s.awakeTo)
        if (f == null || t == null) return "Mac спит"
        return if (inWindow(f, t)) "Mac не отвечает, хотя должен работать до ${s.awakeTo.trim()}"
        else "Mac спит, доступен с ${s.awakeFrom.trim()}"
    }
}
