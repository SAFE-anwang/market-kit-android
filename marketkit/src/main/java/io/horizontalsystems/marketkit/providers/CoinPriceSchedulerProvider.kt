package io.horizontalsystems.marketkit.providers

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

    companion object {
        var isFirstLoad = true
    }

    var dataSource: ICoinPriceCoinUidDataSource? = null

    override val id = "CoinPriceProvider"

    override val lastSyncTimestamp: Long?
        get() = manager.lastSyncTimeStamp(allCoinUids, currencyCode)

    override val expirationInterval: Long
        get() = CoinPrice.expirationInterval

    override val syncSingle: Single<Unit>
        get() {
            val (coinUids, walletUids) = combinedCoinUids
            return if (coinUids.contains("safe-coin") && coinUids.size == 1) {
                provider.getSafeCoinPrices(listOf("safe-anwang"), walletUids, currencyCode)
                    .doOnSuccess {
                        it.forEach { item ->
                            val safeCoinPriceList = mutableListOf<CoinPrice>()
                            // 新增本地safe-erc20、safe-bep20市场价格
                            safeCoinPriceList.add(CoinPrice("safe-coin", item.currencyCode, item.value, item.diff, item.timestamp/1000))
                            manager.handleUpdated(safeCoinPriceList, currencyCode)
                        }
                    }.map {}
            } else {
                val safePrice = if (isFirstLoad) {
                    Single.just(listOf<CoinPrice>()).blockingGet()
                } else {
                    try {
                        provider.getSafeCoinPrices(listOf("safe-anwang"), walletUids, currencyCode).blockingGet()
                    } catch (e: Exception) {
                        Single.just(listOf<CoinPrice>()).blockingGet()
                    }
                }

                provider.getCoinPrices(coinUids, walletUids, currencyCode)
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

    private val allCoinUids: List<String>
        get() = dataSource?.allCoinUids(currencyCode) ?: listOf()

    private val combinedCoinUids: Pair<List<String>, List<String>>
        get() = dataSource?.combinedCoinUids(currencyCode) ?: Pair(listOf(), listOf())

    override fun notifyExpired() {
        manager.notifyExpired(allCoinUids, currencyCode)
    }

    private fun handle(updatedCoinPrices: List<CoinPrice>) {
        manager.handleUpdated(updatedCoinPrices, currencyCode)
    }

}
