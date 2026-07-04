package ua.ukrtv.app.util

import android.util.Base64
import java.util.regex.Pattern

/**
 * Decoder for obfuscated strings used by PlayerJS and similar web players.
 * Ported from various open-source JS/TS decoders (like Rezka/Lumen).
 */
object PlayerJsDecoder {

    private const val TRASH_SEPARATOR = "//_//"
    private val TRASH_SYMBOLS = listOf("@", "#", "!", "^", "$")
    private val CLEAR_BEFORE_SYMBOLS = listOf("/", "=")

    private val trashPattern: Pattern by lazy {
        val symbols = TRASH_SYMBOLS
        val combinations = mutableListOf<String>()
        
        // Generate combinations of 2 and 3 symbols
        for (s1 in symbols) {
            for (s2 in symbols) {
                combinations.add(s1 + s2)
                for (s3 in symbols) {
                    combinations.add(s1 + s2 + s3)
                }
            }
        }
        
        val encoded = combinations.map { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
        Pattern.compile(encoded.joinToString("|") { Pattern.quote(it) })
    }

    private val cache = mutableMapOf<String, String>()

    /**
     * Decodes an encrypted string (usually starting with #h or similar)
     */
    fun decode(encrypted: String): String {
        if (!encrypted.startsWith("#")) return encrypted
        
        // Skip prefix (usually 2 chars: #h, #f, etc)
        val input = encrypted.substring(2)
        val cleaned = decryptRecursive(input)
        
        return try {
            String(Base64.decode(cleaned, Base64.DEFAULT))
        } catch (e: Exception) {
            cleaned 
        }
    }

    private fun decryptRecursive(input: String): String {
        if (cache.containsKey(input)) return cache[input]!!

        val indexes = mutableListOf<Int>()
        var idx = input.indexOf(TRASH_SEPARATOR)
        while (idx != -1) {
            indexes.add(idx)
            idx = input.indexOf(TRASH_SEPARATOR, idx + 1)
        }

        if (indexes.isEmpty()) {
            cache[input] = input
            return input
        }

        var result = input
        for (index in indexes) {
            val afterSeparator = input.substring(index + TRASH_SEPARATOR.length)
            val (before, after) = divideAtFirstOccurrence(afterSeparator, CLEAR_BEFORE_SYMBOLS)
            
            val trashMatcher = trashPattern.matcher(before)
            val cleanedBefore = trashMatcher.replaceAll("")
            
            val candidate = decryptRecursive(input.substring(0, index) + cleanedBefore + after)
            if (candidate.length < result.length) {
                result = candidate
            }
        }

        cache[input] = result
        return result
    }

    private fun divideAtFirstOccurrence(input: String, symbols: List<String>): Pair<String, String> {
        var firstIdx = -1
        for (symbol in symbols) {
            val idx = input.indexOf(symbol)
            if (idx != -1 && (firstIdx == -1 || idx < firstIdx)) {
                firstIdx = idx
            }
        }
        
        return if (firstIdx != -1) {
            input.substring(0, firstIdx + 1) to input.substring(firstIdx + 1)
        } else {
            input to ""
        }
    }
}
