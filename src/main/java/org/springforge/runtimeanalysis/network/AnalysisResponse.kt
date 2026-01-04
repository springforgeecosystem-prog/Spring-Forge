package org.springforge.runtimeanalysis.network

data class AnalysisResponse(
        val answer: String,
        val retrieved_docs: List<RetrievedDoc>
)

data class RetrievedDoc(
        val source: String?,
        val content: String
)
