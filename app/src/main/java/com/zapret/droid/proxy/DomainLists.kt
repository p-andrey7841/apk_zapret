package com.zapret.droid.proxy

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object DomainLists {

    private var generalList: Set<String> = emptySet()
    private var googleList: Set<String> = emptySet()
    private var excludeList: Set<String> = emptySet()

    fun load(context: Context) {
        generalList = loadAsset(context, "lists/list-general.txt")
        googleList = loadAsset(context, "lists/list-google.txt")
        excludeList = loadAsset(context, "lists/list-exclude.txt")
    }

    private fun loadAsset(context: Context, path: String): Set<String> {
        return try {
            val set = mutableSetOf<String>()
            context.assets.open(path).use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        set.add(trimmed.lowercase())
                    }
                }
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun isInGeneralList(host: String): Boolean {
        val lower = host.lowercase()
        return lower in generalList || generalList.any { lower.endsWith(".$it") }
    }

    fun isInGoogleList(host: String): Boolean {
        val lower = host.lowercase()
        return lower in googleList || googleList.any { lower.endsWith(".$it") }
    }

    fun isExcluded(host: String): Boolean {
        val lower = host.lowercase()
        return lower in excludeList || excludeList.any { lower.endsWith(".$it") }
    }

    fun shouldBypass(host: String): Boolean {
        if (isExcluded(host)) return false
        return isInGeneralList(host) || isInGoogleList(host)
    }
}
