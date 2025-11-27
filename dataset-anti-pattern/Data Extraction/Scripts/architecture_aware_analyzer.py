# architecture_aware_analyzer.py
import os
import re
import csv
from radon.raw import analyze as raw_analyze
from radon.complexity import cc_visit
from tqdm import tqdm
from collections import defaultdict

REPOS_DIR = "repos"
OUTPUT = "dataset-anti-pattern/architecture_aware_anti_patterns.csv"

# ==========================================
# STEP 1: ARCHITECTURE PATTERN DETECTION
# ==========================================
def detect_architecture_pattern(repo_path):
    """
    Detect the architecture pattern used in the repository
    Returns: ('layered'|'hexagonal'|'clean_architecture'|'mvc', confidence_score)
    """
    indicators = {
        'layered': 0,
        'hexagonal': 0,
        'clean_architecture': 0,
        'mvc': 0
    }
    
    # Scan directory structure
    for root, dirs, files in os.walk(repo_path):
        root_lower = root.lower()
        
        # Layered/MVC indicators (most common in Spring Boot)
        if 'controller' in root_lower: indicators['layered'] += 2; indicators['mvc'] += 2
        if 'service' in root_lower: indicators['layered'] += 2
        if 'repository' in root_lower or 'dao' in root_lower: indicators['layered'] += 2
        if 'entity' in root_lower or 'model' in root_lower: indicators['layered'] += 1
        
        # Hexagonal indicators
        if 'adapter' in root_lower: indicators['hexagonal'] += 3
        if 'port' in root_lower: indicators['hexagonal'] += 3
        if 'domain' in root_lower and 'adapter' in os.listdir(repo_path): indicators['hexagonal'] += 2
        if 'infrastructure' in root_lower: indicators['hexagonal'] += 2
        
        # Clean Architecture indicators
        if 'usecase' in root_lower: indicators['clean_architecture'] += 3
        if 'gateway' in root_lower: indicators['clean_architecture'] += 2
        if 'presenter' in root_lower: indicators['clean_architecture'] += 2
        if 'interface_adapter' in root_lower: indicators['clean_architecture'] += 3
        
        # Check file contents for patterns
        for file in files:
            if file.endswith('.java'):
                try:
                    filepath = os.path.join(root, file)
                    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
                        content = f.read(50000)  # Read first 50KB
                    
                    # Hexagonal patterns
                    if re.search(r'interface\s+\w+Port\s*{', content): indicators['hexagonal'] += 2
                    if re.search(r'class\s+\w+Adapter\s+implements', content): indicators['hexagonal'] += 2
                    
                    # Clean Architecture patterns
                    if re.search(r'class\s+\w+UseCase', content): indicators['clean_architecture'] += 2
                    if re.search(r'interface\s+\w+Gateway', content): indicators['clean_architecture'] += 2
                    
                except:
                    continue
    
    # Determine architecture
    total_score = sum(indicators.values())
    if total_score == 0:
        return 'layered', 0.3  # Default assumption for Spring Boot
    
    max_arch = max(indicators, key=indicators.get)
    confidence = indicators[max_arch] / total_score
    
    # If layered and mvc have similar scores, prefer mvc for Spring Boot
    if abs(indicators['layered'] - indicators['mvc']) <= 2:
        return 'mvc', confidence
    
    return max_arch, confidence

# ==========================================
# STEP 2: LAYER DETECTION
# ==========================================
def detect_layer(filepath, content):
    """Detect which architectural layer the file belongs to"""
    filepath_lower = filepath.lower()
    
    # Check annotations first (most reliable)
    if re.search(r'@(RestController|Controller)', content):
        return 'controller'
    if re.search(r'@Service', content):
        return 'service'
    if re.search(r'@Repository', content):
        return 'repository'
    if re.search(r'@Entity|@Table', content):
        return 'entity'
    
    # Check file path
    if any(x in filepath_lower for x in ['controller', 'web', 'rest', 'api']):
        return 'controller'
    if any(x in filepath_lower for x in ['service', 'business', 'usecase']):
        return 'service'
    if any(x in filepath_lower for x in ['repository', 'dao', 'jpa']):
        return 'repository'
    if any(x in filepath_lower for x in ['entity', 'model', 'domain', 'dto']):
        return 'entity'
    if 'adapter' in filepath_lower:
        return 'adapter'
    if 'port' in filepath_lower:
        return 'port'
    
    return 'other'

