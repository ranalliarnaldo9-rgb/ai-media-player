package com.example.util

object NetworkUtils {
    /**
     * Resolves a relative or absolute URL against a base URL, handling protocols, scheme-relative, and relative paths.
     */
    fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            relativeOrAbsolute
        } else if (relativeOrAbsolute.startsWith("//")) {
            "https:$relativeOrAbsolute"
        } else {
            val baseWithoutQuery = baseUrl.substringBefore("?")
            val lastSlash = baseWithoutQuery.lastIndexOf('/')
            if (lastSlash != -1) {
                baseWithoutQuery.substring(0, lastSlash + 1) + relativeOrAbsolute
            } else {
                relativeOrAbsolute
            }
        }
    }
}
