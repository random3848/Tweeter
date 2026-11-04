package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [ProfileRepository] for tests that starts with [profiles]; any other user has the default
 * profile until one is saved for them.
 */
class FakeProfileRepository(profiles: List<UserProfile> = emptyList()) : ProfileRepository {
    private val savedProfiles = MutableStateFlow(profiles)

    override fun getProfile(uid: String): Flow<UserProfile> = savedProfiles.map { saved ->
        saved.firstOrNull { it.uid == uid } ?: UserProfile.createDefault(uid)
    }

    override suspend fun saveProfile(profile: UserProfile) {
        savedProfiles.update { saved -> saved.filterNot { it.uid == profile.uid } + profile }
    }
}
