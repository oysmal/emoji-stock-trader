package no.kraftlauget.kiworkshop.models

/**
 * Represents a single price point with timestamp.
 * Uses milliseconds since epoch for timestamp to match System.currentTimeMillis().
 */
data class PricePoint(
    val timestamp: Long,
    val midPrice: Double
)