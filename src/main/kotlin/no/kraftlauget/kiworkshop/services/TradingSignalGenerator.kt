package no.kraftlauget.kiworkshop.services

import OrderSide
import io.github.oshai.kotlinlogging.KotlinLogging
import no.kraftlauget.kiworkshop.models.TradingSignal

private val logger = KotlinLogging.logger {}

/**
 * Generates trading signals based on momentum calculated from price history.
 * Uses index 10 (5 minutes ago at 30-second intervals) for momentum calculation.
 * Returns null if insufficient data or momentum below threshold.
 */
object TradingSignalGenerator {
    
    private const val PRICE_INDEX_5_MIN_AGO = 10 // Index 10 = 5 minutes ago (30s * 10 = 300s = 5min)
    private const val MIN_REQUIRED_POINTS = 11 // Need indices 0-10 (11 points total)
    
    /**
     * Generates a trading signal based on momentum analysis.
     * 
     * @param priceHistoryService Service to get price history
     * @param momentumCalculator Calculator for momentum values  
     * @param symbol The symbol to analyze (typically "🦄")
     * @return TradingSignal if momentum exceeds threshold, null otherwise
     */
    fun generateSignal(
        priceHistoryService: PriceHistoryService,
        momentumCalculator: MomentumCalculator,
        symbol: String
    ): TradingSignal? {
        require(symbol.isNotBlank()) { "Symbol cannot be blank" }
        
        // Get price history
        val priceHistory = priceHistoryService.getPriceHistory(symbol)
        
        // Check if we have enough data points (need indices 0-10)
        if (priceHistory.size < MIN_REQUIRED_POINTS) {
            logger.debug { "Insufficient price history for $symbol: ${priceHistory.size} points (need $MIN_REQUIRED_POINTS)" }
            return null
        }
        
        // Get current price (index 0) and price from 5 minutes ago (index 10)
        val currentPrice = priceHistory[0].midPrice
        val priceFromFiveMinAgo = priceHistory[PRICE_INDEX_5_MIN_AGO].midPrice
        
        // Calculate momentum
        val momentum = try {
            momentumCalculator.calculateMomentum(currentPrice, priceFromFiveMinAgo)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to calculate momentum for $symbol: ${e.message}" }
            return null
        }
        
        // Check if momentum exceeds threshold
        val absoluteMomentum = kotlin.math.abs(momentum)
        if (absoluteMomentum < MomentumCalculator.MOMENTUM_THRESHOLD) {
            logger.debug { "Momentum below threshold for $symbol: $momentum (threshold: ${MomentumCalculator.MOMENTUM_THRESHOLD})" }
            return null
        }
        
        // Determine signal side and create signal
        val signal = if (momentum > 0) {
            TradingSignal(
                side = OrderSide.BUY,
                confidence = absoluteMomentum,
                reason = "Positive momentum: ${String.format("%.4f", momentum)} (${String.format("%.2f", momentum * 100)}%)"
            )
        } else {
            TradingSignal(
                side = OrderSide.SELL,
                confidence = absoluteMomentum,
                reason = "Negative momentum: ${String.format("%.4f", momentum)} (${String.format("%.2f", momentum * 100)}%)"
            )
        }
        
        logger.info { "Generated $symbol signal: ${signal.side} with confidence ${String.format("%.4f", signal.confidence)} - ${signal.reason}" }
        return signal
    }
}