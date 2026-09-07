package app.k9mail.feature.account.setup.ui.credential.fake

import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

/** Only for previewing the UI. */
class FakeRemoteCredentialTransferViewModel :
    BaseViewModel<
        RemoteCredentialTransferContract.State,
        RemoteCredentialTransferContract.Event,
        RemoteCredentialTransferContract.Effect,
        >(RemoteCredentialTransferContract.State()),
    RemoteCredentialTransferContract.ViewModel {

    override val isAvailable: Boolean = true

    override fun event(event: RemoteCredentialTransferContract.Event) = Unit
}
