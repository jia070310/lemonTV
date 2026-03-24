<div align="center">

<img src="https://github.com/jia070310/lemonTV/raw/main/pic/1%20(1).png" alt="柠檬IPTV直播" width="100%"/>

# 🍋 柠檬IPTV直播

**一款专为 Android TV / 电视盒子 打造的 IPTV 直播应用**

[![Release](https://img.shields.io/github/v/release/jia070310/lemonTV?style=flat-square&label=最新版本&color=fddd0e)](https://github.com/jia070310/lemonTV/releases/latest)
[![License](https://img.shields.io/github/license/jia070310/lemonTV?style=flat-square&color=green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-5.0%2B-brightgreen?style=flat-square&logo=android)](https://github.com/jia070310/lemonTV/releases/latest)
[![Hypercommit](https://img.shields.io/badge/Hypercommit-DB2475)](https://hypercommit.com/lemontv)

</div>

---

## ✨ 功能特色

- 📺 **专为电视端优化** — 遥控器完整操控，横向布局，大屏体验极佳
- 📡 **M3U/TvBox 双格式支持** — 兼容主流直播源格式
- 📅 **智能节目单（EPG）** — 自动降级多备用地址，支持 `.xml` / `.xml.gz` 格式
- 🔄 **直播源 UA 智能识别** — 从 M3U 的 `#EXT-X-APP` 参数自动提取 User-Agent 用于 EPG 请求
- ⭐ **频道收藏** — 收藏常用频道，快速切换
- 🔢 **数字选台** — 遥控器直接输入频道号
- 🌐 **局域网推送** — 扫码打开网页端，远程推送直播源地址
- 🔔 **应用内更新** — 自动检测新版本，黄色高亮提醒
- 🖥️ **多界面风格** — 经典面板 / 现代面板自由切换
- 🎬 **硬件解码加速** — 基于 Media3/ExoPlayer，支持 FFmpeg 扩展解码

---

## 📥 下载安装

前往 [Releases](https://github.com/jia070310/lemonTV/releases/latest) 页面下载最新版 APK 安装包。

| 文件名 | 说明 |
|--------|------|
| `LemonIPTV-v1.1.9-release.apk` | 正式发布版（推荐） |
| `LemonIPTV*.apk` | GitHub Actions 自动构建时的产物命名（以 [Releases](https://github.com/jia070310/lemonTV/releases) 页面附件为准） |

> 安装时如提示"未知来源"，请在系统设置中允许安装即可。

---

## 📝 更新日志

### v1.1.9（2026-03-22）

- **修复**：对齐 AndroidX **Media3 1.5.1** 与 **Jellyfin FFmpeg 扩展**（`media3-ffmpeg-decoder`），解决部分设备启动后因 `RendererCapabilities` / `NoSuchMethodError` 导致的闪退。
- **构建**：`compileSdk` / `targetSdk` 提升至 **35**，Android Gradle Plugin **8.6.1**。
- **依赖**：FFmpeg 软解改为 Maven 依赖，移除本地 `lib-decoder-ffmpeg-release.aar`，避免与扩展库冲突。
- **兼容**：适配 API 35 下 `PackageInfo.versionName` 可空等变更。

---

## 🚀 快速上手

1. 下载并安装 APK
2. 打开应用，默认加载内置直播源
3. 按遥控器 **菜单键** 或长按 **确认键** 打开设置
4. 在设置中自定义直播源地址、节目单地址等

### 网页端推送（局域网）

手机和电视在同一局域网下，打开设置 → 关于 → 扫描二维码，在手机浏览器中输入直播源地址远程推送。

---

## 📡 默认节目单地址

| 类型 | 地址 |
|------|------|
| 主地址 | `http://epg.51zmt.top:8000/e1.xml.gz` |
| 备用地址 | `https://epg.zsdc.eu.org/t.xml.gz` |

> 节目单支持多级自动降级：直播源内嵌地址 → 主地址 → 备用地址

---

## 🛠️ 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose for TV（Material3）
- **播放器**：Media3（ExoPlayer）**1.5.x** + [Jellyfin `media3-ffmpeg-decoder`](https://central.sonatype.com/artifact/org.jellyfin.media3/media3-ffmpeg-decoder)（与主库版本对齐）
- **网络**：OkHttp
- **最低系统版本**：Android 5.0（API 21）
- **编译目标**：`compileSdk` / `targetSdk` **35**

---

## 📸 截图

<div align="center">
<img src="https://github.com/jia070310/lemonTV/raw/main/pic/1%20(1).png" alt="主界面" width="80%"/>
</div>

---

## 📄 开源协议

本项目基于原始开源项目二次开发，遵循相应开源协议。详见 [LICENSE](LICENSE)。

---

<div align="center">
  <sub>🍋 柠檬IPTV直播 · 让电视直播更简单</sub>
</div>
