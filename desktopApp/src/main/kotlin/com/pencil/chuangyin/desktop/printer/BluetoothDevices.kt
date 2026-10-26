package com.pencil.chuangyin.desktop.printer

import com.fazecast.jSerialComm.SerialPort
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * 蓝牙设备枚举。
 *
 * Windows 没有像 Android `BluetoothAdapter.getBondedDevices()` 那样的一站式 API
 * （扫描周边设备需要 WinRT 异步互操作，在 JNA 下不可靠），因此桌面端采用**已配对设备**模型：
 * 用户在系统设置里完成配对，本模块读出已配对设备与它们生成的 SPP 串口。
 *
 * 数据源（按重要性排序）：
 *  1. `HKLM\SYSTEM\CurrentControlSet\Services\BTHPORT\Parameters\Devices\<MAC>` ——
 *     **已配对设备主表**。系统里每一台配对过的设备都在这里（含 Windows 设置里被归到
 *     「其他设备」分类、尚未创建任何服务节点的打印机）；键名就是设备地址（正常字节序），
 *     默认值 / `LEName` 是设备名；
 *  2. `HKLM\SYSTEM\CurrentControlSet\Enum\BTHENUM` —— 已创建服务实例的设备节点，
 *     串口服务的端口名在 `Device Parameters\PortName`；
 *  3. jSerialComm 的端口描述 —— 兜底识别「Standard Serial over Bluetooth link」。
 *
 * 注意：只扫 BTHENUM 会漏掉「其他设备」分类的打印机（它没有服务节点），这正是早期版本
 * 只能看到音频 / 键盘等设备的原因，因此必须以 BTHPORT 为主表、BTHENUM 只用来补串口。
 */
object BluetoothDevices {

    private val HKEY = WinReg.HKEY_LOCAL_MACHINE

    private const val BTHENUM_ROOT = "SYSTEM\\CurrentControlSet\\Enum\\BTHENUM"
    private const val BTHPORT_DEVICES = "SYSTEM\\CurrentControlSet\\Services\\BTHPORT\\Parameters\\Devices"
    private const val BT_CLASS_KEY = "SYSTEM\\CurrentControlSet\\Control\\Class\\{e0cbf06c-cd8b-4647-bb8a-263b43f0f974}"

    private const val MAX_DEPTH = 4
    private const val MAX_KEYS = 4_000

    private val MAC_REGEX = Regex("(?i)[0-9a-f]{12}")

    /** 系统里是否存在可用的蓝牙适配器。 */
    fun availability(): BluetoothAvailability = if (hasAdapter()) {
        BluetoothAvailability.Available
    } else {
        BluetoothAvailability.NoAdapter
    }

    fun hasAdapter(): Boolean = try {
        Advapi32Util.registryKeyExists(HKEY, BT_CLASS_KEY) &&
            Advapi32Util.registryGetKeys(HKEY, BT_CLASS_KEY).isNotEmpty()
    } catch (_: Throwable) {
        false
    }

    /**
     * 已配对设备列表（含只有串口信息的退化条目）。
     *
     * 主表是 BTHPORT 的「已配对设备数据库」，因此 Windows 设置里归类为「其他设备」的打印机
     * 同样会出现在这里；BTHENUM 只负责补上它生成的 SPP 串口。
     *
     * 结果按「有串口 > 有设备名 > 其它」排序，方便用户直接找到打印机。
     */
    fun pairedDevices(): List<PrinterDevice> {
        val remembered = scanBthPortDevices()
        val services = scanBthEnumServices()
        val serialPorts = bluetoothSerialPorts()

        // MAC -> SPP 串口
        val ports = services.mapNotNull { entry ->
            val mac = entry.mac ?: return@mapNotNull null
            val port = entry.comPort ?: return@mapNotNull null
            mac to port
        }.toMap()

        val merged = LinkedHashMap<String, PrinterDevice>()

        // 1) 已配对主表（含「其他设备」分类的打印机）
        for ((mac, name) in remembered) {
            merged[mac] = PrinterDevice(
                name = cleanDeviceName(name) ?: formatMac(mac),
                macAddress = formatMac(mac),
                comPort = ports[mac],
            )
        }

        // 2) 只在 BTHENUM 里出现、BTHPORT 没读到的设备（权限不足时的保底）
        for (entry in services) {
            val mac = entry.mac ?: continue
            val existing = merged[mac]
            val port = entry.comPort ?: existing?.comPort
            if (existing == null) {
                merged[mac] = PrinterDevice(
                    name = cleanDeviceName(entry.name) ?: formatMac(mac),
                    macAddress = formatMac(mac),
                    comPort = port,
                )
            } else if (existing.comPort == null && port != null) {
                merged[mac] = existing.copy(comPort = port)
            }
        }

        // 3) 只识别到串口、拿不到 MAC 的条目（保证仍可打印）
        val usedPorts = merged.values.mapNotNull { it.comPort }.toSet()
        for (port in serialPorts) {
            if (port !in usedPorts) {
                merged["COM:$port"] = PrinterDevice(name = "蓝牙串口（$port）", macAddress = "", comPort = port)
            }
        }

        // 没有设备名、也没有串口的条目是系统里的残留 LE 隐私地址，列出来只会干扰用户。
        return merged.values
            .filter { it.comPort != null || it.name != it.macAddress }
            .sortedWith(
                compareByDescending<PrinterDevice> { it.comPort != null }
                    .thenByDescending { it.macAddress.isNotBlank() }
                    .thenBy { it.displayName.lowercase() },
            )
    }

