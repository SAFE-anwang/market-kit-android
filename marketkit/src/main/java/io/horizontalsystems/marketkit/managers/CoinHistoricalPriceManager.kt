package io.horizontalsystems.marketkit.managers

import io.horizontalsystems.marketkit.ProviderError
import io.horizontalsystems.marketkit.SafeExtend.isSafeCoin
import io.horizontalsystems.marketkit.models.CoinHistoricalPrice
import io.horizontalsystems.marketkit.providers.HsProvider
import io.horizontalsystems.marketkit.storage.CoinHistoricalPriceStorage
import io.reactivex.Single
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class CoinHistoricalPriceManager(
    private val storage: CoinHistoricalPriceStorage,
    private val hsProvider: HsProvider,
    private val isSafe4TestNet: Boolean
) {

    fun coinHistoricalPriceSingle(
        coinUid: String,
        currencyCode: String,
        timestamp: Long
    ): Single<BigDecimal> {

        storage.coinPrice(coinUid, currencyCode, timestamp)?.let {
            if (isSafe4TestNet) {
                Single.just(BigDecimal("0"))
            } else {
                return Single.just(it.value)
            }
        }
        val calendar = Calendar.getInstance()
        calendar.set(2022, 6, 28)
        val timestampTmp = calendar.timeInMillis
        // 从 2022-7-28开始，safe的价格从coin gecko获取
        if (coinUid.isSafeCoin() && timestamp * 1000 >= timestampTmp) {
            val date = SimpleDateFormat("dd-MM-yyyy").format(Date(timestamp * 1000))
            val coinId = "safe-anwang"

            return hsProvider.getCoinGeckoHistoryPrice(coinId, date)
                .flatMap { response ->
                    if (response.marketData.currentPrice.containsKey(currencyCode.lowercase())) {
                        val price = response.marketData.currentPrice[currencyCode.lowercase()]
                        price?.let {
                            val coinHistoricalPrice = if (isSafe4TestNet) {
                                CoinHistoricalPrice(coinUid, currencyCode, BigDecimal("0"), timestamp)
                            } else {
                                CoinHistoricalPrice(coinUid, currencyCode, price, timestamp)
                            }
                            storage.save(coinHistoricalPrice)
                        }
                        if (isSafe4TestNet) {
                            Single.just(BigDecimal("0"))
                        } else {
                            Single.just(price)
                        }
                    } else {
                        Single.error(ProviderError.NoDataForCoin())
                    }
                }
        }
        return hsProvider.historicalCoinPriceSingle(coinUid, currencyCode, timestamp)
            .flatMap { response ->
                if (abs(timestamp - response.timestamp) < 24 * 60 * 60) {
                    val coinHistoricalPrice = CoinHistoricalPrice(coinUid, currencyCode, response.price, timestamp)
                    storage.save(coinHistoricalPrice)
                    Single.just(response.price)
                } else {
                    Single.error(ProviderError.ReturnedTimestampIsVeryInaccurate())
                }
            }
    }

    fun coinHistoricalPrice(coinUid: String, currencyCode: String, timestamp: Long): BigDecimal? {
        return storage.coinPrice(coinUid, currencyCode, timestamp)?.value
    }

}
