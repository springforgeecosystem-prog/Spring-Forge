package org.springforge.runtimeanalysis.console

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment

class SpringForgeRunListener : ExecutionListener {

    override fun processStarted(
            executorId: String,
            env: ExecutionEnvironment,
            handler: ProcessHandler
    ) {
        handler.addProcessListener(ConsoleErrorListener(env.project))
    }
}

