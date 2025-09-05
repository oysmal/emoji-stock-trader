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
        println("🎉 Phase 0 Complete! API client working correctly.")
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    } finally {
        apiClient.close()
    }
}