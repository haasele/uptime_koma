package dev.haasele.koma.shared.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Small JSONPath subset covering the expressions monitors realistically need:
 * `$`, `.field`, `['field']`, `[0]` and `[*]`.
 */
object JsonQuery {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun evaluate(payload: String, path: String): List<String> {
        val root = json.parseToJsonElement(payload)
        val tokens = tokenize(path)
        var current = listOf(root)
        for (token in tokens) {
            current = current.flatMap { element -> resolve(element, token) }
            if (current.isEmpty()) return emptyList()
        }
        return current.map { it.asPlainString() }
    }

    private fun tokenize(path: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        val trimmed = path.trim().removePrefix("$")
        while (index < trimmed.length) {
            when (trimmed[index]) {
                '.' -> {
                    index++
                    val start = index
                    while (index < trimmed.length && trimmed[index] != '.' && trimmed[index] != '[') index++
                    val name = trimmed.substring(start, index)
                    if (name.isNotEmpty()) tokens += Token.Field(name)
                }
                '[' -> {
                    val close = trimmed.indexOf(']', index)
                    require(close > index) { "Unclosed bracket in path" }
                    val inner = trimmed.substring(index + 1, close).trim()
                    tokens += when {
                        inner == "*" -> Token.Wildcard
                        inner.startsWith("'") || inner.startsWith("\"") -> Token.Field(inner.trim('\'', '"'))
                        else -> Token.Index(inner.toIntOrNull() ?: error("Invalid array index: $inner"))
                    }
                    index = close + 1
                }
                else -> {
                    val start = index
                    while (index < trimmed.length && trimmed[index] != '.' && trimmed[index] != '[') index++
                    tokens += Token.Field(trimmed.substring(start, index))
                }
            }
        }
        return tokens
    }

    private fun resolve(element: JsonElement, token: Token): List<JsonElement> = when (token) {
        is Token.Field -> when (element) {
            is JsonObject -> listOfNotNull(element[token.name])
            is JsonArray -> element.mapNotNull { (it as? JsonObject)?.get(token.name) }
            else -> emptyList()
        }
        is Token.Index -> (element as? JsonArray)?.getOrNull(token.index)?.let { listOf(it) } ?: emptyList()
        Token.Wildcard -> when (element) {
            is JsonArray -> element.toList()
            is JsonObject -> element.values.toList()
            else -> emptyList()
        }
    }

    private fun JsonElement.asPlainString(): String = when (this) {
        is JsonPrimitive -> if (this is JsonNull) "null" else content
        else -> toString()
    }

    private sealed interface Token {
        data class Field(val name: String) : Token
        data class Index(val index: Int) : Token
        data object Wildcard : Token
    }
}
