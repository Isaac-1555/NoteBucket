package com.example.notebucket.ui.nav

object Routes {
    const val ONBOARDING = "onboarding"
    const val NOTE_INPUT = "noteInput"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val FOLDER_DETAIL = "folder/{folderId}"
    const val NOTE_DETAIL = "note/{noteId}"

    fun folderDetail(folderId: String) = "folder/$folderId"
    fun noteDetail(noteId: String) = "note/$noteId"

    const val FOLDER_DETAIL_ARG = "folderId"
    const val NOTE_DETAIL_ARG = "noteId"
}
