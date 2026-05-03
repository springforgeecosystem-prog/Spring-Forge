# Spring-Forge

<div align="center">

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/springforgeecosystem-prog/Spring-Forge)
[![IntelliJ Plugin](https://img.shields.io/badge/IntelliJ-2024.3%2B-orange.svg)](https://www.jetbrains.com/idea/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://openjdk.org/projects/jdk/21/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.23-purple.svg)](https://kotlinlang.org/)

**Architecture-aware Spring Boot development toolkit for IntelliJ IDEA**

[Features](#features) • [Installation](#installation) • [Quick Start](#quick-start) • [Documentation](#documentation) • [Contributing](#contributing)

</div>

---

## 🚀 Overview

SpringForge is a comprehensive IntelliJ IDEA plugin that streamlines Spring Boot development with AI-powered code generation, architecture analysis, and CI/CD automation. It combines ML-based pattern detection with AWS Bedrock's Claude AI to help developers build better Spring Boot applications faster.

### Key Capabilities

- 🏗️ **Architecture-Aware Code Generation** - Generate Spring Boot projects with proper architectural patterns
- 🤖 **AI-Powered CI/CD** - Automatically generate Dockerfiles, GitHub Actions, and Kubernetes manifests
- 🔍 **Quality Analysis** - ML-based detection of architecture violations and anti-patterns
- 🐛 **Runtime Debugging** - Advanced runtime analysis and performance monitoring
- 🌐 **GitHub Integration** - Analyze remote repositories via MCP protocol

---

## ✨ Features

### 1. Code Generation Module

Generate production-ready Spring Boot projects with intelligent scaffolding:

- **Create New Projects** - Full project setup with architecture template selection
- **Existing Project Analysis** - ML-powered architecture pattern detection
- **Smart Scaffolding** - Generate controllers, services, repositories with proper layers
- **LLM Prompt Generation** - Parse `input.yml` and build context for AI code generation

**Supported Architecture Patterns:**
- Layered Architecture
- Hexagonal (Ports & Adapters)
- Clean Architecture
- Event-Driven Architecture
- Microservices

### 2. CI/CD Assistant

AI-powered DevOps artifact generation using AWS Bedrock Claude Sonnet 4.5:

- **Dockerfile Generation** - Optimized multi-stage builds with architecture detection
- **GitHub Actions Workflows** - Complete CI/CD pipelines with testing, building, and deployment
- **Docker Compose** - Multi-service configurations with detected dependencies
- **Kubernetes Manifests** - Production-ready deployments, services, and ingress

**Source Options:**
- 📁 Local IntelliJ project analysis
- 🔗 Remote GitHub repository analysis (via GitHub MCP Server)

### 3. Quality Assurance

ML-powered architecture violation detection:

- **Anti-Pattern Detection** - Identify common Spring Boot anti-patterns
- **Architecture Compliance** - Validate adherence to architectural principles
- **Severity Levels** - Critical, High, Medium classifications
- **Detailed Reports** - File-level violations with recommendations

### 4. Runtime Debugger

Advanced runtime analysis tools:

- Performance monitoring
- Memory leak detection
- Request tracing
- Metrics collection

### 5. Unified Tool Window

Modern sidebar interface with tabbed navigation:

- Always accessible from IntelliJ sidebar
- Real-time progress tracking
- Integrated output console
- Similar UX to Maven, Gradle, and Copilot panels

---

## 📋 Requirements

### Minimum Requirements

- **IntelliJ IDEA**: 2024.3 or later (Ultimate Edition recommended)
- **Java**: JDK 21 or later
- **Operating System**: Windows, macOS, or Linux

### Required for Full Functionality

- **AWS Account** with Bedrock access (for CI/CD generation)
  - Claude Sonnet 4 model enabled in `us-east-1`
  - IAM credentials with `bedrock:InvokeModel` permission

### Optional (for GitHub Integration)

- **Docker Desktop** (for GitHub MCP Server)
- **GitHub Personal Access Token** (for remote repository analysis)

---

## 🔧 Installation

### Option 1: Install from JetBrains Marketplace (Coming Soon)

1. Open IntelliJ IDEA
2. Go to **Settings** → **Plugins** → **Marketplace**
3. Search for "SpringForge Tools"
4. Click **Install** and restart IDE

### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/springforgeecosystem-prog/Spring-Forge.git
cd Spring-Forge

# Build the plugin
./gradlew buildPlugin

# The plugin will be in build/distributions/
```

Install the plugin:
1. Go to **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk**
2. Select `build/distributions/Spring-Forge-1.0-SNAPSHOT.zip`
3. Restart IntelliJ IDEA

---

## ⚙️ Configuration

### 1. AWS Bedrock Setup (Required for CI/CD)

Enable Claude Sonnet 4 in AWS Bedrock:

```bash
# Verify Bedrock access
aws bedrock list-foundation-models --region us-east-1 \
  --query 'modelSummaries[?contains(modelId, `claude-sonnet-4`)]'
```

Create `.env` file in your project root:

```bash
cp .env.example .env
```

Edit `.env` with your credentials:

```env
# AWS Bedrock Configuration
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your_access_key_here
AWS_SECRET_ACCESS_KEY=your_secret_access_key_here

# Claude Configuration (optional - uses defaults)
CLAUDE_MODEL_ID=us.anthropic.claude-sonnet-4-20250514-v1:0
CLAUDE_MAX_TOKENS=4000
```

**IAM Policy Required:**
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "bedrock:InvokeModel",
    "Resource": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-sonnet-4*"
  }]
}
```

### 2. GitHub MCP Setup (Optional)

For analyzing remote GitHub repositories:

**Step 1: Install Docker Desktop**
- Download from [docker.com](https://www.docker.com/products/docker-desktop)
- Start Docker Desktop

**Step 2: Create GitHub Personal Access Token**
1. Go to [GitHub Settings → Tokens](https://github.com/settings/tokens)
2. Create token with `repo`, `public_repo`, `read:org` permissions
3. Copy the token

**Step 3: Configure Environment**

Add to `.env`:
```env
GITHUB_PERSONAL_ACCESS_TOKEN=ghp_your_token_here
GITHUB_HOST=https://github.com
GITHUB_READ_ONLY=true
```

**Verify Setup:**
```bash
# Test GitHub MCP connectivity
docker ps  # Ensure Docker is running
```

📖 **Detailed Setup Guide**: [docs/github-mcp-setup.md](docs/github-mcp-setup.md)

---

## 🚀 Quick Start

### Using the Tool Window (Recommended)

1. **Open SpringForge Sidebar**
   - Look for "SpringForge" tab on the right side of IntelliJ
   - Or go to **View** → **Tool Windows** → **SpringForge**

2. **Choose a Module**
   - **Code Gen** - Generate new projects or analyze existing ones
   - **CI/CD** - Generate DevOps artifacts
   - **Quality** - Analyze code quality
   - **Runtime** - Launch runtime debugger

### Example: Generate CI/CD Files

**Method 1: Using Tool Window (New!)**

1. Click **SpringForge** in right sidebar
2. Switch to **CI/CD** tab
3. Select source:
   - ✅ Local Project (current IntelliJ project)
   - ✅ GitHub Repository (enter URL like `https://github.com/spring-projects/spring-petclinic`)
4. Check files to generate:
   - ✅ Dockerfile
   - ✅ GitHub Actions Workflow
   - ✅ Docker Compose
5. Click **Generate CI/CD Files**
6. Monitor progress in output console

**Method 2: Using Menu Actions**

1. Open a Spring Boot project in IntelliJ
2. Go to **Tools** → **SpringForge** → **Generate CI/CD Pipeline**
3. Select source and options
4. Click **OK**

**Output Files:**
```
your-project/
├── Dockerfile                    # Multi-stage optimized build
├── docker-compose.yml            # Multi-service orchestration
├── .github/
│   └── workflows/
│       └── build.yml             # Complete CI/CD pipeline
└── k8s/
    └── deployment.yml            # Kubernetes manifests
```

### Example: Analyze Code Quality

1. Open **SpringForge** tool window
2. Switch to **Quality** tab
3. Click **Analyze Code Quality**
4. View results in the panel:
   - Architecture pattern detected
   - Violations by severity
   - Affected files and recommendations

### Example: Create New Project

1. Open **SpringForge** tool window
2. Switch to **Code Gen** tab
3. Click **Create New Spring Boot Project**
4. Select:
   - Architecture pattern (Layered, Hexagonal, Clean, etc.)
   - Dependencies (Web, JPA, Security, etc.)
   - Project metadata (group, artifact, package)
5. Click **Generate**

---

## 📚 Documentation

### User Guides

- [Getting Started Guide](docs/getting-started.md) - Complete walkthrough
- [CI/CD Generation Guide](docs/cicd-guide.md) - Detailed CI/CD usage
- [GitHub MCP Setup](docs/github-mcp-setup.md) - Remote repository analysis
- [Sidebar Tool Window Guide](docs/sidebar-tool-window-setup.md) - Using the unified interface
- [Custom Icon Guide](docs/custom-icon-guide.md) - Customize plugin appearance

### Technical Documentation

- [Architecture Overview](docs/architecture.md) - Plugin architecture
- [GitHub MCP Protocol](docs/github-mcp-module-detection.md) - MCP integration details
- [Testing Guide](docs/how-to-test-mcp-integration.md) - Testing MCP features
- [Troubleshooting](docs/aws-credentials-troubleshooting.md) - Common issues

### Feature Documentation

- [Branch Auto-Fetch](docs/branch-auto-fetch-feature.md)
- [Architecture Detection](docs/github-architecture-detection-fix.md)
- [Enhanced Results Display](docs/GITHUB_MCP_IMPLEMENTATION_SUMMARY.md)

---

## 🏗️ Architecture

### Project Structure

```
Spring-Forge/
├── src/main/
│   ├── java/org/springforge/
│   │   ├── cicdassistant/          # CI/CD generation module
│   │   │   ├── actions/            # IntelliJ actions
│   │   │   ├── bedrock/            # AWS Bedrock client
│   │   │   ├── github/             # GitHub MCP integration
│   │   │   ├── mcp/                # MCP protocol models
│   │   │   ├── parsers/            # Code analyzers
│   │   │   └── services/           # Business logic
│   │   ├── codegeneration/         # Code generation module
│   │   │   ├── actions/            # Project creation actions
│   │   │   └── ui/                 # UI dialogs
│   │   ├── qualityassurance/       # Quality analysis module
│   │   │   ├── actions/            # Analysis actions
│   │   │   ├── toolwindow/         # Legacy tool window
│   │   │   └── ui/                 # UI components
│   │   ├── runtimeanalysis/        # Runtime debugger module
│   │   │   └── actions/            # Debugger actions
│   │   ├── toolwindow/             # Unified sidebar panel (NEW)
│   │   │   ├── panels/             # Tab panels
│   │   │   ├── SpringForgeToolWindowFactory.kt
│   │   │   ├── SpringForgeToolWindowPanel.kt
│   │   │   └── SpringForgeToolWindowService.kt
│   │   └── icons/                  # Icon provider
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml          # Plugin configuration
│       └── icons/                  # Plugin icons
├── docs/                           # Documentation
├── build.gradle.kts                # Gradle build configuration
└── .env.example                    # Environment template
```

### Key Technologies

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Plugin Framework** | IntelliJ Platform SDK | IDE integration |
| **Language** | Kotlin 1.9.23 | Plugin development |
| **AI Integration** | AWS Bedrock + Claude Sonnet 4.5 | Code generation |
| **Code Analysis** | JavaParser 3.25.7 | AST analysis |
| **MCP Protocol** | Custom JSON-RPC 2.0 | GitHub integration |
| **HTTP Client** | OkHttp 4.11.0 | Network communication |
| **YAML Parsing** | SnakeYAML 2.1 | Configuration parsing |
| **Async Operations** | Kotlin Coroutines | Background tasks |

---

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/springforgeecosystem-prog/Spring-Forge.git
   cd Spring-Forge
   ```

2. **Open in IntelliJ IDEA**
   - File → Open → Select `Spring-Forge` directory
   - Wait for Gradle sync to complete

3. **Configure environment**
   ```bash
   cp .env.example .env
   # Add your AWS credentials
   ```

4. **Run the plugin**
   - Click **Run** → **Run Plugin**
   - New IntelliJ window opens with plugin installed

### Development Workflow

1. **Create a feature branch**
   ```bash
   git checkout -b feat/your-feature-name
   ```

2. **Make changes**
   - Follow Kotlin coding conventions
   - Add tests for new features
   - Update documentation

3. **Test thoroughly**
   ```bash
   ./gradlew test
   ./gradlew runPluginVerifier
   ```

4. **Commit with conventional commits**
   ```bash
   git commit -m "feat: add new feature description"
   ```

   Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

5. **Push and create PR**
   ```bash
   git push origin feat/your-feature-name
   ```

### Code Standards

- **Kotlin Style**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Documentation**: Add KDoc comments for public APIs
- **Testing**: Write unit tests for business logic
- **Error Handling**: Use proper exception handling and user-friendly messages

### Areas for Contribution

- 🐛 **Bug Fixes** - Check [Issues](https://github.com/springforgeecosystem-prog/Spring-Forge/issues)
- ✨ **New Features** - Architecture patterns, generators, analyzers
- 📖 **Documentation** - Improve guides, add examples
- 🧪 **Testing** - Increase test coverage
- 🎨 **UI/UX** - Enhance user interface

---

## 🧪 Testing

### Run Tests

```bash
# Run all tests
./gradlew test

# Run plugin verifier (compatibility check)
./gradlew runPluginVerifier

# Run with coverage
./gradlew test jacocoTestReport
```

### Manual Testing

1. **Test CI/CD Generation**
   ```bash
   # Use test project
   cd test-projects/spring-petclinic
   # Generate via plugin
   ```

2. **Test GitHub Integration**
   ```bash
   # Run MCP verification
   ./test-github-mcp.ps1
   ```

3. **Test Quality Analysis**
   - Open a Spring Boot project
   - Run quality analysis from tool window
   - Verify results are accurate

---

## 📦 Building for Distribution

### Build Plugin ZIP

```bash
./gradlew buildPlugin
```

Output: `build/distributions/Spring-Forge-1.0-SNAPSHOT.zip`

### Build for Marketplace

```bash
# Set version in build.gradle.kts
version = "1.0.0"

# Build
./gradlew buildPlugin

# Verify
./gradlew runPluginVerifier
```

---

## 🐛 Troubleshooting

### Common Issues

**❌ AWS Credentials Not Working**
- Verify credentials in `.env` file
- Check IAM permissions for Bedrock
- Test with: `aws bedrock list-foundation-models --region us-east-1`
- See: [AWS Credentials Troubleshooting](docs/aws-credentials-troubleshooting.md)

**❌ GitHub MCP Connection Failed**
- Ensure Docker Desktop is running
- Verify `GITHUB_PERSONAL_ACCESS_TOKEN` in `.env`
- Check token permissions: `repo`, `public_repo`
- See: [GitHub MCP Debugging](docs/github-mcp-debugging-changes.md)

**❌ Plugin Not Showing in Sidebar**
- Restart IntelliJ IDEA
- Check: **View** → **Tool Windows** → **SpringForge**
- Verify plugin is enabled in Settings → Plugins

**❌ Icon Not Displaying**
- Rebuild plugin: `./gradlew clean buildPlugin`
- Reinstall plugin
- See: [Custom Icon Guide](docs/custom-icon-guide.md)

### Getting Help

1. Check [Documentation](docs/)
2. Search [Issues](https://github.com/springforgeecosystem-prog/Spring-Forge/issues)
3. Create new issue with:
   - Plugin version
   - IntelliJ version
   - Error logs from **Help** → **Show Log in Explorer**

---

## 📝 Changelog

### Version 1.0.0 (2026-01-03)

#### ✨ Features
- **Unified Tool Window** - New sidebar interface with 4 modules
- **GitHub MCP Integration** - Analyze remote repositories
- **Enhanced CI/CD Generation** - AWS Bedrock Claude Sonnet 4.5 integration
- **Architecture Detection** - ML-powered pattern recognition
- **Background Task Progress** - Real-time progress tracking

#### 🐛 Bug Fixes
- Fixed GitHub branch auto-detection
- Resolved Docker line-ending issues
- Improved error handling for MCP protocol

#### 📖 Documentation
- Added comprehensive setup guides
- Created troubleshooting documentation
- Added API documentation

See [full changelog](CHANGELOG.md) for complete history.

---


## 🙏 Acknowledgments

- **AWS Bedrock** - Claude AI integration
- **Anthropic** - Claude Sonnet 4.5 model
- **JetBrains** - IntelliJ Platform SDK
- **Spring Framework** - Architecture patterns and best practices
- **JavaParser** - Java code analysis
- **Model Context Protocol (MCP)** - GitHub integration protocol

---

## 📞 Contact & Support

- **Issues**: [GitHub Issues](https://github.com/springforgeecosystem-prog/Spring-Forge/issues)
- **Discussions**: [GitHub Discussions](https://github.com/springforgeecosystem-prog/Spring-Forge/discussions)
- **Email**: springforgeecosystem@gmail.com

---

## 🗺️ Roadmap

### v1.1.0 (Planned)
- [ ] JetBrains Marketplace release
- [ ] Additional architecture patterns (CQRS, Event Sourcing)
- [ ] Terraform/CloudFormation generation
- [ ] GitLab CI integration
- [ ] Enhanced test generation

### v1.2.0 (Future)
- [ ] Multi-module project support
- [ ] Custom architecture templates
- [ ] Integration with SonarQube
- [ ] Performance benchmarking tools
- [ ] Database migration generators

### v2.0.0 (Vision)
- [ ] VS Code extension
- [ ] CLI tool for CI/CD integration
- [ ] Cloud-based analysis service
- [ ] Team collaboration features

---

<div align="center">

**Made with ❤️ by the SpringForge Team**

⭐ Star us on GitHub — it helps!

[⬆ Back to Top](#springforge-tools)

</div>
