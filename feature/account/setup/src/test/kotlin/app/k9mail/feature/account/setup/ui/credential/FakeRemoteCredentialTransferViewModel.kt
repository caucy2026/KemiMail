package app.k9mail.feature.account.setup.ui.credential

import net.thunderbird.core.ui.contract.mvi.BaseViewModel

class FakeRemoteCredentialTransferViewModel(
    override val isAvailable: Boolean = true,
) : BaseViewModel<
    RemoteCredentialTransferContract.State,
    RemoteCredentialTransferContract.Event,
    RemoteCredentialTransferContract.Effect,
    >(RemoteCredentialTransferContract.State()),
    RemoteCredentialTransferContract.ViewModel {

    val events = mutableListOf<RemoteCredentialTransferContract.Event>()

    override fun event(event: RemoteCredentialTransferContract.Event) {
        events.add(event)
    }
}
