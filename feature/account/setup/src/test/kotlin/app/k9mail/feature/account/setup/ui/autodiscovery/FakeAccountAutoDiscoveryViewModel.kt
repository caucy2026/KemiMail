package app.k9mail.feature.account.setup.ui.autodiscovery

import app.k9mail.feature.account.oauth.ui.AccountOAuthContract
import app.k9mail.feature.account.oauth.ui.fake.FakeAccountOAuthViewModel
import app.k9mail.feature.account.setup.ui.autodiscovery.AccountAutoDiscoveryContract.Effect
import app.k9mail.feature.account.setup.ui.autodiscovery.AccountAutoDiscoveryContract.Event
import app.k9mail.feature.account.setup.ui.autodiscovery.AccountAutoDiscoveryContract.State
import app.k9mail.feature.account.setup.ui.credential.FakeRemoteCredentialTransferViewModel
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

class FakeAccountAutoDiscoveryViewModel(
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), AccountAutoDiscoveryContract.ViewModel {

    val events = mutableListOf<Event>()

    override val oAuthViewModel: AccountOAuthContract.ViewModel = FakeAccountOAuthViewModel()
    override val remoteCredentialTransferViewModel = FakeRemoteCredentialTransferViewModel()

    override fun initState(state: State) {
        updateState { state }
    }

    override fun event(event: Event) {
        events.add(event)
    }

    fun effect(effect: Effect) {
        emitEffect(effect)
    }
}
