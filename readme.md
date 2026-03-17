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

如果你希望抓取成功后自动打开 Viewer，可以直接执行：

```bash
grab -v
```

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

## 本地联调 `codelocator-pro-android`

如果你在同级目录维护 `../TestApplication` 和 `../codelocator-pro-android`，推荐用下面这条链路做本地验证：

```bash
cd ../TestApplication
bash scripts/publish-local-codelocator.sh
./gradlew :app:installDebug -PuseLocalCodeLocatorMaven=true

cd ../codelocatorpro
bash dev-grab.sh -v
```

说明：

- `publish-local-codelocator.sh` 会把本地 Android SDK 源码发布到 `mavenLocal()`。
- `dev-grab.sh` 会构建当前仓库里的 adapter，并默认把 `CODELOCATOR_SOURCE_ROOT` 指向 `../TestApplication`，这样 Compose source path 会直接归一化到测试工程源码。

## CLI 常用命令

```bash
BIN="./adapter/build/install/grab/bin/grab"

# 实时抓取（可选 --device-serial）
$BIN
$BIN live --device-serial <optional>
$BIN -v
$BIN live --device-serial <optional> --viewer

# 从文件导入抓取（可选 --path）
$BIN file --path <optional>
$BIN file --path <optional> --viewer

# 列出抓取记录
$BIN list

# 打开 Viewer（指定 grab_id）
$BIN viewer open --grab-id <grab_id>

# 启动 Viewer 服务
$BIN viewer serve --port 49622

# 启动 MCP stdio 服务
$BIN mcp

# 查询 Compose 语义节点（node_id 或 compose_key）
$BIN inspect compose-node --grab-id <grab_id> --node-id <compose_node_id_or_compose_key>
```

## Compose 兼容说明

- `codelocatorpro` 已支持解析 `mComposeNodes`（`b5`）并生成 `compose_index.json`。
- Viewer 支持显示 Compose Semantics 表格，并支持 `nodeId/testTag/contentDescription` 搜索。
- MCP/CLI 新增 `get_compose_node` 能力，便于按 `node_id` 或 `compose_key` 精确检索。

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

当前仓库已引入统一版本文件 `VERSION`，`grab --version` 与 adapter 构建版本会保持一致。正式发布 Homebrew 升级链路时，按以下流程操作：

1. 更新 `VERSION`，推送 `codelocatorpro`，并创建对应 tag，例如 `v0.2.2`。
2. 进入 tap 仓库 `homebrew-codelocatorpro`，执行 `./scripts/update_grab_formula.sh 0.2.2`。
3. 提交并推送 tap 仓库中的 `Formula/grab.rb`。
4. 用户执行 `brew update && brew upgrade grab`。

说明：历史上安装过 `version "main"` 配方的机器，在切到首个版本化配方时，可能需要一次性执行 `brew reinstall grab`；之后即可正常走 `brew upgrade grab`。
