package dev.haasele.koma.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConditionOperator(val id: String, val label: String) {
    @SerialName("equals") EQUALS("equals", "equals"),
    @SerialName("not_equals") NOT_EQUALS("not_equals", "not equals"),
    @SerialName("contains") CONTAINS("contains", "contains"),
    @SerialName("not_contains") NOT_CONTAINS("not_contains", "not contains"),
    @SerialName("starts_with") STARTS_WITH("starts_with", "starts with"),
    @SerialName("ends_with") ENDS_WITH("ends_with", "ends with"),
    @SerialName("lt") LESS_THAN("lt", "<"),
    @SerialName("lte") LESS_OR_EQUAL("lte", "<="),
    @SerialName("gt") GREATER_THAN("gt", ">"),
    @SerialName("gte") GREATER_OR_EQUAL("gte", ">="),
    @SerialName("record_contains") RECORD_CONTAINS("record_contains", "record contains");

    companion object {
        fun fromId(id: String): ConditionOperator = entries.firstOrNull { it.id == id } ?: EQUALS
    }
}

@Serializable
enum class ConditionJoin { AND, OR }

@Serializable
data class Condition(
    val variable: String,
    val operator: ConditionOperator = ConditionOperator.EQUALS,
    val value: String = "",
    val join: ConditionJoin = ConditionJoin.AND,
)

@Serializable
data class ConditionGroup(
    val conditions: List<Condition> = emptyList(),
    val join: ConditionJoin = ConditionJoin.AND,
)

/**
 * Evaluates the condition tree against the variables a check produced.
 * An empty tree always passes so monitors without conditions behave as before.
 */
object ConditionEvaluator {
    fun evaluate(groups: List<ConditionGroup>, variables: Map<String, List<String>>): Boolean {
        if (groups.isEmpty()) return true
        var result = evaluateGroup(groups.first(), variables)
        for (group in groups.drop(1)) {
            val groupResult = evaluateGroup(group, variables)
            result = if (group.join == ConditionJoin.OR) result || groupResult else result && groupResult
        }
        return result
    }

    private fun evaluateGroup(group: ConditionGroup, variables: Map<String, List<String>>): Boolean {
        if (group.conditions.isEmpty()) return true
        var result = evaluateCondition(group.conditions.first(), variables)
        for (condition in group.conditions.drop(1)) {
            val next = evaluateCondition(condition, variables)
            result = if (condition.join == ConditionJoin.OR) result || next else result && next
        }
        return result
    }

    private fun evaluateCondition(condition: Condition, variables: Map<String, List<String>>): Boolean {
        val values = variables[condition.variable] ?: return false
        if (condition.operator == ConditionOperator.RECORD_CONTAINS) {
            return values.any { it.contains(condition.value, ignoreCase = true) }
        }
        return values.any { compare(it, condition.operator, condition.value) }
    }

    private fun compare(actual: String, operator: ConditionOperator, expected: String): Boolean = when (operator) {
        ConditionOperator.EQUALS -> actual == expected
        ConditionOperator.NOT_EQUALS -> actual != expected
        ConditionOperator.CONTAINS -> actual.contains(expected, ignoreCase = true)
        ConditionOperator.NOT_CONTAINS -> !actual.contains(expected, ignoreCase = true)
        ConditionOperator.STARTS_WITH -> actual.startsWith(expected, ignoreCase = true)
        ConditionOperator.ENDS_WITH -> actual.endsWith(expected, ignoreCase = true)
        ConditionOperator.RECORD_CONTAINS -> actual.contains(expected, ignoreCase = true)
        ConditionOperator.LESS_THAN -> numericCompare(actual, expected) { a, b -> a < b }
        ConditionOperator.LESS_OR_EQUAL -> numericCompare(actual, expected) { a, b -> a <= b }
        ConditionOperator.GREATER_THAN -> numericCompare(actual, expected) { a, b -> a > b }
        ConditionOperator.GREATER_OR_EQUAL -> numericCompare(actual, expected) { a, b -> a >= b }
    }

    private inline fun numericCompare(actual: String, expected: String, compare: (Double, Double) -> Boolean): Boolean {
        val left = actual.trim().toDoubleOrNull() ?: return false
        val right = expected.trim().toDoubleOrNull() ?: return false
        return compare(left, right)
    }
}
