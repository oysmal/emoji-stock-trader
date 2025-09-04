# 🚀 Emoji Stock Exchange Workshop - Getting Started

## What You're Building

You'll create a trading bot that autonomously buys and sells emoji stocks (🦄, 💎, ❤️, 🍌, 🍾, 💻) to maximize profit. Your bot will compete against others in real-time on a live exchange.

## How the Exchange Works

- **Starting Position**: $10,000 cash + 100 shares of each emoji symbol
- **Goal**: Maximize your total equity (cash + stock value)
- **Trading**: Submit buy/sell orders via REST API
- **Competition**: Real-time leaderboard shows P&L rankings
- **Rate Limit**: 50 requests/second per team

## Resources

- **API Documentation**: [Swagger UI](http://localhost:8080/docs) - Interactive API explorer
- **Exchange URL**: `http://localhost:8080`
- **Leaderboard**: Check `/leaderboard` endpoint for rankings

## Workshop Tasks

### 1. 🔌 **Registration & Connection** (30 min)
- Register your team with a unique team ID
- Get your API key for authentication
- Test basic connectivity

### 2. 📊 **Submit Your First Orders** (30 min)
- Learn order submission format
- Place buy and sell limit orders
- View orders in the order book

### 3. 💼 **Track Your Portfolio** (30 min)
- Monitor cash and stock positions
- Track trade executions (fills)
- Calculate profit & loss

### 4. 🎯 **Build a Simple Strategy** (30 min)
- Implement buy-low, sell-high logic
- Set target prices for each symbol
- Add position and cash management

### 5. 🏭 **Detect Industrial Orders** (30 min)
- Spot large orders (quantity > 100)
- React quickly to guaranteed profit opportunities
- Compete for the best execution

## Success Tips

- **Start simple** - Get basic trading working first
- **Watch your positions** - Don't overexpose to one symbol
- **Monitor industrial orders** - They're often free money
- **Test frequently** - Use the Swagger UI to experiment
- **Check the leaderboard** - Track your performance vs others

## Available Symbols
- 🦄 Unicorn
- 💎 Diamond  
- ❤️ Heart
- 🍌 Banana
- 🍾 Champagne
- 💻 Computer

**Ready to trade? Let's build your bot! 📈**