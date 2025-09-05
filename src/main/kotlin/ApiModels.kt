import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val teamId: String
)

@Serializable
data class RegisterResponse(
    val teamId: String,
    val apiKey: String,
    val initialCash: Double
)

@Serializable
data class PortfolioResponse(
    val teamId: String,
    val cash: Double,
    val positions: Map<String, Int>,
    val equity: Double
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String? = null
)

@Serializable
data class OrderBookLevel(
    val price: Double,
    val quantity: Int,
    val timestamp: String
)

@Serializable
data class OrderBookResponse(
    val symbol: String,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val timestamp: String
)