package com.blocksite.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BlocklistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("blocklist", Context.MODE_PRIVATE)

    private val _domains = MutableStateFlow(loadDomains())
    val domains: StateFlow<Set<String>> = _domains

    private fun loadDomains(): Set<String> =
        prefs.getStringSet("domains", emptySet()) ?: emptySet()

    fun addDomain(domain: String) {
        val normalized = domain.lowercase().trim().removePrefix("www.")
        val updated = _domains.value + normalized
        save(updated)
    }

    fun removeDomain(domain: String) {
        val updated = _domains.value - domain
        save(updated)
    }

    fun isBlocked(domain: String): Boolean {
        val d = domain.lowercase().trimEnd('.')
        return _domains.value.any { blocked ->
            d == blocked || d.endsWith(".$blocked")
        }
    }

    private fun save(domains: Set<String>) {
        prefs.edit().putStringSet("domains", domains).apply()
        _domains.value = domains
    }
}
