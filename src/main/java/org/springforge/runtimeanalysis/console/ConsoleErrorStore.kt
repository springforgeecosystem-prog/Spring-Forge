package org.springforge.runtimeanalysis.console

object ConsoleErrorStore {

    private var error: String? = null

    fun set(value: String) {
        error = value
    }

    fun get(): String? {
        return error
    }

    fun clear() {
        error = null
    }
}
