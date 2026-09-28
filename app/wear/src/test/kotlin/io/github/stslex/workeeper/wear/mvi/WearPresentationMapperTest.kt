package io.github.stslex.workeeper.wear.mvi

import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.mvi.mapper.WearPresentationMapper
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.Locale

internal class WearPresentationMapperTest {
    @Test
    fun ambientTicksAndNotificationChangesReuseTheFormattedModel() {
        val env = RuntimeTestEnvironment().also { it.accept() }
        val mapper = WearPresentationMapper()
        val first = mapper.map(env.owner.snapshot.value, WearPlatformState())
        val tick = mapper.map(
            env.owner.snapshot.value.copy(ongoing = OngoingStatus.PermissionDenied),
            WearPlatformState(ambient = WearAmbientState(isAmbient = true, timestampMillis = 45_000L)),
        )
        assertSame(first.model, tick.model, "An ambient or notification update must not reformat workout values")
        assertEquals(45_000L, tick.ambient.timestampMillis)
    }

    @Test
    fun localeChangeFormatsTheCurrentDraftOnceIntoANewModel() {
        val env = RuntimeTestEnvironment().also { it.accept() }
        env.owner.onAction(ControllerAction.SetWeight(1_234))
        val mapper = WearPresentationMapper()
        val english = mapper.map(env.owner.snapshot.value, WearPlatformState())
        env.owner.setLocale(Locale.forLanguageTag("ru"))
        val russian = mapper.map(env.owner.snapshot.value, WearPlatformState())
        assertNotSame(english.model, russian.model)
        assertEquals("12.34", english.model.formattedValues.weight)
        assertEquals("12,34", russian.model.formattedValues.weight)
        assertEquals(english.model.reps, russian.model.reps)
        assertSame(russian.model, mapper.map(env.owner.snapshot.value, WearPlatformState()).model)
    }
}
