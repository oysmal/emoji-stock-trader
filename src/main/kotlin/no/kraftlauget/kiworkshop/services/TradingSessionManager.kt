package no.kraftlauget.kiworkshop.services

import ApiClient
import OrderSide
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import no.kraftlauget.kiworkshop.models.TradingSignal
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Manages trading session with autonomous price polling and trading cycles.
 * Runs until manually canceled or 10 orders reached.
 * 
 * Features:
 * - Price polling every 30 seconds
 * - Trading cycles every 30 seconds with 15-second offset
 * - Session tracking with P&L and order history
 * - Graceful shutdown on cancellation
 */
class TradingSessionManager(
    private val apiClient: ApiClient,
    private val priceHistoryService: PriceHistoryService,
    private val tradingSignalGenerator: TradingSignalGenerator,
    private val orderExecutor: OrderExecutor,
    private val orderManager: OrderManager,
    private val positionTracker: PositionTracker,
    private val symbols: List<String> = listOf("🦄", "💎", "❤️", "🍌", "🍾", "💻")
) {
    
    companion object {
        private const val MAX_ORDERS = 10
        private const val PRICE_POLLING_INTERVAL_SECONDS = 30L
        private const val TRADING_CYCLE_INTERVAL_SECONDS = 30L
        private const val TRADING_OFFSET_SECONDS = 15L
    }
    
    // Session tracking
    private val sessionStartTime = System.currentTimeMillis()
    private var ordersPlaced = 0
    private var lastBuyOrder: String? = null
    private var lastSellOrder: String? = null
    private var cachedPnL: Double? = null
    
    // Coroutine jobs
    private var pricePollingJob: Job? = null
    private var tradingCycleJob: Job? = null
    private var isRunning = false
    
    /**
     * Starts the trading session with price polling and trading cycles.
     * Returns immediately - trading runs in background coroutines.
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) {
            logger.warn { "Trading session already running" }
            return
        }
        
        isRunning = true
        logger.info { "Starting trading session for ${symbols.joinToString(", ")}" }
        logger.info { "Price polling every ${PRICE_POLLING_INTERVAL_SECONDS}s, trading cycles every ${TRADING_CYCLE_INTERVAL_SECONDS}s with ${TRADING_OFFSET_SECONDS}s offset" }
        logger.info { "Session will run until manually canceled or $MAX_ORDERS orders placed" }
        
        // Start price polling immediately
        pricePollingJob = scope.launch {
            try {
                pricePollingLoop()
            } catch (e: CancellationException) {
                logger.info { "Price polling canceled" }
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Price polling failed unexpectedly" }
            }
        }
        
        // Start trading cycles with offset
        tradingCycleJob = scope.launch {
            try {
                // Wait for offset before starting trading cycles
                delay(TRADING_OFFSET_SECONDS.seconds)
                tradingCycleLoop()
            } catch (e: CancellationException) {
                logger.info { "Trading cycle canceled" }
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Trading cycle failed unexpectedly" }
            }
        }
    }
    
    /**
     * Stops the trading session immediately.
     * Cancels all background coroutines without waiting for pending operations.
     */
    fun stop() {
        if (!isRunning) {
            return
        }
        
        logger.info { "Stopping trading session..." }
        isRunning = false
        
        // Cancel jobs immediately
        pricePollingJob?.cancel()
        tradingCycleJob?.cancel()
        
        logger.info { "Trading session stopped" }
    }
    
    /**
     * Checks if the session should stop due to order limit.
     */
    fun shouldStop(): Boolean {
        return ordersPlaced >= MAX_ORDERS
    }
    
    /**
     * Gets current session status for reporting.
     * P&L calculation is cached and updated periodically to avoid blocking operations.
     */
    fun getSessionStatus(): SessionStatus {
        val elapsedTimeMs = System.currentTimeMillis() - sessionStartTime
        val elapsedTimeMinutes = elapsedTimeMs / 60_000
        
        return SessionStatus(
            ordersPlaced = ordersPlaced,
            timeElapsedMinutes = elapsedTimeMinutes,
            currentPnL = cachedPnL,
            lastBuyOrder = lastBuyOrder,
            lastSellOrder = lastSellOrder,
            isRunning = isRunning
        )
    }
    
    /**
     * Safely calculates and caches P&L without blocking.
     * Called periodically from price polling loop.
     */
    private suspend fun updateCachedPnL() {
        try {
            val portfolio = apiClient.getPortfolio()
            
            // Calculate total position value across all symbols
            var totalPositionValue = 0.0
            var validPrices = 0
            
            for (symbol in symbols) {
                val currentMarketPrice = getCurrentMarketPrice(symbol)
                if (currentMarketPrice > 0.0) {
                    val positionSize = portfolio.positions[symbol] ?: 0
                    totalPositionValue += positionSize * currentMarketPrice
                    validPrices++
                }
            }
            
            if (validPrices > 0) {
                val initialCash = 100_000.0 // Standard starting cash
                val currentValue = portfolio.cash + totalPositionValue
                cachedPnL = currentValue - initialCash
                logger.debug { "Updated cached P&L: ${String.format("%.2f", cachedPnL ?: 0.0)} (${validPrices}/${symbols.size} symbols)" }
            }
        } catch (e: Exception) {
            logger.debug { "Could not update P&L cache: ${e.message}" }
            // Keep existing cached value
        }
    }
    
    /**
     * Price polling loop - runs every 30 seconds.
     * Also updates P&L cache periodically for safe reporting.
     */
    private suspend fun pricePollingLoop() {
        while (isRunning && !shouldStop()) {
            try {
                // Poll prices for all symbols sequentially
                symbols.forEach { symbol ->
                    try {
                        pollPrice(symbol)
                    } catch (e: Exception) {
                        logger.error(e) { "Price polling failed for $symbol: ${e.message}" }
                        // Continue with next symbol - don't stop on single symbol failure
                    }
                }
                // Update P&L cache periodically (safe non-blocking operation)
                updateCachedPnL()
            } catch (e: Exception) {
                logger.error(e) { "Price polling cycle failed, skipping cycle: ${e.message}" }
                // Continue to next cycle - don't stop on API failures
            }
            
            delay(PRICE_POLLING_INTERVAL_SECONDS.seconds)
        }
    }
    
    /**
     * Trading cycle loop - runs every 30 seconds with 15-second offset.
     */
    private suspend fun tradingCycleLoop() {
        while (isRunning && !shouldStop()) {
            try {
                // Execute trading cycles for all symbols sequentially
                symbols.forEach { symbol ->
                    if (!shouldStop()) { // Check before each symbol to stop immediately when limit reached
                        try {
                            executeTradingCycle(symbol)
                        } catch (e: Exception) {
                            logger.error(e) { "Trading cycle failed for $symbol: ${e.message}" }
                            // Continue with next symbol - don't stop on single symbol failure
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Trading cycle failed, skipping cycle: ${e.message}" }
                // Continue to next cycle - don't stop on API failures
            }
            
            delay(TRADING_CYCLE_INTERVAL_SECONDS.seconds)
        }
        
        if (shouldStop()) {
            logger.info { "Trading session completed: reached maximum orders ($MAX_ORDERS)" }
            stop()
        }
    }
    
    /**
     * Polls current market price and updates price history for a specific symbol.
     */
    private suspend fun pollPrice(symbol: String) {
        try {
            val orderBook = apiClient.getOrderBook(symbol)
            val midPrice = apiClient.getCurrentSpread(orderBook)?.let { spread ->
                val bestBid = orderBook.bids.firstOrNull()?.price ?: return
                val bestAsk = orderBook.asks.firstOrNull()?.price ?: return
                (bestBid + bestAsk) / 2.0
            }
            
            if (midPrice != null && midPrice > 0) {
                priceHistoryService.addPrice(symbol, midPrice)
                logger.debug { "Updated $symbol price: $midPrice" }
            } else {
                logger.warn { "Could not calculate valid mid price for $symbol" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to poll price for $symbol: ${e.message}" }
            throw e // Re-throw to be caught by caller
        }
    }
    
    /**
     * Executes one trading cycle for a specific symbol: generate signal and execute if valid.
     */
    private suspend fun executeTradingCycle(symbol: String) {
        try {
            logger.debug { "Executing trading cycle for $symbol" }
            
            // Generate trading signal
            val signal = tradingSignalGenerator.generateSignal(
                priceHistoryService, 
                MomentumCalculator, 
                symbol
            )
            
            if (signal == null) {
                logger.debug { "No trading signal generated for $symbol" }
                return
            }
            
            // Execute the signal
            val orderPlaced = orderExecutor.executeSignal(
                signal, 
                symbol, 
                apiClient, 
                positionTracker, 
                orderManager
            )
            
            if (orderPlaced) {
                ordersPlaced++
                
                // Track last order by type
                when (signal.side) {
                    OrderSide.BUY -> lastBuyOrder = "$symbol Buy #$ordersPlaced at ${System.currentTimeMillis()}"
                    OrderSide.SELL -> lastSellOrder = "$symbol Sell #$ordersPlaced at ${System.currentTimeMillis()}"
                }
                
                logger.info { "Order placed successfully for $symbol. Total orders: $ordersPlaced/$MAX_ORDERS" }
                
                if (shouldStop()) {
                    logger.info { "Reached maximum orders limit ($MAX_ORDERS)" }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute trading cycle for $symbol: ${e.message}" }
            throw e // Re-throw to be caught by caller
        }
    }
    
    /**
     * Gets current market price for P&L calculation for a specific symbol.
     */
    private suspend fun getCurrentMarketPrice(symbol: String): Double {
        return try {
            val orderBook = apiClient.getOrderBook(symbol)
            val bestBid = orderBook.bids.firstOrNull()?.price ?: 0.0
            val bestAsk = orderBook.asks.firstOrNull()?.price ?: 0.0
            if (bestBid > 0 && bestAsk > 0) {
                (bestBid + bestAsk) / 2.0
            } else {
                0.0
            }
        } catch (e: Exception) {
            logger.warn { "Could not get market price for $symbol: ${e.message}" }
            0.0
        }
    }
}

/**
 * Session status data class for reporting.
 */
data class SessionStatus(
    val ordersPlaced: Int,
    val timeElapsedMinutes: Long,
    val currentPnL: Double?,
    val lastBuyOrder: String?,
    val lastSellOrder: String?,
    val isRunning: Boolean
)