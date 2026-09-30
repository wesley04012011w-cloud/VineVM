package com.vinevm.core

enum class VmState { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

enum class BackendType { AUTO, VINEOS, QEMU, AVF }

data class VmConfig(
    val id: String,
    val name: String,
    val romPath: String,
    val ramMb: Int = 2048,
    val cpuCores: Int = 4,
    val storageMb: Int = 8192,
    val backend: BackendType = BackendType.AUTO
)