# ==========================================
# STEP 3: CROSS-LAYER DEPENDENCY ANALYSIS
# ==========================================
def analyze_dependencies(content, layer):
    """
    Analyze dependencies to other layers
    Returns dict with counts of dependencies to each layer
    """
    deps = {
        'controller': 0,
        'service': 0,
        'repository': 0,
        'entity': 0,
        'adapter': 0,
        'port': 0,
        'usecase': 0,
        'gateway': 0
    }
    
    # Find @Autowired and constructor injection dependencies
    autowired_pattern = r'@Autowired\s+(?:private\s+)?(\w+)\s+(\w+);'
    constructor_pattern = r'public\s+\w+\s*\(([^)]+)\)'
    
    # @Autowired dependencies
    for match in re.finditer(autowired_pattern, content):
        dep_type = match.group(1).lower()
        if 'controller' in dep_type: deps['controller'] += 1
        if 'service' in dep_type: deps['service'] += 1
        if 'repository' in dep_type or 'dao' in dep_type: deps['repository'] += 1
        if 'adapter' in dep_type: deps['adapter'] += 1
        if 'port' in dep_type: deps['port'] += 1
        if 'usecase' in dep_type: deps['usecase'] += 1
        if 'gateway' in dep_type: deps['gateway'] += 1
    
    # Constructor injection
    for match in re.finditer(constructor_pattern, content):
        params = match.group(1)
        if 'Controller' in params: deps['controller'] += 1
        if 'Service' in params: deps['service'] += 1
        if 'Repository' in params or 'Dao' in params: deps['repository'] += 1
        if 'Adapter' in params: deps['adapter'] += 1
        if 'Port' in params: deps['port'] += 1
        if 'UseCase' in params: deps['usecase'] += 1
        if 'Gateway' in params: deps['gateway'] += 1
    
    return deps