    /** 该 MAC 对应的 SPP 虚拟串口名（如 `COM4`）；没有则 null。 */
    fun sppPortFor(mac: String): String? {
        val normalized = normalizeMac(mac) ?: return null
        return scanBthEnumServices().firstOrNull { it.mac == normalized }?.comPort
    }

    /** 系统里看起来像蓝牙串口的端口名列表。 */
    fun bluetoothSerialPorts(): List<String> = try {
        SerialPort.getCommPorts()
            .filter { port ->
                val description = port.portDescription.orEmpty().lowercase()
                val path = runCatching { port.systemPortPath.orEmpty() }.getOrDefault("").lowercase()
                description.contains("bluetooth") ||
                    description.contains("bth") ||
                    path.contains("bth")
            }
            .map { it.systemPortName }
    } catch (_: Throwable) {
        emptyList()
    }

    // ---------------------------------------------------------------- 注册表扫描

    private data class ServiceEntry(val mac: String?, val name: String?, val comPort: String?)

    private fun scanBthEnumServices(): List<ServiceEntry> {
        val results = ArrayList<ServiceEntry>()
        var visited = 0
        fun walk(path: String, depth: Int, nameHint: String?, macHint: String?) {
            if (depth > MAX_DEPTH || visited >= MAX_KEYS) return
            visited++
            val children = try {
                Advapi32Util.registryGetKeys(HKEY, path)
            } catch (_: Throwable) {
                return
            }
            if (children.isEmpty()) {
                val comPort = readString(path, "PortName")
                    ?: readString("$path\\Device Parameters", "PortName")
                val friendly = nameHint ?: readString(path, "FriendlyName")
                if (macHint != null && (comPort != null || friendly != null)) {
                    results += ServiceEntry(macHint, friendly, comPort)
                }
                return
            }
            for (child in children) {
                if (visited >= MAX_KEYS) return
                val childPath = "$path\\$child"
                val childMac = normalizeMac(child) ?: macHint
                val childName = readString(childPath, "FriendlyName") ?: nameHint
                walk(childPath, depth + 1, childName, childMac)
            }
        }
        walk(BTHENUM_ROOT, 0, null, null)
        return results
    }

    /**
     * BTHPORT 已配对主表：MAC -> 设备名（读不到名字时为 null）。
     *
     * 这是 Windows 自己的配对数据库，键名即设备地址（正常字节序，与 BTHENUM 里的 MAC 一致），
     * 设备名存在默认值（REG_BINARY / UTF-8）里，低功耗设备另存 `LEName`。
     */
    private fun scanBthPortDevices(): Map<String, String?> {
        val result = LinkedHashMap<String, String?>()
        val keys = try {
            Advapi32Util.registryGetKeys(HKEY, BTHPORT_DEVICES)
        } catch (_: Throwable) {
            return result
        }
        for (key in keys) {
            val mac = normalizeMac(key) ?: continue
            val path = "$BTHPORT_DEVICES\\$key"
            result[mac] = readNameValue(path) ?: readString(path, "LEName")
        }
        return result
    }

    /** BTHPORT 的设备名是 REG_BINARY 存 UTF-8；兼容被存成 REG_SZ / 名为 `Name` 的情况。 */
    private fun readNameValue(path: String): String? = try {
        val values = Advapi32Util.registryGetValues(HKEY, path)
        val raw = values[""] ?: values["Name"]
        when (raw) {
            is ByteArray -> raw.toString(Charsets.UTF_8)
            is String -> raw
            else -> null
        }?.trim()?.trimEnd('\u0000')
    } catch (_: Throwable) {
        null
    }

    private fun readString(path: String, name: String): String? = try {
        if (!Advapi32Util.registryValueExists(HKEY, path, name)) {
            null
        } else {
            Advapi32Util.registryGetStringValue(HKEY, path, name).takeIf { it.isNotBlank() }
        }
    } catch (_: Throwable) {
        null
    }

    // ---------------------------------------------------------------- 地址工具

    /**
     * 清理注册表里的「间接字符串」设备名。
     *
     * PnP 友好名常见形态：`@System32\drivers\bthhfenum.sys,#1;%1 Hands-Free AG%0;(BULL TWS W40)`，
     * 真正可读的是末尾括号里的设备名；`%1` / `%0` 是未解析的占位符，需要整体剥掉，
     * 否则连接页会直接显示这串谁也看不懂的注册表路径。
     */
    private fun cleanDeviceName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var name = raw.trim()
        if (name.startsWith("@")) {
            name = name.substringAfterLast(';').trim()
            val bracket = BRACKET_TAIL_REGEX.find(name)
            name = if (bracket != null) {
                bracket.groupValues[1].trim()
            } else {
                name.replace(PLACEHOLDER_REGEX, "").trim()
            }
        }
        return name.takeIf { it.isNotBlank() }
    }

    private val BRACKET_TAIL_REGEX = Regex("""\(([^()]+)\)\s*$""")
    private val PLACEHOLDER_REGEX = Regex("""%\d""")

    private fun normalizeMac(raw: String): String? = MAC_REGEX.find(raw)?.value?.lowercase()

    private fun formatMac(normalized: String): String =
        normalized.uppercase().chunked(2).joinToString(":")
}
