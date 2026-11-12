package edu.csuci.tweeter.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Where user profiles are stored and read from.
 *
 * The app uses [FirestoreProfileRepository]; screens only see this interface, so `AppContainer`
 * can swap in another without changing any screen.
 */
interface ProfileRepository {
    /**
     * The profile of user [uid], or [UserProfile.createDefault] if they haven't set one up.
     * Emits again whenever it changes.
     *
     * The flow fails with a `FirebaseException` if the profile can't be read.
     */
    fun getProfile(uid: String): Flow<UserProfile>

    /**
     * Stores [profile] as the profile of user [UserProfile.uid], replacing what was there.
     *
     * The new profile shows on the phone at once, but this returns only when the server has it.
     * While the phone is offline the change waits on the phone and is sent once it's back online.
     *
     * @throws ProfileSaveException If it can't be stored, such as when [profile] isn't the
     *     logged-in user's.
     */
    suspend fun saveProfile(profile: UserProfile)
}

/**
 * The logged-in user's profile, or null while nobody is logged in. Follows [accountRepository]
 * as users log in and out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun ProfileRepository.getLoggedInProfile(accountRepository: AccountRepository): Flow<UserProfile?> =
    accountRepository.loggedInUid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else getProfile(uid)
    }
