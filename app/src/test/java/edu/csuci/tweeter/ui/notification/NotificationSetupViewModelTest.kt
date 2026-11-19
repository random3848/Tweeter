package edu.csuci.tweeter.ui.notification

import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeNumberStore
import edu.csuci.tweeter.notification.NotificationPermissionRecord
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val USER_UID = "maya"

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSetupViewModelTest {
    private val accountRepository = FakeAccountRepository(initialUid = USER_UID)
    private val permissionRecord =
        NotificationPermissionRecord(FakeNumberStore(), Clock.systemUTC())
    private var canNotify = false
    private var locationUpdates = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun shouldRequestPermission_loggedInWithoutPermission_isTrue() {
        assertTrue(createViewModel().shouldRequestPermission.value)
    }

    @Test
    fun shouldRequestPermission_guest_isFalse() {
        val viewModel = createViewModel(FakeAccountRepository(initialUid = null))

        assertFalse(viewModel.shouldRequestPermission.value)
    }

    @Test
    fun shouldRequestPermission_permissionGranted_isFalse() {
        canNotify = true

        assertFalse(createViewModel().shouldRequestPermission.value)
    }

    @Test
    fun shouldRequestPermission_requestedBefore_isFalse() = runTest {
        permissionRecord.recordRequest()

        assertFalse(createViewModel().shouldRequestPermission.value)
    }

    @Test
    fun onPermissionRequested_neverRequestsAgain() {
        createViewModel().onPermissionRequested()

        assertFalse(createViewModel().shouldRequestPermission.value)
    }

    @Test
    fun onPermissionRequested_stopsRequesting() {
        val viewModel = createViewModel()

        viewModel.onPermissionRequested()

        assertFalse(viewModel.shouldRequestPermission.value)
    }

    @Test
    fun shouldRequestPermission_guestLogsIn_becomesTrue() {
        val guestRepository = FakeAccountRepository(initialUid = null)
        val viewModel = createViewModel(guestRepository)

        guestRepository.logInAs(USER_UID)

        assertTrue(viewModel.shouldRequestPermission.value)
    }

    @Test
    fun init_loggedIn_updatesLastLocationOnce() {
        createViewModel()

        assertEquals(1, locationUpdates)
    }

    @Test
    fun init_guest_leavesLastLocationAlone() {
        createViewModel(FakeAccountRepository(initialUid = null))

        assertEquals(0, locationUpdates)
    }

    private fun createViewModel(
        accountRepository: FakeAccountRepository = this.accountRepository
    ): NotificationSetupViewModel = NotificationSetupViewModel(
        accountRepository = accountRepository,
        permissionRecord = permissionRecord,
        canNotify = { canNotify },
        updateLastLocation = { locationUpdates++ }
    )
}
