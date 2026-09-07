package app.k9mail

import app.k9mail.featureflag.K9FeatureFlagFactory
import assertk.assertThat
import assertk.assertions.isFalse
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.thundermail.featureflag.ThundermailFeatureFlags

class K9FeatureFlagFactoryTest {
    @Test
    fun `does not expose Thunderbird branded mailbox service`() = runTest {
        val featureFlag = K9FeatureFlagFactory()
            .getCatalog()
            .first()
            .single { it.key == ThundermailFeatureFlags.ThundermailOnboardingEnabled }

        assertThat(featureFlag.enabled).isFalse()
    }
}
