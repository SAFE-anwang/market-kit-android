package io.horizontalsystems.marketkit.providers

import android.content.Context
import io.horizontalsystems.marketkit.SafeExtend
import io.horizontalsystems.marketkit.SafeExtend.isSafeCoin
import io.horizontalsystems.marketkit.managers.CoinPriceManager
import io.horizontalsystems.marketkit.managers.ICoinPriceCoinUidDataSource
import io.horizontalsystems.marketkit.models.CoinPrice
import io.horizontalsystems.marketkit.storage.MarketDatabase
import java.math.BigDecimal

interface ISchedulerProvider {
    val id: String
    val lastSyncTimestamp: Long?
    val expirationInterval: Long

    suspend fun sync()
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

    override suspend fun sync() {
        val coinUids = allCoinUids

        // Always persist Safe / Safe4 native price first (separate Safe API), even if HS fails.
        persistSafePrices()

        // Single Safe-family wallet: Safe API already covered prices — skip HS.
        if (coinUids.size == 1 && coinUids[0].isSafeCoin()) {
            return
        }

        // HS only for non-Safe uids (custom-safe4 / safe4-coin break HS query)
        val hsUids = coinUids.filterNot { it.isSafeCoin() || it.startsWith("custom-") }
        val priceList = mutableListOf<CoinPrice>()
        if (hsUids.isNotEmpty()) {
            try {
                val marketPrices = provider.getCoinPrices(hsUids, currencyCode)
                marketPrices.findLast { it.coinUid.lowercase() == "tether" }?.let {
                    val ts = normalizeUnixSeconds(it.timestamp)
                    // Keep USDT peg for Safe4USDT on mainnet; testnet zeroes in persistSafePrices
                    if (!SafeExtend.isSafe4TestNet) {
                        priceList.add(
                            CoinPrice("Safe4USDT", it.currencyCode, it.value, it.diff24h, it.diff1d, ts)
                        )
                    }
                }
                priceList.addAll(marketPrices)
            } catch (_: Exception) {
                // Safe prices already persisted; HS failure must not wipe Safe4 native price
            }
        }
        if (priceList.isNotEmpty()) {
            handle(priceList)
        }
    }

    /**
     * Fan-out safewallet.anwang.com/v1/coins?uids=safe-anwang into safe-coin / safe4-coin / bridges.
     * Native Safe4 always gets the live market price (same as BEP20/ERC20 SAFE).
     */
    private suspend fun persistSafePrices() {
        val safePrice = fetchSafePrice() ?: return
        // Safe API last_updated is often hours old; use "now" so CoinPrice.expired is false
        val apiTs = normalizeUnixSeconds(safePrice.timestamp)
        val now = System.currentTimeMillis() / 1000
        val ts = if (now - apiTs > CoinPrice.expirationInterval) now else apiTs
        val value = safePrice.value
        val diff24h = safePrice.diff24h
        val diff1d = safePrice.diff1d
        val code = safePrice.currencyCode.ifBlank { currencyCode }

        val list = mutableListOf(
            CoinPrice("safe-coin", code, value, diff24h, diff1d, ts),
            CoinPrice("safe4-coin", code, value, diff24h, diff1d, ts),
            CoinPrice(SafeExtend.SAFE4_ERC_COIN_UID, code, value, diff24h, diff1d, ts),
            CoinPrice(SafeExtend.SAFE4_MATIC_COIN_UID, code, value, diff24h, diff1d, ts),
            CoinPrice(SafeExtend.SAFE4_BEP20_COIN_UID, code, value, diff24h, diff1d, ts),
        )
        if (SafeExtend.isSafe4TestNet) {
            // Only zero Safe4USDT on testnet; native SAFE keeps market price for fiat display
            list.add(CoinPrice("Safe4USDT", code, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, ts))
        }

        manager.handleUpdated(list, currencyCode)
        saveSafePrice(
            value.toPlainString(),
            diff24h?.toPlainString() ?: "0",
            diff1d?.toPlainString() ?: "0",
            ts
        )
    }

    private suspend fun fetchSafePrice(): CoinPrice? {
        if (isInit || isFirstLoad) {
            if (!isInit) {
                isFirstLoad = false
            }
            if (isInit) {
                isInit = false
            }
            // Prefer live Safe API even on first load; fall back to cache/default
            return try {
                provider.getSafeCoinPrices(listOf("safe-anwang"), currencyCode).firstOrNull()
                    ?: getSafeCoinPrice()
            } catch (_: Exception) {
                getSafeCoinPrice()
            }
        }
        return try {
            provider.getSafeCoinPrices(listOf("safe-anwang"), currencyCode).firstOrNull()
                ?: getSafeCoinPrice()
        } catch (_: Exception) {
            // Keep writing safe4-coin from cache so native Safe4 never stays N/A
            getSafeCoinPrice()
        }
    }

    private val allCoinUids: List<String>
        get() = dataSource?.allCoinUids(currencyCode) ?: listOf()

    override fun notifyExpired() {
        manager.notifyExpired(allCoinUids, currencyCode)
    }

    private fun handle(updatedCoinPrices: List<CoinPrice>) {
        val temp = updatedCoinPrices.map { coinPrice ->
            if (SafeExtend.deployCoinHash.containsValue(coinPrice.coinUid)) {
                val key = SafeExtend.deployCoinHash.filter { it.value == coinPrice.coinUid }.keys
                coinPrice.copy(coinUid = key.first())
            } else {
                coinPrice
            }
        }
        manager.handleUpdated(temp, currencyCode)
    }

    private fun getSafeCoinPrice(): CoinPrice {
        return manager.coinPrice("safe-coin", currencyCode)
            ?: manager.coinPrice("safe-coin", "USD")
            ?: getDefaultSafePrice()
    }

    /** Accept either unix seconds or millis from Safe / HS APIs. */
    private fun normalizeUnixSeconds(timestamp: Long): Long =
        if (timestamp > 10_000_000_000L) timestamp / 1000 else timestamp

    private fun saveSafePrice(price: String, diff: String, diff1d: String, time: Long) {
        val sp = MarketDatabase.application?.getSharedPreferences("safe_price.xml", Context.MODE_PRIVATE) ?: return
        val editor = sp.edit()
        editor.putString("price", price)
        editor.putString("diff", diff)
        editor.putString("diff1d", diff1d)
        editor.putLong("time", time)
        editor.commit()
    }

    private fun getDefaultSafePrice(): CoinPrice {
        val sp = MarketDatabase.application?.getSharedPreferences("safe_price.xml", Context.MODE_PRIVATE)
        val price = sp?.getString("price", "3.28741459") ?: "3.28741459"
        val diff = sp?.getString("diff", "-6.42345300") ?: "-6.42345300"
        val diff1d = sp?.getString("diff1d", "-6.42345300") ?: "-6.42345300"
        val time = sp?.getLong("time", (System.currentTimeMillis() - 24 * 60 * 60 * 1000) / 1000)
            ?: ((System.currentTimeMillis() - 24 * 60 * 60 * 1000) / 1000)
        val priceBd = price.toBigDecimalOrNull() ?: BigDecimal("3.28741459")
        val diffBd = diff.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val diff1dBd = diff1d.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return CoinPrice(
            "safe-coin", currencyCode,
            priceBd,
            diffBd,
            diff1dBd,
            normalizeUnixSeconds(time)
        )
    }
}
