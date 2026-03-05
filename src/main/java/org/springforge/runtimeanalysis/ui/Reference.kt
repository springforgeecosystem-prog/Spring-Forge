package org.springforge.runtimeanalysis.ui

/**
 * Represents a documentation reference returned alongside an analysis result.
 * Extracted as a top-level data class so it can be used from unit tests
 * without loading IntelliJ/Swing classes (AnalysisResultDialog extends DialogWrapper).
 */
data class Reference(
    val title: String,
    val url: String
)
