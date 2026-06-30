package com.er1cmo.xiaozhiandroid.core

/**
 * Reverse-order cleanup registry.
 *
 * This mirrors the resource-pool idea from py-xiaozhi, but keeps it Android
 * lifecycle friendly and explicit.
 */
class ResourceRegistry(
    private val eventBus: AppEventBus,
) {
    private data class Entry(
        val name: String,
        val cleanup: suspend () -> Unit,
    )

    private val entries = mutableListOf<Entry>()

    fun register(name: String, cleanup: suspend () -> Unit) {
        entries += Entry(name = name, cleanup = cleanup)
    }

    suspend fun shutdown() {
        entries.asReversed().forEach { entry ->
            try {
                entry.cleanup()
            } catch (exception: Exception) {
                eventBus.publish(
                    AppEvent.Error(
                        message = "释放资源失败：${entry.name}：${exception.message ?: exception::class.java.simpleName}",
                        cause = exception,
                    ),
                )
            }
        }
        entries.clear()
    }
}
