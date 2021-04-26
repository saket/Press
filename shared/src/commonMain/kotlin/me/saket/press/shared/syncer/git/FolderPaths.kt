package me.saket.press.shared.syncer.git

import me.saket.press.PressDatabase
import me.saket.press.data.shared.Folder
import me.saket.press.data.shared.FolderQueries
import me.saket.press.shared.db.FolderId
import me.saket.press.shared.db.NoteId

@OptIn(ExperimentalStdlibApi::class)
internal class FolderPaths(private val database: PressDatabase) {
  private val noteQueries get() = database.noteQueries
  private val folderQueries get() = database.folderQueries

  fun createFlatPath(
    id: FolderId?,
    existingFolders: () -> List<Folder> = { database.folderQueries.allFolders().executeAsList() }
  ): String {
    return createPath(id, existingFolders).flatten()
  }

  private fun createPath(
    id: FolderId?,
    existingFolders: () -> List<Folder> = { database.folderQueries.allFolders().executeAsList() }
  ): FolderPath {
    if (id == null) {
      return FolderPath(emptyList())
    }

    return FolderPath(
      buildList {
        val idsToFolders = existingFolders().associateBy { it.id }

        var parentId: FolderId? = id
        while (parentId != null) {
          val parent = idsToFolders[parentId]!!
          add(parent.name)
          parentId = parent.parent
        }
      }.reversed()
    )
  }

  /**
   * @param folderPath path/to/a/folder
   * @return FolderId that represents [folderPath].
   */
  @Suppress("NAME_SHADOWING")
  fun mkdirs(folderPath: String): FolderId? {
    val folderPath = folderPath.removeSuffix("/")
    if (folderPath.isBlank()) {
      return null
    }

    val allFolders = folderQueries.allFolders().executeAsList()
    val pathsToFolderIds = allFolders
      .associate { createFlatPath(id = it.id, existingFolders = { allFolders }) to it.id }
      .toMutableMap()

    var nextPath = ""
    var nextParent: FolderId? = null

    for (path in folderPath.split("/")) {
      nextPath += path

      if (nextPath !in pathsToFolderIds) {
        val folderId = FolderId.generate()
        pathsToFolderIds[nextPath] = folderId
        folderQueries.insert(id = folderId, name = path, parent = nextParent)
      }

      nextParent = pathsToFolderIds[nextPath]!!
      nextPath += "/"
    }

    return pathsToFolderIds[folderPath]!!
  }

  fun setArchived(noteId: NoteId, archive: Boolean) {
    val note = noteQueries.note(noteId).executeAsOne()
    val currentPath = createPath(note.folderId)

    if (archive) {
      check(currentPath.head() != "archive") { "Note is already archived" }
    } else {
      check(currentPath.head() == "archive") { "Note is already unarchived" }
    }

    val newPath = when {
      archive -> currentPath.pushToHead(with = "archive")
      else -> currentPath.popHead()
    }
    noteQueries.updateFolder(
      id = note.id,
      folderId = mkdirs(newPath.flatten())
    )
  }

  fun isArchived(folderId: FolderId?): Boolean {
    val path = createPath(folderId)
    return path.head() == "archive"
  }
}

private class FolderPath(private val paths: List<String>) {
  fun flatten(): String =
    paths.joinToString(separator = "/")

  fun head(): String? =
    paths.firstOrNull()

  fun pushToHead(with: String) =
    FolderPath(listOf(with) + paths)

  fun popHead() =
    FolderPath(paths.subList(1, paths.size))
}
