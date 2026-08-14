package com.yangyx.iptools.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.yangyx.iptools.data.geo.Ip2RegionSearcher
import com.yangyx.iptools.data.geo.IpGeoEngine
import com.yangyx.iptools.data.geo.IpGeoInfo
import com.yangyx.iptools.data.geo.PublicIpInfo
import com.yangyx.iptools.data.local.AppDatabase
import com.yangyx.iptools.data.local.FavoriteIp
import com.yangyx.iptools.data.local.HistoryRecord
import com.yangyx.iptools.data.network.NetworkInfoScanner
import com.yangyx.iptools.data.network.NetworkOverview
import com.yangyx.iptools.data.network.NetworkSpeed
import com.yangyx.iptools.data.tools.DnsQueryResult
import com.yangyx.iptools.data.tools.DnsRecordResult
import com.yangyx.iptools.data.tools.DnsWhoisEngine
import com.yangyx.iptools.data.tools.FrpClientConfig
import com.yangyx.iptools.data.tools.FrpConfigStorage
import com.yangyx.iptools.data.tools.FrpEngine
import com.yangyx.iptools.data.tools.FrpProxyConfig
import com.yangyx.iptools.data.tools.FrpServerConfig
import com.yangyx.iptools.data.tools.FrpStatus
import com.yangyx.iptools.data.tools.FscanEngine
import com.yangyx.iptools.data.tools.FscanHostResult
import com.yangyx.iptools.data.tools.FscanProgress
import com.yangyx.iptools.data.tools.IperfEngine
import com.yangyx.iptools.data.tools.IperfPoint
import com.yangyx.iptools.data.tools.PingEngine
import com.yangyx.iptools.data.tools.PingPacketResult
import com.yangyx.iptools.data.tools.PingSummary
import com.yangyx.iptools.data.tools.PortScanEngine
import com.yangyx.iptools.data.tools.PortScanProgress
import com.yangyx.iptools.data.tools.RootState
import com.yangyx.iptools.data.tools.RootUtils
import com.yangyx.iptools.data.tools.TraceEngine
import com.yangyx.iptools.data.tools.TraceHop
import com.yangyx.iptools.data.tools.WhoisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen(val title: String, val iconName: String) {
    HOME("网络概览", "Home"),
    PING("Ping 测试", "Ping"),
    TRACE("路由追踪 Trace", "Trace"),
    PORT_SCAN("端口扫描", "PortScan"),
    FSCAN("Fscan 扫描", "Fscan"),
    IPERF("iPerf 测速", "Iperf"),
    DNS("DNS 解析", "Dns"),
    WHOIS("Whois 查询", "Whois"),
    IP_GEO("IP 归属地", "IpGeo"),
    FRP("FRP 穿透", "Frp"),
    FAVORITES("IP 收藏夹", "Favorites"),
    HISTORY("历史记录", "History")
}

data class SelectedIpAction(
    val ip: String,
    val sourceTool: String = ""
)

data class SpeedSample(
    val timestampMs: Long = System.currentTimeMillis(),
    val downloadBytesPerSec: Long = 0,
    val uploadBytesPerSec: Long = 0
)

