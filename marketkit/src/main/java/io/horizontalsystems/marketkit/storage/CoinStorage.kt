package io.horizontalsystems.marketkit.storage

import androidx.sqlite.db.SimpleSQLiteQuery
import io.horizontalsystems.marketkit.models.*

class CoinStorage(val marketDatabase: MarketDatabase) {

    private val coinDao = marketDatabase.coinDao()

    fun coin(coinUid: String): Coin? =
        coinDao.getCoin(coinUid)

    fun coins(coinUids: List<String>): List<Coin> =
        coinDao.getCoins(coinUids)

    fun allCoins(): List<Coin> = coinDao.getAllCoins()

    fun fullCoins(filter: String, limit: Int): List<FullCoin> {
        val sql = """
            SELECT * FROM Coin
            WHERE ${filterWhereStatement(filter)}
            ORDER BY ${filterOrderByStatement(filter)}
            LIMIT $limit
        """.trimIndent()

        return coinDao.getFullCoins(SimpleSQLiteQuery(sql)).map { it.fullCoin }
    }

    fun fullCoin(uid: String): FullCoin? =
        coinDao.getFullCoin(uid)?.fullCoin

    fun fullCoins(uids: List<String>): List<FullCoin> =
        coinDao.getFullCoins(uids).map { it.fullCoin }

    fun getToken(query: TokenQuery): Token? {
        val sql = "SELECT * FROM TokenEntity WHERE ${filterByTokenQuery(query)} LIMIT 1"

        return coinDao.getToken(SimpleSQLiteQuery(sql))?.token
    }

    fun getTokens(queries: List<TokenQuery>): List<Token> {
        if (queries.isEmpty()) return listOf()

        val queriesStr = queries.toSet().toList().map { filterByTokenQuery(it) }.joinToString(" OR ")
        val sql = "SELECT * FROM TokenEntity WHERE $queriesStr"

        return coinDao.getTokens(SimpleSQLiteQuery(sql)).map { it.token }
    }

    fun getTokens(reference: String): List<Token> {
        val queriesStr = "`TokenEntity`.`reference` LIKE '%$reference'"
        val sql = "SELECT * FROM TokenEntity WHERE $queriesStr"

        return coinDao.getTokens(SimpleSQLiteQuery(sql)).map { it.token }
    }

    fun getTokens(blockchainType: BlockchainType, filter: String, limit: Int): List<Token> {
        val sql = """
            SELECT * FROM TokenEntity
            JOIN Coin ON `Coin`.`uid` = `TokenEntity`.`coinUid`
            WHERE 
              `TokenEntity`.`blockchainUid` = '${blockchainType.uid}'
              AND (${filterWhereStatement(filter)})
            ORDER BY ${filterOrderByStatement(filter)}
            LIMIT $limit
        """.trimIndent()

        return coinDao.getTokens(SimpleSQLiteQuery(sql)).map { it.token }
    }

    fun getBlockchain(uid: String): Blockchain? =
        coinDao.getBlockchain(uid)?.blockchain

    fun getBlockchains(uids: List<String>): List<Blockchain> =
        coinDao.getBlockchains(uids).map { it.blockchain }

    fun getAllBlockchains(): List<Blockchain> =
        coinDao.getAllBlockchains().map { it.blockchain }

    fun insertTokenEntity(tokenEntity: TokenEntity) =
        coinDao.insert(tokenEntity)

    fun removeTokenEntity(coinUid: String, reference: String) =
        coinDao.removeToken(coinUid, reference)

    fun insertCoin(coin: Coin) =
        coinDao.insert(coin)

    private fun filterByTokenQuery(query: TokenQuery): String {
        val (type, reference) = query.tokenType.values

        val conditions = mutableListOf(
            "`TokenEntity`.`blockchainUid` = '${query.blockchainType.uid}'",
            "`TokenEntity`.`type` = '$type'"
        )

        if (reference.isNotBlank()) {
            conditions.add("`TokenEntity`.`reference` LIKE '%$reference'")
        }

        return conditions.joinToString(" AND ", "(", ")")
    }

    private fun filterWhereStatement(filter: String) =
        "`Coin`.`name` LIKE '%$filter%' OR `Coin`.`code` LIKE '%$filter%'"

    private fun filterOrderByStatement(filter: String) = """
        CASE 
            WHEN `Coin`.`code` LIKE '$filter' THEN 1 
            WHEN `Coin`.`code` LIKE '$filter%' THEN 2 
            WHEN `Coin`.`name` LIKE '$filter%' THEN 3 
            ELSE 4 
        END, 
        CASE 
            WHEN `Coin`.`marketCapRank` IS NULL THEN 1 
            ELSE 0 
        END, 
        `Coin`.`marketCapRank` ASC, 
        `Coin`.`name` ASC 
    """

    fun update(coins: List<Coin>, blockchainEntities: List<BlockchainEntity>, tokenEntities: List<TokenEntity>) {
        marketDatabase.runInTransaction {
            coinDao.deleteAllCoins()
            coinDao.deleteAllBlockchains()
            coinDao.deleteAllTokens()
            coins.forEach { coinDao.insert(it) }
            blockchainEntities.forEach { coinDao.insert(it) }
            tokenEntities.forEach { coinDao.insert(it) }

            // insert safe
            val safeCoin = Coin("safe-coin", "SAFE", "SAFE",0,"safe-anwang")
            val safe4Coin = Coin("safe4-coin", "SAFE", "SAFE",0,"safe-anwang")
//            val safeErc = Coin("safe-coin", "SAFE", "SAFE",20,"safe-anwang")
//            val safeBep = Coin("custom_safe-erc20-SAFE", "SAFE", "SAFE",20,"safe-anwang")
            coinDao.insert(safeCoin)
            coinDao.insert(safe4Coin)
//            coinDao.insert(safeErc)
//            coinDao.insert(safeBep)

            val safeErcBlockchain = BlockchainEntity("safe-coin", "SAFE",null)
            val safe4Blockchain = BlockchainEntity("safe4-coin", "SAFE",null)
//            val safeBepBlockchain = BlockchainEntity("custom_safe-bep20-SAFE", "Safe",null)
            coinDao.insert(safeErcBlockchain)
            coinDao.insert(safe4Blockchain)
//            coinDao.insert(safeBepBlockchain)

            val safeToken = TokenEntity("safe-coin", "safe-coin","native", 8, "")
            val safe4Token = TokenEntity("safe4-coin", "safe4-coin","native", 18, "")
            val safeErcToken = TokenEntity("safe-coin", "ethereum","eip20", 18, "0xee9c1ea4dcf0aaf4ff2d78b6ff83aa69797b65eb")
            val safeBepToken = TokenEntity("safe-coin", "binance-smart-chain","eip20", 18, "0x4d7fa587ec8e50bd0e9cd837cb4da796f47218a1")
            val safeMaticToken = TokenEntity("safe-coin", "polygon-pos","eip20", 18, "0xb7dd19490951339fe65e341df6ec5f7f93ff2779")
            val safeSafe4Token2 = TokenEntity("safe4-coin", "safe4-coin","eip20", 18, "0x0000000000000000000000000000000000001101")

            coinDao.insert(safeToken)
            coinDao.insert(safe4Token)
            coinDao.insert(safeErcToken)
            coinDao.insert(safeBepToken)
            coinDao.insert(safeMaticToken)
            coinDao.insert(safeSafe4Token2)
        }
    }

    fun getTokenEntity(uids: List<String>, type: String): List<TokenEntity> =
        coinDao.getTokenEntity(uids, type)

}
