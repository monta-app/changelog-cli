package com.monta.changelog.model

import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.json
import kotlinx.serialization.Serializable

/**
 * A single system/component that was deployed in this release, as produced by
 * the deploy pipeline's rollout wait (e.g. the argocd-wait-sync-multi action)
 * and ingested from a JSON array via [parseAll].
 *
 * Only [name] is required; every other field is optional so partial data still
 * renders.
 */
@Serializable
data class DeployedSystem(
    /** Display name of the system/component, e.g. `"hub"`. */
    val name: String,
    /** Deployed git commit SHA (full or short), shown for reference, e.g. `"80aad1c"`. */
    val revision: String? = null,
    /**
     * When the rollout started, as an **ISO 8601 UTC** timestamp
     * (e.g. `"2026-08-26T08:19:18Z"`). Non-ISO values are rendered verbatim.
     */
    val start: String? = null,
    /**
     * When the system became healthy, as an **ISO 8601 UTC** timestamp
     * (e.g. `"2026-08-26T08:30:59Z"`). Non-ISO values are rendered verbatim.
     */
    val end: String? = null,
    /** Rollout health, e.g. `"healthy"` (a fail-fast wait only reports healthy). */
    val status: String? = null,
    /** Link to the system, e.g. its ArgoCD application URL. */
    val url: String? = null,
) {
    companion object {
        /**
         * Parses the deployments JSON array. Unknown fields are ignored and
         * malformed input yields an empty list rather than failing the run — a
         * changelog should never fail because deployment metadata was off.
         */
        fun parseAll(raw: String?): List<DeployedSystem> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }
            return try {
                json.decodeFromString<List<DeployedSystem>>(raw)
            } catch (exception: Exception) {
                DebugLogger.warn("Failed to parse deployments JSON, ignoring it: ${exception.message}")
                emptyList()
            }
        }
    }
}

enum class DeployOutcome {
    HEALTHY,
    PARTIAL,
    FAILED,
}

private fun DeployedSystem.isHealthy(): Boolean = status == null || status.equals("healthy", ignoreCase = true)

fun List<DeployedSystem>.outcome(): DeployOutcome = when {
    isEmpty() || all { it.isHealthy() } -> DeployOutcome.HEALTHY
    none { it.isHealthy() } -> DeployOutcome.FAILED
    else -> DeployOutcome.PARTIAL
}