# ==========================================
# STEP 4: ARCHITECTURE-AWARE VIOLATION DETECTION
# ==========================================
def detect_architecture_violations(content, layer, architecture, deps):
    """
    Detect violations specific to the declared architecture pattern
    """
    violations = []
    severity = 'low'
    
    # ===== LAYERED / MVC ARCHITECTURE =====
    if architecture in ['layered', 'mvc']:
        # Violation 1: Controller accessing Repository directly (layer skip)
        if layer == 'controller' and deps['repository'] > 0:
            violations.append('layer_skip_in_layered')
            severity = 'high'
        
        # Violation 2: Service accessing Controller (reversed dependency)
        if layer == 'service' and deps['controller'] > 0:
            violations.append('reversed_dependency_in_layered')
            severity = 'high'
        
        # Violation 3: Business logic in Controller
        if layer == 'controller':
            business_logic_patterns = [
                r'if\s*\([^)]*\)\s*{[^}]*(?:save|update|delete|calculate)',
                r'for\s*\([^)]*\)\s*{[^}]*(?:process|compute)',
                r'\.stream\(\)\.filter\(',
                r'switch\s*\([^)]*\)\s*{[^}]*case'
            ]
            for pattern in business_logic_patterns:
                if re.search(pattern, content, re.DOTALL):
                    violations.append('business_logic_in_controller_layered')
                    severity = 'medium'
                    break
        
        # Violation 4: Missing @Transactional in Service
        if layer == 'service':
            if re.search(r'\.(save|delete|update)\(', content) and not re.search(r'@Transactional', content):
                violations.append('missing_transaction_in_layered')
                severity = 'high'
    
    # ===== HEXAGONAL ARCHITECTURE =====
    elif architecture == 'hexagonal':
        # Violation 1: Service/Domain accessing infrastructure directly
        if layer == 'service':
            if deps['repository'] > 0 and deps['port'] == 0:
                violations.append('missing_port_adapter_in_hexagonal')
                severity = 'critical'
        
        # Violation 2: Domain layer depending on framework
        if layer == 'service':
            framework_imports = [
                r'import\s+org\.springframework\.',
                r'import\s+javax\.persistence\.',
                r'import\s+org\.hibernate\.'
            ]
            for pattern in framework_imports:
                if re.search(pattern, content):
                    violations.append('framework_dependency_in_domain_hexagonal')
                    severity = 'critical'
                    break
        
        # Violation 3: Missing port interfaces
        if layer == 'adapter' and not re.search(r'implements\s+\w+Port', content):
            violations.append('adapter_without_port_hexagonal')
            severity = 'medium'
    
    # ===== CLEAN ARCHITECTURE =====
    elif architecture == 'clean_architecture':
        # Violation 1: Outer layer depending on inner layer details
        if layer == 'controller' and (deps['entity'] > 0 or deps['repository'] > 0):
            violations.append('outer_depends_on_inner_clean')
            severity = 'critical'
        
        # Violation 2: Use case depending on frameworks
        if layer == 'service':  # UseCase layer
            if re.search(r'@(Controller|RestController|Repository|Entity)', content):
                violations.append('usecase_framework_coupling_clean')
                severity = 'critical'
        
        # Violation 3: Entity with framework annotations
        if layer == 'entity':
            if re.search(r'@(Entity|Table|Column|Id)', content):
                violations.append('entity_framework_coupling_clean')
                severity = 'medium'
        
        # Violation 4: Missing gateway interface
        if layer == 'service' and deps['repository'] > 0 and deps['gateway'] == 0:
            violations.append('missing_gateway_interface_clean')
            severity = 'high'
    
    # ===== COMMON ANTI-PATTERNS (all architectures) =====
    
    # Broad exception catching
    if re.search(r'catch\s*\(\s*(Exception|Throwable)\s+', content):
        violations.append('broad_catch')
        if severity == 'low': severity = 'medium'
    
    # Missing input validation
    if layer == 'controller':
        if re.search(r'@(PostMapping|PutMapping).*@RequestBody', content, re.DOTALL):
            if not re.search(r'@Valid|@Validated', content):
                violations.append('no_validation')
                if severity == 'low': severity = 'medium'
    
    # Tight coupling (using 'new' keyword for dependencies)
    if re.search(r'new\s+(.*?)(Service|Repository|Dao|Adapter)\(', content):
        violations.append('tight_coupling_new_keyword')
        if severity == 'low': severity = 'medium'
    
    return violations if violations else ['clean'], severity

# ==========================================
# STEP 5: CODE CHARACTERISTIC ANALYSIS
# ==========================================
def analyze_code_characteristics(content, layer):
    """Analyze what the code does"""
    characteristics = {
        'has_business_logic': False,
        'has_data_access': False,
        'has_http_handling': False,
        'has_validation': False,
        'has_transaction': False
    }
    
    # Business logic indicators
    business_patterns = [r'if\s*\(', r'for\s*\(', r'while\s*\(', r'switch\s*\(', r'\.stream\(\)']
    characteristics['has_business_logic'] = any(re.search(p, content) for p in business_patterns)
    
    # Data access
    data_patterns = [r'\.(save|find|delete|update|query|execute)\(', r'@Query', r'JpaRepository']
    characteristics['has_data_access'] = any(re.search(p, content) for p in data_patterns)
    
    # HTTP handling
    http_patterns = [r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)', 
                     r'HttpServletRequest', r'HttpServletResponse']
    characteristics['has_http_handling'] = any(re.search(p, content) for p in http_patterns)
    
    # Validation
    characteristics['has_validation'] = bool(re.search(r'@Valid|@Validated|@NotNull|@NotEmpty', content))
    
    # Transaction
    characteristics['has_transaction'] = bool(re.search(r'@Transactional', content))
    
    return characteristics

