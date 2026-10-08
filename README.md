# GPT Mobile · 本分支

基于 [Taewan-P/gpt_mobile](https://github.com/Taewan-P/gpt_mobile) 的 Android 多模型聊天客户端，增加 OpenAI 与兼容接口的模型管理、会话控制和用量展示，并完善简体中文体验。

**当前版本：0.9.1-1（Android 内部版本号 28）**

[下载安装包](https://github.com/def-Richard/gpt_mobile/releases/latest) · [详细功能指南](docs/openai-enhancements.md) · [发布说明](docs/release-notes-v0.9.1-1.md)

## 新增功能

| 功能 | 行为 |
| --- | --- |
| Responses 图片发送 | 新图片直接随请求发送，兼容未提供 Files API 的网关；复用已有可用文件引用。 |
| 模型目录持久化 | 使用 API 地址和密钥拉取模型，下拉选择；目录随提供商保存，重启和切换无需重新请求。 |
| 手动刷新与检测 | 刷新成功替换目录，失败保留旧目录；保存旁增加实际模型请求检测。 |
| 会话内控制 | 输入框上方直接切换提供商、模型和推理强度，保留历史回复。 |
| 推理强度 | 使用 `low / medium / high / xhigh / max`；关闭或选择“默认”时不发送强度参数。 |
| OpenAI Fast | 模型列表顶部默认关闭，主动勾选才发送 `service_tier: "priority"`，按会话中的提供商保存。 |
| 上下文用量 | 用量环显示最近一次请求的占用，点击查看已用、剩余及窗口容量，缺失数据保持未知。 |
| 回复统计 | 紧凑显示 `14:36 · 5s · 172k`；点击查看完整时间或输入、输出、缓存 token 明细。 |
| 紧凑布局与中文 | 底部收紧为两行操作信息和一行提示；补齐 298 条中文文案及复数资源。 |

## 安装、升级与数据

- 最低要求为 Android 12 / API 31。安装包与校验文件位于[本仓库 Releases](https://github.com/def-Richard/gpt_mobile/releases)。
- 公开包采用压缩优化的 Release 构建，关闭调试标志；沿用本分支先前本地测试包的开发签名证书，以支持保留数据升级。它不是上游商店签名，不承诺覆盖其他渠道的安装包。
- 本次内部版本号为 28，高于 `0.9.0-2` 公开包的 27，支持同签名覆盖升级。Android 根据内部版本号及签名判断升级，显示名称相同不代表安装包相同。
- 旧会话会自动迁移保留。已有提供商首次升级后需手动刷新一次，建立本地模型目录；旧回复没有记录的统计不会补算。
- 会话、目录、偏好与工具记录保存在设备本地。凭据使用 Android Keystore 加密保护，不包含在会话导出或系统备份中。
- 刷新、检测及对话会访问用户配置的服务。检测可能消耗少量 token；Fast 的可用性、速度和费用由服务商决定。
- 模型 ID、协议值、品牌名和服务端原始错误信息不自动翻译。本仓库不包含个人 API 配置、设备数据库或签名私钥。

## 构建

需要 JDK 17、Android SDK Platform 37，仓库自带 Gradle 9.4.1 Wrapper。通过本地 `local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` 指定 SDK；机器路径不提交仓库。

在 PowerShell 7 中执行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

APK 输出至 `app/build/outputs/apk/`。Release 默认产物未签名，分发前需使用自己的密钥签名。Kotlin 格式检查使用 ktlint 1.3.1。

本分支在本地构建签名。启用上游的标签签名工作流前，需要配置 `APP_KEYSTORE`、`KEY_ALIAS`、`KEY_PASSWORD` 三项 GitHub Secrets；未配置时不要启用自动签名构建。不要把密钥或口令提交到源码仓库。

## 上游与许可证

保留上游多提供商对话、工具调用、MCP、LiteRT-LM 本地模型和 Material 3 界面能力。部分本地模型需要 Hugging Face 授权；默认源码未配置 OAuth 客户端，可使用应用的访问令牌入口。

本项目按 [GPL-3.0](LICENSE) 分发。第三方许可证可在应用“关于 → 许可证”查看。下面保留上游原始介绍，其中下载渠道与签名说明属于上游，不代表本分支。

<details>
<summary>上游原始项目介绍</summary>

<div align="center">

<img width="200" height="200" style="display: block;" src="./images/logo.png">

# GPT Mobile

## Multi-provider AI chat and optional on-device agents for Android.

<p>
  <a href="https://mailchi.mp/kotlinweekly/kotlin-weekly-431"><img alt="Kotlin Weekly" src="https://img.shields.io/badge/Kotlin%20Weekly-%23431-blue"/></a>
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-green.svg"/>
  <img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/Taewan-P/gpt_mobile/release-build.yml">
  <a href="https://hosted.weblate.org/engage/gptmobile/"><img src="https://hosted.weblate.org/widget/gptmobile/gptmobile/svg-badge.svg" alt="Translation status" /></a>
  <a href="https://github.com/Taewan-P/gpt_mobile/releases/"><img alt="GitHub Releases Total Downloads" src="https://img.shields.io/github/downloads/Taewan-P/gpt_mobile/total?label=Downloads&logo=github"/></a>
  <a href="https://github.com/Taewan-P/gpt_mobile/releases/latest/"><img alt="GitHub Releases (latest by date)" src="https://img.shields.io/github/v/release/Taewan-P/gpt_mobile?color=black&label=Stable&logo=github"/></a>
</p>


</div>


## Screenshots

<div align="center">

<img style="display: block;" src="./images/screenshots.webp">

</div>

## Demos


| <video src="https://github.com/Taewan-P/gpt_mobile/assets/27392567/96229e6d-6795-48b4-a915-aca915bd2527"/> | <video src="https://github.com/Taewan-P/gpt_mobile/assets/27392567/1cc13413-7320-4f6f-ace9-de76de58adcc"/> | <video src="https://github.com/Taewan-P/gpt_mobile/assets/27392567/546e2694-953d-4d67-937f-a29fba81046f"/> |
|------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|


## Features

- **Chat with multiple models at once**
  - Uses official APIs for each platforms
  - Supported platforms:
    - OpenAI GPT
    - Anthropic Claude
    - Google Gemini
    - Groq
    - Ollama
  - Can customize temperature, top p (Nucleus sampling), and system prompt
  - Custom API URLs, Custom Models are also supported
- **Optional agent tools per provider profile**
  - Native tool calling with OpenAI, OpenAI-compatible/Groq, Anthropic, and Gemini
  - Firecrawl, Perplexity, or Exa web search and hardened URL reading
  - MCP Streamable HTTP servers with public, bearer, or OAuth authentication
  - Parallel runs, persistent traces, cancellation, and foreground progress
  - Existing and newly migrated profiles remain chat-only until tools are assigned
- Local chat history
  - Chat history is **only saved locally**
  - Credentials are encrypted with Android Keystore and excluded from backup/export
  - During chats, requests go only to selected model providers and assigned tools. Opening a profile's Tools dialog may contact every saved MCP server for discovery and may include its bearer or OAuth credential.
- [Material You](https://m3.material.io/) style UI, Icons
  - Supports dark mode, system dynamic theming **without Activity restart**
- Per app language setting for Android 13+
- 100% Kotlin, Jetpack Compose, Single Activity, [Modern App Architecture](https://developer.android.com/topic/architecture#modern-app-architecture) in Android developers documentation


## Agent documentation

See [Agent tools, privacy, and security](docs/agent-tools.md) and the [0.8.0 release notes](docs/release-notes-v0.8.0.md).

If you have any feature requests, please open an issue.


## Downloads

You can download the app from the following sites:

[<img height="80" alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"/>](https://f-droid.org/packages/dev.chungjungsoo.gptmobile)
[<img height="80" alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/>](https://play.google.com/store/apps/details?id=dev.chungjungsoo.gptmobile&utm_source=github&utm_campaign=gh-readme)
[<img height="80" alt='Get it on GitHub' src='https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png'/>](https://github.com/Taewan-P/gpt_mobile/releases)

Cross platform updates are supported. However, GitHub Releases will be the fastest track among the platforms since there is no verification/auditing process. (Probably 1 week difference?)



## Contributions

Contributions are welcome! The contribution guideline is not yet available, but I will be happy to review it! 💯

For translations, we are using [Hosted Weblate](https://hosted.weblate.org/engage/gptmobile/). If you want your language supported, help us translate the app!

<a href="https://hosted.weblate.org/engage/gptmobile/">
  <img src="https://hosted.weblate.org/widget/gptmobile/gptmobile/multi-auto.svg" alt="Translation status" />
</a>


## Star History

[![Star History Chart](https://star-history.dera.page/svg?repos=Taewan-P/gpt_mobile&type=Timeline)](https://star-history.dera.page/#Taewan-P/gpt_mobile&Timeline)


## License

See [LICENSE](./LICENSE) for details.

[F-Droid Icon License](https://gitlab.com/fdroid/artwork/-/blob/master/fdroid-logo-2015/README.md)

</details>
