package no.kraftlauget.kiworkshop.services

import OrderSide
import io.github.oshai.kotlinlogging.KotlinLogging
import no.kraftlauget.kiworkshop.models.TradingSignal
import no.kraftlauget.kiworkshop.models.PricePoint

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
        
        // Check if we have enough data points for full momentum analysis
        if (priceHistory.size < MIN_REQUIRED_POINTS) {
            // Fallback strategy: use short-term momentum if we have at least 2 points
            if (priceHistory.size >= 2) {
                logger.debug { "Using fallback short-term momentum for $symbol: ${priceHistory.size} points" }
                return generateFallbackSignal(priceHistory, momentumCalculator, symbol)
            } else if (priceHistory.size == 1) {
                logger.debug { "Using initial trading strategy for $symbol: 1 price point" }
                return generateInitialTradingSignal(priceHistory, symbol)
            } else {
                logger.debug { "No price history available for $symbol" }
                return null
            }
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
    
    /**
     * Generates a fallback trading signal using short-term momentum.
     * Uses the most recent available price point for comparison.
     */
    private fun generateFallbackSignal(
        priceHistory: List<PricePoint>,
        momentumCalculator: MomentumCalculator,
        symbol: String
    ): TradingSignal? {
        // Use current price vs most recent previous price
        val currentPrice = priceHistory[0].midPrice
        val previousPrice = priceHistory[priceHistory.size - 1].midPrice
        
        // Calculate short-term momentum
        val momentum = try {
            momentumCalculator.calculateMomentum(currentPrice, previousPrice)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Failed to calculate fallback momentum for $symbol: ${e.message}" }
            return null
        }
        
        // Use lower threshold for fallback (0.5% instead of 1%)
        val fallbackThreshold = MomentumCalculator.MOMENTUM_THRESHOLD * 0.5
        val absoluteMomentum = kotlin.math.abs(momentum)
        
        if (absoluteMomentum < fallbackThreshold) {
            logger.debug { "Fallback momentum below threshold for $symbol: $momentum (threshold: $fallbackThreshold)" }
            return null
        }
        
        // Create signal with reduced confidence due to limited data
        val adjustedConfidence = absoluteMomentum * 0.7 // Reduce confidence by 30%
        
        val signal = if (momentum > 0) {
            TradingSignal(
                side = OrderSide.BUY,
                confidence = adjustedConfidence,
                reason = "Fallback positive momentum: ${String.format("%.4f", momentum)} (${String.format("%.2f", momentum * 100)}%) [${priceHistory.size} points]"
            )
        } else {
            TradingSignal(
                side = OrderSide.SELL,
                confidence = adjustedConfidence,
                reason = "Fallback negative momentum: ${String.format("%.4f", momentum)} (${String.format("%.2f", momentum * 100)}%) [${priceHistory.size} points]"
            )
        }
        
        logger.info { "Generated fallback $symbol signal: ${signal.side} with reduced confidence ${String.format("%.4f", signal.confidence)} - ${signal.reason}" }
        return signal
    }
    
    /**
     * Generates an initial trading signal when we have only one price point.
     * Uses a simple heuristic based on price level to generate conservative signals.
     */
    private fun generateInitialTradingSignal(
        priceHistory: List<PricePoint>,
        symbol: String
    ): TradingSignal? {
        require(priceHistory.size == 1) { "Initial trading strategy requires exactly 1 price point" }
        
        val currentPrice = priceHistory[0].midPrice
        
        // Simple heuristic: if price is low (< $50), consider buying; if high (> $150), consider selling
        // Use conservative confidence since we have no trend data
        val signal = when {
            currentPrice < 50.0 -> {
                TradingSignal(
                    side = OrderSide.BUY,
                    confidence = 0.005, // Very low confidence (0.5%)
                    reason = "Initial buy signal: low price at ${String.format("%.2f", currentPrice)} [1 point]"
                )
            }
            currentPrice > 150.0 -> {
                TradingSignal(
                    side = OrderSide.SELL,
                    confidence = 0.005, // Very low confidence (0.5%)
                    reason = "Initial sell signal: high price at ${String.format("%.2f", currentPrice)} [1 point]"
                )
            }
            else -> {
                // Price between $50-$150, no clear signal
                logger.debug { "Initial price for $symbol at ${String.format("%.2f", currentPrice)} is neutral, no signal generated" }
                return null
            }
        }
        
        logger.info { "Generated initial $symbol signal: ${signal.side} with minimal confidence ${String.format("%.4f", signal.confidence)} - ${signal.reason}" }
        return signal
    }
}