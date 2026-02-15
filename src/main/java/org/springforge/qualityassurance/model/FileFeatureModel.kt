// src/main/java/org/springforge/qualityassurance/model/FileFeatureModel.kt
package org.springforge.qualityassurance.model

data class FileFeatureModel(
    var architecture_pattern: String = "",
    var architecture_confidence: Double = 0.95,
    var file_name: String = "",
    var file_path: String = "",
    var layer: String = "unknown",
    var loc: Int = 0,
    var methods: Int = 0,
    var classes: Int = 0,
    var avg_cc: Double = 1.5,
    var imports: Int = 0,
    var annotations: Int = 0,
    var controller_deps: Int = 0,
    var service_deps: Int = 0,
    var repository_deps: Int = 0,
    var entity_deps: Int = 0,
    var adapter_deps: Int = 0,
    var port_deps: Int = 0,
    var usecase_deps: Int = 0,
    var gateway_deps: Int = 0,
    var total_cross_layer_deps: Int = 0,
    var has_business_logic: Boolean = false,
    var has_data_access: Boolean = false,
    var has_http_handling: Boolean = false,
    var has_validation: Boolean = false,
    var has_transaction: Boolean = false,
    var violates_layer_separation: Boolean = false
)