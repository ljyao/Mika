package com.wangzan101.mika.cap.phone.shell

import android.annotation.SuppressLint
import android.net.wifi.WifiInfo
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 已经由用户通过 USB 启动的 shell 助手,使用系统原生 ADB 配对服务初始化 Mika 的身份。 */
internal class ShellWirelessBootstrap {
    private var owner: String? = null
    private val timeout = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "mika-wireless-init").apply { isDaemon = true }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi", "MissingPermission")
    private fun manager(): Any {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "adb") as? IBinder ?: error("ADB service unavailable")
        return Class.forName("android.debug.IAdbManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun begin(name: String, password: String): Boolean {
        require(name.matches(Regex("mika-[0-9a-f]{16}")))
        require(password.matches(Regex("[0-9a-f]{32}")))
        if (owner != null) return false
        val identity = Binder.clearCallingIdentity()
        try {
            val adb = manager()
            if (adb.javaClass.getMethod("isAdbWifiSupported").invoke(adb) != true) return false
            val wifiBinder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "wifi") as? IBinder ?: return false
            val wifiService = Class.forName("android.net.wifi.IWifiManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, wifiBinder)
            val wifi = wifiService.javaClass.getMethod("getConnectionInfo", String::class.java, String::class.java)
                .invoke(wifiService, ShellContext.SHELL_PACKAGE, null) as? WifiInfo
            val bssid = wifi?.bssid?.takeIf { it != "02:00:00:00:00:00" && it.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) }
                ?: return false
            // 与系统的「始终允许此网络」使用同一接口,只授权当前实际连接的网络。
            adb.javaClass.getMethod("allowWirelessDebugging", Boolean::class.javaPrimitiveType, String::class.java)
                .invoke(adb, true, bssid)
            // 开启无线 ADB 与生成设备 GUID 是异步的,服务就绪后再创建配对会话。
            val deadline = SystemClock.elapsedRealtime() + 10_000
            val port = adb.javaClass.getMethod("getAdbWirelessPort")
            while ((port.invoke(adb) as Int) !in 1..65535) {
                if (SystemClock.elapsedRealtime() >= deadline) return false
                Thread.sleep(150)
            }
            owner = name
            adb.javaClass.getMethod("enablePairingByQrCode", String::class.java, String::class.java)
                .invoke(adb, name, password)
            timeout.schedule({ end(name) }, 60, TimeUnit.SECONDS)
            return true
        } catch (_: Exception) {
            end(name)
            return false
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @Synchronized
    fun end(name: String) {
        if (owner != name) return
        val identity = Binder.clearCallingIdentity()
        try {
            runCatching { val adb = manager(); adb.javaClass.getMethod("disablePairing").invoke(adb) }
        } finally {
            owner = null
            Binder.restoreCallingIdentity(identity)
        }
    }
}
