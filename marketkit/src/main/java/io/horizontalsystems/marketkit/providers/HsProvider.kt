package io.horizontalsystems.marketkit.providers

import com.google.gson.annotations.SerializedName
import io.horizontalsystems.marketkit.models.*
import io.reactivex.Single
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.math.BigDecimal
import java.util.*

class HsProvider(baseUrl: String, apiKey: String, appVersion: String, appId: String?) {

    private val service by lazy {
        val headerMap = mutableMapOf<String, String>()
        headerMap["app_platform"] = "android"
        headerMap["app_version"] = appVersion
        appId?.let {
            headerMap["app_id"] = it
        }
        headerMap["apikey"] = apiKey

        RetrofitUtils.build("${baseUrl}/v1/", headerMap)
            .create(MarketService::class.java)
    }

    private val safeService by lazy {
        RetrofitUtils.buildUnsafe("https://safewallet.anwang.com/v1/")
            .create(MarketService::class.java)
    }

    private val coinGeckoService by lazy {
        RetrofitUtils.build("https://api.coingecko.com/api/v3/").create(CoinGeckoProvider.CoinGeckoService::class.java)
    }

    fun marketInfosSingle(
        top: Int,
        currencyCode: String,
        defi: Boolean,
        apiTag: String,
    ): Single<List<MarketInfoRaw>> {
        return service.getMarketInfos(
            apiTag = apiTag,
            top = top,
            currencyCode = currencyCode,
            defi = defi
        )
    }

    fun advancedMarketInfosSingle(
        top: Int,
        currencyCode: String,
    ): Single<List<MarketInfoRaw>> {
        return service.getAdvancedMarketInfos(
            apiTag = "advanced_search",
            top = top,
            currencyCode = currencyCode
        )
    }

    fun marketInfosSingle(
        coinUids: List<String>,
        currencyCode: String,
        apiTag: String,
    ): Single<List<MarketInfoRaw>> {
        return service.getMarketInfos(
            apiTag = apiTag,
            uids = coinUids.joinToString(","),
            currencyCode = currencyCode
        )
    }

    fun marketSafeInfosSingle(
        coinUids: List<String>,
        currencyCode: String,
        apiTag: String,
    ): Single<List<MarketInfoRaw>> {
        return safeService.getMarketInfos(
            apiTag = apiTag,
            uids = coinUids.joinToString(","),
            currencyCode = currencyCode
        )
    }

    fun marketInfosSingle(
        categoryUid: String,
        currencyCode: String,
        apiTag: String
    ): Single<List<MarketInfoRaw>> {
        return service.getMarketInfosByCategory(
            apiTag = apiTag,
            categoryUid = categoryUid,
            currencyCode = currencyCode
        )
    }

    fun getCoinCategories(currencyCode: String): Single<List<CoinCategory>> {
        return service.getCategories(currencyCode)
    }

    fun coinCategoryMarketPointsSingle(
        categoryUid: String,
        timePeriod: HsTimePeriod,
        currencyCode: String,
    ): Single<List<CoinCategoryMarketPoint>> {
        return service.coinCategoryMarketPoints(categoryUid, timePeriod.value, currencyCode)
    }

    fun getCoinPrices(
        coinUids: List<String>,
        walletCoinUids: List<String>,
        currencyCode: String
    ): Single<List<CoinPrice>> {
        val additionalParams = mutableMapOf<String, String>()
        if (walletCoinUids.isNotEmpty()) {
            additionalParams["enabled_uids"] = walletCoinUids.joinToString(separator = ",")
        }
        return service.getCoinPrices(
            apiTag = "coin_prices",
            uids = coinUids.joinToString(separator = ","),
            currencyCode = currencyCode,
            additionalParams = additionalParams
        )
            .map { coinPrices ->
                coinPrices.mapNotNull { coinPriceResponse ->
                    coinPriceResponse.coinPrice(currencyCode)
                }
            }
    }

    fun historicalCoinPriceSingle(
        coinUid: String,
        currencyCode: String,
        timestamp: Long
    ): Single<HistoricalCoinPriceResponse> {
        return service.getHistoricalCoinPrice(coinUid, currencyCode, timestamp)
    }

    fun coinPriceChartSingle(
        coinUid: String,
        currencyCode: String,
        periodType: HsPointTimePeriod,
        fromTimestamp: Long?
    ): Single<List<ChartCoinPriceResponse>> {
        return service.getCoinPriceChart(coinUid, currencyCode, fromTimestamp, periodType.value)
    }

