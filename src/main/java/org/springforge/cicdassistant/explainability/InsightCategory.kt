package org.springforge.cicdassistant.explainability

enum class InsightCategory {
    SECURITY,       // Non-root user, secret exposure, image hardening
    PERFORMANCE,    // Layer caching, multi-stage builds, JVM tuning
    BUILD,          // Build tool choices, dependency ordering, copy ordering
    CONFIGURATION,  // Ports, env vars, volume mounts, labels
    RELIABILITY,    // Health checks, restart policies, resource limits
    DESIGN          // Architecture patterns, service dependencies, naming
}
