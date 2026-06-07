# AIAssistant

一个基于 Spring Boot 的桌面语音识别 + 翻译 + 实时字幕悬浮窗工具（Windows 为主）。  
主要功能：捕获系统/麦克风音频 → ASR（百度语音）→ 翻译（百度大模型文本翻译，目前主英文）→ 通过 WebSocket 推送到悬浮窗(可拖动)显示。

打包后的exe链接：https://pan.quark.cn/s/cc2ffe678811?pwd=qRTH


主要技术栈
- Java 8
- Spring Boot 2.7.x
- javax.websocket（服务端）、Tyrus 客户端（悬浮窗）
- Apache HttpClient、commons-io、fastjson
- Swing（悬浮窗 UI）
- Maven 构建

仓库结构（关键文件）
- `pom.xml` - Maven 配置（依赖/打包）
- `src/main/java/com/example/aiassistant/`
  - `AiAssistantApplication.java` - 应用入口，启动服务并在启动后触发音频初始化与悬浮窗
  - `service/AudioCaptureTask.java` - 音频捕获、分片、能量检测、拼接并触发 ASR/翻译
  - `service/AudioAutoInstall.java` - （Windows）安装音频驱动/脚本执行
  - `service/AudioRouteManager.java` - 管理音频路由脚本（启动/停止）
  - `service/TransWebSocket.java` - WebSocket 服务端（`/trans/ws`），负责向客户端推送翻译结果
  - `ui/SubtitleFloatWindow.java` - 悬浮窗客户端（Swing + Tyrus websocket client）
- `src/main/resources/application.yml` - 运行时配置（端口、百度 API keys、音频设备等）
- `src/main/resources/script.win/` - Windows 平台脚本（install.bat、route_start.bat、route_stop.bat）
- `src/main/resources/static/index.html` - 前端页面用于测试

快速开始（Windows）
1. 安装依赖
   - 安装 Java 8（JDK8）并确保 `java`、`javac` 在 PATH 中
   - 安装 Maven（可选：IDE 内置也行）

2. 修改配置
   编辑 `src/main/resources/application.yml`，至少填入百度翻译与 ASR 的 appId/secret（不要将密钥提交到公共仓库）：

   ```yaml
   server:
     port: 8080

   baidu-trans:
     appId: "你的翻译appid"
     secretKey: "你的翻译secret"

   baidu-asr:
     appId: "你的ASR appid"
     apiKey: "你的ASR apiKey"
     secretKey: "你的ASR secret"
     langEn: 1737
     langJp: 10001

   audio:
     captureDeviceName: "Voicemeeter In 1 (VB-Audio Voicemeeter VAIO)"
     sampleRate: 48000
     sampleBit: 16
     channel: 2





   
