package com.iyr.db.database.internal

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Generador de push-id compatible con Firebase: 20 chars, lexicográficamente
 * ordenable por tiempo. Primeros 8 chars = timestamp (48 bits) en base64 push,
 * últimos 12 = aleatorio. Replicar este algoritmo es lo que preserva el orden
 * por clave que asume la app al usar push() + orderByKey().
 */
internal object PushId {
    private const val PUSH_CHARS = "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz"

    private var lastPushTime = 0L
    private val lastRandChars = IntArray(12)

    fun generate(now: Long = Clock.System.now().toEpochMilliseconds()): String {
        val duplicateTime = now == lastPushTime
        lastPushTime = now

        val timeStampChars = CharArray(8)
        var t = now
        for (i in 7 downTo 0) {
            timeStampChars[i] = PUSH_CHARS[(t % 64).toInt()]
            t /= 64
        }
        val builder = StringBuilder(20)
        builder.append(timeStampChars)

        if (!duplicateTime) {
            for (i in 0 until 12) lastRandChars[i] = Random.nextInt(64)
        } else {
            var i = 11
            while (i >= 0 && lastRandChars[i] == 63) {
                lastRandChars[i] = 0
                i--
            }
            if (i >= 0) lastRandChars[i]++
        }
        for (i in 0 until 12) builder.append(PUSH_CHARS[lastRandChars[i]])
        return builder.toString()
    }
}
