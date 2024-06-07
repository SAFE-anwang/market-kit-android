package io.horizontalsystems.marketkit.providers

import io.horizontalsystems.marketkit.SafeExtend.isSafeCoin
import io.horizontalsystems.marketkit.models.*
import io.reactivex.Single
import okhttp3.internal.connection.Exchange
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigDecimal

class CoinGeckoProvider(private val baseUrl: String) {

    private val coinGeckoService: CoinGeckoService by lazy {
        RetrofitUtils.build(baseUrl).create(CoinGeckoService::class.java)
    }

    private val safeService: CoinGeckoService by lazy {
        RetrofitUtils.buildUnsafe("https://safewallet.anwang.com/v1/").create(CoinGeckoService::class.java)
    }

    fun marketTickersSingle(coinGeckoId: String): Single<CoinGeckoCoinResponse> {
        val coinGeckoUid = if (coinGeckoId.isSafeCoin()) "safe-anwang" else coinGeckoId
        return coinGeckoService.marketTickers(
            coinGeckoUid,
            "true",
            "false",
            "false",
            "false",
            "false",
            "false",
        )
    }

    fun getCoinPrice(coinIds: String, currency: String): Single<List<GeckoCoinPriceResponse>> {
        return coinGeckoService.getCoinPrice(coinIds, currency)
    }

    fun marketTickersSingleSafe(coinGeckoId: String): Single<CoinGeckoCoinResponse> {
        val coinGeckoUid = if (coinGeckoId.isSafeCoin()) "safe-anwang" else coinGeckoId
        return safeService.marketTickers(
            coinGeckoUid,
            "true",
            "false",
            "false",
            "false",
            "false",
            "false",
        )
    }


    interface CoinGeckoService {

        @GET("coins/{coinId}")
        fun marketTickers(
            @Path("coinId") coinId: String,
            @Query("tickers") tickers: String,
            @Query("localization") localization: String,
            @Query("market_data") marketData: String,
            @Query("community_data") communityData: String,
            @Query("developer_data") developerData: String,
            @Query("sparkline") sparkline: String,
        ): Single<CoinGeckoCoinResponse>

        @GET("coins/markets")
        fun getCoinPrice (
            @Query("ids") ids: String,
            @Query("vs_currency") currency: String
        ): Single<List<GeckoCoinPriceResponse>>

        @GET("simple/price")
        fun getCoinSimplePrice (
            @Query("ids") ids: String,
            @Query("vs_currencies") currency: String,
            @Query("include_24hr_change") change: Boolean = true
        ): Single<List<GeckoCoinPriceResponse>>

        @GET("coins/{id}/history")
        fun getCoinHistoryPrice (
            @Path("id") coinId: String,
            @Query("date") date: String,
        ): Single<GeckoCoinHistoryPriceResponse>

        @GET("coins/{coinId}")
        fun getMarketInfoOverview(
            @Path("coinId") coinId: String,
            @Query("tickers") tickers: String,
            @Query("localization") localization: String,
            @Query("market_data") marketData: String,
            @Query("community_data") communityData: String,
            @Query("developer_data") developerData: String,
            @Query("sparkline") sparkline: String,
        ): Single<CoinGeckoMarketResponse>

        object Response {
            data class HistoricalMarketData(
                val prices: List<List<BigDecimal>>,
                val market_caps: List<List<BigDecimal>>,
                val total_volumes: List<List<BigDecimal>>,
            )
        }
    }

}
