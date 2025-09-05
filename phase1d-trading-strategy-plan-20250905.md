# Phase 1D: Momentum Trading Strategy - Implementation Plan

## Executive Summary

Implement absolute momentum trading strategy with 2% threshold, in-memory price history, and immediate trade execution. Build on existing ApiClient, OrderManager, and PositionTracker components.

## Implementation Steps

### Step 1: Price History Service [2 hours]

**Component**: `PriceHistoryService`

- **What**: Track price history for momentum calculations
- **Why**: Enable momentum signal generation
- **How**:
  - Create circular buffer storing last 50 price points per symbol
  - Store timestamp, symbol, price for each entry
  - Provide getCurrentPrice() and getOldPrice(minutesBack) functions
  - Thread-safe concurrent access with simple synchronization
- **Dependencies**: None (standalone service)
- **Effort**: 2 hours
- **Test Criteria**: Can store/retrieve prices, handles concurrent access, maintains size limit

### Step 2: Momentum Calculator [1 hour]

**Component**: `MomentumCalculator`

- **What**: Calculate absolute momentum using price history
- **Why**: Generate buy/sell signals
- **How**:
  - Single function: `calculateMomentum(symbol, minutesBack): Double?`
  - Formula: `(current - old) / old`
  - Return null if insufficient data
  - No state, pure calculation function
- **Dependencies**: PriceHistoryService
- **Effort**: 1 hour
- **Test Criteria**: Correct momentum calculation, handles missing data gracefully

### Step 3: Trading Signal Generator [2 hours]

**Component**: `TradingSignalGenerator`

- **What**: Generate BUY/SELL/HOLD signals from momentum
- **Why**: Translate momentum into trading decisions
- **How**:
  - Check momentum >= 2% → BUY signal
  - Check momentum <= -2% → SELL signal (if position exists)
  - Otherwise → HOLD
  - Include position awareness via PositionTracker
- **Dependencies**: MomentumCalculator, PositionTracker
- **Effort**: 2 hours
- **Test Criteria**: Correct signals for various momentum values, respects position limits

### Step 4: Main Trading Loop Integration [3 hours]

**Component**: `TradingBot` enhancement

- **What**: Integrate strategy into existing main loop
- **Why**: Execute complete trading workflow
- **How**:
  - Update main loop to call PriceHistoryService on each price fetch
  - Generate trading signals after price history update
  - Execute trades immediately via OrderManager when signals trigger
  - Continue with stale data on API failures (don't update history)
  - Maintain existing stop conditions (10 orders OR 2 hours)
- **Dependencies**: All previous components, existing ApiClient/OrderManager
- **Effort**: 3 hours
- **Test Criteria**: Complete workflow runs, trades execute on signals, graceful API failure handling

### Step 5: Enhanced Logging & Monitoring [1 hour]

**Component**: Logging enhancements

- **What**: Add strategy-specific logging
- **Why**: Track momentum signals and trading decisions
- **How**:
  - Log momentum values and signals generated
  - Log trading decisions with reasoning
  - Log price history updates and failures
  - Maintain existing structured logging format
- **Dependencies**: All components
- **Effort**: 1 hour
- **Test Criteria**: Clear logs show strategy operation, easy to debug

## Technical Specifications

### Data Structures

```kotlin
data class PricePoint(
    val timestamp: Instant,
    val symbol: String,
    val price: Double
)

enum class TradingSignal { BUY, SELL, HOLD }

data class SignalContext(
    val signal: TradingSignal,
    val momentum: Double?,
    val currentPrice: Double,
    val oldPrice: Double?,
    val reason: String
)
```

### Key Parameters

- **Momentum Threshold**: 2% (0.02)
- **Price History Lookback**: 5 minutes
- **History Buffer Size**: 50 entries per symbol
- **Order Size**: $100 (existing configuration)
- **Stop Conditions**: 10 orders OR 2 hours (existing)

### Integration Points

- **ApiClient**: Existing price fetching (no changes needed)
- **OrderManager**: Existing order placement (no changes needed)  
- **PositionTracker**: Existing position management (no changes needed)
- **Main Loop**: Enhanced with strategy components

### Error Handling Strategy

- **API Failures**: Continue with last known prices, don't update history
- **Insufficient Data**: Skip momentum calculation, continue monitoring
- **Order Failures**: Log error, continue strategy execution
- **All Failures**: Graceful degradation, maintain system stability

## Success Criteria

- [ ] Price history accurately maintained in memory
- [ ] Momentum calculated correctly (2% absolute threshold)
- [ ] Trading signals generated based on momentum + position state
- [ ] At least 1 trade executed during 2-hour test run
- [ ] System continues operating through API failures
- [ ] Clear logs show strategy decision-making process
- [ ] No system crashes or unhandled exceptions

## Next Steps After Implementation

1. Run 2-hour test session
2. Analyze trading performance and logs
3. Measure: trades executed, momentum accuracy, API failure resilience
4. Document lessons learned for Phase 2 strategy enhancements

---

**Target Outcome**: Execute at least 1 profitable momentum-based trade within 2-hour test window while maintaining system reliability.