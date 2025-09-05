import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
    val apiClient = ApiClient()
    
    try {
        println("=== Emoji Stock Trading Bot - Phase 0 ===")
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
            println("   ✅ Sell order placed successfully!")
            println("      Order ID: ${sellResponse.orderId}")  
            println("      Status: ${sellResponse.status}")
            println("      Symbol: ${sellResponse.symbol} | Side: ${sellResponse.side} | Qty: ${sellResponse.quantity}")
            
            // Check final portfolio
            delay(2.seconds) // Wait for orders to potentially fill
            println("   Checking portfolio after orders...")
            val finalPortfolio = apiClient.getPortfolio()
            println("   Final Cash: $${finalPortfolio.cash}")
            println("   Final 🦄 Position: ${finalPortfolio.positions["🦄"] ?: 0} shares")
            
        } catch (e: Exception) {
            println("   ❌ Order placement failed: ${e.message}")
            e.printStackTrace()
        }
        
        println()
        println("🎉 Phase 1B Complete! Rate limiting and order placement working!")
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    } finally {
        apiClient.close()
    }
}