# ==========================================
# STEP 6: DEPENDENCY DIRECTION ANALYSIS
# ==========================================
def analyze_dependency_direction(layer, deps, architecture):
    """
    Analyze if dependency direction is correct for the architecture
    """
    if architecture in ['layered', 'mvc']:
        # Correct flow: Controller -> Service -> Repository -> Entity
        if layer == 'controller':
            if deps['repository'] > 0 or deps['entity'] > 0:
                return 'skip_layer'  # Skipping service layer
            elif deps['service'] > 0:
                return 'correct'
        elif layer == 'service':
            if deps['controller'] > 0:
                return 'reversed'  # Service shouldn't depend on controller
            elif deps['repository'] >= 0:
                return 'correct'
        elif layer == 'repository':
            if deps['service'] > 0 or deps['controller'] > 0:
                return 'reversed'
            else:
                return 'correct'
    
    elif architecture == 'hexagonal':
        # Correct: Adapter -> Port <- Domain
        if layer == 'adapter':
            if deps['port'] > 0:
                return 'correct'
            else:
                return 'missing_port'
        elif layer == 'service':  # Domain
            if deps['adapter'] > 0:
                return 'reversed'  # Domain shouldn't depend on adapter
            elif deps['port'] > 0:
                return 'correct'
    
    elif architecture == 'clean_architecture':
        # Correct: Outer layers depend on inner, never reverse
        if layer == 'controller':  # Outer
            if deps['usecase'] > 0 or deps['gateway'] > 0:
                return 'correct'
            elif deps['entity'] > 0 or deps['repository'] > 0:
                return 'dependency_rule_violation'
        elif layer == 'service':  # Use case
            if deps['controller'] > 0:
                return 'reversed'
            elif deps['gateway'] > 0 or deps['entity'] > 0:
                return 'correct'
    
    return 'unknown'

# ==========================================
# STEP 7: FILE ANALYSIS
# ==========================================
def analyze_file(filepath, architecture, architecture_confidence):
    """Complete analysis of a single Java file"""
    try:
        with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read(5_000_000)  # Max 5MB
    except:
        return None

    # Skip if empty or too small
    if len(content) < 50:
        return None

    try:
        raw = raw_analyze(content)
    except:
        return None

    layer = detect_layer(filepath, content)
    
    # Skip non-Spring Boot files
    if layer == 'other':
        return None

    # Analyze dependencies
    deps = analyze_dependencies(content, layer)
    
    # Code characteristics
    characteristics = analyze_code_characteristics(content, layer)
    
    # Dependency direction
    dep_direction = analyze_dependency_direction(layer, deps, architecture)
    
    # Architecture violations
    violations, severity = detect_architecture_violations(content, layer, architecture, deps)
    
    # Calculate metrics
    methods_count = 0
    if hasattr(raw, 'functions'):
        methods_count += len(raw.functions)
    if hasattr(raw, 'methods'):
        methods_count += len(raw.methods)

    # Determine primary anti-pattern
    primary_anti_pattern = violations[0] if violations and violations[0] != 'clean' else 'clean'
    
    # Create context-specific label (THIS IS KEY FOR ML)
    context_label = f"{primary_anti_pattern}_in_{architecture}_{layer}"
    if primary_anti_pattern == 'clean':
        context_label = f"clean_{architecture}_{layer}"
    
    # Build row
    row = {
        "file": os.path.basename(filepath),
        "repo": os.path.basename(os.path.dirname(os.path.dirname(filepath))) or "unknown",
        "layer": layer,
        "architecture_pattern": architecture,
        "architecture_confidence": round(architecture_confidence, 2),
        
        # Code metrics
        "loc": getattr(raw, 'loc', 0),
        "methods": methods_count,
        "classes": len(getattr(raw, 'classes', [])),
        "avg_cc": round(safe_cc(content), 2),
        "imports": len(re.findall(r"^import\s", content, re.MULTILINE)),
        "annotations": len(re.findall(r"@[A-Za-z]", content)),
        
        # Dependencies
        "controller_deps": deps['controller'],
        "service_deps": deps['service'],
        "repository_deps": deps['repository'],
        "entity_deps": deps['entity'],
        "adapter_deps": deps['adapter'],
        "port_deps": deps['port'],
        "usecase_deps": deps['usecase'],
        "gateway_deps": deps['gateway'],
        "total_cross_layer_deps": sum(deps.values()),
        
        # Characteristics
        "has_business_logic": characteristics['has_business_logic'],
        "has_data_access": characteristics['has_data_access'],
        "has_http_handling": characteristics['has_http_handling'],
        "has_validation": characteristics['has_validation'],
        "has_transaction": characteristics['has_transaction'],
        
        # Architecture analysis
        "dependency_direction": dep_direction,
        "violates_layer_separation": dep_direction in ['skip_layer', 'reversed', 'dependency_rule_violation', 'missing_port'],
        
        # Anti-patterns
        "anti_pattern": primary_anti_pattern,
        "all_violations": '|'.join(violations),
        "severity": severity,
        
        # ML training label (CRITICAL)
        "context_specific_label": context_label
    }

    return row

