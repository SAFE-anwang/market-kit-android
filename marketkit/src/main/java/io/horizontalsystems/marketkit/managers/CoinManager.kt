package io.horizontalsystems.marketkit.managers

import android.util.Log
import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.CoinGeckoMarketResponse
import io.horizontalsystems.marketkit.models.DefiMarketInfo
import io.horizontalsystems.marketkit.models.DefiMarketInfoResponse
import io.horizontalsystems.marketkit.models.FullCoin
import io.horizontalsystems.marketkit.models.GeckoCoinPriceResponse
import io.horizontalsystems.marketkit.models.HsTimePeriod
import io.horizontalsystems.marketkit.models.LinkType
import io.horizontalsystems.marketkit.models.MarketInfo
import io.horizontalsystems.marketkit.models.MarketInfoOverview
import io.horizontalsystems.marketkit.models.MarketInfoRaw
import io.horizontalsystems.marketkit.models.MarketTicker
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenEntity
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.storage.CoinStorage
import io.reactivex.Single

class CoinManager(
    private val storage: CoinStorage,
) {

    fun coin(coinUid: String) = storage.coin(coinUid)
    fun coins(coinUids: List<String>) = storage.coins(coinUids)

    fun fullCoin(uid: String): FullCoin? =
        storage.fullCoin(uid)

    fun fullCoins(filter: String, limit: Int): List<FullCoin> =
        storage.fullCoins(filter, limit)

    fun fullCoins(coinUids: List<String>): List<FullCoin> =
        storage.fullCoins(coinUids)

    fun allCoins(): List<Coin> = storage.allCoins()

    fun token(query: TokenQuery): Token? =
        storage.getToken(query)

    fun tokens(queries: List<TokenQuery>): List<Token> =
        storage.getTokens(queries)

    fun tokens(reference: String): List<Token> =
        storage.getTokens(reference)

    fun tokens(blockchainType: BlockchainType, filter: String, limit: Int): List<Token> =
        storage.getTokens(blockchainType, filter, limit)

    fun blockchain(uid: String): Blockchain? =
        storage.getBlockchain(uid)

    fun blockchains(uids: List<String>): List<Blockchain> =
        storage.getBlockchains(uids)

    fun allBlockchains(): List<Blockchain> =
        storage.getAllBlockchains()

    fun getMarketInfos(rawMarketInfos: List<MarketInfoRaw>): List<MarketInfo> {
        return buildList {
            rawMarketInfos.chunked(700).forEach { chunkedRawMarketInfos ->
                try {
                    val fullCoins = storage.fullCoins(chunkedRawMarketInfos.map { it.uid })
                    val hashMap = fullCoins.associateBy { it.coin.uid }

                    addAll(
                        chunkedRawMarketInfos.mapNotNull { rawMarketInfo ->
                            val fullCoin = hashMap[rawMarketInfo.uid] ?: return@mapNotNull null
                            MarketInfo(rawMarketInfo, fullCoin)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("CoinManager", "getMarketInfos: ", e)
                }
            }
        }
    }


    /*fun marketInfosSingle(coinUids: List<String>, currencyCode: String): Single<List<MarketInfo>> {
        // 获取Safe币价格
        var coinGeckoInfo: List<GeckoCoinPriceResponse>? = null
        if (coinUids.contains("safe-coin")) {
            coinGeckoInfo = hsProvider.getCoinGeckoInfo(listOf("safe-anwang"), currencyCode).blockingGet()
        }
        return hsProvider.marketInfosSingle(coinUids, currencyCode).map {
            var result = getMarketInfos(it)
            var safeMarketInfo: MarketInfo? = null
            if (coinUids.contains("safe-coin")) {
                val safeInfo = storage.fullCoin("safe-coin")
                if (safeInfo != null && coinGeckoInfo != null) {
                    val coinInfo = coinGeckoInfo[0]

                    safeMarketInfo = MarketInfo(safeInfo,
                            price = coinInfo.current_price,
                            priceChange24h = coinInfo.priceChange,
                            priceChange7d = null,
                            priceChange14d = null,
                            priceChange30d = null,
                            priceChange200d = null,
                            priceChange1y = null,
                            marketCap = coinInfo.marketCap,
                            marketCapRank = null,
                            totalVolume = coinInfo.totalVolume,
                            athPercentage = coinInfo.totalVolume,
                            atlPercentage = coinInfo.totalVolume)
                }
            }
            if (safeMarketInfo != null) {
                val totalResult = mutableListOf<MarketInfo>()
                totalResult.add(safeMarketInfo)
                totalResult.addAll(result)
                totalResult
            } else {
                result
            }
        }
    }*/


    /*fun marketInfoOverviewSingle(
            coinUid: String,
            currencyCode: String,
            language: String
    ): Single<MarketInfoOverview> {
        return if (coinUid == "safe-coin") {
            hsProvider.getSafeMarketInfoOverview("safe-anwang", currencyCode, language).map { rawOverview ->
                val fullCoin = fullCoin(coinUid) ?: throw Exception("No Full Coin")

                rawOverview.marketInfoOverview(fullCoin)
            }
        } else {
            hsProvider.getMarketInfoOverview(coinUid, currencyCode, language).map { rawOverview ->
                val fullCoin = fullCoin(coinUid) ?: throw Exception("No Full Coin")

                // 获取Safe信息
                var coinGeckoInfo: CoinGeckoMarketResponse? = null
                if (coinUid == "safe-coin") {
                    coinGeckoInfo = hsProvider.getCoinGeckoMarketInfoOverview("safe-anwang").blockingGet()
                }
                *//*val categoriesMap = categoryManager.coinCategories(overviewRaw.categoryIds)
                    .map { it.uid to it }
                    .toMap()*//*

                val performance = rawOverview.performance.map { (vsCurrency, v) ->
                    vsCurrency to v.mapNotNull { (timePeriodRaw, performance) ->
                        if (performance == null) return@mapNotNull null

                        val timePeriod = when (timePeriodRaw) {
                            "7d" -> HsTimePeriod.Week1
                            "30d" -> HsTimePeriod.Month1
                            else -> return@mapNotNull null
                        }

                        timePeriod to performance
                    }.toMap()
                }.toMap()

                val links = rawOverview.links
                        .mapNotNull { (linkTypeRaw, link) ->
                            LinkType.fromString(linkTypeRaw)?.let {
                                it to link
                            }
                        }.toMap().toMutableMap()

                coinGeckoInfo?.let {
                    if(it.links.homepage.size > 0) {
                        links[LinkType.Website] = it.links.homepage[0]
                    }
                    it.links.twitter?.let {
                        links[LinkType.Twitter] = it
                    }
                    it.links.telegram?.let {
                        links[LinkType.Telegram] = it
                    }
                    it.links.repos_url?.let {
                        if (it.containsKey("github") && it["github"]?.size!! > 0) {
                            links[LinkType.Github] = it["github"]!![0]
                        }
                    }
                }

                rawOverview.marketInfoOverview(fullCoin)
            }
        }
    }

    fun marketTickersSingle(coinUid: String): Single<List<MarketTicker>> {
        val coinGeckoId = storage.coin(coinUid)?.coinGeckoId ?: return Single.just(emptyList())
        return if (coinUid == "safe-anwang") {
            coinGeckoProvider.marketTickersSingleSafe(coinGeckoId)
                    .map { response ->
                        val coinUids =
                                (response.tickers.map { it.coinId } + response.tickers.mapNotNull { it.targetCoinId }).distinct()
                        val coins = storage.coins(coinUids)
                        val imageUrls = exchangeManager.imageUrlsMap(response.exchangeIds)
                        response.marketTickers(imageUrls, coins)
                    }
        } else {
            coinGeckoProvider.marketTickersSingle(coinGeckoId)
                    .map { response ->
                        val coinUids =
                                (response.tickers.map { it.coinId } + response.tickers.mapNotNull { it.targetCoinId }).distinct()
                        val coins = storage.coins(coinUids)
                        val imageUrls = exchangeManager.imageUrlsMap(response.exchangeIds)
                        response.marketTickers(imageUrls, coins)
                    }
        }
    }*/

    fun getDefiMarketInfos(rawDefiMarketInfos: List<DefiMarketInfoResponse>): List<DefiMarketInfo> {
        val fullCoins = storage.fullCoins(rawDefiMarketInfos.mapNotNull { it.uid })
        val hashMap = fullCoins.map { it.coin.uid to it }.toMap()

        return rawDefiMarketInfos.map { rawDefiMarketInfo ->
            val fullCoin = hashMap[rawDefiMarketInfo.uid]
            DefiMarketInfo(rawDefiMarketInfo, fullCoin)
        }
    }


    fun getTokenEntity(coinUids: List<String>, type: String): List<TokenEntity> =
            storage.getTokenEntity(coinUids, type)

}
