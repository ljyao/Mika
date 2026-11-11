package com.wangzan101.mika.core.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

/** 只有真实的本机 TLS 连接验证成功后才能保存配对完成,失败与取消都关闭临时配对服务。 */
class WirelessBootstrap(private val port: Port, private val secret: () -> String = { UUID.randomUUID().toString().replace("-", "") }) {
    interface Port {
        suspend fun start(name: String, password: String): Boolean
        suspend fun find(name: String): Int?
        suspend fun pair(port: Int, password: String)
        suspend fun verify()
        fun savePaired()
        suspend fun stop(name: String)
    }

    suspend fun initialize(): Boolean {
        val name = "mika-${secret().take(16)}"
        val password = secret()
        try {
            if (!port.start(name, password)) return false
            val pairingPort = port.find(name) ?: return false
            port.pair(pairingPort, password)
            port.verify()
            port.savePaired()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        } finally {
            withContext(NonCancellable) { runCatching { port.stop(name) } }
        }
    }
}

enum class WirelessBootstrapState { Idle, Preparing, Ready, NeedUsb, NoWifi, Disabled, Failed }
