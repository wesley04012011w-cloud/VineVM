package com.vinevm.core

interface VmBackend {
    val type: BackendType
    fun isAvailable(): Boolean
    fun start(config: VmConfig): Result<Unit>
    fun stop(): Result<Unit>
}

class VineOsBackend : VmBackend {
    override val type = BackendType.VINEOS
    override fun isAvailable(): Boolean = true
    override fun start(config: VmConfig): Result<Unit> = Result.success(Unit)
    override fun stop(): Result<Unit> = Result.success(Unit)
}

class QemuBackend : VmBackend {
    override val type = BackendType.QEMU
    override fun isAvailable(): Boolean = false
    override fun start(config: VmConfig): Result<Unit> =
        Result.failure(UnsupportedOperationException("QEMU backend ainda não integrado"))
    override fun stop(): Result<Unit> = Result.success(Unit)
}

class AvfBackend : VmBackend {
    override val type = BackendType.AVF
    override fun isAvailable(): Boolean = android.os.Build.VERSION.SDK_INT >= 31
    override fun start(config: VmConfig): Result<Unit> =
        Result.failure(UnsupportedOperationException("AVF backend será integrado na fase 2"))
    override fun stop(): Result<Unit> = Result.success(Unit)
}

class BackendSelector {
    private val backends = listOf(VineOsBackend(), QemuBackend(), AvfBackend())

    fun select(preference: BackendType): VmBackend {
        if (preference != BackendType.AUTO) {
            return backends.first { it.type == preference }
        }
        return backends.firstOrNull { it.type == BackendType.AVF && it.isAvailable() }
            ?: backends.first { it.type == BackendType.VINEOS && it.isAvailable() }
    }
}
