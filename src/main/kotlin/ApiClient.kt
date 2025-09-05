import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import no.kraftlauget.kiworkshop.utils.RateLimiter

class ApiClient {
    companion object {
        const val baseUrl = "http://localhost:8080"
    }
    
    private var teamId: String? = null
    private var apiKey: String? = null
    private val logger = KotlinLogging.logger {}
    
    // Rate limiter with dedicated CoroutineScope
    private val rateLimiterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rateLimiter = RateLimiter(rateLimiterScope)
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }
    
    suspend fun register(teamId: String): RegisterResponse {
        logger.info { "Registering team: $teamId" }
        
        val response = httpClient.post("$baseUrl/v1/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(teamId))
        }
        
        if (response.status.isSuccess()) {
            val registerResponse: RegisterResponse = response.body()
            
            // Store credentials for future API calls
            this.teamId = registerResponse.teamId
            this.apiKey = registerResponse.apiKey
            
            logger.info { "Successfully registered team: ${registerResponse.teamId}" }
            return registerResponse
        } else {
            val errorResponse: ErrorResponse = response.body()
            throw Exception("Registration failed: ${errorResponse.error}")
        }
    }
    
    suspend fun getPortfolio(): PortfolioResponse {
        if (teamId == null || apiKey == null) {
            throw IllegalStateException("Not authenticated. Please register first.")
        }
        
        rateLimiter.acquirePermit()
        logger.info { "Fetching portfolio for team: $teamId" }
        
        val response = httpClient.get("$baseUrl/v1/portfolio/$teamId") {
            header("X-Team-Id", teamId)
            header("X-Api-Key", apiKey)
        }
        
        if (response.status.isSuccess()) {
            val portfolioResponse: PortfolioResponse = response.body()
            logger.info { "Successfully fetched portfolio. Equity: ${portfolioResponse.equity}" }
            return portfolioResponse
        } else {
            val errorResponse: ErrorResponse = response.body()
            throw Exception("Failed to fetch portfolio: ${errorResponse.error}")
        }
    }
    
    suspend fun getOrderBook(symbol: String): OrderBookResponse {
        if (teamId == null || apiKey == null) {
            throw IllegalStateException("Not authenticated. Please register first.")
        }
        
        rateLimiter.acquirePermit()
        logger.info { "Fetching orderbook for symbol: $symbol" }
        
        val response = httpClient.get("$baseUrl/v1/orderbook") {
            parameter("symbol", symbol)
            header("X-Team-Id", teamId)
            header("X-Api-Key", apiKey)
        }
        
        if (response.status.isSuccess()) {
            val orderBookResponse: OrderBookResponse = response.body()
            logger.info { "Successfully fetched orderbook for $symbol. Bids: ${orderBookResponse.bids.size}, Asks: ${orderBookResponse.asks.size}" }
            return orderBookResponse
        } else {
            val errorResponse: ErrorResponse = response.body()
            throw Exception("Failed to fetch orderbook: ${errorResponse.error}")
        }
    }
    
    fun getCurrentSpread(orderBook: OrderBookResponse): Double? {
        if (orderBook.bids.isEmpty() || orderBook.asks.isEmpty()) {
            return null
        }
        
        val bestBid = orderBook.bids[0].price
        val bestAsk = orderBook.asks[0].price
        
        return bestAsk - bestBid
    }
    
    suspend fun placeOrder(request: PlaceOrderRequest): OrderResponse {
        if (teamId == null || apiKey == null) {
            throw IllegalStateException("Not authenticated. Please register first.")
        }
        
        rateLimiter.acquirePermit()
        logger.info { "Placing order: ${request.side} ${request.quantity} ${request.symbol}" }
        
        val response = httpClient.post("$baseUrl/v1/orders") {
            contentType(ContentType.Application.Json)
            header("X-Team-Id", teamId)
            header("X-Api-Key", apiKey)
            setBody(request)
        }
        
        if (response.status.isSuccess()) {
            val orderResponse: OrderResponse = response.body()
            logger.info { "Successfully placed order: ${orderResponse.orderId} for ${request.symbol}" }
            return orderResponse
        } else {
            val errorResponse: ErrorResponse = response.body()
            throw Exception("Failed to place order: ${errorResponse.error}")
        }
    }
    
    fun close() {
        rateLimiter.close()
        rateLimiterScope.cancel()
        httpClient.close()
    }
}