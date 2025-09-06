package no.kraftlauget.kiworkshop.services

import ApiClient
import Fill
import OrderSide
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks positions for all emoji symbols by monitoring fills and reconciling with portfolio API.
 * Thread-safe implementation using simple synchronization.
 */
class PositionTracker(
    private val apiClient: ApiClient
) {
    private val logger = KotlinLogging.logger {}
    private val symbols = listOf("🦄", "💎", "❤️", "🍌", "🍾", "💻")
    private val positionMutex = Mutex()
    
    // Current positions for all symbols - thread-safe atomic integers
    private val currentPositions = mutableMapOf<String, AtomicInteger>()
    
    init {
        // Initialize positions for all symbols
        symbols.forEach { symbol ->
            currentPositions[symbol] = AtomicInteger(0)
        }
    }
    
    /**
     * Gets the current calculated position for a specific symbol.
     * Defaults to 🦄 for backward compatibility.
     */
    suspend fun getCurrentPosition(symbol: String = "🦄"): Int = positionMutex.withLock {
        val position = currentPositions[symbol]?.get() ?: 0
        logger.debug { "Current $symbol position: $position" }
        return position
    }
    
    /**
     * Gets all current positions.
     */
    suspend fun getAllPositions(): Map<String, Int> = positionMutex.withLock {
        return currentPositions.mapValues { it.value.get() }
    }
    
    /**
     * Updates position based on a fill.
     * Called by FillDetector when new fills are processed.
     * BUY fills increment position, SELL fills decrement position.
     */
    suspend fun onFillReceived(fill: Fill) = positionMutex.withLock {
        // Check if we track this symbol
        val positionCounter = currentPositions[fill.symbol]
        if (positionCounter == null) {
            logger.debug { "Ignoring fill for ${fill.symbol} - not tracking this symbol" }
            return@withLock
        }
        
        val previousPosition = positionCounter.get()
        val newPosition = when (fill.side) {
            OrderSide.BUY -> {
                val updated = positionCounter.addAndGet(fill.quantity)
                logger.info { "BUY fill processed: +${fill.quantity} ${fill.symbol}. Position: $previousPosition → $updated" }
                updated
            }
            OrderSide.SELL -> {
                val updated = positionCounter.addAndGet(-fill.quantity)
                logger.info { "SELL fill processed: -${fill.quantity} ${fill.symbol}. Position: $previousPosition → $updated" }
                updated
            }
        }
    }
    
    /**
     * Reconciles calculated positions with actual portfolio positions from API.
     * Called at startup to ensure position accuracy.
     * Logs discrepancies and continues with API position on success.
     * On API failure, logs error and continues with calculated position.
     */
    suspend fun reconcilePosition() = positionMutex.withLock {
        logger.info { "Starting position reconciliation for all symbols..." }
        
        try {
            val portfolio = apiClient.getPortfolio()
            
            symbols.forEach { symbol ->
                val actualPosition = portfolio.positions[symbol] ?: 0
                val calculatedPosition = currentPositions[symbol]?.get() ?: 0
                
                if (actualPosition == calculatedPosition) {
                    logger.debug { "Position reconciliation successful: $symbol position matches at $actualPosition shares" }
                } else {
                    logger.warn { "Position discrepancy detected for $symbol: calculated=$calculatedPosition, actual=$actualPosition. Updating to actual position." }
                    currentPositions[symbol]?.set(actualPosition)
                    logger.info { "Position corrected for $symbol to $actualPosition shares" }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to reconcile positions via API - continuing with calculated positions" }
            // Continue with calculated positions as per user requirements
        }
    }
}