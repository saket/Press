package me.saket.press.shared.note

import co.touchlab.stately.collections.frozenCopyOnWriteList
import co.touchlab.stately.concurrency.AtomicInt
import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.completable.completableFromFunction
import com.badoo.reaktive.completable.observeOn
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.observableFromFunction
import com.badoo.reaktive.observable.observeOn
import com.badoo.reaktive.test.scheduler.TestScheduler
import com.benasher44.uuid.Uuid
import me.saket.press.data.shared.Note
import me.saket.press.shared.fakedata.fakeNote
import me.saket.press.shared.util.Optional
import me.saket.press.shared.util.toOptional
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeNoteRepository : NoteRepository {
  val savedNotes = frozenCopyOnWriteList<Note.Impl>()

  private val _updateCount = AtomicInt(0)
  val updateCount: Int get() = _updateCount.get()
  private val scheduler = TestScheduler()

  private fun findNote(noteUuid: Uuid) = savedNotes.find { it.uuid == noteUuid }

  override fun note(noteUuid: Uuid): Observable<Optional<Note>> {
    return observableFromFunction { findNote(noteUuid).toOptional() }.observeOn(scheduler)
  }

  override fun notes(): Observable<List<Note>> =
    observableFromFunction { savedNotes }.observeOn(scheduler)

  override fun create(vararg insertNotes: InsertNote): Completable {
    return completableFromFunction {
      for (note in insertNotes) {
        assertNull(findNote(note.uuid))
        savedNotes += fakeNote(uuid = note.uuid, content = note.content)
      }
    }.observeOn(scheduler)
  }

  override fun update(noteUuid: Uuid, content: String): Completable {
    return completableFromFunction {
      assertTrue(savedNotes.remove(findNote(noteUuid)))
      savedNotes += fakeNote(uuid = noteUuid, content = content)
      _updateCount.addAndGet(1)
    }.observeOn(scheduler)
  }

  override fun markAsDeleted(noteUuid: Uuid): Completable {
    return completableFromFunction {
      val existingNote = findNote(noteUuid)!!
      assertTrue(savedNotes.remove(existingNote))
      savedNotes += existingNote.copy(deletedAtString = "current_time")
      _updateCount.addAndGet(1)
    }.observeOn(scheduler)
  }

  override fun markAsArchived(noteUuid: Uuid): Completable {
    return completableFromFunction {
      val existingNote = findNote(noteUuid)!!
      assertTrue(savedNotes.remove(existingNote))
      savedNotes += existingNote.copy(archivedAtString = "current_time")
      _updateCount.addAndGet(1)
    }.observeOn(scheduler)
  }
}
