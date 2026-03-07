package org.springforge.qualityassurance.model

/**
 * Feature vector for a single Java source file sent to the ML service.
 *
 * v2 changes:
 *   - [uses_new_keyword]  added — flags tight coupling via `new` instantiation
 *                         (supports tight_coupling_new_keyword anti-pattern detection)
 *   - [has_broad_catch]   added — flags broad Exception/Throwable catch blocks
 *                         (supports broad_catch anti-pattern detection)
 *   - [usecase_deps] and [gateway_deps] are now populated by PsiFeatureExtractor
 *     (were always 0 in v1, breaking hexagonal/clean_architecture detection)
 *
 * The field names must match the FastAPI schemas.py FileFeatures model exactly
 * (snake_case). Gson serialises them verbatim.
 */
data class FileFeatureModel(
    var architecture_pattern    : String  = "",
    var architecture_confidence : Double  = 0.95,
    var file_name               : String  = "",
    var file_path               : String  = "",
    var layer                   : String  = "unknown",

    // Size metrics
    var loc                     : Int     = 0,
    var methods                 : Int     = 0,
    var classes                 : Int     = 0,
    var avg_cc                  : Double  = 1.5,
    var imports                 : Int     = 0,
    var annotations             : Int     = 0,

    // Cross-layer dependency counts
    var controller_deps         : Int     = 0,
    var service_deps            : Int     = 0,
    var repository_deps         : Int     = 0,
    var entity_deps             : Int     = 0,
    var adapter_deps            : Int     = 0,
    var port_deps               : Int     = 0,
    var usecase_deps            : Int     = 0,   // ← v2: was always 0
    var gateway_deps            : Int     = 0,   // ← v2: was always 0
    var total_cross_layer_deps  : Int     = 0,

    // Boolean flags
    var has_business_logic      : Boolean = false,
    var has_data_access         : Boolean = false,
    var has_http_handling       : Boolean = false,
    var has_validation          : Boolean = false,
    var has_transaction         : Boolean = false,
    var violates_layer_separation: Boolean = false,

    // v2: new anti-pattern detection flags
    var uses_new_keyword        : Boolean = false,  // tight_coupling_new_keyword
    var has_broad_catch         : Boolean = false,  // broad_catch

    // v3: source code for LLM validation — when provided, Gemini validates
    // ML predictions against real code, filters false positives, and generates
    // context-aware fixes. When null, ML-only behavior is preserved.
    var source_code             : String? = null
)