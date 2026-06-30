package com.er1cmo.xiaozhiandroid.core

/**
 * Small module registry used by AppController.
 *
 * Phase 8A-1 registers core placeholders only. MCP, CameraX, wake word and
 * notification modules will be added here instead of being wired directly into
 * MainViewModel.
 */
class ModuleManager(
    private val eventBus: AppEventBus,
) {
    private val modules = linkedMapOf<String, AppModule>()

    fun register(module: AppModule) {
        if (modules.containsKey(module.name)) return
        modules[module.name] = module
        eventBus.publish(AppEvent.ModuleRegistered(module.name))
    }

    fun get(name: String): AppModule? = modules[name]

    fun names(): List<String> = modules.keys.toList()

    suspend fun startAll() {
        modules.values.forEach { module ->
            try {
                module.start()
                eventBus.publish(AppEvent.ModuleStarted(module.name))
            } catch (exception: Exception) {
                eventBus.publish(
                    AppEvent.ModuleFailed(
                        moduleName = module.name,
                        message = exception.message ?: exception::class.java.simpleName,
                    ),
                )
            }
        }
    }

    suspend fun stopAll() {
        modules.values.toList().asReversed().forEach { module ->
            try {
                module.stop()
                eventBus.publish(AppEvent.ModuleStopped(module.name))
            } catch (exception: Exception) {
                eventBus.publish(
                    AppEvent.ModuleFailed(
                        moduleName = module.name,
                        message = exception.message ?: exception::class.java.simpleName,
                    ),
                )
            }
        }
    }

    suspend fun disposeAll() {
        modules.values.toList().asReversed().forEach { module ->
            try {
                module.dispose()
            } catch (exception: Exception) {
                eventBus.publish(
                    AppEvent.ModuleFailed(
                        moduleName = module.name,
                        message = exception.message ?: exception::class.java.simpleName,
                    ),
                )
            }
        }
        modules.clear()
    }
}
