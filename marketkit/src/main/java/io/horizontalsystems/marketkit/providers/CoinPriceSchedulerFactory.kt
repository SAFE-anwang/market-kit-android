package io.horizontalsystems.marketkit.providers

import io.horizontalsystems.marketkit.managers.CoinPriceManager
import io.horizontalsystems.marketkit.managers.ICoinPriceCoinUidDataSource
import io.horizontalsystems.marketkit.Scheduler

class CoinPriceSchedulerFactory(
    private val manager: CoinPriceManager,
    private val provider: HsProvider,
    private val isSafe4TestNet: Boolean
) {
    fun scheduler(currencyCode: String, coinUidDataSource: ICoinPriceCoinUidDataSource): Scheduler {
        val schedulerProvider = CoinPriceSchedulerProvider(currencyCode, manager, provider, isSafe4TestNet)
        schedulerProvider.dataSource = coinUidDataSource
        return Scheduler(schedulerProvider)
    }
}
