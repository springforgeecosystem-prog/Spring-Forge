package org.springforge.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * SpringForge Icon Provider
 */
object SpringForgeIcons {

    /**
     * Tool window icon (13x13 SVG for best display)
     * IntelliJ will automatically load the appropriate size for HiDPI displays
     */
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/springforge.svg", SpringForgeIcons::class.java)

    /**
     * Large logo icon (for dialogs, about screens, etc.)
     */
    @JvmField
    val Logo: Icon = IconLoader.getIcon("/icons/springforge.png", SpringForgeIcons::class.java)
}
