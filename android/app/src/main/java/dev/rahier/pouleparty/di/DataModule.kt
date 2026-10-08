package dev.rahier.pouleparty.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.rahier.pouleparty.data.ChallengeSubmissionRepository
import dev.rahier.pouleparty.data.FirebaseChallengeSubmissionRepository
import dev.rahier.pouleparty.data.FirebaseGameFunctions
import dev.rahier.pouleparty.data.FirestoreGameRepository
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.PresenceRepository
import dev.rahier.pouleparty.data.RealtimePresenceRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun gameRepository(impl: FirestoreGameRepository): GameRepository
    @Binds abstract fun presenceRepository(impl: RealtimePresenceRepository): PresenceRepository
    @Binds abstract fun gameFunctions(impl: FirebaseGameFunctions): GameFunctions
    @Binds abstract fun challengeSubmissions(impl: FirebaseChallengeSubmissionRepository): ChallengeSubmissionRepository
}
