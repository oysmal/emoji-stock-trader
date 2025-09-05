package no.kraftlauget.kiworkshop.services

import no.kraftlauget.kiworkshop.models.PricePoint
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

private val logger = KotlinLogging.logger {}

/**
 * Thread-safe price history storage with 50-entry circular buffer per symbol.
 * Uses ConcurrentHashMap with ConcurrentLinkedDeque for lock-free thread safety.
 */
class PriceHistoryService {
    private val priceHistories = ConcurrentHashMap<String, ConcurrentLinkedDeque<PricePoint>>()
    
    companion object {
        private const val MAX_HISTORY_SIZE = 50
    }

    /**
     * Adds a new price point with current timestamp.
     * Maintains maximum history size by removing oldest entries.
     */
    fun addPrice(symbol: String, midPrice: Double) {
        require(symbol.isNotBlank()) { "Symbol cannot be blank" }
        require(midPrice > 0) { "Mid price must be positive" }
        
        try {
            val currentTime = System.currentTimeMillis()
            val pricePoint = PricePoint(currentTime, midPrice)
            
            // Get or create ConcurrentLinkedDeque for this symbol
            val history = priceHistories.getOrPut(symbol) { ConcurrentLinkedDeque() }
            
            // Thread-safe operations on the ConcurrentLinkedDeque
            // Add to front (latest first)
            history.addFirst(pricePoint)
            
            // Remove oldest if exceeding max size
            while (history.size > MAX_HISTORY_SIZE) {
                history.removeLast()
            }
            
            logger.debug { "Added price for $symbol: $midPrice at $currentTime (history size: ${history.size})" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to add price for $symbol: $midPrice - ${e.message}" }
            throw e // Re-throw to maintain current behavior
        }
    }

    /**
     * Returns complete price history for a symbol (latest first).
     * Returns empty list if no history exists.
     */
    fun getPriceHistory(symbol: String): List<PricePoint> {
        require(symbol.isNotBlank()) { "Symbol cannot be blank" }
        
        val history = priceHistories[symbol] ?: return emptyList()
        
        // Return a defensive copy - ConcurrentLinkedDeque provides thread safety
        return history.toList()
    }

    /**
     * Returns the most recent price for a symbol.
     * Returns null if no prices exist.
     */
    fun getLatestPrice(symbol: String): PricePoint? {
        require(symbol.isNotBlank()) { "Symbol cannot be blank" }
        
        val history = priceHistories[symbol] ?: return null
        
        // ConcurrentLinkedDeque provides thread-safe access
        return history.firstOrNull()
    }

    /**
     * Returns price at specific index (0 = latest, 1 = previous, etc.).
     * Returns null if index is out of bounds or no history exists.
     */
    fun getPriceAtIndex(symbol: String, index: Int): PricePoint? {
        require(symbol.isNotBlank()) { "Symbol cannot be blank" }
        require(index >= 0) { "Index must be non-negative" }
        
        val history = priceHistories[symbol] ?: return null
        
        // Convert to list for indexed access - thread-safe snapshot
        val snapshot = history.toList()
        return if (index < snapshot.size) {
            snapshot[index]
        } else {
            null
        }
    }
}