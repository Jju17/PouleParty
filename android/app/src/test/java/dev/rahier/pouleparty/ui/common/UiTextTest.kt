package dev.rahier.pouleparty.ui.common

import dev.rahier.pouleparty.model.mock
import android.content.Context
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.data.ApiErrorCode
import dev.rahier.pouleparty.data.ApiException
import dev.rahier.pouleparty.data.SubmissionRejectedException
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.challenges.submissionErrorRes
import dev.rahier.pouleparty.ui.gamelogic.chickenSubtitleRes
import dev.rahier.pouleparty.ui.gamelogic.hunterSubtitleRes
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {

    @Test
    fun `nested resources are resolved before formatting`() {
        val context = mockk<Context> {
            every { getString(R.string.powerup_jammer) } returns "Brouilleur"
            every { getString(R.string.notif_powerup_activated, *anyVararg()) } answers {
                "Activé : ${(args[1] as Array<*>).single()} !"
            }
        }
        assertEquals("Activé : Brouilleur !", uiText(R.string.notif_powerup_activated, uiText(R.string.powerup_jammer)).resolve(context))
        assertEquals("The Foxes", UiText.Verbatim("The Foxes").resolve(context))
    }

    @Test
    fun `subtitles follow mode and visibility`() {
        val follow = Game.mock.copy(gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue, chickenCanSeeHunters = false)
        val stay = Game.mock.copy(gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue, chickenCanSeeHunters = false)
        assertEquals(R.string.subtitle_chicken_hide, chickenSubtitleRes(follow))
        assertEquals(R.string.subtitle_stay_in_zone, chickenSubtitleRes(stay))
        assertEquals(R.string.subtitle_chicken_sees, chickenSubtitleRes(follow.copy(chickenCanSeeHunters = true)))
        assertEquals(R.string.subtitle_hunter_catch, hunterSubtitleRes(follow))
        assertEquals(R.string.subtitle_hunter_seen, hunterSubtitleRes(stay.copy(chickenCanSeeHunters = true)))
    }

    @Test
    fun `proof upload errors explain duplicates and server reasons`() {
        assertEquals(R.string.challenge_already_pending, submissionErrorRes(SubmissionRejectedException(SubmissionRejectedException.Reason.ALREADY_PENDING)))
        assertEquals(R.string.challenge_already_validated, submissionErrorRes(SubmissionRejectedException(SubmissionRejectedException.Reason.ALREADY_VALIDATED)))
        assertEquals(R.string.api_error_game_over, submissionErrorRes(ApiException(ApiErrorCode.GAME_OVER)))
    }
}
