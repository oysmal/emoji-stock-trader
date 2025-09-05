package no.kraftlauget.kiworkshop.services

import ApiClient
import FillsResponse
import Fill
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.atomic.AtomicReference

/**
 * Detects and processes fills by polling the API every 5 seconds.
 * Removes filled orders from OrderManager and notifies PositionTracker of fills.
 */
class FillDetector(
    private val apiClient: ApiClient,
    private val orderManager: OrderManager,
    private val positionTracker: PositionTracker
) {
    private val logger = KotlinLogging.logger {}
    private val fillDetectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pollingJob = AtomicReference<Job?>(null)
    private val lastSince = AtomicReference<Long?>(null)
    
    /**
     * Starts the fill detection polling process.
     * Safe to call multiple times - will not start duplicate polling.
     */
    fun start() {
        if (pollingJob.get()?.isActive == true) {
            logger.warn { "Fill detector is already running" }
            return
        }
        
        logger.info { "Starting fill detector - polling every 5 seconds" }
        
        pollingJob.set(fillDetectorScope.launch {
            while (isActive) {
                try {
                    pollForFills()
                    delay(5.seconds)
                } catch (e: CancellationException) {
                    logger.info { "Fill detector polling cancelled" }
                    break
                } catch (e: Exception) {
                    logger.error(e) { "Error during fill polling, will retry in 10 seconds" }
                    delay(10.seconds)
                }
            }
        })
    }
    
    /**
     * Stops the fill detection polling process.
     * Safe to call multiple times.
     */
    fun stop() {
        logger.info { "Stopping fill detector" }
        pollingJob.get()?.cancel()
        pollingJob.set(null)
    }
    
    /**
     * Polls the API for new fills and processes them.
     */
    private suspend fun pollForFills() {
        try {
            val fillsResponse: FillsResponse = apiClient.getFills(since = lastSince.get())
            
            if (fillsResponse.fills.isNotEmpty()) {
                logger.info { "Received ${fillsResponse.fills.size} new fills" }
                
                fillsResponse.fills.forEach { fill ->
                    processFill(fill)
                }
            } else {
                logger.debug { "No new fills received" }
            }
            
            // Update lastSince for next poll
            lastSince.set(fillsResponse.nextSince)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch fills from API" }
            throw e // Re-throw to trigger retry logic in start()
        }
    }
    
    /**
     * Processes a single fill by removing the corresponding order from OrderManager
     * and notifying PositionTracker to update position.
     * Handles duplicate fills gracefully by ignoring them if order already removed.
     */
    private suspend fun processFill(fill: Fill) {
        logger.info { "Processing fill: ${fill.fillId} for order ${fill.orderId} (${fill.symbol} ${fill.side} ${fill.quantity}@${fill.price})" }
        
        // Remove order from tracking
        val removedOrder = orderManager.removeOrder(fill.orderId)
        
        if (removedOrder != null) {
            logger.info { "Successfully removed filled order ${fill.orderId} from tracking" }
        } else {
            logger.debug { "Order ${fill.orderId} was not being tracked (likely already removed) - ignoring duplicate fill" }
        }
        
        // Notify position tracker of the fill (regardless of whether order was tracked)
        positionTracker.onFillReceived(fill)
    }
    
    /**
     * Closes the fill detector and cancels all operations.
     */
    fun close() {
        stop()
        fillDetectorScope.cancel()
        logger.info { "Fill detector closed" }
    }
}