package no.kraftlauget.kiworkshop.examples

import no.kraftlauget.kiworkshop.services.PriceHistoryService
import no.kraftlauget.kiworkshop.services.MomentumCalculator

/**
 * Example demonstrating PriceHistoryService and MomentumCalculator usage.
 * This shows how the components work together for momentum-based trading decisions.
 */
fun main() {
    val priceHistory = PriceHistoryService()
    // MomentumCalculator is an object, not a class
    
    println("=== Price History & Momentum Calculation Example ===\n")
    
    // Simulate receiving price data over time
    println("1. Adding price data for 🚀 symbol:")
    priceHistory.addPrice("🚀", 100.0)
    Thread.sleep(100) // Small delay to show different timestamps
    
    priceHistory.addPrice("🚀", 102.5)
    Thread.sleep(100)
    
    priceHistory.addPrice("🚀", 98.7)
    Thread.sleep(100)
    
    priceHistory.addPrice("🚀", 105.2)
    
    // Display current price history
    val history = priceHistory.getPriceHistory("🚀")
    println("Current history (latest first):")
    history.forEachIndexed { index, pricePoint ->
        println("  Index $index: ${pricePoint.midPrice} at ${pricePoint.timestamp}")
    }
    
    // Get latest price
    val latestPrice = priceHistory.getLatestPrice("🚀")
    println("\n2. Latest price: ${latestPrice?.midPrice}")
    
    // Get price at specific indices
    val previousPrice = priceHistory.getPriceAtIndex("🚀", 1)
    val twoPointsAgo = priceHistory.getPriceAtIndex("🚀", 2)
    
    println("Previous price (index 1): ${previousPrice?.midPrice}")
    println("Two points ago (index 2): ${twoPointsAgo?.midPrice}")
    
    // Calculate momentum between latest and previous
    if (latestPrice != null && previousPrice != null) {
        println("\n3. Momentum calculations:")
        
        val shortTermMomentum = MomentumCalculator.calculateMomentum(
            latestPrice.midPrice, 
            previousPrice.midPrice
        )
        println("Short-term momentum (latest vs previous): ${String.format("%.4f", shortTermMomentum)} (${String.format("%.2f", shortTermMomentum * 100)}%)")
        
        if (twoPointsAgo != null) {
            val longerTermMomentum = MomentumCalculator.calculateMomentum(
                latestPrice.midPrice,
                twoPointsAgo.midPrice
            )
            println("Longer-term momentum (latest vs 2 points ago): ${String.format("%.4f", longerTermMomentum)} (${String.format("%.2f", longerTermMomentum * 100)}%)")
        }
    }
    
    // Demonstrate error handling
    println("\n4. Error handling demonstration:")
    try {
        MomentumCalculator.calculateMomentum(100.0, 0.0)
    } catch (e: IllegalArgumentException) {
        println("Caught expected error for zero old price: ${e.message}")
    }
    
    try {
        MomentumCalculator.calculateMomentum(100.0, -5.0)
    } catch (e: IllegalArgumentException) {
        println("Caught expected error for negative old price: ${e.message}")
    }
    
    // Show multiple symbols work independently
    println("\n5. Multiple symbols:")
    priceHistory.addPrice("🦄", 50.0)
    priceHistory.addPrice("🦄", 55.5)
    
    println("🚀 history size: ${priceHistory.getPriceHistory("🚀").size}")
    println("🦄 history size: ${priceHistory.getPriceHistory("🦄").size}")
    println("Non-existent symbol history: ${priceHistory.getPriceHistory("❓").size}")
    
    println("\n=== Example completed successfully ===")
}