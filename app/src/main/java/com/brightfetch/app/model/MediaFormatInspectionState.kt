package com.brightfetch.app.model

/** Atomic UI state for inspecting the concrete download choices of one detected video. */
sealed interface MediaFormatInspectionState {
    data object Hidden : MediaFormatInspectionState

    data class Loading(
        val candidate: MediaCandidate,
    ) : MediaFormatInspectionState

    data class Ready(
        val candidate: MediaCandidate,
        val options: List<MediaDownloadOption>,
    ) : MediaFormatInspectionState

    data class Failed(
        val candidate: MediaCandidate,
        val message: String,
    ) : MediaFormatInspectionState
}
