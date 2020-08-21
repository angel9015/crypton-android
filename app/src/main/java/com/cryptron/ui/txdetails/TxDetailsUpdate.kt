/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 9/17/19.
 * Copyright (c) 2019 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cryptron.ui.txdetails

import com.cryptron.breadbox.defaultUnit
import com.cryptron.breadbox.feeForToken
import com.cryptron.breadbox.isErc20
import com.cryptron.breadbox.isEthereum
import com.cryptron.breadbox.isReceived
import com.cryptron.breadbox.toBigDecimal
import com.cryptron.breadbox.toSanitizedString
import com.cryptron.ui.models.TransactionState
import com.cryptron.ui.send.TransferField
import com.cryptron.ui.txdetails.TxDetails.E
import com.cryptron.ui.txdetails.TxDetails.F
import com.cryptron.ui.txdetails.TxDetails.M
import com.platform.entities.TxMetaDataEmpty
import com.platform.entities.TxMetaDataValue
import com.spotify.mobius.Next
import com.spotify.mobius.Next.dispatch
import com.spotify.mobius.Next.next
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import java.util.Date

const val MAX_CRYPTO_DIGITS = 8

object TxDetailsUpdate : Update<M, E, F>, TxDetailsUpdateSpec {

    override fun update(
        model: M,
        event: E
    ): Next<M, F> = patch(model, event)

    override fun onTransactionUpdated(
        model: M,
        event: E.OnTransactionUpdated
    ): Next<M, F> {
        val updatedModel = with(event.transaction) {
            val confirmations = confirmations.orNull()?.toInt() ?: 0
            val confirmationsUntilFinal =
                wallet.walletManager.network.confirmationsUntilFinal.toInt()
            model.copy(
                isEth = amount.currency.isEthereum(),
                isErc20 = amount.currency.isErc20(),
                cryptoTransferredAmount = amount.toBigDecimal(wallet.defaultUnit),
                fee = fee.toBigDecimal(wallet.defaultUnit),
                isReceived = isReceived(),
                blockNumber = confirmation.orNull()?.blockNumber?.toInt() ?: 0,
                toOrFromAddress = when {
                    isReceived() -> source
                    else -> target
                }.orNull()?.toSanitizedString() ?: "",
                confirmationDate = confirmation
                    .transform { it?.confirmationTime }
                    .or { Date() },
                confirmedInBlockNumber = confirmation
                    .transform { it?.blockNumber?.toString() }
                    .or(""),
                transactionState = TransactionState.valueOf(state),
                isCompleted = confirmations >= confirmationsUntilFinal,
                gasPrice = event.gasPrice,
                gasLimit = event.gasLimit,
                feeToken = feeForToken(),
                confirmations = confirmations,
                transferFields = event.transaction
                    .attributes
                    .map { attribute ->
                        TransferField(
                            key = attribute.key,
                            required = attribute.isRequired,
                            invalid = false,
                            value = attribute.value.orNull()
                        )
                    }
            )
        }

        return next(
            updatedModel,
            setOf(
                F.LoadFiatAmountNow(
                    updatedModel.cryptoTransferredAmount,
                    updatedModel.currencyCode,
                    updatedModel.preferredFiatIso
                )
            )
        )
    }

    override fun onFiatAmountNowUpdated(
        model: M,
        event: E.OnFiatAmountNowUpdated
    ): Next<M, F> =
        next(
            model.copy(
                fiatAmountNow = event.fiatAmountNow
            )
        )

    override fun onMetaDataUpdated(
        model: M,
        event: E.OnMetaDataUpdated
    ): Next<M, F> =
        when (event.metaData) {
            is TxMetaDataValue -> {
                next(
                    model.copy(
                        memo = event.metaData.comment ?: "",
                        memoLoaded = true,
                        exchangeCurrencyCode = event.metaData.exchangeCurrency ?: "",
                        exchangeRate = event.metaData.exchangeRate.toBigDecimal()
                    )
                )
            }
            is TxMetaDataEmpty -> next(
                model.copy(
                    memo = "",
                    memoLoaded = true
                )
            )
        }

    override fun onMemoChanged(
        model: M,
        event: E.OnMemoChanged
    ): Next<M, F> {
        return when {
            model.memoLoaded -> dispatch(
                setOf(
                    F.UpdateMemo(
                        model.currencyCode,
                        model.transactionHash,
                        event.memo
                    )
                )
            )
            else -> noChange()
        }
    }

    override fun onClosedClicked(model: M): Next<M, F> =
        dispatch(setOf(F.Close))

    override fun onShowHideDetailsClicked(
        model: M
    ): Next<M, F> = next(
        model.copy(
            showDetails = !model.showDetails
        )
    )
}