enum class SpeedTimeRange(val label: String, val seconds: Int) {
    MIN_1("最近 1 min", 60),
    MIN_5("最近 5 min", 300),
    MIN_15("最近 15 min", 900)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    init {
        Ip2RegionSearcher.init(application)
    }

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "iptools.db"
    ).fallbackToDestructiveMigration().build()

    val favoriteIps: StateFlow<List<FavoriteIp>> = db.favoriteIpDao().getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyRecords: StateFlow<List<HistoryRecord>> = db.historyRecordDao().getRecentHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Theme Mode: null = system default, true = dark, false = light
    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    fun toggleDarkMode(dark: Boolean?) {
        _isDarkMode.value = dark
    }

    // Current Screen
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // IP Action Bottom Sheet State
    private val _selectedIpAction = MutableStateFlow<SelectedIpAction?>(null)
    val selectedIpAction: StateFlow<SelectedIpAction?> = _selectedIpAction.asStateFlow()

    fun selectIpForAction(ip: String, source: String = "") {
        if (ip.isNotBlank() && ip != "*") {
            _selectedIpAction.value = SelectedIpAction(ip.trim(), source)
        }
    }

    fun dismissIpAction() {
        _selectedIpAction.value = null
    }

    // Background Active Task State
    private val _activeTaskName = MutableStateFlow<String?>(null)
    val activeTaskName: StateFlow<String?> = _activeTaskName.asStateFlow()

    // 1. Home / Device Network State
    private val _networkOverview = MutableStateFlow(NetworkOverview())
    val networkOverview: StateFlow<NetworkOverview> = _networkOverview.asStateFlow()

    private val _publicIpV4 = MutableStateFlow("查询中...")
    val publicIpV4: StateFlow<String> = _publicIpV4.asStateFlow()

    private val _publicIpV6 = MutableStateFlow("查询中...")
    val publicIpV6: StateFlow<String> = _publicIpV6.asStateFlow()

    private val _publicIpV4Details = MutableStateFlow<PublicIpInfo?>(null)
    val publicIpV4Details: StateFlow<PublicIpInfo?> = _publicIpV4Details.asStateFlow()

    private val _publicIpV6Details = MutableStateFlow<PublicIpInfo?>(null)
    val publicIpV6Details: StateFlow<PublicIpInfo?> = _publicIpV6Details.asStateFlow()

    private val _currentSpeed = MutableStateFlow(NetworkSpeed())
    val currentSpeed: StateFlow<NetworkSpeed> = _currentSpeed.asStateFlow()

    // Speed History chart
    private val _speedSamples = MutableStateFlow<List<SpeedSample>>(emptyList())
    val speedSamples: StateFlow<List<SpeedSample>> = _speedSamples.asStateFlow()

    val selectedSpeedRange = MutableStateFlow(SpeedTimeRange.MIN_1)

    // Subnet Devices scan in Home
    private val _lanScanProgress = MutableStateFlow<FscanProgress?>(null)
    val lanScanProgress: StateFlow<FscanProgress?> = _lanScanProgress.asStateFlow()
    private val _isLanScanning = MutableStateFlow(false)
    val isLanScanning: StateFlow<Boolean> = _isLanScanning.asStateFlow()

    // System Active DNS Server
    private val _systemDnsServer = MutableStateFlow("223.5.5.5")
    val systemDnsServer: StateFlow<String> = _systemDnsServer.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Root Privilege State
    private val _rootState = MutableStateFlow(RootState.CHECKING)
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    fun refreshRootState() {
        viewModelScope.launch(Dispatchers.IO) {
            _rootState.value = RootState.CHECKING
            _rootState.value = RootUtils.checkRootState()
        }
    }

    init {
        refreshNetworkOverview()
        startSpeedMonitoring()
        fetchPublicIps()
        registerNetworkCallback()
        refreshRootState()
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshNetworkOverview()
                    fetchPublicIps()
                }

                override fun onLost(network: Network) {
                    refreshNetworkOverview()
                    fetchPublicIps()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    refreshNetworkOverview()
                }
            }
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
    }

    fun refreshNetworkOverview() {
        viewModelScope.launch {
            _networkOverview.value = NetworkInfoScanner.getNetworkOverview(getApplication())
            _systemDnsServer.value = NetworkInfoScanner.getActiveDnsServer(getApplication())
        }
    }

    fun fetchPublicIps() {
        viewModelScope.launch {
            _publicIpV4.value = "查询中..."
            _publicIpV6.value = "查询中..."
            _publicIpV4Details.value = null
            _publicIpV6Details.value = null

            val v4Info = IpGeoEngine.getPublicIpV4Info()
            val v6Info = IpGeoEngine.getPublicIpV6Info()

            _publicIpV4Details.value = v4Info
            _publicIpV6Details.value = v6Info

            _publicIpV4.value = if (v4Info.ip.isNotBlank()) v4Info.ip else "未配置 / 无公网 IPv4"
            _publicIpV6.value = if (v6Info.ip.isNotBlank()) v6Info.ip else "未配置 / 无公网 IPv6"
        }
    }

    private fun startSpeedMonitoring() {
        viewModelScope.launch {
            NetworkInfoScanner.getSpeedTrafficFlow().collect { speed ->
                _currentSpeed.value = speed
                val sample = SpeedSample(
                    timestampMs = System.currentTimeMillis(),
                    downloadBytesPerSec = speed.downloadSpeedBytesPerSec,
                    uploadBytesPerSec = speed.uploadSpeedBytesPerSec
                )
                // Keep max 900 samples (15 minutes)
                _speedSamples.value = (_speedSamples.value + sample).takeLast(900)
            }
        }
    }


    fun scanLanSubnet() {
        if (_isLanScanning.value) return
        val lanIp = _networkOverview.value.lanIpV4
        if (lanIp == "未连接" || lanIp.isBlank()) return

        val subnetCidr = lanIp.substringBeforeLast(".") + ".0/24"
        viewModelScope.launch {
            _isLanScanning.value = true
            _activeTaskName.value = "局域网设备扫描"
            FscanEngine.executeFscan(context = getApplication(), targetInput = subnetCidr, portRangeInput = "80, 443, 445, 22", timeoutMs = 400).collect { progress ->
                _lanScanProgress.value = progress
            }
            _isLanScanning.value = false
            _activeTaskName.value = null
        }
    }

    // 2. Ping State
    val pingHost = MutableStateFlow("8.8.8.8")
    val pingCount = MutableStateFlow(4)
    val isContinuousPing = MutableStateFlow(false)
    val pingIntervalMs = MutableStateFlow(1000)
    val pingTimeoutMs = MutableStateFlow(1000)
    val pingDontFragment = MutableStateFlow(false)
    val pingSize = MutableStateFlow(56)
    val pingTtl = MutableStateFlow(64)
    private val _pingPackets = MutableStateFlow<List<PingPacketResult>>(emptyList())
    val pingPackets: StateFlow<List<PingPacketResult>> = _pingPackets.asStateFlow()
    private val _pingSummary = MutableStateFlow<PingSummary?>(null)
    val pingSummary: StateFlow<PingSummary?> = _pingSummary.asStateFlow()
    private val _isPingRunning = MutableStateFlow(false)
    val isPingRunning: StateFlow<Boolean> = _isPingRunning.asStateFlow()
    private var pingJob: Job? = null

    fun startPing() {
        pingJob?.cancel()
        _pingPackets.value = emptyList()
        _pingSummary.value = null
        _isPingRunning.value = true
        _activeTaskName.value = "Ping: ${pingHost.value}"

        val actualCount = if (isContinuousPing.value) -1 else pingCount.value

        pingJob = viewModelScope.launch {
            PingEngine.executePing(
                host = pingHost.value,
                count = actualCount,
                size = pingSize.value,
                timeoutMs = pingTimeoutMs.value,
                intervalMs = pingIntervalMs.value,
                dontFragment = pingDontFragment.value,
                ttl = pingTtl.value
            ).collect { (pkt, summary) ->
                if (pkt != null) {
                    _pingPackets.value = _pingPackets.value + pkt
                }
                if (summary != null) {
                    _pingSummary.value = summary
                    _isPingRunning.value = false
                    _activeTaskName.value = null
                    recordHistory("Ping", pingHost.value, "Loss: ${summary.packetLossPercentage.toInt()}%, Avg: ${summary.avgRttMs}ms")
                }
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        _isPingRunning.value = false
        _activeTaskName.value = null
    }

    // 3. Trace State
    val traceHost = MutableStateFlow("1.1.1.1")
    val traceMaxHops = MutableStateFlow(20)
    val traceTimeoutMs = MutableStateFlow(1500)
    val traceMode = MutableStateFlow("ICMP") // ICMP or UDP
    private val _traceHops = MutableStateFlow<List<TraceHop>>(emptyList())
    val traceHops: StateFlow<List<TraceHop>> = _traceHops.asStateFlow()
    private val _isTraceRunning = MutableStateFlow(false)
    val isTraceRunning: StateFlow<Boolean> = _isTraceRunning.asStateFlow()
    private var traceJob: Job? = null

    fun startTrace() {
        traceJob?.cancel()
        _traceHops.value = emptyList()
        _isTraceRunning.value = true
        _activeTaskName.value = "Trace: ${traceHost.value}"

        traceJob = viewModelScope.launch {
            TraceEngine.executeTraceroute(
                targetHost = traceHost.value,
                maxHops = traceMaxHops.value,
                timeoutMs = traceTimeoutMs.value,
                mode = traceMode.value
            ).collect { hop ->
                _traceHops.value = _traceHops.value + hop
            }
            _isTraceRunning.value = false
            _activeTaskName.value = null
            recordHistory("Trace", traceHost.value, "Tracked ${_traceHops.value.size} hops")
        }
    }

    fun stopTrace() {
        traceJob?.cancel()
        _isTraceRunning.value = false
        _activeTaskName.value = null
    }

    // 4. Port Scan State
    val portScanTarget = MutableStateFlow("127.0.0.1")
    val portScanPortsText = MutableStateFlow("21, 22, 80, 443, 3306, 6379, 8080, 3389")
    val portScanConcurrencyText = MutableStateFlow("100")
    val portScanTimeoutText = MutableStateFlow("400")
    private val _portScanProgress = MutableStateFlow<PortScanProgress?>(null)
    val portScanProgress: StateFlow<PortScanProgress?> = _portScanProgress.asStateFlow()
    private val _isPortScanRunning = MutableStateFlow(false)
    val isPortScanRunning: StateFlow<Boolean> = _isPortScanRunning.asStateFlow()
    private var portScanJob: Job? = null

    fun startPortScan() {
        portScanJob?.cancel()
        _portScanProgress.value = null
        _isPortScanRunning.value = true
        _activeTaskName.value = "端口扫描: ${portScanTarget.value}"

        val ports = PortScanEngine.parsePortRange(portScanPortsText.value)
        val concurrency = portScanConcurrencyText.value.toIntOrNull()?.coerceIn(10, 500) ?: 100
        val timeoutMs = portScanTimeoutText.value.toIntOrNull()?.coerceIn(50, 5000) ?: 400

        portScanJob = viewModelScope.launch {
            PortScanEngine.scanPorts(
                host = portScanTarget.value,
                portsToScan = ports,
                timeoutMs = timeoutMs,
                concurrency = concurrency
            ).collect { progress ->
                _portScanProgress.value = progress
            }
            _isPortScanRunning.value = false
            _activeTaskName.value = null
            val openCount = _portScanProgress.value?.openPorts?.size ?: 0
            recordHistory("PortScan", portScanTarget.value, "Found $openCount open ports out of ${ports.size}")
        }
    }

    fun stopPortScan() {
        portScanJob?.cancel()
        _isPortScanRunning.value = false
        _activeTaskName.value = null
    }

    // 5. Fscan State
    val fscanTargetText = MutableStateFlow("")
    val fscanPortRangeText = MutableStateFlow("21,22,23,25,53,80,81,88,110,111,135,139,143,161,389,443,445,465,502,512,513,514,515,548,554,587,623,636,873,902,993,995,1080,1099,1194,1433,1434,1521,1522,1525,1723,1883,2049,2121,2181,2200,2222,2375,2376,2379,2380,3000,3128,3268,3269,3306,3389,3690,4369,4444,4848,5000,5005,5044,5060,5432,5601,5631,5632,5671,5672,5900,5984,5985,5986,6000,6379,6380,6443,6666,6667,7001,7002,7474,7687,8000,8005,8008,8009,8080,8081,8086,8088,8089,8090,8161,8180,8443,8500,8834,8848,8880,8883,8888,9000,9001,9042,9080,9090,9092,9093,9160,9200,9300,9418,9443,9999,10000,10051,10250,10255,11211,15672,22222,26379,27017,27018,50000,50070,50075,61613,61614,61616")
    val fscanThreadCountText = MutableStateFlow("1000")
    val fscanTimeoutSecText = MutableStateFlow("1")
    val fscanDisableBrute = MutableStateFlow(true)
    val fscanDisablePing = MutableStateFlow(false)
    val fscanEnableWebScan = MutableStateFlow(true)

    private val _fscanProgress = MutableStateFlow<FscanProgress?>(null)
    val fscanProgress: StateFlow<FscanProgress?> = _fscanProgress.asStateFlow()
    private val _isFscanRunning = MutableStateFlow(false)
    val isFscanRunning: StateFlow<Boolean> = _isFscanRunning.asStateFlow()
    private var fscanJob: Job? = null

    fun startFscan() {
        fscanJob?.cancel()
        _fscanProgress.value = null
        _isFscanRunning.value = true
        _activeTaskName.value = "Fscan 扫描: ${fscanTargetText.value}"

        val threads = fscanThreadCountText.value.toIntOrNull()?.coerceIn(1, 10000) ?: 1000
        val timeoutSec = fscanTimeoutSecText.value.toIntOrNull()?.coerceIn(1, 60) ?: 1

        fscanJob = viewModelScope.launch {
            try {
                FscanEngine.executeFscan(
                    context = getApplication(),
                    targetInput = fscanTargetText.value,
                    portRangeInput = fscanPortRangeText.value,
                    disableBrute = fscanDisableBrute.value,
                    disablePing = fscanDisablePing.value,
                    enableWebScan = fscanEnableWebScan.value,
                    portScanThreads = threads,
                    timeoutSec = timeoutSec
                ).collect { progress ->
                    _fscanProgress.value = progress
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                _isFscanRunning.value = false
                _activeTaskName.value = null
            }
            val aliveCount = _fscanProgress.value?.aliveHosts?.size ?: 0
            recordHistory("Fscan", fscanTargetText.value, "Found $aliveCount active hosts")
        }
    }

    fun stopFscan() {
        fscanJob?.cancel()
        _isFscanRunning.value = false
        _activeTaskName.value = null
    }

    // 6. iPerf State
    val iperfServer = MutableStateFlow("")
    val iperfPort = MutableStateFlow(5201)
    val iperfDuration = MutableStateFlow(5)
    val iperfProtocol = MutableStateFlow("TCP")
    val iperfMode = MutableStateFlow("CLIENT") // CLIENT or SERVER
    val iperfBandwidth = MutableStateFlow("0")
    val iperfParallel = MutableStateFlow(1)
    val iperfIsReverse = MutableStateFlow(false)

    private val _iperfPoints = MutableStateFlow<List<IperfPoint>>(emptyList())
    val iperfPoints: StateFlow<List<IperfPoint>> = _iperfPoints.asStateFlow()
    private val _isIperfRunning = MutableStateFlow(false)
    val isIperfRunning: StateFlow<Boolean> = _isIperfRunning.asStateFlow()
    private var iperfJob: Job? = null

    fun startIperf() {
        iperfJob?.cancel()
        _iperfPoints.value = emptyList()
        _isIperfRunning.value = true
        _activeTaskName.value = if (iperfMode.value == "SERVER") "iPerf Server Mode" else "iPerf Client: ${iperfServer.value}"

        iperfJob = viewModelScope.launch {
            if (iperfMode.value == "SERVER") {
                IperfEngine.runServerMode(listenPort = iperfPort.value).collect { point ->
                    _iperfPoints.value = _iperfPoints.value + point
                }
            } else {
                IperfEngine.runClientTest(
                    context = getApplication(),
                    serverHost = iperfServer.value,
                    port = iperfPort.value,
                    durationSec = iperfDuration.value,
                    protocol = iperfProtocol.value,
                    bandwidthLimit = iperfBandwidth.value,
                    parallelStreams = iperfParallel.value,
                    isReverse = iperfIsReverse.value
                ).collect { point ->
                    _iperfPoints.value = _iperfPoints.value + point
                }
            }
            _isIperfRunning.value = false
            _activeTaskName.value = null
            val last = _iperfPoints.value.lastOrNull()
            recordHistory("iPerf", if (iperfMode.value == "SERVER") "Server Mode (${iperfPort.value})" else iperfServer.value, "Avg: ${last?.bitrateMbps ?: 0f} Mbps")
        }
    }

    fun stopIperf() {
        IperfEngine.stopIperf()
        iperfJob?.cancel()
        _isIperfRunning.value = false
        _activeTaskName.value = null
    }

    // Geo Provider Selection (IP2REGION vs ZXINC_API)
    val geoProviderChoice = MutableStateFlow("IP2REGION") // "IP2REGION" or "ZXINC_API"

    // 7. DNS State & Parameters (DIG / NSLOOKUP / Standard)
    val dnsDomain = MutableStateFlow("google.com")
    val dnsServerChoice = MutableStateFlow("DEFAULT") // DEFAULT, 8.8.8.8, 1.1.1.1, 223.5.5.5, 114.114.114.114, CUSTOM
    val dnsCustomServer = MutableStateFlow("")
    val dnsToolMode = MutableStateFlow("DIG") // DIG, NSLOOKUP, STANDARD
    val dnsRecordType = MutableStateFlow("A") // ANY, A, AAAA, CNAME, MX, TXT, NS, PTR, SOA
    val digShort = MutableStateFlow(false)
    val digTrace = MutableStateFlow(false)
    val digTcp = MutableStateFlow(false)
    val digRecurse = MutableStateFlow(true)

    private val _dnsQueryResult = MutableStateFlow<DnsQueryResult?>(null)
    val dnsQueryResult: StateFlow<DnsQueryResult?> = _dnsQueryResult.asStateFlow()
    private val _dnsResults = MutableStateFlow<List<DnsRecordResult>>(emptyList())
    val dnsResults: StateFlow<List<DnsRecordResult>> = _dnsResults.asStateFlow()
    private val _isDnsQuerying = MutableStateFlow(false)
    val isDnsQuerying: StateFlow<Boolean> = _isDnsQuerying.asStateFlow()

    fun runDnsQuery() {
        viewModelScope.launch {
            _isDnsQuerying.value = true
            val actualServer = when (dnsServerChoice.value) {
                "DEFAULT" -> systemDnsServer.value
                "CUSTOM" -> dnsCustomServer.value.ifBlank { systemDnsServer.value }
                else -> dnsServerChoice.value
            }
            val result = DnsWhoisEngine.resolveNslookupAndDigAdvanced(
                domain = dnsDomain.value,
                dnsServer = actualServer,
                mode = dnsToolMode.value,
                recordType = dnsRecordType.value,
                digShort = digShort.value,
                digTrace = digTrace.value,
                digTcp = digTcp.value,
                digRecurse = digRecurse.value
            )
            _dnsQueryResult.value = result
            _dnsResults.value = result.records
            _isDnsQuerying.value = false
            recordHistory("DNS", dnsDomain.value, "[${dnsToolMode.value}] Server: $actualServer, Found ${result.records.size} recs")
        }
    }

    // 8. Whois State
    val whoisQueryText = MutableStateFlow("example.com")
    private val _whoisResult = MutableStateFlow<WhoisResult?>(null)
    val whoisResult: StateFlow<WhoisResult?> = _whoisResult.asStateFlow()
    private val _isWhoisQuerying = MutableStateFlow(false)
    val isWhoisQuerying: StateFlow<Boolean> = _isWhoisQuerying.asStateFlow()

    fun runWhoisQuery() {
        viewModelScope.launch {
            _isWhoisQuerying.value = true
            val res = DnsWhoisEngine.queryWhois(whoisQueryText.value)
            _whoisResult.value = res
            _isWhoisQuerying.value = false
            recordHistory("Whois", whoisQueryText.value, "Registrar: ${res.registrar}")
        }
    }

    // 9. IP Geo Query State & ip2region .xdb Database Management
    val ipGeoInput = MutableStateFlow("8.8.8.8")
    private val _ipGeoResult = MutableStateFlow<IpGeoInfo?>(null)
    val ipGeoResult: StateFlow<IpGeoInfo?> = _ipGeoResult.asStateFlow()
    private val _isGeoQuerying = MutableStateFlow(false)
    val isGeoQuerying: StateFlow<Boolean> = _isGeoQuerying.asStateFlow()

    val xdbStatusText = MutableStateFlow(Ip2RegionSearcher.getDbStatusInfo(getApplication()))
    val isXdbUpdating = MutableStateFlow(false)
    val xdbUpdateProgressMessage = MutableStateFlow("")

    // ip2region Download Source & Proxy configuration
    val xdbSourceChoice = MutableStateFlow("gh.dpik.top") // RAW, gh.dpik.top, gh-proxy.com, github.tbap.top, github.dpik.top, ghfile.geekertao.top, ghproxy.net, CUSTOM
    val xdbCustomProxy = MutableStateFlow("")

    // Fscan Binary Status & Online Download
    val fscanStatusText = MutableStateFlow(FscanEngine.getBinaryStatusInfo(getApplication()))
    val isFscanUpdating = MutableStateFlow(false)
    val fscanUpdateProgressMessage = MutableStateFlow("")
    val fscanSourceChoice = MutableStateFlow("gh.dpik.top")
    val fscanCustomProxy = MutableStateFlow("")
    val fscanVerifyStatusText = MutableStateFlow("")
    val isFscanVerifying = MutableStateFlow(false)

    fun verifyFscanBinary() {
        viewModelScope.launch {
            isFscanVerifying.value = true
            fscanVerifyStatusText.value = "正在验证二进制架构与可执行性..."
            val result = FscanEngine.verifyBinary(getApplication())
            fscanVerifyStatusText.value = result
            isFscanVerifying.value = false
        }
    }

    fun updateFscanBinaryOnline() {
        viewModelScope.launch {
            isFscanUpdating.value = true
            fscanUpdateProgressMessage.value = "正在连接指定节点拉取最新 fscan..."
            val result = FscanEngine.downloadOrUpdateBinaryOnline(
                context = getApplication(),
                sourceChoice = fscanSourceChoice.value,
                customProxy = fscanCustomProxy.value,
                onProgress = { msg ->
                    fscanUpdateProgressMessage.value = msg
                }
            )
            isFscanUpdating.value = false
            fscanStatusText.value = FscanEngine.getBinaryStatusInfo(getApplication())
            if (result.isSuccess) {
                fscanUpdateProgressMessage.value = "fscan 二进制最新版更新部署完毕！"
            } else {
                fscanUpdateProgressMessage.value = "更新失败: ${result.exceptionOrNull()?.localizedMessage ?: "超时"}"
            }
        }
    }

    // FRP Binary Status & Online Download
    val frpStatusText = MutableStateFlow(FrpEngine.getBinaryStatusInfo(getApplication()))
    val isFrpUpdating = MutableStateFlow(false)
    val frpUpdateProgressMessage = MutableStateFlow("")
    val frpSourceChoice = MutableStateFlow("gh.dpik.top")
    val frpCustomProxy = MutableStateFlow("")

    fun updateFrpBinaryOnline() {
        viewModelScope.launch {
            isFrpUpdating.value = true
            frpUpdateProgressMessage.value = "正在连接指定节点拉取最新 FRP (frpc & frps)..."
            val result = FrpEngine.downloadOrUpdateBinaryOnline(
                context = getApplication(),
                sourceChoice = frpSourceChoice.value,
                customProxy = frpCustomProxy.value,
                onProgress = { msg ->
                    frpUpdateProgressMessage.value = msg
                }
            )
            isFrpUpdating.value = false
            frpStatusText.value = FrpEngine.getBinaryStatusInfo(getApplication())
            if (result.isSuccess) {
                frpUpdateProgressMessage.value = "FRP (frpc & frps) 二进制最新版更新部署完毕！"
            } else {
                frpUpdateProgressMessage.value = "更新失败: ${result.exceptionOrNull()?.localizedMessage ?: "超时"}"
            }
        }
    }

    fun updateXdbDatabaseOnline() {
        viewModelScope.launch {
            isXdbUpdating.value = true
            xdbUpdateProgressMessage.value = "正在连接指定下载节点..."

            val rawV4 = "https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v4.xdb"
            val rawV6 = "https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v6.xdb"

            val choice = xdbSourceChoice.value
            val (v4Url, v6Url) = when {
                choice == "RAW" -> Pair(rawV4, rawV6)
                choice == "CUSTOM" -> {
                    var prefix = xdbCustomProxy.value.trim()
                    if (prefix.isBlank()) {
                        xdbUpdateProgressMessage.value = "错误: 请输入自定义加速代理地址"
                        isXdbUpdating.value = false
                        return@launch
                    }
                    if (!prefix.startsWith("http://") && !prefix.startsWith("https://")) {
                        prefix = "https://$prefix"
                    }
                    if (!prefix.endsWith("/")) {
                        prefix += "/"
                    }
                    Pair("${prefix}$rawV4", "${prefix}$rawV6")
                }
                else -> {
                    val prefix = "https://$choice/"
                    Pair("${prefix}$rawV4", "${prefix}$rawV6")
                }
            }

            val result = Ip2RegionSearcher.updateDbOnline(
                context = getApplication(),
                v4Url = v4Url,
                v6Url = v6Url,
                onProgress = { msg ->
                    xdbUpdateProgressMessage.value = msg
                }
            )

            isXdbUpdating.value = false
            if (result.isSuccess) {
                xdbStatusText.value = Ip2RegionSearcher.getDbStatusInfo(getApplication())
                xdbUpdateProgressMessage.value = "ip2region_v4/v6.xdb 离线库更新完毕！"
            } else {
                xdbUpdateProgressMessage.value = "更新失败: ${result.exceptionOrNull()?.localizedMessage ?: "下载超时"}"
            }
        }
    }

    fun runIpGeoQuery(targetIp: String = ipGeoInput.value) {
        viewModelScope.launch {
            _isGeoQuerying.value = true
            ipGeoInput.value = targetIp
            val res = IpGeoEngine.lookup(targetIp)
            _ipGeoResult.value = res
            _isGeoQuerying.value = false
            recordHistory("IPGeo", targetIp, "${res.country} ${res.region} ${res.city} (${res.isp})")
        }
    }

    // Quick Fill IP into tools
    fun quickFillIpToTool(ip: String, screen: AppScreen) {
        when (screen) {
            AppScreen.PING -> pingHost.value = ip
            AppScreen.TRACE -> traceHost.value = ip
            AppScreen.PORT_SCAN -> portScanTarget.value = ip
            AppScreen.FSCAN -> fscanTargetText.value = ip
            AppScreen.IPERF -> iperfServer.value = ip
            AppScreen.DNS -> dnsDomain.value = ip
            AppScreen.WHOIS -> whoisQueryText.value = ip
            AppScreen.IP_GEO -> runIpGeoQuery(ip)
            else -> {}
        }
        navigateTo(screen)
        dismissIpAction()
    }

    // FRP Management
    val frpServerStatus: StateFlow<FrpStatus> = FrpEngine.serverStatus
    val frpClientStatus: StateFlow<FrpStatus> = FrpEngine.clientStatus
    val frpClientLogs: StateFlow<List<String>> = FrpEngine.clientLogs
    val frpServerLogs: StateFlow<List<String>> = FrpEngine.serverLogs

    val frpServerConfig: MutableStateFlow<FrpServerConfig> = MutableStateFlow(FrpConfigStorage.loadServerConfig(application))
    val frpClientConfig: MutableStateFlow<FrpClientConfig> = MutableStateFlow(FrpConfigStorage.loadClientConfig(application))

    fun updateFrpServerConfig(config: FrpServerConfig) {
        frpServerConfig.value = config
        FrpConfigStorage.saveServerConfig(getApplication(), config)
    }

    fun updateFrpClientConfig(config: FrpClientConfig) {
        frpClientConfig.value = config
        FrpConfigStorage.saveClientConfig(getApplication(), config)
    }

    fun startFrpServer() {
        _activeTaskName.value = "FRP 服务端 (frps)"
        FrpConfigStorage.saveServerConfig(getApplication(), frpServerConfig.value)
        FrpEngine.startServer(getApplication(), viewModelScope, frpServerConfig.value)
        recordHistory("FRP_SERVER", "Port:${frpServerConfig.value.bindPort}", "FRP 服务端运行中")
    }

    fun stopFrpServer() {
        FrpEngine.stopServer()
        if (_activeTaskName.value?.contains("frps") == true) {
            _activeTaskName.value = null
        }
    }

    fun startFrpClient() {
        _activeTaskName.value = "FRP 客户端 (frpc)"
        FrpConfigStorage.saveClientConfig(getApplication(), frpClientConfig.value)
        FrpEngine.startClient(getApplication(), viewModelScope, frpClientConfig.value)
        recordHistory("FRP_CLIENT", frpClientConfig.value.serverAddr, "FRP 客户端运行中 (${frpClientConfig.value.proxies.size}条规则)")
    }

    fun stopFrpClient() {
        FrpEngine.stopClient()
        if (_activeTaskName.value?.contains("frpc") == true) {
            _activeTaskName.value = null
        }
    }

    fun addFrpProxyRule(rule: FrpProxyConfig) {
        frpClientConfig.update { cfg ->
            val updated = cfg.copy(proxies = cfg.proxies + rule)
            FrpConfigStorage.saveClientConfig(getApplication(), updated)
            updated
        }
    }

    fun removeFrpProxyRule(id: String) {
        frpClientConfig.update { cfg ->
            val updated = cfg.copy(proxies = cfg.proxies.filter { it.id != id })
            FrpConfigStorage.saveClientConfig(getApplication(), updated)
            updated
        }
    }

    fun toggleFrpProxyRule(id: String) {
        frpClientConfig.update { cfg ->
            val updated = cfg.copy(proxies = cfg.proxies.map {
                if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
            })
            FrpConfigStorage.saveClientConfig(getApplication(), updated)
            updated
        }
    }

    // Favorites Management
    fun addFavorite(ip: String, title: String = ip, note: String = "") {
        viewModelScope.launch {
            db.favoriteIpDao().insertFavorite(FavoriteIp(ip = ip, title = title, note = note))
        }
    }

    fun removeFavorite(ip: String) {
        viewModelScope.launch {
            db.favoriteIpDao().deleteFavorite(ip)
        }
    }

    // History Management
    private fun recordHistory(tool: String, target: String, summary: String) {
        viewModelScope.launch {
            db.historyRecordDao().insertHistory(
                HistoryRecord(
                    toolType = tool,
                    target = target,
                    resultSummary = summary
                )
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.historyRecordDao().clearAllHistory()
        }
    }
}

