package com.terminalide.capture

/**
 * 抓包过滤规则：
 *  - 黑名单命中 → **不做 MITM、不记录**（直接透明转发，流量保持加密）
 *  - 白名单非空时 → 只抓白名单内的域名，其余同样透明转发
 *  - 黑名单优先于白名单
 *
 * 域名匹配：完全相等 或 以 ".域名" 结尾（即包含所有子域名）。
 */
object CaptureFilter {

    @Volatile
    var blacklist: List<String> = emptyList()

    @Volatile
    var whitelist: List<String> = emptyList()

    /** 是否应该解密并记录该 host。 */
    fun shouldCapture(host: String): Boolean {
        if (blacklist.any { matches(host, it) }) return false
        if (whitelist.isNotEmpty() && whitelist.none { matches(host, it) }) return false
        return true
    }

    private fun matches(host: String, pattern: String): Boolean {
        val p = pattern.trim().lowercase()
        if (p.isEmpty()) return false
        val h = host.lowercase()
        return h == p || h.endsWith(".$p")
    }

    /** 把多行文本解析成域名列表。 */
    fun parse(text: String): List<String> =
        text.split('\n', ',', '，', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()

    fun format(list: List<String>): String = list.joinToString("\n")

    /** 常用敏感 App 的预设域名（建议放黑名单）。 */
    val SENSITIVE_PRESET: List<String> = listOf(
        "weixin.qq.com", "wx.qq.com", "wechat.com", "servicewechat.com",
        "qq.com", "gtimg.cn",
        "alipay.com", "alipayobjects.com", "alipay.cn",
        "unionpay.com", "95516.com",
    )
}
