import kotlinx.coroutines.runBlocking
import kotlin.random.Random

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
        println("🎉 Phase 1A Complete! Market data integration working.")
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    } finally {
        apiClient.close()
    }
}