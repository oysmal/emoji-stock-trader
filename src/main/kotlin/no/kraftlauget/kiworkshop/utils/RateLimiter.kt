package no.kraftlauget.kiworkshop.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration.Companion.seconds

/**
 * Rate limiter that allows 40 requests per second with bucket refresh strategy.
 * All 40 permits are refreshed every second regardless of current usage.
 */
class RateLimiter(
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}
    private val semaphore = Semaphore(40) // Start with 40 permits available
    private val refreshJob: Job
    
    private var statusLogCounter = 0
    private var totalBlockedRequests = 0L
    
    init {
        logger.info { "RateLimiter initialized with 40 permits, starting refresh timer" }
        refreshJob = startRefreshTimer()
    }
    
    /**
     * Acquire a permit for making a request.
     * Blocks if no permits are available until the next refresh cycle.
     */
    suspend fun acquirePermit() {
        val availablePermits = semaphore.availablePermits
        
        if (availablePermits == 0) {
            logger.warn { "Rate limit hit - no permits available, waiting for refresh" }
            totalBlockedRequests++
        }
        
        semaphore.acquire()
        logger.debug { "Permit acquired, ${semaphore.availablePermits} permits remaining" }
    }
    
    private fun startRefreshTimer(): Job = scope.launch {
        try {
            while (true) {
                delay(1.seconds)
                refreshPermits()
                logStatusPeriodically()
            }
        } catch (e: CancellationException) {
            logger.info { "Rate limiter refresh timer cancelled" }
            throw e
        }
    }
    
    private fun refreshPermits() {
        val currentAvailable = semaphore.availablePermits
        val permitsToRelease = 40 - currentAvailable
        
        if (permitsToRelease > 0) {
            repeat(permitsToRelease) {
                semaphore.release()
            }
        }
        
        logger.debug { "Permits refreshed: released $permitsToRelease permits, available=${semaphore.availablePermits}" }
    }
    
    private fun logStatusPeriodically() {
        statusLogCounter++
        if (statusLogCounter >= 10) { // Log every 10 seconds
            val availablePermits = semaphore.availablePermits
            val usedPermits = 40 - availablePermits
            
            logger.info { 
                "Rate limiter status: $usedPermits/40 permits used, $availablePermits available, " +
                "total blocked requests: $totalBlockedRequests" 
            }
            
            statusLogCounter = 0
        }
    }
    
    /**
     * Get current available permits count for monitoring.
     */
    fun availablePermits(): Int = semaphore.availablePermits
    
    /**
     * Get total number of requests that were blocked due to rate limiting.
     */
    fun totalBlockedRequests(): Long = totalBlockedRequests
    
    /**
     * Cancel the refresh timer and cleanup resources.
     */
    fun close() {
        logger.info { "Closing rate limiter, cancelling refresh timer" }
        refreshJob.cancel()
    }
}