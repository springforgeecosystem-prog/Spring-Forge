# SpringForge Sidebar Tool Window

## Overview

The SpringForge plugin now includes a comprehensive sidebar tool window that provides quick access to all SpringForge features directly from the IntelliJ IDEA sidebar, similar to Maven, Copilot, and other popular extensions.

## Features

The sidebar includes 4 main tabs:

### 1. **Code Gen** (Code Generation)
- Create New Spring Boot Project
- Analyze Existing Project (Architecture Detection)
- Generate LLM Prompt for Code Generation

### 2. **CI/CD** (CI/CD Pipeline Generator)
- Source Selection: Local Project or GitHub Repository
- Generate:
  - Dockerfile
  - GitHub Actions Workflow
  - Docker Compose
  - Kubernetes Manifests
- Real-time progress tracking
- Output console for generation results

### 3. **Quality** (Quality Assurance)
- ML-powered architecture violation detection
- Anti-pattern analysis
- Results displayed directly in the panel

### 4. **Runtime** (Runtime Debugger)
- Runtime performance monitoring
- Memory leak detection
- Request tracing
- Metrics collection

## How to Access

### Opening the Tool Window

1. **From the sidebar**: Look for the "SpringForge" tab on the right side of your IDE
2. **From the menu**: View → Tool Windows → SpringForge
3. **Using keyboard**: Press `Alt+S` (Windows/Linux) or `⌘+S` (Mac) - *if configured*

### Using the Tool Window

1. Click on the SpringForge tab in the right sidebar
2. The tool window will open showing all 4 tabs
3. Click on any tab to access its features:
   - **Code Gen**: Click "Run" buttons to execute code generation tasks
   - **CI/CD**: Select options and click "Generate CI/CD Files"
   - **Quality**: Click "Analyze" to run code quality checks
   - **Runtime**: Click "Start" to launch the runtime debugger

## CI/CD Panel Usage

### Local Project Analysis
1. Select "Local Project" radio button
2. Choose which files to generate (Dockerfile, GitHub Actions, etc.)
3. Click "Generate CI/CD Files"
4. Monitor progress in the output console
5. Generated files will be saved to your project root

### GitHub Repository Analysis
1. Select "GitHub Repository" radio button
2. Enter the repository URL (e.g., `https://github.com/spring-projects/spring-petclinic`)
3. Enter the branch name (default: `main`)
4. Choose which files to generate
5. Click "Generate CI/CD Files"
6. Monitor progress in the output console

**Requirements for GitHub Analysis:**
- Docker Desktop must be installed and running
- GitHub Personal Access Token must be configured in `.env` file

## Benefits of Sidebar Approach

### Compared to Menu Actions:
- ✅ **Always Accessible**: No need to navigate through menus
- ✅ **Persistent UI**: Keep the panel open while working
- ✅ **Real-time Feedback**: See progress and results immediately
- ✅ **Better Organization**: All features grouped in one place
- ✅ **Workflow Integration**: Works alongside Maven, Gradle, Git tabs

### User Experience:
- Similar to GitHub Copilot's sidebar interface
- Familiar tab-based navigation
- Clean, modern UI with IntelliJ native components
- Progress indicators for long-running tasks
- Output console for detailed logs

## Architecture

### Components

```
org.springforge.toolwindow/
├── SpringForgeToolWindowFactory.kt    # Main factory that creates the tool window
├── SpringForgeToolWindowService.kt    # Service to manage tool window state
├── SpringForgeToolWindowPanel.kt      # Main panel with tabs
└── panels/
    ├── CICDPanel.kt                   # CI/CD generation panel
    ├── CodeGenerationPanel.kt         # Code generation panel
    ├── QualityAssurancePanel.kt       # Quality analysis panel
    └── RuntimeDebuggerPanel.kt        # Runtime debugger panel
```

### Plugin Configuration

The tool window is registered in `plugin.xml`:

```xml
<toolWindow
    id="SpringForge"
    anchor="right"
    factoryClass="org.springforge.toolwindow.SpringForgeToolWindowFactory"/>
```

## Customization

### Changing Position
By default, the tool window appears on the right side (`anchor="right"`). You can change this in `plugin.xml`:
- `anchor="left"` - Left sidebar
- `anchor="bottom"` - Bottom panel
- `anchor="right"` - Right sidebar (default)

### Adding Custom Tabs
To add a new tab to the tool window:

1. Create a new panel in `org.springforge.toolwindow.panels/`:
```kotlin
class MyCustomPanel(private val project: Project) : JPanel() {
    init {
        layout = BorderLayout()
        // Add your UI components
    }
}
```

2. Register it in `SpringForgeToolWindowPanel.kt`:
```kotlin
private val myCustomPanel = MyCustomPanel(project)

tabbedPane.apply {
    addTab("Code Gen", codeGenPanel)
    addTab("CI/CD", cicdPanel)
    addTab("Quality", qualityPanel)
    addTab("Runtime", runtimePanel)
    addTab("My Custom", myCustomPanel)  // Add your tab
}
```

## Migration from Menu Actions

The sidebar tool window provides the same functionality as the menu actions:

| Menu Action | Sidebar Location |
|------------|------------------|
| Tools → SpringForge → Generate Code → Create New Project | Code Gen Tab → Create New Spring Boot Project |
| Tools → SpringForge → Generate Code → Existing Project | Code Gen Tab → Analyze Existing Project |
| Tools → SpringForge → Generate Code → Generate Prompt | Code Gen Tab → Generate LLM Prompt |
| Tools → SpringForge → Generate CI/CD Pipeline | CI/CD Tab → Generate CI/CD Files |
| Tools → SpringForge → Analyze Code Quality | Quality Tab → Analyze Code Quality |
| Tools → SpringForge → Start Runtime Debugger | Runtime Tab → Start Runtime Debugger |

**Note**: Both the sidebar and menu actions remain available. Users can choose their preferred workflow.

## Troubleshooting

### Tool Window Not Appearing
1. Check that the plugin is properly installed
2. Restart IntelliJ IDEA
3. Go to View → Tool Windows and look for "SpringForge"

### Panel Features Not Working
1. Ensure all required dependencies are installed (AWS credentials, Docker, etc.)
2. Check the IntelliJ event log for errors
3. Review the plugin logs in `.idea/` directory

### GitHub Integration Not Working
1. Verify Docker Desktop is running
2. Check that `GITHUB_PERSONAL_ACCESS_TOKEN` is set in `.env`
3. Ensure the repository URL is valid

## Future Enhancements

Potential improvements for future versions:
- Drag-and-drop support for files
- Inline editing of generated files
- History of previous generations
- Favorites/bookmarks for frequently used templates
- Settings panel within the tool window
- Keyboard shortcuts for quick actions

## Feedback

If you encounter any issues or have suggestions for the sidebar tool window, please:
1. Check the troubleshooting section above
2. Review existing GitHub issues
3. Open a new issue with detailed reproduction steps

---

**Version**: 1.0.0
**Last Updated**: January 2026
