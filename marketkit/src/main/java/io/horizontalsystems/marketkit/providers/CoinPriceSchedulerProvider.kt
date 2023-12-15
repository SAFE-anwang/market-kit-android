package io.horizontalsystems.marketkit.providers

import android.content.Context
import android.util.Log
import io.horizontalsystems.marketkit.managers.CoinPriceManager
import io.horizontalsystems.marketkit.managers.ICoinPriceCoinUidDataSource
import io.horizontalsystems.marketkit.models.CoinPrice
import io.horizontalsystems.marketkit.storage.MarketDatabase
import io.reactivex.Single
import java.math.BigDecimal

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

    private var isInit = true

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
                            saveSafePrice(item.value.toString(), item.diff.toString(), item.timestamp)
                        }
                    }.map {}
            } else {
                val safePrice = if (isInit || isFirstLoad) {
                    if (!isInit) {
                        isFirstLoad = false
                    }
                    if (isInit) {
                        isInit = false
                    }
                    Single.just(listOf<CoinPrice>(getSafeCoinPrice())).blockingGet()
                } else {
                    try {
                        val price = provider.getSafeCoinPrices(listOf("safe-anwang"), walletUids, currencyCode).blockingGet()
                        if (price != null && price.isNotEmpty()) {
                            saveSafePrice(price[0].value.toString(), price[0].diff.toString(), price[0].timestamp)
                        }
                        price
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

    private fun getSafeCoinPrice(): CoinPrice {
        return manager.coinPrice("safe-coin", "USD")
                ?: getDefaultSafePrice()
    }

    private fun saveSafePrice(price: String, diff: String, time: Long) {
        val sp = MarketDatabase.application?.getSharedPreferences("safe_price.xml", Context.MODE_PRIVATE) ?: return
        val editor = sp.edit()
        editor.putString("price", price)
        editor.putString("diff", diff)
        editor.putLong("time", time)
        editor.commit()
    }

    private fun getDefaultSafePrice(): CoinPrice {
        val sp = MarketDatabase.application?.getSharedPreferences("safe_price.xml", Context.MODE_PRIVATE)
        val price = sp?.getString("price", "3.28741459") ?: "3.28741459"
        val diff = sp?.getString("diff", "-6.42345300") ?: "-6.42345300"
        val time = sp?.getLong("time", (System.currentTimeMillis() - 24 * 60 * 60 * 1000) / 1000)
                ?: ((System.currentTimeMillis() - 24 * 60 * 60 * 1000) / 1000)
        return CoinPrice("safe-coin", "USD",
                BigDecimal(price),
                BigDecimal(diff),
                time)
    }


}
