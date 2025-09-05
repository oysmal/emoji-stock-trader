package no.kraftlauget.kiworkshop.services

import OrderResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Manages active orders with thread-safe storage and status tracking.
 * Uses OrderResponse's built-in status field for state management.
 */
class OrderManager {
    private val orders = ConcurrentHashMap<String, OrderResponse>()

    /**
     * Tracks an order in the manager.
     * Logs WARN for duplicate orders, INFO for successful tracking.
     */
    fun trackOrder(orderResponse: OrderResponse) {
        require(orderResponse.orderId.isNotBlank()) { "Order ID cannot be blank" }
        
        val existingOrder = orders.put(orderResponse.orderId, orderResponse)
        
        if (existingOrder != null) {
            logger.warn { "Duplicate order tracking for orderId: ${orderResponse.orderId}" }
        } else {
            logger.info { "Tracking order: ${orderResponse.orderId} (${orderResponse.symbol} ${orderResponse.side} ${orderResponse.quantity}@${orderResponse.limitPrice})" }
        }
        
        logger.debug { "Full OrderResponse: $orderResponse" }
    }

    /**
     * Removes an order from tracking and returns it.
     * Returns null if order not found.
     */
    fun removeOrder(orderId: String): OrderResponse? {
        require(orderId.isNotBlank()) { "Order ID cannot be blank" }
        
        val removedOrder = orders.remove(orderId)
        
        if (removedOrder != null) {
            logger.info { "Removed order: $orderId" }
        }
        
        return removedOrder
    }

    /**
     * Returns an immutable snapshot of all tracked orders.
     * Note: Returns ALL tracked orders regardless of their status.
     */
    fun getAllTrackedOrders(): Map<String, OrderResponse> {
        return orders.toMap()
    }
}