package no.kraftlauget.kiworkshop.services

import ApiClient
import OrderBookResponse
import OrderSide
import OrderType
import PlaceOrderRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import no.kraftlauget.kiworkshop.models.TradingSignal

private val logger = KotlinLogging.logger {}

/**
 * Executes trading orders based on signals, with position checking and risk management.
 * Uses singleton pattern for simple implementation.
 */
object OrderExecutor {
    
    private const val BUDGET_PER_TRADE = 200.0 // $200 budget per trade
    private const val POSITION_SIZE_PERCENT = 0.10 // Trade 10% of current position
    private const val BUY_PRICE_DISCOUNT = 0.05 // Buy 5% below market price
    
    /**
     * Executes a trading signal by placing appropriate orders.
     * 
     * @param signal The trading signal to execute
     * @param symbol The symbol to trade
     * @param apiClient Client for API calls
     * @param positionTracker Tracker for current positions
     * @param orderManager Manager for tracking orders
     * @return true if order was successfully placed, false otherwise
     */
    suspend fun executeSignal(
        signal: TradingSignal,
        symbol: String,
        apiClient: ApiClient,
        positionTracker: PositionTracker,
        orderManager: OrderManager
    ): Boolean {
        require(symbol.isNotBlank()) { "Symbol cannot be blank" }
        
        logger.info { "Executing $symbol signal: ${signal.side} with confidence ${String.format("%.4f", signal.confidence)}" }
        
        try {
            // Get current orderbook
            val orderBook = apiClient.getOrderBook(symbol)
            
            // Return false immediately for empty orderbook
            if (orderBook.bids.isEmpty() || orderBook.asks.isEmpty()) {
                logger.warn { "Empty orderbook for $symbol - cannot execute trade" }
                return false
            }
            
            // Handle different order sides
            val orderResult = when (signal.side) {
                OrderSide.BUY -> executeBuyOrder(symbol, orderBook, apiClient, orderManager)
                OrderSide.SELL -> executeSellOrder(symbol, orderBook, apiClient, positionTracker, orderManager)
            }
            
            return orderResult
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute $symbol ${signal.side} signal: ${e.message}" }
            return false
        }
    }
    
    /**
     * Executes a BUY order using 5% discount from market price.
     * Calculates shares based on $1000 budget, rounded down.
     */
    private suspend fun executeBuyOrder(
        symbol: String,
        orderBook: OrderBookResponse,
        apiClient: ApiClient,
        orderManager: OrderManager
    ): Boolean {
        val marketPrice = orderBook.asks[0].price
        val limitPrice = marketPrice * (1.0 - BUY_PRICE_DISCOUNT) // 5% below market
        val sharesToBuy = (BUDGET_PER_TRADE / limitPrice).toInt() // Round down as requested
        
        if (sharesToBuy <= 0) {
            logger.warn { "Cannot buy $symbol: calculated shares=$sharesToBuy (marketPrice=$marketPrice, limitPrice=$limitPrice, budget=$BUDGET_PER_TRADE)" }
            return false
        }
        
        logger.info { "Placing BUY order for $sharesToBuy shares of $symbol at $limitPrice (5% below market $marketPrice, budget: $BUDGET_PER_TRADE)" }
        
        val orderRequest = PlaceOrderRequest(
            symbol = symbol,
            side = OrderSide.BUY,
            quantity = sharesToBuy,
            orderType = OrderType.LIMIT,
            limitPrice = limitPrice
        )
        
        return try {
            val orderResponse = apiClient.placeOrder(orderRequest)
            orderManager.trackOrder(orderResponse)
            logger.info { "Successfully placed BUY order: ${orderResponse.orderId}" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to place BUY order for $symbol: ${e.message}" }
            // Exchange validates cash and returns error if too low - handle gracefully
            false
        }
    }
    
    /**
     * Executes a SELL order using the best bid price.
     * Sells 10% of current position. Returns false if no position.
     */
    private suspend fun executeSellOrder(
        symbol: String,
        orderBook: OrderBookResponse,
        apiClient: ApiClient,
        positionTracker: PositionTracker,
        orderManager: OrderManager
    ): Boolean {
        // Check current position before selling
        val currentPosition = positionTracker.getCurrentPosition()
        
        if (currentPosition <= 0) {
            logger.debug { "Ignoring SELL signal for $symbol: no position (current: $currentPosition)" }
            return false
        }
        
        val bestBidPrice = orderBook.bids[0].price
        val sharesToSell = kotlin.math.max(1, (currentPosition * POSITION_SIZE_PERCENT).toInt()) // Sell 10% of position, minimum 1 share
        
        logger.info { "Placing SELL order for $sharesToSell shares of $symbol at $bestBidPrice (10% of position $currentPosition)" }
        
        val orderRequest = PlaceOrderRequest(
            symbol = symbol,
            side = OrderSide.SELL,
            quantity = sharesToSell,
            orderType = OrderType.LIMIT,
            limitPrice = bestBidPrice
        )
        
        return try {
            val orderResponse = apiClient.placeOrder(orderRequest)
            orderManager.trackOrder(orderResponse)
            logger.info { "Successfully placed SELL order: ${orderResponse.orderId}" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to place SELL order for $symbol: ${e.message}" }
            false
        }
    }
}