    fun coinPriceChartStartTime(coinUid: String): Single<Long> {
        return service.getCoinPriceChartStart(coinUid).map { it.timestamp }
    }

    fun topPlatformMarketCapStartTime(platform: String): Single<Long> {
        return service.getTopPlatformMarketCapStart(platform).map { it.timestamp }
    }

    fun getMarketInfoOverview(
        coinUid: String,
        currencyCode: String,
        language: String,
        apiTag: String,
    ): Single<MarketInfoOverviewRaw> {
        return service.getMarketInfoOverview(
            apiTag = apiTag,
            coinUid = coinUid,
            currencyCode = currencyCode,
            language = language
        )
    }

    fun getGlobalMarketPointsSingle(
        currencyCode: String,
        timePeriod: HsTimePeriod,
    ): Single<List<GlobalMarketPoint>> {
        return service.globalMarketPoints(timePeriod.value, currencyCode)
    }

    fun defiMarketInfosSingle(currencyCode: String, apiTag: String): Single<List<DefiMarketInfoResponse>> {
        return service.getDefiMarketInfos(apiTag = apiTag, currencyCode = currencyCode)
    }

    fun marketInfoTvlSingle(
        coinUid: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<ChartPoint>> {
        return service.getMarketInfoTvl(coinUid, currencyCode, timePeriod.value)
            .map { responseList ->
                responseList.mapNotNull {
                    it.tvl?.let { tvl -> ChartPoint(tvl, it.timestamp, null) }
                }
            }
    }

    fun marketInfoGlobalTvlSingle(
        chain: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<ChartPoint>> {

        return service.getMarketInfoGlobalTvl(
            currencyCode,
            timePeriod.value,
            blockchain = if (chain.isNotBlank()) chain else null
        ).map { responseList ->
            responseList.mapNotNull {
                it.tvl?.let { tvl ->
                    ChartPoint(tvl, it.timestamp, null)
                }
            }
        }
    }

    fun tokenHoldersSingle(
        authToken: String,
        coinUid: String,
        blockchainUid: String
    ): Single<TokenHolders> {
        return service.getTokenHolders(authToken, coinUid, blockchainUid)
    }

    fun coinTreasuriesSingle(coinUid: String, currencyCode: String): Single<List<CoinTreasury>> {
        return service.getCoinTreasuries(coinUid, currencyCode).map { responseList ->
            responseList.mapNotNull {
                try {
                    CoinTreasury(
                        type = CoinTreasury.TreasuryType.fromString(it.type)!!,
                        fund = it.fund,
                        fundUid = it.fundUid,
                        amount = it.amount,
                        amountInCurrency = it.amountInCurrency,
                        countryCode = it.countryCode
                    )
                } catch (exception: Exception) {
                    null
                }
            }
        }
    }

    fun investmentsSingle(coinUid: String): Single<List<CoinInvestment>> {
        return service.getInvestments(coinUid)
    }

    fun coinReportsSingle(coinUid: String): Single<List<CoinReport>> {
        return service.getCoinReports(coinUid)
    }

    fun topPlatformsSingle(currencyCode: String, apiTag: String): Single<List<TopPlatformResponse>> {
        return service.getTopPlatforms(apiTag = apiTag, currencyCode = currencyCode)
    }

    fun topPlatformMarketCapPointsSingle(
        chain: String,
        currencyCode: String,
        periodType: HsPointTimePeriod,
        fromTimestamp: Long?
    ): Single<List<TopPlatformMarketCapPoint>> {
        return service.getTopPlatformMarketCapPoints(chain, currencyCode, fromTimestamp, periodType.value)
    }

    fun topPlatformCoinListSingle(
        chain: String,
        currencyCode: String,
        apiTag: String
    ): Single<List<MarketInfoRaw>> {
        return service.getTopPlatformCoinList(
            apiTag = apiTag,
            chain = chain,
            currencyCode = currencyCode
        )
    }

    fun dexLiquiditySingle(
        authToken: String,
        coinUid: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<Analytics.VolumePoint>> {
        return service.getDexLiquidities(authToken, coinUid, currencyCode, timePeriod.value)
    }

    fun dexVolumesSingle(
        authToken: String,
        coinUid: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<Analytics.VolumePoint>> {
        return service.getDexVolumes(authToken, coinUid, currencyCode, timePeriod.value)
    }

    fun transactionDataSingle(
        authToken: String,
        coinUid: String,
        timePeriod: HsTimePeriod,
        platform: String?
    ): Single<List<Analytics.CountVolumePoint>> {
        return service.getTransactions(authToken, coinUid, timePeriod.value, platform)
    }

    fun activeAddressesSingle(
        authToken: String,
        coinUid: String,
        timePeriod: HsTimePeriod
    ): Single<List<Analytics.CountPoint>> {
        return service.getActiveAddresses(authToken, coinUid, timePeriod.value)
    }

    fun marketOverviewSingle(currencyCode: String): Single<MarketOverviewResponse> {
        return service.getMarketOverview(currencyCode)
    }

    fun marketTickers(coinUid: String): Single<List<MarketTicker>> {
        return if (coinUid == "safe-coin") {
            safeService.getMarketTickers("safe-anwang")
        } else {
            service.getMarketTickers(coinUid)
        }
    }

    fun topMoversRawSingle(currencyCode: String): Single<TopMoversRaw> {
        return service.getTopMovers(currencyCode)
    }

    fun statusSingle(): Single<HsStatus> {
        return service.getStatus()
    }

    fun allCoinsSingle(): Single<List<CoinResponse>> {
        return service.getAllCoins()
    }

    fun allBlockchainsSingle(): Single<List<BlockchainResponse>> {
        return service.getAllBlockchains()
    }

    fun allTokensSingle(): Single<List<TokenResponse>> {
        return service.getAllTokens()
    }

    fun analyticsPreviewSingle(coinUid: String, addresses: List<String>, apiTag: String): Single<AnalyticsPreview> {
        return service.getAnalyticsPreview(
            apiTag = apiTag,
            coinUid = coinUid,
            address = if (addresses.isEmpty()) null else addresses.joinToString(",")
        )
    }

    fun analyticsSingle(
        authToken: String,
        coinUid: String,
        currencyCode: String,
        apiTag: String
    ): Single<Analytics> {
        return service.getAnalyticsData(
            apiTag = apiTag,
            authToken = authToken,
            coinUid = coinUid,
            currencyCode = currencyCode
        )
    }

    fun safeAnalyticsPreviewSingle(coinUid: String, addresses: List<String>, apiTag: String): Single<AnalyticsPreview> {
        return safeService.getAnalyticsPreview(apiTag, coinUid, addresses.joinToString(","))
    }

    fun rankValueSingle(
        authToken: String,
        type: String,
        currencyCode: String
    ): Single<List<RankValue>> {
        return service.getRankValue(authToken, type, currencyCode)
    }

    fun safeAnalyticsSingle(
        authToken: String,
        coinUid: String,
        currencyCode: String,
        apiTag: String): Single<Analytics> {
        return safeService.getAnalyticsData(apiTag, coinUid, currencyCode, authToken)
    }

    fun rankMultiValueSingle(
        authToken: String,
        type: String,
        currencyCode: String
    ): Single<List<RankMultiValue>> {
        return service.getRankMultiValue(authToken, type, currencyCode)
    }

    fun subscriptionsSingle(
        addresses: List<String>
    ): Single<List<SubscriptionResponse>> {
        return service.getSubscriptions(addresses.joinToString(separator = ","))
    }

    fun authGetSignMessage(address: String): Single<String> {
        return service.authGetSignMessage(address)
            .map { it["message"] }
    }

    fun authenticate(signature: String, address: String): Single<String> {
        return service.authenticate(signature, address)
            .map { it["token"] }
    }

    fun requestPersonalSupport(authToken: String, username: String): Single<Response<Void>> {
        return service.requestPersonalSupport(authToken, username)
    }

    fun verifiedExchangeUids(): Single<List<String>> {
        return service.verifiedExchangeUids()
    }

    fun topPairsSingle(currencyCode: String, page: Int, limit: Int): Single<List<TopPair>> {
        return service.getTopPairs(currencyCode, page, limit)
    }


    fun getCoinGeckoPrices(coinUids: List<String>, currencyCode: String): Single<List<CoinPrice>> {
        val result = coinGeckoService.getCoinPrice(coinUids.joinToString(separator = ","), currencyCode)
        return result
                .map { coinPrices ->
                    coinPrices.mapNotNull { coinPriceResponse ->
                        coinPriceResponse.coinPrice(currencyCode)
                    }
                }
    }

    fun getCoinGeckoInfo(coinUids: List<String>, currencyCode: String): Single<List<GeckoCoinPriceResponse>> {
        return coinGeckoService.getCoinPrice(coinUids.joinToString(separator = ","), currencyCode)
    }

    fun getCoinGeckoHistoryPrice(coinUid: String, date: String): Single<GeckoCoinHistoryPriceResponse> {
        return coinGeckoService.getCoinHistoryPrice(coinUid, date)
    }

    fun getCoinGeckoMarketInfoOverview(
            coinUid: String,
    ): Single<CoinGeckoMarketResponse> {
        return coinGeckoService.getMarketInfoOverview(coinUid,
                "false",
                "true",
                "true",
                "false",
                "false",
                "false")
    }

    fun coinSafePriceChartStartTime(coinUid: String): Single<Long> {
        return safeService.getCoinPriceChartStart(coinUid).map { it.timestamp }
    }

    fun getSafeMarketInfoOverview(
            coinUid: String,
            currencyCode: String,
            language: String,
            apiTag: String
    ): Single<MarketInfoOverviewRaw> {
        return safeService.getMarketInfoOverview(apiTag, coinUid, currencyCode, language)
    }

    fun getSafeCoinPrices(
            coinUids: List<String>,
            walletCoinUids: List<String>,
            currencyCode: String
    ): Single<List<CoinPrice>> {
        val additionalParams = mutableMapOf<String, String>()
        if (walletCoinUids.isNotEmpty()) {
            additionalParams["enabled_uids"] = walletCoinUids.joinToString(separator = ",")
        }
        return safeService.getCoinPrices(
                apiTag = "coin_prices",
                uids = coinUids.joinToString(separator = ","),
                currencyCode = currencyCode,
                additionalParams = additionalParams
        ).map { coinPrices ->
            coinPrices.mapNotNull { coinPriceResponse ->
                coinPriceResponse.coinPrice(currencyCode)
            }
        }
    }

    fun coinSafePriceChartSingle(
            coinUid: String,
            currencyCode: String,
            periodType: HsPointTimePeriod,
            fromTimestamp: Long?
    ): Single<List<ChartCoinPriceResponse>> {
        return safeService.getCoinPriceChart(coinUid, currencyCode, fromTimestamp, periodType.value)
    }

    fun getSafeMarketInfoDetails(coinUid: String, currency: String): Single<MarketInfoDetailsResponse> {
        return safeService.getMarketInfoDetails(coinUid, currency)
    }

    private interface MarketService {

        @GET("coins")
        fun getMarketInfos(
            @Header("app_tag") apiTag: String,
            @Query("limit") top: Int,
            @Query("currency") currencyCode: String,
            @Query("defi") defi: Boolean,
            @Query("order_by_rank") orderByRank: Boolean = true,
            @Query("fields") fields: String = marketInfoFields,
        ): Single<List<MarketInfoRaw>>

        @GET("coins/filter")
        fun getAdvancedMarketInfos(
            @Header("app_tag") apiTag: String,
            @Query("limit") top: Int,
            @Query("currency") currencyCode: String,
            @Query("order_by_rank") orderByRank: Boolean = true,
            @Query("page") page: Int = 1,
        ): Single<List<MarketInfoRaw>>

        @GET("coins")
        fun getMarketInfos(
            @Header("app_tag") apiTag: String,
            @Query("uids") uids: String,
            @Query("currency") currencyCode: String,
            @Query("fields") fields: String = marketInfoFields,
        ): Single<List<MarketInfoRaw>>

        @GET("categories/{categoryUid}/coins")
        fun getMarketInfosByCategory(
            @Header("app_tag") apiTag: String,
            @Path("categoryUid") categoryUid: String,
            @Query("currency") currencyCode: String,
        ): Single<List<MarketInfoRaw>>

        @GET("categories")
        fun getCategories(
            @Query("currency") currencyCode: String
        ): Single<List<CoinCategory>>

        @GET("categories/{categoryUid}/market_cap")
        fun coinCategoryMarketPoints(
            @Path("categoryUid") categoryUid: String,
            @Query("interval") interval: String,
            @Query("currency") currencyCode: String,
        ): Single<List<CoinCategoryMarketPoint>>

        @GET("coins")
        fun getCoinPrices(
            @Header("app_tag") apiTag: String,
            @Query("uids") uids: String,
            @Query("currency") currencyCode: String,
            @Query("fields") fields: String = coinPriceFields,
            @QueryMap additionalParams: Map<String, String>,
        ): Single<List<CoinPriceResponse>>

        @GET("coins/{coinUid}/price_history")
        fun getHistoricalCoinPrice(
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String,
            @Query("timestamp") timestamp: Long,
        ): Single<HistoricalCoinPriceResponse>

        @GET("coins/{coinUid}/price_chart")
        fun getCoinPriceChart(
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String,
            @Query("from_timestamp") timestamp: Long?,
            @Query("interval") interval: String,
        ): Single<List<ChartCoinPriceResponse>>

        @GET("coins/{coinUid}/price_chart_start")
        fun getCoinPriceChartStart(
            @Path("coinUid") coinUid: String
        ): Single<ChartStart>

        @GET("coins/{coinUid}")
        fun getMarketInfoOverview(
            @Header("app_tag") apiTag: String,
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String,
            @Query("language") language: String,
        ): Single<MarketInfoOverviewRaw>

        @GET("defi-protocols")
        fun getDefiMarketInfos(
            @Header("app_tag") apiTag: String,
            @Query("currency") currencyCode: String
        ): Single<List<DefiMarketInfoResponse>>

        @GET("coins/{coinUid}/details")
        fun getMarketInfoDetails(
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String
        ): Single<MarketInfoDetailsResponse>

        @GET("analytics/{coinUid}/preview")
        fun getAnalyticsPreview(
            @Header("app_tag") apiTag: String,
            @Path("coinUid") coinUid: String,
            @Query("address") address: String?,
        ): Single<AnalyticsPreview>

        @GET("analytics/{coinUid}")
        fun getAnalyticsData(
            @Header("app_tag") apiTag: String,
            @Header("authorization") authToken: String,
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String,
        ): Single<Analytics>

        @GET("analytics/{coinUid}/dex-liquidity")
        fun getDexLiquidities(
            @Header("authorization") auth: String,
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String,
            @Query("interval") interval: String,
        ): Single<List<Analytics.VolumePoint>>

        @GET("analytics/{coinUid}/dex-volumes")
        fun getDexVolumes(
            @Header("authorization") auth: String,
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String,
            @Query("interval") interval: String
        ): Single<List<Analytics.VolumePoint>>

        @GET("analytics/{coinUid}/transactions")
        fun getTransactions(
            @Header("authorization") auth: String,
            @Path("coinUid") coinUid: String,
            @Query("interval") interval: String,
            @Query("platform") platform: String?
        ): Single<List<Analytics.CountVolumePoint>>

        @GET("analytics/{coinUid}/addresses")
        fun getActiveAddresses(
            @Header("authorization") auth: String,
            @Path("coinUid") coinUid: String,
            @Query("interval") interval: String
        ): Single<List<Analytics.CountPoint>>

        @GET("analytics/{coinUid}/holders")
        fun getTokenHolders(
            @Header("authorization") authToken: String,
            @Path("coinUid") coinUid: String,
            @Query("blockchain_uid") blockchainUid: String
        ): Single<TokenHolders>

        @GET("analytics/ranks")
        fun getRankValue(
            @Header("authorization") authToken: String,
            @Query("type") type: String,
            @Query("currency") currencyCode: String,
        ): Single<List<RankValue>>

        @GET("analytics/ranks")
        fun getRankMultiValue(
            @Header("authorization") authToken: String,
            @Query("type") type: String,
            @Query("currency") currencyCode: String,
        ): Single<List<RankMultiValue>>

        @GET("analytics/subscriptions")
        fun getSubscriptions(
            @Query("address") addresses: String
        ): Single<List<SubscriptionResponse>>

        @GET("defi-protocols/{coinUid}/tvls")
        fun getMarketInfoTvl(
            @Path("coinUid") coinUid: String,
            @Query("currency") currencyCode: String,
            @Query("interval") interval: String
        ): Single<List<MarketInfoTvlResponse>>

        @GET("global-markets/tvls")
        fun getMarketInfoGlobalTvl(
            @Query("currency") currencyCode: String,
            @Query("interval") interval: String,
            @Query("blockchain") blockchain: String?
        ): Single<List<MarketInfoTvlResponse>>

        @GET("funds/treasuries")
        fun getCoinTreasuries(
            @Query("coin_uid") coinUid: String,
            @Query("currency") currencyCode: String
        ): Single<List<CoinTreasuryResponse>>

        @GET("funds/investments")
        fun getInvestments(
            @Query("coin_uid") coinUid: String,
        ): Single<List<CoinInvestment>>

        @GET("reports")
        fun getCoinReports(
            @Query("coin_uid") coinUid: String
        ): Single<List<CoinReport>>

        @GET("global-markets")
        fun globalMarketPoints(
            @Query("interval") timePeriod: String,
            @Query("currency") currencyCode: String,
        ): Single<List<GlobalMarketPoint>>

        @GET("top-platforms")
        fun getTopPlatforms(
            @Header("app_tag") apiTag: String,
            @Query("currency") currencyCode: String
        ): Single<List<TopPlatformResponse>>

        @GET("top-platforms/{platform}/market_chart_start")
        fun getTopPlatformMarketCapStart(
            @Path("platform") platform: String
        ): Single<ChartStart>

        @GET("top-platforms/{platform}/market_chart")
        fun getTopPlatformMarketCapPoints(
            @Path("platform") platform: String,
            @Query("currency") currencyCode: String,
            @Query("from_timestamp") timestamp: Long?,
            @Query("interval") interval: String
        ): Single<List<TopPlatformMarketCapPoint>>

        @GET("top-platforms/{chain}/list")
        fun getTopPlatformCoinList(
            @Header("app_tag") apiTag: String,
            @Path("chain") chain: String,
            @Query("currency") currencyCode: String,
        ): Single<List<MarketInfoRaw>>

        @GET("markets/overview")
        fun getMarketOverview(
            @Query("currency") currencyCode: String,
            @Query("simplified") simplified: Boolean = true
        ): Single<MarketOverviewResponse>

        @GET("exchanges/tickers/{coinUid}")
        fun getMarketTickers(
            @Path("coinUid") coinUid: String
        ): Single<List<MarketTicker>>

        @GET("coins/top-movers")
        fun getTopMovers(
            @Query("currency") currencyCode: String
        ): Single<TopMoversRaw>

        @GET("status/updates")
        fun getStatus(): Single<HsStatus>

        @GET("coins/list")
        fun getAllCoins(): Single<List<CoinResponse>>

        @GET("blockchains/list")
        fun getAllBlockchains(): Single<List<BlockchainResponse>>

        @GET("tokens/list")
        fun getAllTokens(): Single<List<TokenResponse>>

        @GET("auth/get-sign-message")
        fun authGetSignMessage(
            @Query("address") address: String
        ): Single<Map<String, String>>

        @FormUrlEncoded
        @POST("auth/authenticate")
        fun authenticate(
            @Field("signature") signature: String,
            @Field("address") address: String
        ): Single<Map<String, String>>

        @FormUrlEncoded
        @POST("support/start-chat")
        fun requestPersonalSupport(
            @Header("authorization") auth: String,
            @Field("username") username: String,
        ): Single<Response<Void>>

        @GET("exchanges/whitelist")
        fun verifiedExchangeUids(): Single<List<String>>

        @GET("exchanges/top-pairs")
        fun getTopPairs(
            @Query("currency") currencyCode: String,
            @Query("page") page: Int,
            @Query("limit") limit: Int
        ): Single<List<TopPair>>

        companion object {
            private const val marketInfoFields =
                "name,code,price,price_change_24h,price_change_7d,price_change_30d,market_cap_rank,coingecko_id,market_cap,market_cap_rank,total_volume"
            private const val coinPriceFields = "price,price_change_24h,last_updated"
            private const val advancedMarketFields =
                "all_platforms,price,market_cap,total_volume,price_change_24h,price_change_7d,price_change_14d,price_change_30d,price_change_200d,price_change_1y,ath_percentage,atl_percentage"
        }
    }


    private interface CoinGeckoMarketService {

    }
}

data class HistoricalCoinPriceResponse(
    val timestamp: Long,
    val price: BigDecimal,
)

data class ChartStart(val timestamp: Long)

data class ChartCoinPriceResponse(
    val timestamp: Long,
    val price: BigDecimal,
    @SerializedName("volume")
    val totalVolume: BigDecimal?
) {
    val chartPoint: ChartPoint
        get() {
            return ChartPoint(
                price,
                timestamp,
                totalVolume
            )
        }
}
