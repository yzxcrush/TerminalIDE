package com.terminalide.capture

/**
 * 常见 App 的域名映射（包名 → 域名列表）。
 * 用于在「应用列表」里勾选 App 时，自动把它的域名加入/移出黑名单。
 */
object AppDomainMap {

    val MAP: Map<String, List<String>> = mapOf(
        // 社交
        "com.tencent.mm" to listOf("weixin.qq.com", "wx.qq.com", "wechat.com", "servicewechat.com", "qpic.cn"),
        "com.tencent.mobileqq" to listOf("qq.com", "gtimg.cn", "qlogo.cn", "idqqimg.com"),
        "com.sina.weibo" to listOf("weibo.com", "weibo.cn", "sinaimg.cn", "sina.com.cn"),
        "com.xingin.xhs" to listOf("xiaohongshu.com", "xhscdn.com", "xhsimg.com"),
        "com.ss.android.ugc.aweme" to listOf("douyin.com", "amemv.com", "snssdk.com", "douyinpic.com"),
        "com.ss.android.article.news" to listOf("toutiao.com", "snssdk.com", "toutiaoimg.com"),
        // 支付 / 金融
        "com.eg.android.AlipayGphone" to listOf("alipay.com", "alipayobjects.com", "alipay.cn"),
        "com.unionpay" to listOf("unionpay.com", "95516.com"),
        "com.icbc" to listOf("icbc.com.cn", "icbc.com"),
        "com.chinamworld.main" to listOf("ccb.com", "ccb.cn"),
        "cmb.pb" to listOf("cmbchina.com", "cmbimg.com"),
        // 电商
        "com.taobao.taobao" to listOf("taobao.com", "alicdn.com", "tbcdn.cn", "tmall.com"),
        "com.jingdong.app.mall" to listOf("jd.com", "360buyimg.com", "jd.hk"),
        "com.xunmeng.pinduoduo" to listOf("pinduoduo.com", "yangkeduo.com", "pddpic.com"),
        "com.taobao.idlefish" to listOf("goofish.com", "taobao.com"),
        // 内容 / 视频
        "tv.danmaku.bili" to listOf("bilibili.com", "hdslb.com", "biliapi.net", "bilibili.tv"),
        "com.ss.android.ugc.aweme.lite" to listOf("douyin.com", "snssdk.com"),
        "com.tencent.qqlive" to listOf("qq.com", "gtimg.cn", "video.qq.com"),
        "com.qiyi.video" to listOf("iqiyi.com", "qiyipic.com", "71.am"),
        "com.youku.phone" to listOf("youku.com", "ykimg.com"),
        "com.netease.cloudmusic" to listOf("music.163.com", "126.net", "163.com"),
        // 出行 / 生活
        "com.sdu.didi.psnger" to listOf("didiglobal.com", "xiaojukeji.com", "didialift.com"),
        "com.autonavi.minimap" to listOf("amap.com", "autonavi.com", "gaode.com"),
        "com.baidu.BaiduMap" to listOf("baidu.com", "bdimg.com", "map.baidu.com"),
        "com.sankuai.meituan" to listOf("meituan.com", "meituan.net", "dianping.com"),
        "me.ele" to listOf("ele.me", "elemecdn.com"),
        // 工具
        "com.alibaba.android.rimet" to listOf("dingtalk.com", "alicdn.com", "alipay.com"),
        "com.tencent.wework" to listOf("work.weixin.qq.com", "qq.com", "gtimg.cn"),
        "com.baidu.searchbox" to listOf("baidu.com", "bdstatic.com", "bdimg.com"),
        "com.UCMobile" to listOf("uc.cn", "ucweb.com", "alibaba.com"),
        "org.telegram.messenger" to listOf("telegram.org", "t.me", "telegram.me", "cdn-telegram.org"),
        "com.whatsapp" to listOf("whatsapp.com", "whatsapp.net"),
        "com.google.android.gm" to listOf("gmail.com", "google.com", "googleapis.com"),
        "com.android.chrome" to listOf("google.com", "gstatic.com", "googleapis.com"),
    )

    /** 已知域名数据的所有包名。 */
    fun knownPackages(): Set<String> = MAP.keys

    /** 取某个包名的域名列表。 */
    fun domainsOf(pkg: String): List<String> = MAP[pkg] ?: emptyList()

    /** 反查：这个域名属于哪个 App（用于展示）。 */
    fun appOfDomain(domain: String): String? =
        MAP.entries.firstOrNull { (_, domains) -> domains.any { domain == it || domain.endsWith(".$it") } }?.key
}
