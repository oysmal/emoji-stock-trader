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
    val orderCount: Int
)

@Serializable
data class OrderBookResponse(
    val symbol: String,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val timestamp: String
)

@Serializable
enum class OrderSide {
    BUY, SELL
}

@Serializable
enum class OrderType {
    MARKET, LIMIT
}

@Serializable
enum class OrderStatus {
    ACCEPTED, PARTIALLY_FILLED, FILLED, CANCELLED
}

@Serializable
data class PlaceOrderRequest(
    val symbol: String,
    val side: OrderSide,
    val quantity: Int,
    val orderType: OrderType,
    val limitPrice: Double
)

@Serializable
data class OrderResponse(
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val quantity: Int,
    val limitPrice: Double,
    val status: OrderStatus,
    val filledQuantity: Int,
    val avgFillPrice: Double? = null,
    val remainingQuantity: Int,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class Fill(
    val fillId: Long,
    val orderId: String,
    val teamId: String,
    val symbol: String,
    val side: OrderSide,
    val quantity: Int,
    val price: Double,
    val timestamp: String,
    val seq: Int
)

@Serializable
data class FillsResponse(
    val fills: List<Fill>,
    val nextSince: Long
)