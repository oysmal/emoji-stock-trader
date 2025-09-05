package no.kraftlauget.kiworkshop.examples

import ApiClient
import no.kraftlauget.kiworkshop.services.*

/**
 * Example demonstrating the trading strategy components working together.
 * Shows typical usage pattern for momentum-based trading.
 */
object TradingStrategyExample {
    
    /**
     * Example of a complete trading cycle:
     * 1. Generate signal based on price history
     * 2. Execute signal if generated
     * 3. Handle success/failure appropriately
     */
    suspend fun exampleTradingCycle() {
        // Initialize services (typically done once at startup)
        val apiClient = ApiClient()
        val priceHistoryService = PriceHistoryService()
        // MomentumCalculator is an object, not a class
        val positionTracker = PositionTracker(apiClient)
        val orderManager = OrderManager()
        
        val symbol = "🦄"
        
        // Register with exchange (normally done once)
        try {
            apiClient.register("team-example")
            positionTracker.reconcilePosition()
        } catch (e: Exception) {
            println("Setup failed: ${e.message}")
            return
        }
        
        // Simulate having some price history (normally populated by polling)
        // In real usage, this would be done by a separate polling service
        println("Note: In real usage, price history would be populated by orderbook polling")
        
        // Generate trading signal
        val signal = TradingSignalGenerator.generateSignal(
            priceHistoryService = priceHistoryService,
            momentumCalculator = MomentumCalculator,
            symbol = symbol
        )
        
        when (signal) {
            null -> {
                println("No trading signal generated - insufficient momentum or data")
            }
            else -> {
                println("Generated signal: ${signal.side} with confidence ${signal.confidence}")
                println("Reason: ${signal.reason}")
                
                // Execute the signal
                val orderPlaced = OrderExecutor.executeSignal(
                    signal = signal,
                    symbol = symbol,
                    apiClient = apiClient,
                    positionTracker = positionTracker,
                    orderManager = orderManager
                )
                
                if (orderPlaced) {
                    println("Order successfully placed")
                } else {
                    println("Failed to place order")
                }
            }
        }
        
        // Cleanup
        apiClient.close()
    }
    
    /**
     * Example showing how to integrate with existing polling loop.
     * This would typically be called from your main trading loop.
     */
    suspend fun exampleIntegratedTrading(
        apiClient: ApiClient,
        priceHistoryService: PriceHistoryService,
        momentumCalculator: MomentumCalculator,
        positionTracker: PositionTracker,
        orderManager: OrderManager
    ) {
        val symbol = "🦄"
        
        // Generate signal based on current price history
        val signal = TradingSignalGenerator.generateSignal(
            priceHistoryService = priceHistoryService,
            momentumCalculator = MomentumCalculator,
            symbol = symbol
        )
        
        // Execute if signal generated
        signal?.let { tradingSignal ->
            val success = OrderExecutor.executeSignal(
                signal = tradingSignal,
                symbol = symbol,
                apiClient = apiClient,
                positionTracker = positionTracker,
                orderManager = orderManager
            )
            
            if (success) {
                println("Executed ${tradingSignal.side} signal successfully")
            } else {
                println("Failed to execute ${tradingSignal.side} signal")
            }
        }
    }
}