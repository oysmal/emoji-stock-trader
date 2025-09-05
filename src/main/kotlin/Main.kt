import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import no.kraftlauget.kiworkshop.services.OrderManager
import no.kraftlauget.kiworkshop.services.FillDetector
import no.kraftlauget.kiworkshop.services.PositionTracker

/**
 * Tests order placement functionality with both BUY and SELL limit orders.
 * Shows portfolio status, tracked orders, and position calculation after placing orders.
 */
suspend fun testOrderPlacement(
    apiClient: ApiClient,
    orderManager: OrderManager,
    positionTracker: PositionTracker
) {
    println("💰 Testing order placement with real orders...")
    
    try {
        // Test limit buy order - use a reasonable price like $50
        println("   Placing limit BUY order for 🦄 at $50...")
        val buyRequest = PlaceOrderRequest(
            symbol = "🦄",
            side = OrderSide.BUY,
            quantity = 1,
            orderType = OrderType.LIMIT,
            limitPrice = 50.0
        )
        
        val buyResponse = apiClient.placeOrder(buyRequest)
        orderManager.trackOrder(buyResponse)
        println("   ✅ Buy order placed successfully!")
        println("      Order ID: ${buyResponse.orderId}")
        println("      Status: ${buyResponse.status}")
        println("      Symbol: ${buyResponse.symbol} | Side: ${buyResponse.side} | Qty: ${buyResponse.quantity}")
        
        delay(2.seconds) // Wait 2 seconds
        
        // Test limit sell order - use a higher price like $150
        println("   Placing limit SELL order for 🦄 at $150...")
        val sellRequest = PlaceOrderRequest(
            symbol = "🦄", 
            side = OrderSide.SELL,
            quantity = 1,
            orderType = OrderType.LIMIT,
            limitPrice = 150.0
        )
        
        val sellResponse = apiClient.placeOrder(sellRequest)
        orderManager.trackOrder(sellResponse)
        println("   ✅ Sell order placed successfully!")
        println("      Order ID: ${sellResponse.orderId}")  
        println("      Status: ${sellResponse.status}")
        println("      Symbol: ${sellResponse.symbol} | Side: ${sellResponse.side} | Qty: ${sellResponse.quantity}")
        
        // Check final portfolio and tracked orders
        delay(2.seconds) // Wait for orders to potentially fill
        println("   Checking portfolio after orders...")
        val finalPortfolio = apiClient.getPortfolio()
        println("   Final Cash: $${finalPortfolio.cash}")
        println("   Final 🦄 Position: ${finalPortfolio.positions["🦄"] ?: 0} shares")
        
        // Show currently tracked orders
        val trackedOrders = orderManager.getAllTrackedOrders()
        println("   Currently tracking ${trackedOrders.size} orders")
        if (trackedOrders.isNotEmpty()) {
            println("   Tracked orders:")
            trackedOrders.values.forEach { order ->
                println("     ${order.orderId}: ${order.symbol} ${order.side} ${order.quantity}@${order.limitPrice} (${order.status})")
            }
        }
        
        // Show current position tracking
        val currentPosition = positionTracker.getCurrentPosition()
        println("   Current 🦄 position (calculated): $currentPosition shares")
        
    } catch (e: Exception) {
        println("   ❌ Order placement failed: ${e.message}")
        e.printStackTrace()
    }
}

suspend fun main() {
    val apiClient = ApiClient()
    lateinit var fillDetector: FillDetector
    var fillDetectorInitialized = false
    
    try {
        println("=== Emoji Stock Trading Bot - Phase 1C ===")
        println()
        
        // Generate a unique team ID for this run
        val teamId = "trading-bot-${Random.nextInt(1000, 9999)}"
        println("🚀 Registering team: $teamId")
        
        // Register the team
        val registrationResponse = apiClient.register(teamId)
        println("✅ Registration successful!")
        println("   Team ID: ${registrationResponse.teamId}")
        println("   Initial Cash: $${registrationResponse.initialCash}")
        println()
        
        // Initialize order management, position tracking, and fill detection - Phase 1C.3
        println("⚙️ Starting order management, position tracking, and fill detection systems...")
        val orderManager = OrderManager()
        val positionTracker = PositionTracker(apiClient)
        
        // Perform position reconciliation at startup
        println("🔄 Reconciling position with portfolio...")
        positionTracker.reconcilePosition()
        println("✅ Position reconciliation complete")
        
        fillDetector = FillDetector(apiClient, orderManager, positionTracker)
        fillDetectorInitialized = true
        fillDetector.start()
        println("✅ Fill detector started (polling every 5 seconds)")
        println()
        
        // Fetch portfolio information
        println("📊 Fetching portfolio information...")
        val portfolio = apiClient.getPortfolio()
        println("✅ Portfolio fetched successfully!")
        println("   Team: ${portfolio.teamId}")
        println("   Cash: $${portfolio.cash}")
        println("   Equity: $${portfolio.equity}")
        println("   Positions:")
        
        if (portfolio.positions.isEmpty()) {
            println("     (No positions yet)")
        } else {
            portfolio.positions.forEach { (symbol, quantity) ->
                println("     $symbol: $quantity shares")
            }
        }
        
        println()
        
        // Market data testing section
        try {
            println("📈 Fetching market data for 🦄...")
            val orderBook = apiClient.getOrderBook("🦄")
            println("✅ Market data retrieved successfully!")
            
            // Display top 3 bids
            println("   Top 3 Bids:")
            if (orderBook.bids.isEmpty()) {
                println("     (No bids available)")
            } else {
                orderBook.bids.take(3).forEach { bid ->
                    println("     $${String.format("%.2f", bid.price)} (${bid.quantity} shares)")
                }
            }
            
            // Display top 3 asks
            println("   Top 3 Asks:")
            if (orderBook.asks.isEmpty()) {
                println("     (No asks available)")
            } else {
                orderBook.asks.take(3).forEach { ask ->
                    println("     $${String.format("%.2f", ask.price)} (${ask.quantity} shares)")
                }
            }
            
            // Display spread
            val spread = apiClient.getCurrentSpread(orderBook)
            if (spread != null) {
                println("   Spread: $${String.format("%.2f", spread)}")
            } else {
                println("   Spread: No bids/asks available")
            }
            
        } catch (e: Exception) {
            println("❌ Error fetching market data: ${e.message}")
        }
        
        println()
        
        println("⚡ Rate limiting test previously passed!")
        println()
        
        // Order placement test - Phase 1B  
        testOrderPlacement(apiClient, orderManager, positionTracker)
        
        println()
        println("🎉 Phase 1C.3 Complete! Position tracking and fill detection systems running!")
        
        // Let the fill detector run for a few more seconds to demonstrate
        println("⏳ Letting fill detector run for 15 more seconds to check for fills...")
        delay(15.seconds)
        
        // Final check of tracked orders and position
        val finalTrackedOrders = orderManager.getAllTrackedOrders()
        println("📋 Final tracked orders count: ${finalTrackedOrders.size}")
        
        val finalPosition = positionTracker.getCurrentPosition()
        println("📊 Final calculated 🦄 position: $finalPosition shares")
        
        // Stop the fill detector
        if (fillDetectorInitialized) {
            fillDetector.stop()
        }
        println("🛑 Fill detector stopped")
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    } finally {
        if (fillDetectorInitialized) {
            fillDetector.close()
        }
        apiClient.close()
    }
}