package io.horizontalsystems.marketkit.managers

import io.horizontalsystems.marketkit.ProviderError
import io.horizontalsystems.marketkit.SafeExtend
import io.horizontalsystems.marketkit.SafeExtend.isSafe4Coin
import io.horizontalsystems.marketkit.SafeExtend.isSafeFourCoin
import io.horizontalsystems.marketkit.SafeExtend.isSafeFourCustomCoin
import io.horizontalsystems.marketkit.models.CoinHistoricalPrice
import io.horizontalsystems.marketkit.providers.HsProvider
import io.horizontalsystems.marketkit.storage.CoinHistoricalPriceStorage
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

class CoinHistoricalPriceManager(
    private val storage: CoinHistoricalPriceStorage,
    private val hsProvider: HsProvider,
) {

    suspend fun coinHistoricalPriceSingle(
        coinUid: String,
        currencyCode: String,
        timestamp: Long
    ): BigDecimal {

        storage.coinPrice(coinUid, currencyCode, timestamp)?.let {
            if (coinUid.isSafeFourCoin() && SafeExtend.isSafe4TestNet) {
                return BigDecimal("0")
            } else {
                if (it.value.toString() != "0") {
                    return it.value
                }
            }
        }
        val calendar = Calendar.getInstance()
        calendar.set(2022, 6, 28)
        val timestampTmp = calendar.timeInMillis
        // 从 2022-7-28开始，safe的价格从coin gecko获取
        if (coinUid.isSafe4Coin() && timestamp * 1000 >= timestampTmp) {
            val date = SimpleDateFormat("dd-MM-yyyy").format(Date(timestamp * 1000))
            val coinId = "safe-anwang"

            val response = hsProvider.getCoinGeckoHistoryPrice(coinId, date)
            if (response.marketData.currentPrice.containsKey(currencyCode.lowercase())) {
                val price = response.marketData.currentPrice[currencyCode.lowercase()]
                price?.let {
                    val coinHistoricalPrice = if (coinUid.isSafeFourCoin() && SafeExtend.isSafe4TestNet) {
                        CoinHistoricalPrice(coinUid, currencyCode, BigDecimal("0"), timestamp)
                    } else {
                        CoinHistoricalPrice(coinUid, currencyCode, price, timestamp)
                    }
                    storage.save(coinHistoricalPrice)
                }
                return if (coinUid.isSafeFourCoin() && SafeExtend.isSafe4TestNet) {
                    BigDecimal("0")
                } else {
                    price ?: throw ProviderError.NoDataForCoin()
                }
            } else {
                throw ProviderError.NoDataForCoin()
            }
        }
        val uid = if (coinUid.isSafeFourCustomCoin()) {
            SafeExtend.deployCoinHash[coinUid] ?: coinUid
        } else {
            coinUid
        }

        val response = hsProvider.historicalCoinPriceSingle(uid, currencyCode, timestamp)
        if (abs(timestamp - response.timestamp) < 24 * 60 * 60) {
            val coinHistoricalPrice = CoinHistoricalPrice(coinUid, currencyCode, response.price, timestamp)
            storage.save(coinHistoricalPrice)
            return response.price
        } else {
            throw ProviderError.ReturnedTimestampIsVeryInaccurate()
        }
    }

    fun coinHistoricalPrice(coinUid: String, currencyCode: String, timestamp: Long): BigDecimal? {
        return storage.coinPrice(coinUid, currencyCode, timestamp)?.value
    }

}
