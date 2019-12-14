package me.saket.press.shared.note

import assertk.assertThat
import assertk.assertions.hasSize
import com.badoo.reaktive.test.scheduler.TestScheduler
import me.saket.press.shared.settings.FakeSetting
import kotlin.test.Test

class PrePopulatedNotesTest {

  private val setting = FakeSetting(PrePopulatedNotesInserted(false))
  private val repository = FakeNoteRepository()

  private val prePopulatedNotes = PrePopulatedNotes(
      setting = setting,
      repository = repository,
      ioScheduler = TestScheduler()
  )

  @Test fun `notes are pre-populated on first app launch`() {
    setting.set(PrePopulatedNotesInserted(false))

    assertThat(repository.savedNotes).hasSize(0)

    prePopulatedNotes.doWork()
    assertThat(repository.savedNotes).hasSize(2)
  }

  @Test fun `notes aren't pre-populated on subsequent app launches`() {
    setting.set(PrePopulatedNotesInserted(true))

    prePopulatedNotes.doWork()
    assertThat(repository.savedNotes).hasSize(0)
  }
}
