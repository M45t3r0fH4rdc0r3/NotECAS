package de.mohcomputer.notecas.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.sign

data class VirtualComponent(val sourceId: Long, val factor: Double)

enum class VirtualIssueCode {
    MALFORMED_DEFINITION,
    MISSING_SOURCE,
    ZERO_FACTOR,
    CYCLE,
}

data class VirtualDefinitionIssue(
    val meterId: Long,
    val code: VirtualIssueCode,
    val detail: String,
)

data class ParsedVirtualDefinition(
    val components: List<VirtualComponent>,
    val issues: List<VirtualDefinitionIssue>,
)

object VirtualMeterDefinitions {
    fun parse(
        meterId: Long,
        definition: String,
        availableMeters: Map<Long, Meter>,
    ): ParsedVirtualDefinition {
        if (definition.isBlank()) return ParsedVirtualDefinition(emptyList(), emptyList())
        val tokens = definition.split('|')
        val components = mutableListOf<VirtualComponent>()
        val issues = mutableListOf<VirtualDefinitionIssue>()
        var index = 0
        while (index < tokens.size) {
            if (index + 1 >= tokens.size) {
                issues += VirtualDefinitionIssue(meterId, VirtualIssueCode.MALFORMED_DEFINITION, "Missing factor for '${tokens[index]}'")
                break
            }
            val sourceId = tokens[index].trim().toLongOrNull()
            val factor = tokens[index + 1].trim().toDoubleOrNull()
            when {
                sourceId == null || factor == null || !factor.isFinite() ->
                    issues += VirtualDefinitionIssue(meterId, VirtualIssueCode.MALFORMED_DEFINITION, "Invalid pair '${tokens[index]}|${tokens[index + 1]}'")
                factor == 0.0 ->
                    issues += VirtualDefinitionIssue(meterId, VirtualIssueCode.ZERO_FACTOR, "Source $sourceId has a zero factor")
                sourceId !in availableMeters ->
                    issues += VirtualDefinitionIssue(meterId, VirtualIssueCode.MISSING_SOURCE, "Source $sourceId does not exist")
                else -> components += VirtualComponent(sourceId, factor)
            }
            index += 2
        }
        return ParsedVirtualDefinition(components, issues)
    }

    fun cyclicMeterIds(
        definitions: Map<Long, List<VirtualComponent>>,
        meters: Map<Long, Meter>,
    ): Set<Long> {
        val state = mutableMapOf<Long, Int>()
        val stack = mutableListOf<Long>()
        val cyclic = mutableSetOf<Long>()

        fun visit(id: Long) {
            state[id] = 1
            stack += id
            for (next in definitions[id].orEmpty().map { it.sourceId }) {
                if (meters[next]?.type != MeterType.VIRTUAL || next !in definitions) continue
                when (state[next] ?: 0) {
                    0 -> visit(next)
                    1 -> {
                        val cycleStart = stack.indexOf(next)
                        if (cycleStart >= 0) cyclic += stack.subList(cycleStart, stack.size)
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            state[id] = 2
        }

        definitions.keys.forEach { if ((state[it] ?: 0) == 0) visit(it) }
        return cyclic
    }
}

data class SourceInterval(val delta: Double, val costPerUnit: Double)

data class VirtualIntervalResult(val delta: Double, val variableCost: Double)

object VirtualMeterCalculations {
    fun combine(
        components: List<VirtualComponent>,
        sources: Map<Long, SourceInterval>,
    ): VirtualIntervalResult {
        var value = 0.0
        var cost = 0.0
        for (component in components) {
            val source = requireNotNull(sources[component.sourceId]) {
                "Missing interval for source ${component.sourceId}"
            }
            value += source.delta * component.factor
            cost += source.delta * source.costPerUnit * component.factor.sign
        }
        return VirtualIntervalResult(value, cost)
    }

    fun between(
        components: List<VirtualComponent>,
        meters: Map<Long, Meter>,
        readingsByMeter: Map<Long, List<Reading>>,
        from: LocalDateTime,
        until: LocalDateTime,
    ): VirtualIntervalResult {
        require(from < until) { "Virtual interval must be positive" }
        val intervals = components.associate { component ->
            val meter = requireNotNull(meters[component.sourceId]) { "Missing meter ${component.sourceId}" }
            val readings = requireNotNull(readingsByMeter[component.sourceId]) {
                "Missing readings for source ${component.sourceId}"
            }
            component.sourceId to SourceInterval(
                delta = deltaBetween(meter, readings, from, until),
                costPerUnit = meter.costPerUnit,
            )
        }
        return combine(components, intervals)
    }

    private fun deltaBetween(
        meter: Meter,
        readings: List<Reading>,
        from: LocalDateTime,
        until: LocalDateTime,
    ): Double {
        val ordered = readings.sortedBy { it.timestamp }
        require(ordered.size >= 2 && from >= ordered.first().timestamp && until <= ordered.last().timestamp) {
            "Requested interval is outside source ${meter.id} coverage"
        }
        require(ordered.all { it.meterId == meter.id }) { "Readings do not belong to source ${meter.id}" }
        var result = 0.0
        for ((older, newer) in ordered.zipWithNext()) {
            require(older.timestamp < newer.timestamp) { "Reading timestamps must be unique" }
            val overlapStart = maxOf(from, older.timestamp)
            val overlapEnd = minOf(until, newer.timestamp)
            if (overlapStart < overlapEnd) {
                val complete = Duration.between(older.timestamp, newer.timestamp).toNanos().toDouble()
                val overlap = Duration.between(overlapStart, overlapEnd).toNanos().toDouble()
                result += MeterCalculations.delta(meter, older, newer) * overlap / complete
            }
        }
        return result
    }
}
