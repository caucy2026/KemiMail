package net.thunderbird.core.preference.display.coreSettings

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.preference.DualScreenMode
import net.thunderbird.core.preference.storage.Storage
import net.thunderbird.core.preference.storage.StorageEditor
import net.thunderbird.core.preference.storage.StoragePersister
import net.thunderbird.core.preference.storage.StorageUpdater

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDisplayCoreSettingsPreferenceManagerTest {
    private val logger = TestLogger()

    @Test
    fun `missing dual screen mode should default to immersive`() = runTest {
        val storage = TestPreferenceStorage()
        val testSubject = createTestSubject(storage)

        assertThat(testSubject.getConfig().dualScreenMode).isEqualTo(DualScreenMode.IMMERSIVE)
    }

    @Test
    fun `saved smart mode should be restored by a new manager`() = runTest {
        val storage = TestPreferenceStorage()
        val testSubject = createTestSubject(storage)

        testSubject.save(testSubject.getConfig().copy(dualScreenMode = DualScreenMode.SMART))

        val restoredSubject = createTestSubject(storage)
        assertThat(restoredSubject.getConfig().dualScreenMode).isEqualTo(DualScreenMode.SMART)
    }

    @Test
    fun `invalid dual screen mode should fall back to immersive`() = runTest {
        val storage = TestPreferenceStorage(mapOf(KEY_DUAL_SCREEN_MODE to "UNKNOWN"))
        val testSubject = createTestSubject(storage)

        assertThat(testSubject.getConfig().dualScreenMode).isEqualTo(DualScreenMode.IMMERSIVE)
    }

    private fun createTestSubject(storage: TestPreferenceStorage): DefaultDisplayCoreSettingsPreferenceManager {
        return DefaultDisplayCoreSettingsPreferenceManager(
            logger = logger,
            storagePersister = storage,
            storageEditor = storage,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }
}

private class TestPreferenceStorage(
    initialValues: Map<String, String> = emptyMap(),
) : Storage, StoragePersister, StorageEditor {
    private val values = initialValues.toMutableMap()

    override fun loadValues(): Storage = this

    override fun createStorageEditor(storageUpdater: StorageUpdater): StorageEditor = this

    override fun isEmpty(): Boolean = values.isEmpty()

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun getAll(): Map<String, String> = values.toMap()

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key]?.toBooleanStrictOrNull() ?: defValue

    override fun getInt(key: String, defValue: Int): Int = values[key]?.toIntOrNull() ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key]?.toLongOrNull() ?: defValue

    override fun getString(key: String): String = values.getValue(key)

    override fun getStringOrDefault(key: String, defValue: String): String = values[key] ?: defValue

    override fun getStringOrNull(key: String): String? = values[key]

    override fun putBoolean(key: String, value: Boolean): StorageEditor = putString(key, value.toString())

    override fun putInt(key: String, value: Int): StorageEditor = putString(key, value.toString())

    override fun putLong(key: String, value: Long): StorageEditor = putString(key, value.toString())

    override fun putString(key: String, value: String?): StorageEditor = apply {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun remove(key: String): StorageEditor = apply { values.remove(key) }

    override fun commit(): Boolean = true
}
