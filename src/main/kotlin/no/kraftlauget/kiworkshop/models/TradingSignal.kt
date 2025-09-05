package no.kraftlauget.kiworkshop.models

import OrderSide

/**
 * Represents a trading signal with side, confidence, and reasoning.
 * 
 * @param side The trading side (BUY or SELL)
 * @param confidence The absolute momentum value indicating signal strength
 * @param reason Human-readable explanation of why this signal was generated
 */
data class TradingSignal(
    val side: OrderSide,
    val confidence: Double,
    val reason: String
)