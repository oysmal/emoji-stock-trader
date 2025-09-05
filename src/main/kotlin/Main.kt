import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import no.kraftlauget.kiworkshop.services.*
import io.github.oshai.kotlinlogging.KotlinLogging

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

private val logger = KotlinLogging.logger {}

suspend fun main() {
    val apiClient = ApiClient()
    var tradingSession: TradingSessionManager? = null
    
    try {
        println("=== Emoji Stock Trading Bot - Autonomous Trading Mode ===")
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
        
        // Initialize all services in correct order
        println("⚙️ Initializing trading services...")
        
        // Core services - no dependencies
        val orderManager = OrderManager()
        val priceHistoryService = PriceHistoryService()
        // MomentumCalculator is an object, not a class - no need to instantiate
        
        // Services that depend on ApiClient
        val positionTracker = PositionTracker(apiClient)
        
        // Perform position reconciliation at startup
        println("🔄 Reconciling position with portfolio...")
        positionTracker.reconcilePosition()
        println("✅ Position reconciliation complete")
        
        // Start fill detection
        val fillDetector = FillDetector(apiClient, orderManager, positionTracker)
        fillDetector.start()
        println("✅ Fill detector started (polling every 5 seconds)")
        
        // Create trading session manager
        tradingSession = TradingSessionManager(
            apiClient = apiClient,
            priceHistoryService = priceHistoryService,
            tradingSignalGenerator = TradingSignalGenerator,
            orderExecutor = OrderExecutor,
            orderManager = orderManager,
            positionTracker = positionTracker,
            symbol = "🦄"
        )
        println("✅ Trading session manager initialized")
        println()
        
        // Display initial portfolio
        println("📊 Initial portfolio status:")
        val portfolio = apiClient.getPortfolio()
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
        
        // Display initial market data
        try {
            println("📈 Current market data for 🦄:")
            val orderBook = apiClient.getOrderBook("🦄")
            
            // Display top 3 bids and asks
            println("   Top 3 Bids:")
            if (orderBook.bids.isEmpty()) {
                println("     (No bids available)")
            } else {
                orderBook.bids.take(3).forEach { bid ->
                    println("     $${String.format("%.2f", bid.price)} (${bid.quantity} shares)")
                }
            }
            
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
        
        // Start autonomous trading
        println("🤖 Starting autonomous trading session...")
        println("   - Price polling every 30 seconds")
        println("   - Trading cycles every 30 seconds (15-second offset)")
        println("   - Maximum 10 orders")
        println("   - Press Ctrl+C to stop")
        println()
        
        // Create coroutine scope for trading
        coroutineScope {
            // Start trading session
            tradingSession.start(this)
            
            // Status reporting loop
            val statusJob = launch {
                while (true) {
                    delay(30.seconds) // Report status every 30 seconds
                    
                    val status = tradingSession.getSessionStatus()
                    println("📊 Session Status:")
                    println("   Orders placed: ${status.ordersPlaced}/10")
                    println("   Time elapsed: ${status.timeElapsedMinutes} minutes")
                    
                    status.currentPnL?.let { pnl ->
                        val pnlFormatted = if (pnl >= 0) "+$${String.format("%.2f", pnl)}" else "-$${String.format("%.2f", kotlin.math.abs(pnl))}"
                        println("   Current P&L: $pnlFormatted")
                    } ?: println("   Current P&L: Unable to calculate")
                    
                    status.lastBuyOrder?.let { println("   Last buy: $it") }
                    status.lastSellOrder?.let { println("   Last sell: $it") }
                    
                    println("   Status: ${if (status.isRunning) "Running" else "Stopped"}")
                    println()
                    
                    // Check if trading should stop
                    if (tradingSession.shouldStop()) {
                        println("🎯 Maximum orders reached! Stopping trading session...")
                        break
                    }
                }
            }
            
            // Wait for trading to complete or manual cancellation
            try {
                statusJob.join()
            } catch (e: CancellationException) {
                println("\n🛑 Trading session canceled by user")
            }
        }
        
        println()
        println("📋 Final session summary:")
        val finalStatus = tradingSession.getSessionStatus()
        println("   Total orders placed: ${finalStatus.ordersPlaced}")
        println("   Total time: ${finalStatus.timeElapsedMinutes} minutes")
        
        finalStatus.currentPnL?.let { pnl ->
            val pnlFormatted = if (pnl >= 0) "+$${String.format("%.2f", pnl)}" else "-$${String.format("%.2f", kotlin.math.abs(pnl))}"
            println("   Final P&L: $pnlFormatted")
        } ?: println("   Final P&L: Unable to calculate")
        
        // Final portfolio check
        val finalPortfolio = apiClient.getPortfolio()
        println("   Final cash: $${finalPortfolio.cash}")
        println("   Final 🦄 position: ${finalPortfolio.positions["🦄"] ?: 0} shares")
        
        // Stop all services
        tradingSession.stop()
        fillDetector.stop()
        fillDetector.close()
        
        println("🎉 Trading session complete!")
        
    } catch (e: Exception) {
        logger.error(e) { "Trading bot error: ${e.message}" }
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    } finally {
        tradingSession?.stop()
        apiClient.close()
        println("🔧 Cleanup complete")
    }
}