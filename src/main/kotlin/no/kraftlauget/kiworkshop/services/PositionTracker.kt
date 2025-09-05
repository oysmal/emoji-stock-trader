package no.kraftlauget.kiworkshop.services

import ApiClient
import Fill
import OrderSide
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks position for the 🦄 symbol by monitoring fills and reconciling with portfolio API.
 * Thread-safe implementation using simple synchronization.
 */
class PositionTracker(
    private val apiClient: ApiClient
) {
    private val logger = KotlinLogging.logger {}
    private val unicornSymbol = "🦄"
    private val positionMutex = Mutex()
    
    // Current position for 🦄 symbol - thread-safe atomic integer
    private val currentPosition = AtomicInteger(0)
    
    /**
     * Gets the current calculated position for 🦄.
     */
    suspend fun getCurrentPosition(): Int = positionMutex.withLock {
        val position = currentPosition.get()
        logger.debug { "Current $unicornSymbol position: $position" }
        return position
    }
    
    /**
     * Updates position based on a fill.
     * Called by FillDetector when new fills are processed.
     * BUY fills increment position, SELL fills decrement position.
     */
    suspend fun onFillReceived(fill: Fill) = positionMutex.withLock {
        // Only track 🦄 symbol for now
        if (fill.symbol != unicornSymbol) {
            logger.debug { "Ignoring fill for ${fill.symbol} - only tracking $unicornSymbol" }
            return@withLock
        }
        
        val previousPosition = currentPosition.get()
        val newPosition = when (fill.side) {
            OrderSide.BUY -> {
                val updated = currentPosition.addAndGet(fill.quantity)
                logger.info { "BUY fill processed: +${fill.quantity} $unicornSymbol. Position: $previousPosition → $updated" }
                updated
            }
            OrderSide.SELL -> {
                val updated = currentPosition.addAndGet(-fill.quantity)
                logger.info { "SELL fill processed: -${fill.quantity} $unicornSymbol. Position: $previousPosition → $updated" }
                updated
            }
        }
    }
    
    /**
     * Reconciles calculated position with actual portfolio position from API.
     * Called at startup to ensure position accuracy.
     * Logs discrepancies and continues with API position on success.
     * On API failure, logs error and continues with calculated position.
     */
    suspend fun reconcilePosition() = positionMutex.withLock {
        logger.info { "Starting position reconciliation for $unicornSymbol..." }
        
        try {
            val portfolio = apiClient.getPortfolio()
            val actualPosition = portfolio.positions[unicornSymbol] ?: 0
            val calculatedPosition = currentPosition.get()
            
            if (actualPosition == calculatedPosition) {
                logger.info { "Position reconciliation successful: $unicornSymbol position matches at $actualPosition shares" }
            } else {
                logger.warn { "Position discrepancy detected for $unicornSymbol: calculated=$calculatedPosition, actual=$actualPosition. Updating to actual position." }
                currentPosition.set(actualPosition)
                logger.info { "Position corrected to $actualPosition shares" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to reconcile position via API - continuing with calculated position (${currentPosition.get()} shares)" }
            // Continue with calculated position as per user requirements
        }
    }
}