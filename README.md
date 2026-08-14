# IPTools - 全功能 Android 网络诊断与安全渗透工具箱

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20MVVM-orange.svg)]()
[![Root Support](https://img.shields.io/badge/Root-Supported%20(Optional)-red.svg)]()

> **IPTools** 是一款基于现代 **Kotlin + Jetpack Compose (Material 3)** 打造的专业级 Android 网络诊断、测速、内网穿透与安全扫描集成工具箱。
>
> 免去繁琐的命令行操作与繁杂依赖，原生集成 **Fscan (内网安全探测)**、**FRP (反向代理与穿透)**、**iPerf3 (网络带宽吞吐测速)** 等行业顶级开源引擎，支持自动提取、一键多源更新、架构兼容验证以及 Root 提权执行与普通双模式。

---

## 📸 功能特性与模块概览

### 🏠 1. 网络全景看板 (Network Overview & Diagnostics)
- **网络状态监控**：实时感知 Wi-Fi / 移动蜂窝（5G/4G/3G/2G）连接状态、BSSID、SSID、频段、信号强度 (RSSI) 及链路速率。
- **双栈 IP 探测**：一键检测并展示公网 IPv4、公网 IPv6 地址及地理位置、运营商 ASN 归属信息。
- **实时网络测速**：集成动态双向（下载/上传）实时速度采样与平滑曲线图表绘制。
- **局域网设备扫描**：基于 ARP / Ping 网段快速发现局域网内所有存活主机、MAC 地址及厂商信息。

---

### 🛡️ 2. Fscan 综合内网安全探测引擎 (Fscan Scanner)
- **多资产目标格式全面支持**：
  - 单 IP（如 `192.168.1.1`）
  - 标准 CIDR 网段（如 `192.168.1.0/24`）
  - 横杠范围（如 `192.168.1.1-254`）
  - **IPv6 资产解析**（支持 `[2408:8000::1]:80` 标准格式及原生 IPv6）
- **高并发端口与服务识别**：集成 100+ 常见高危及服务端口预设（SSH、RDP、SMB、MySQL、Redis、Web 等），支持展开/折叠与自定义输入。
- **双视图展示**：
  - **交互式卡片视图**：按主机自动归类开放端口、Web 标题 (Title)、HTTP 状态码、Server 指纹，提供一键浏览器跳转与快捷操作。
  - **极客终端日志视图**：支持 ANSI 彩色语法高亮、横向滚动与单行等宽排版，完美适配手机窄屏展示 ASCII 字符艺术 Banner。
- **二进制引擎自主管理**：
  - 原生内置 ARM64 / ARMv7 预编译二进制。
  - 支持从 GitHub Release 及国内多节点代理（`gh.dpik.top`、`moeyy.cn` 等）在线拉取最新 Fscan 版本。
  - 独立 **「🧪 验证二进制」** 测试工具，自动检测 CPU 架构兼容性与 Root 提权状态。

---

### 🚀 3. FRP 高性能内网穿透 (FRP Client)
- **多配置文件管理**：支持创建、重命名、编辑、删除与切换多个 `frpc` 穿透配置。
- **双格式编辑支持**：
  - **INI 格式**（传统 `frpc.ini`，直观易懂）
  - **TOML 格式**（现代新版标准 `frpc.toml`）
- **后台常驻运行**：前台服务保障后台稳定穿透，实时抓取输出终端日志，支持清空、复制与状态监听。
- **引擎在线升级**：内置 FRP ARM64 二进制，支持在线多源更新引擎。

---

### ⚡ 4. iPerf3 极速带宽测速 (iPerf3 Performance)
- **客户端与服务端双模式**：
  - **Client 模式**：指定远程服务端 IP/端口、测试时长、并发连接流（Streams）、反向测试模式（Reverse）及 TCP/UDP 协议。
  - **Server 模式**：一键开启本地监听，将手机作为测试服务端。
- **实时吞吐量采样**：实时流式输出测试带宽（Mbits/sec）、传输字节量与抖动丢包率（Jitter / Loss）。

---

### 🔍 5. 经典网络诊断工具集
- **⚡ Ping 诊断**：支持自定义发包次数、数据包大小 (MTU)、TTL、超时时间与间隔，实时展示延迟曲线与最小/最大/平均/抖动统计。
- **🛣️ 路由追踪 (Traceroute)**：逐跳显示网关跃点节点、IP 归属、往返时延 (RTT)，直观定位网络拥塞瓶颈。
- **🚪 端口扫描 (Port Scanner)**：单目标多端口批量探测，提供常用端口组一键填充与服务探测。
- **🌐 DNS 解析 (DNS Lookup)**：支持查询 A、AAAA、CNAME、MX、TXT、NS、PTR、SOA 等记录类型，可自由指定公共 DNS 服务器（如 223.5.5.5, 8.8.8.8, 1.1.1.1）。
- **📋 WHOIS 查询**：实时查询域名注册商、创建/过期时间、注册人组织与 NameServers 详细信息。
- **📍 IP 归属地查询 (IP Geo)**：精确查询任意 IP / 域名的地理位置经纬度、国家省市、邮编、时区与 AS 运营商。

---

### ⭐ 6. 快捷操作、收藏夹与历史记录
- **全局 IP 快捷菜单 (IP Action Bottom Sheet)**：在任何页面点击 IP 均可唤起底部操作面板，一键发起 Ping、路由追踪、端口扫描、Fscan、IP 归属查询或复制到剪贴板。
- **⭐ 收藏夹 (Favorites)**：快速收藏常用服务器、路由器或测试目标，支持自定义备注与一键唤起诊断。
- **🕒 历史记录 (History)**：基于 Room 数据库持久化存储所有诊断记录，支持按分类筛选与一键清空。
- **🌓 深色模式**：深度适配 Material 3 动态色彩与深色/浅色主题自由切换。

---

## 🛠️ 技术栈与架构设计

- **开发语言**：100% Kotlin (Coroutines + Flow 异步反应式流)
- **UI 框架**：Jetpack Compose + Material Design 3 (M3)
- **架构模式**：MVVM (Model-View-ViewModel) + 状态单向数据流 (UDF)
- **本地持久化**：AndroidX Room + SQLite
- **网络通信**：Ktor / OkHttp / 原生 Socket
- **原生二进制交互**：ProcessBuilder + 流式异步读取 + Linux Native Permissions + RootUtils 提权支持

---

## 📥 快速下载与安装

前往项目的 [Releases 页面](../../releases) 下载最新的 `IPTools-vX.X.X.apk` 安装包。

### 权限说明
- `INTERNET` / `ACCESS_NETWORK_STATE`：用于网络请求与连接状态诊断。
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`：用于 Android 8.0+ 获取当前 Wi-Fi SSID / BSSID 信息。
- `FOREGROUND_SERVICE`：用于 FRP 内网穿透与后台长时间测速服务的持续稳定运行。
- `ROOT (可选)`：部分功能（如 Fscan 原始套接字 SYN 扫描、系统级 ping 权限）在 Root 手机上可获得更优性能与更强能力，**非 Root 设备同样完全可用**。

---

## 🚀 本地构建指南

### 前置要求
- **Android Studio** Ladybug (2024.2.1) 或更高版本
- **JDK** 17+
- **Android SDK** API 35 (Min SDK: 24)

### 编译步骤
```bash
# 1. 克隆代码仓库
git clone https://github.com/your-username/iptools.git
cd iptools

# 2. 编译 Debug APK
./gradlew assembleDebug

# 3. 运行单元测试
./gradlew testDebugUnitTest
```
编译生成的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

---

## 🤝 鸣谢与开源致敬

- [fscan](https://github.com/shadow1ng/fscan) - 一款内网综合扫描工具，方便一键自动化、全方位漏洞扫描。
- [frp](https://github.com/fatedier/frp) - A fast reverse proxy to help you expose a local server behind a NAT or firewall to the internet.
- [iPerf3](https://github.com/esnet/iperf) - The ultimate speed test tool for TCP, UDP and SCTP.

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源发布。
