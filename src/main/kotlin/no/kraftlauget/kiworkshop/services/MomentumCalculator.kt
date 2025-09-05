package no.kraftlauget.kiworkshop.services

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Simple momentum calculator using percentage change formula.
 * Formula: (current - old) / old
 */
object MomentumCalculator {
    
    const val MOMENTUM_THRESHOLD = 0.01 // 1% threshold for significant momentum
    
    /**
     * Calculates momentum as percentage change between current and old price.
     * 
     * @param currentPrice The current/latest price
     * @param oldPrice The older price to compare against
     * @return Momentum as decimal percentage (e.g., 0.1 = 10% increase)
     * @throws IllegalArgumentException if oldPrice <= 0 (indicates corrupted data)
     */
    fun calculateMomentum(currentPrice: Double, oldPrice: Double): Double {
        require(currentPrice >= 0) { "Current price cannot be negative: $currentPrice" }
        require(oldPrice > 0) { 
            "Old price must be positive (got $oldPrice). Zero or negative prices indicate data corruption." 
        }
        
        val momentum = (currentPrice - oldPrice) / oldPrice
        
        logger.debug { "Calculated momentum: current=$currentPrice, old=$oldPrice, momentum=$momentum" }
        
        return momentum
    }
}