def safe_cc(content):
    try:
        results = cc_visit(content)
        return sum(c.complexity for c in results) / len(results) if results else 1.0
    except:
        return 1.0

# ==========================================
# MAIN EXECUTION
# ==========================================
def main():
    if not os.path.exists(REPOS_DIR):
        print(f"ERROR: '{REPOS_DIR}' folder not found!")
        return

    print("="*70)
    print("ARCHITECTURE-AWARE ANTI-PATTERN DATASET GENERATION")
    print("="*70)
    
    # STEP 1: Detect architecture for each repository
    print("\n📐 Step 1: Detecting architecture patterns...")
    repo_architectures = {}
    
    repo_dirs = [d for d in os.listdir(REPOS_DIR) if os.path.isdir(os.path.join(REPOS_DIR, d))]
    
    for repo_name in tqdm(repo_dirs, desc="Detecting architectures"):
        repo_path = os.path.join(REPOS_DIR, repo_name)
        arch, confidence = detect_architecture_pattern(repo_path)
        repo_architectures[repo_name] = (arch, confidence)
    
    # Show architecture distribution
    arch_dist = defaultdict(int)
    for arch, _ in repo_architectures.values():
        arch_dist[arch] += 1
    
    print("\n📊 Architecture Distribution:")
    for arch, count in arch_dist.items():
        print(f"  {arch}: {count} repositories")
    
    # STEP 2: Analyze Java files
    print("\n🔍 Step 2: Analyzing Java files...")
    java_files = []
    for root, _, files in os.walk(REPOS_DIR):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(root, f))

    print(f"Found {len(java_files)} Java files\n")

    rows = []
    for path in tqdm(java_files, desc="Analyzing files"):
        # Get repository and its architecture
        parts = path.split(os.sep)
        repo_name = parts[1] if len(parts) > 1 else "unknown"
        arch, confidence = repo_architectures.get(repo_name, ('layered', 0.5))
        
        result = analyze_file(path, arch, confidence)
        if result:
            rows.append(result)

    # STEP 3: Save dataset
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", newline="", encoding="utf-8") as f:
        if rows:
            writer = csv.DictWriter(f, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)

    # STEP 4: Show statistics
    print("\n" + "="*70)
    print("✅ SUCCESS! ARCHITECTURE-AWARE DATASET CREATED")
    print("="*70)
    print(f"\n📁 Dataset saved: {OUTPUT}")
    print(f"📊 Total samples: {len(rows)}")
    
    # Create DataFrame for analysis
    import pandas as pd
    df = pd.DataFrame(rows)
    
    print(f"\n📐 Architecture Pattern Distribution:")
    print(df['architecture_pattern'].value_counts())
    
    print(f"\n🏗️  Layer Distribution:")
    print(df['layer'].value_counts())
    
    print(f"\n⚠️  Anti-Pattern Distribution:")
    print(df['anti_pattern'].value_counts())
    
    print(f"\n🎯 Context-Specific Label Distribution (for ML training):")
    print(df['context_specific_label'].value_counts().head(20))
    
    print(f"\n📈 Severity Distribution:")
    print(df['severity'].value_counts())
    
    print("\n" + "="*70)
    print("✅ Dataset is ready for ML model training!")
    print("="*70)

if __name__ == "__main__":
    main()