# CodeLocatorPRO Workspace

CodeLocatorPRO 当前仓库以 `adapter` 为核心，用于 Android UI 抓取、定位与可视化分析；Android SDK 已拆分到独立仓库 `codelocator-pro-android`。

## Open Source Notice / 开源声明

### 中文

- 本项目基于开源项目 [bytedance/CodeLocator](https://github.com/bytedance/CodeLocator) 进行二次开发，属于其衍生作品。
- 本项目采用 **Apache License 2.0** 开源，许可证全文见 [LICENSE](./LICENSE)。
- 对上游代码的修改、归属和补充声明见 [NOTICE](./NOTICE)。
- 第三方组件及许可证信息见 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)。
- 除许可证允许的合理描述外，本项目不主张任何上游项目或其权利人的商标授权或官方背书。

### English

- This project is a derivative work based on [bytedance/CodeLocator](https://github.com/bytedance/CodeLocator).
- This repository is released under the **Apache License 2.0**. See [LICENSE](./LICENSE).
- Attribution and modification notices for upstream code are provided in [NOTICE](./NOTICE).
- Third-party components and their licenses are listed in [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).
- Except for reasonable descriptive use allowed by license terms, no trademark license or endorsement by upstream right holders is implied.

## 1 分钟快速开始（本地）

```bash
./build.sh 49622
```

启动后会自动打开浏览器，默认地址 `http://127.0.0.1:49622/`。

## Homebrew 安装（跨机器）

### 前置条件

- macOS + Homebrew。
- 已安装并可用 `adb`（建议 `brew install --cask android-platform-tools`）。
- 手机已开启开发者模式和 USB 调试，且 `adb devices` 可见。
- 待抓取 App 已集成/接入 CodeLocator 能力。
- 安装机可访问 `github.com` 和 `repo.maven.apache.org`（首次安装会拉取依赖并本地编译）。

### 安装步骤

```bash
brew tap git54496/codelocatorpro
brew install grab
```

对应 tap 仓库：`https://github.com/git54496/homebrew-codelocatorpro`

安装后直接抓屏：

```bash
grab
```

`grab` 无参数时默认等价于 `grab live`。

## 目录结构

- `adapter`
  - CLI + MCP(stdio) + 本地 Viewer HTTP 服务（内置前端页面资源）。
- `build.sh`
  - 一键构建 adapter 并启动本地 Viewer。
- `../codelocator-pro-android`（独立仓库）
  - Android 侧抓取、分析、协议模型与动作执行核心逻辑。

## 手动构建 Adapter

```bash
cd adapter
./gradlew clean installDist --no-daemon
```

产物路径：

```bash
adapter/build/install/grab/bin/grab
```

## CLI 常用命令

```bash
BIN="./adapter/build/install/grab/bin/grab"

# 实时抓取（可选 --device-serial）
$BIN
$BIN live --device-serial <optional>

# 从文件导入抓取（可选 --path）
$BIN file --path <optional>

# 列出抓取记录
$BIN list

# 打开 Viewer（指定 grab_id）
$BIN viewer open --grab-id <grab_id>

# 启动 Viewer 服务
$BIN viewer serve --port 49622

# 启动 MCP stdio 服务
$BIN mcp
```

## `build.sh` 参数

```bash
./build.sh [port] [grab_id]
```

- `port`：可选，默认 `49622`。
- `grab_id`：可选，传入后自动打开对应抓取记录页面。

## Homebrew Tap 维护

配方已拆分到独立仓库：

- `https://github.com/git54496/homebrew-codelocatorpro`
- `Formula/grab.rb`

当前配方默认通过 git 追踪 `CodeLocatorPRO` 的 `main` 分支，适合快速分发。建议后续切换到 tag 固定版本：

1. 发布 `vX.Y.Z` tag。
2. 将 `Formula/grab.rb` 的 `url` 改为对应 tag 压缩包地址。
3. 填入该压缩包的 `sha256`。
4. `brew update && brew upgrade grab` 验证升级链路。
