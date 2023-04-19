package io.horizontalsystems.marketkit.providers

import android.util.Log
import io.horizontalsystems.marketkit.managers.CoinPriceManager
import io.horizontalsystems.marketkit.managers.ICoinPriceCoinUidDataSource
import io.horizontalsystems.marketkit.models.CoinPrice
import io.reactivex.Single
import java.util.stream.Collectors

interface ISchedulerProvider {
    val id: String
    val lastSyncTimestamp: Long?
    val expirationInterval: Long
    val syncSingle: Single<Unit>
    val syncGeckoSingle: Single<Unit>

    fun notifyExpired()
}

class CoinPriceSchedulerProvider(
    private val currencyCode: String,
    private val manager: CoinPriceManager,
    private val provider: HsProvider
) : ISchedulerProvider {
    var dataSource: ICoinPriceCoinUidDataSource? = null

    override val id = "CoinPriceProvider"

    override val lastSyncTimestamp: Long?
        get() = manager.lastSyncTimeStamp(coinUids, currencyCode)

    override val expirationInterval: Long
        get() = CoinPrice.expirationInterval

    override val syncSingle: Single<Unit>
        get() {
            return if (coinUids.contains("safe-coin") && coinUids.size == 1) {
                provider.getSafeCoinPrices(listOf("safe-anwang"), currencyCode)
                    .doOnSuccess {
                        it.forEach { item ->
                            val safeCoinPriceList = mutableListOf<CoinPrice>()
                            // 新增本地safe-erc20、safe-bep20市场价格
                            safeCoinPriceList.add(CoinPrice("safe-coin", item.currencyCode, item.value, item.diff, item.timestamp/1000))
                            manager.handleUpdated(safeCoinPriceList, currencyCode)
                        }
                    }.map {}
            } else {
                val safePrice = try {
                    provider.getSafeCoinPrices(listOf("safe-anwang"), currencyCode).blockingGet()
                } catch (e: Exception) {
                    null
                }

                provider.getCoinPrices(coinUids, currencyCode)
                    .doOnSuccess {
                        val priceList = mutableListOf<CoinPrice>()
                        safePrice?.forEach {
                            priceList.add(it.copy(coinUid = "safe-coin"))
                        }
                        priceList.addAll(it)
                        handle(priceList)
                    }.map {}
            }
        }
    override val syncGeckoSingle: Single<Unit>
        get() = provider.getCoinGeckoPrices(listOf("safe-anwang"), currencyCode)
            .doOnSuccess {
                it.stream().forEach { item ->
                    if (item.coinUid == "safe-anwang") {
                        val safeCoinPriceList = mutableListOf<CoinPrice>()
                        // 新增本地safe-erc20、safe-bep20市场价格
                        safeCoinPriceList.add(CoinPrice("safe-coin", item.currencyCode, item.value, item.diff, item.timestamp/1000))
                        safeCoinPriceList.add(CoinPrice("custom_safe-erc20-SAFE", item.currencyCode, item.value, item.diff, item.timestamp/1000))
                        safeCoinPriceList.add(CoinPrice("custom_safe-bep20-SAFE", item.currencyCode, item.value, item.diff, item.timestamp/1000))
                        manager.handleUpdated(safeCoinPriceList, currencyCode)
                    }
                }
            }.map {}

    private val coinUids: List<String>
        get() = dataSource?.coinUids(currencyCode) ?: listOf()

    override fun notifyExpired() {
        manager.notifyExpired(coinUids, currencyCode)
    }

    private fun handle(updatedCoinPrices: List<CoinPrice>) {
        manager.handleUpdated(updatedCoinPrices, currencyCode)
    }

}
