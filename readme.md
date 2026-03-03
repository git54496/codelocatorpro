# CodeLocatorMCP

本目录包含两个核心模块：

## 1. CodeLocator

路径：`/Users/yebingyue/code/baron/CodeLocatorMCP/CodeLocator`

主要功能：
- Android 侧 UI 抓取与运行时分析能力（Activity、Fragment、View 树等）。
- IntelliJ/Android Studio 插件形态的可视化排查工具。
- 支持查看 View 属性、调用链路、类信息、截图与历史抓取等。

典型使用方式：
1. 在目标 App 集成 CodeLocator SDK。
2. 在 Android Studio 安装并打开 CodeLocator 插件。
3. 连接设备后手动抓取页面，进行可视化排查。

## 2. CodeLocatorMCPAdapter

路径：`/Users/yebingyue/code/baron/CodeLocatorMCP/CodeLocatorMCPAdapter`

主要功能：
- 提供统一 CLI 内核。
- 提供 MCP stdio 服务，供 LLM 调用。
- 提供本地 Web Viewer，可视化抓取结果（树、截图、memAddr 定位）。
- 支持两种输入：
  - 实时抓取（live）
  - 读取本地 `.codeLocator` 历史文件（file）

### 构建

```bash
cd /Users/yebingyue/code/baron/CodeLocatorMCP/CodeLocatorMCPAdapter
./gradlew installDist
```

可执行文件：

```bash
/Users/yebingyue/code/baron/CodeLocatorMCP/CodeLocatorMCPAdapter/build/install/CodeLocatorMCPAdapter/bin/CodeLocatorMCPAdapter
```

### 常用命令

```bash
# 1) 读取历史抓取并生成 grab_id
CodeLocatorMCPAdapter grab file --json

# 2) 实时抓取（需要设备和 SDK）
CodeLocatorMCPAdapter grab live --json

# 3) 打开 Viewer
CodeLocatorMCPAdapter viewer open --grab-id <grab_id> --json

# 4) 启动 MCP 服务
CodeLocatorMCPAdapter mcp
```

### MCP 客户端配置示例

```json
{
  "mcpServers": {
    "codelocator": {
      "command": "/Users/yebingyue/code/baron/CodeLocatorMCP/CodeLocatorMCPAdapter/build/install/CodeLocatorMCPAdapter/bin/CodeLocatorMCPAdapter",
      "args": ["mcp"]
    }
  }
}
```

## 快速排查流程

1. 先获取 `grab_id`（`grab live` 或 `grab file`）。
2. 打开 Viewer（`viewer open --grab-id ...`）。
3. 用 MCP/CLI 调 `inspect` 系列工具补充证据：
   - `inspect view-data`
   - `inspect class-info`
   - `inspect touch`
4. 输出结论：现象、证据、根因、修复建议。

