package com.blocksite.data

import android.content.Context
import java.security.MessageDigest

class PinRepository(context: Context) {

    private val prefs = context.getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    fun setPin(pin: String) {
        prefs.edit().putString("pin_hash", hash(pin)).apply()
    }

    fun checkPin(pin: String): Boolean = hash(pin) == prefs.getString("pin_hash", null)

    fun setDelaySeconds(seconds: Int) {
        prefs.edit().putInt("delay_seconds", seconds).apply()
    }

    fun getDelaySeconds(): Int = prefs.getInt("delay_seconds", 300) // 5 минут по умолчанию

    // Записывается в момент правильного ввода PIN — до этого момента вход закрыт
    fun startDelay() {
        val until = System.currentTimeMillis() + getDelaySeconds() * 1000L
        prefs.edit().putLong("delay_until", until).apply()
    }

    fun getRemainingDelayMs(): Long {
        val until = prefs.getLong("delay_until", 0L)
        return maxOf(0L, until - System.currentTimeMillis())
    }

    fun isDelayActive(): Boolean = getRemainingDelayMs() > 0

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
