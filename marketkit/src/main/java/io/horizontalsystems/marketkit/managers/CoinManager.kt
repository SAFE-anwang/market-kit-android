package io.horizontalsystems.marketkit.managers

import io.horizontalsystems.marketkit.models.*
import io.horizontalsystems.marketkit.providers.CoinGeckoProvider
import io.horizontalsystems.marketkit.providers.DefiYieldProvider
import io.horizontalsystems.marketkit.providers.HsProvider
import io.horizontalsystems.marketkit.storage.CoinStorage
import io.reactivex.Single

class CoinManager(
    private val storage: CoinStorage,
    private val hsProvider: HsProvider,
    private val coinGeckoProvider: CoinGeckoProvider,
    private val defiYieldProvider: DefiYieldProvider,
    private val exchangeManager: ExchangeManager
) {

    fun fullCoin(uid: String): FullCoin? =
        storage.fullCoin(uid)

    fun fullCoins(filter: String, limit: Int): List<FullCoin> =
        storage.fullCoins(filter, limit)

    fun fullCoins(coinUids: List<String>): List<FullCoin> =
        storage.fullCoins(coinUids)

    fun marketInfosSingle(top: Int, currencyCode: String, defi: Boolean): Single<List<MarketInfo>> {
        return hsProvider.marketInfosSingle(top, currencyCode, defi).map {
            getMarketInfos(it)
        }
    }

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

    fun advancedMarketInfosSingle(top: Int, currencyCode: String): Single<List<MarketInfo>> {
        return hsProvider.advancedMarketInfosSingle(top, currencyCode).map {
            getMarketInfos(it)
        }
    }

    fun marketInfosSingle(coinUids: List<String>, currencyCode: String): Single<List<MarketInfo>> {
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
    }

    fun marketInfosSingle(categoryUid: String, currencyCode: String): Single<List<MarketInfo>> {
        return hsProvider.marketInfosSingle(categoryUid, currencyCode).map {
            getMarketInfos(it)
        }
    }

    fun marketInfoOverviewSingle(
        coinUid: String,
        currencyCode: String,
        language: String
    ): Single<MarketInfoOverview> {
        return hsProvider.getMarketInfoOverview(coinUid, currencyCode, language).map { rawOverview ->
            val fullCoin = fullCoin(coinUid) ?: throw Exception("No Full Coin")

            // 获取Safe信息
            var coinGeckoInfo: CoinGeckoMarketResponse? = null
            if (coinUid == "safe-coin") {
                coinGeckoInfo = hsProvider.getCoinGeckoMarketInfoOverview("safe-anwang").blockingGet()
            }
            /*val categoriesMap = categoryManager.coinCategories(overviewRaw.categoryIds)
                .map { it.uid to it }
                .toMap()*/

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

    fun marketTickersSingle(coinUid: String): Single<List<MarketTicker>> {
        val coinGeckoId = storage.coin(coinUid)?.coinGeckoId ?: return Single.just(emptyList())

        return coinGeckoProvider.marketTickersSingle(coinGeckoId)
            .map { response ->
                val coinUids =
                    (response.tickers.map { it.coinId } + response.tickers.mapNotNull { it.targetCoinId }).distinct()
                val coins = storage.coins(coinUids)
                val imageUrls = exchangeManager.imageUrlsMap(response.exchangeIds)
                response.marketTickers(imageUrls, coins)
            }
    }

    fun defiMarketInfosSingle(currencyCode: String): Single<List<DefiMarketInfo>> {
        return hsProvider.defiMarketInfosSingle(currencyCode).map {
            getDefiMarketInfos(it)
        }
    }

    fun auditReportsSingle(addresses: List<String>): Single<List<Auditor>> {
        return defiYieldProvider.auditReportsSingle(addresses)
    }

    fun topPlatformCoinListSingle(chain: String, currencyCode: String): Single<List<MarketInfo>> {
        return hsProvider.topPlatformCoinListSingle(chain, currencyCode)
            .map { getMarketInfos(it) }
    }

    private fun getMarketInfos(rawMarketInfos: List<MarketInfoRaw>, containsSafe: Boolean = false): List<MarketInfo> {
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
                } catch (e: Exception) { }
            }
        }
    }

    private fun getDefiMarketInfos(rawDefiMarketInfos: List<DefiMarketInfoResponse>): List<DefiMarketInfo> {
        val fullCoins = storage.fullCoins(rawDefiMarketInfos.mapNotNull { it.uid })
        val hashMap = fullCoins.map { it.coin.uid to it }.toMap()

        return rawDefiMarketInfos.map { rawDefiMarketInfo ->
            val fullCoin = hashMap[rawDefiMarketInfo.uid]
            DefiMarketInfo(rawDefiMarketInfo, fullCoin)
        }
    }

    fun topMoversSingle(currencyCode: String): Single<TopMovers> =
        hsProvider.topMoversRawSingle(currencyCode)
            .map { raw ->
                TopMovers(
                    gainers100 = getMarketInfos(raw.gainers100),
                    gainers200 = getMarketInfos(raw.gainers200),
                    gainers300 = getMarketInfos(raw.gainers300),
                    losers100 = getMarketInfos(raw.losers100),
                    losers200 = getMarketInfos(raw.losers200),
                    losers300 = getMarketInfos(raw.losers300)
                )
            }

}
