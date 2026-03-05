---
name: codelocator-view-issue-auto-diagnose
description: 当用户只用自然语言描述某个 view 异常（例如“我的标题 view 展示有问题”）且未给出 memAddr/id/text 时，自动执行 CodeLocator grab CLI 抓取最新数据并加载截图与 JSON，推断可能目标 view；若无法唯一确定则追问用户；目标确定后转交 codelocator-grab-reader 规则做正式排查。
---

# Codelocator View Issue Auto Diagnose

## Overview

- 这是“特定 view 问题排查”的编排 skill。
- 先抓取和定位目标 view，再把排查执行交给 `codelocator-grab-reader`，避免规则重复。

## Trigger Examples

- 我的标题 view 展示有问题
- 这个按钮点不动，帮我看下哪个 view 出问题
- 某个文案颜色不对，但我不知道具体 id
- 帮我先抓一下然后定位这个异常 view

## Workflow

1. 判断是否已有可用上下文
   - 若用户提供 `CodeLocator Grab Context` / `grab_id` / 路径，直接使用。
   - 若没有，先执行 grab CLI 采集最新数据。

2. 执行 grab CLI（优先 live，失败回退 file）
   - 优先命令：`grab`（通过 `brew tap git54496/codelocatorpro && brew install grab` 安装）。
   - 若 `grab` 不在 PATH 且当前在源码仓库内，回退到本地二进制：`adapter/build/install/grab/bin/grab`。
   - 本地构建（仅回退场景）：
     - `cd adapter && ./gradlew clean installDist --no-daemon`
   - 抓取：
     - `grab`（等价 `grab live`）
     - `grab live --device-serial <optional>`
     - `grab file --path <optional>`（live 失败时）
   - grab 输出默认为 JSON；从输出提取 `grabId`。

3. 加载 grab 文件
   - `~/.codeLocator_mcp/grabs/<grab_id>/snapshot.json`
   - `~/.codeLocator_mcp/grabs/<grab_id>/index.json`
   - `~/.codeLocator_mcp/grabs/<grab_id>/screenshot.png`

4. 根据用户描述做候选 view 推断
   - 使用描述关键词匹配 `idStr/text/className`。
   - 输出 Top 候选（至少 3 个，如果存在）。
   - 候选信息至少包含：`memAddr`, `idStr`, `className`, `text`, `frame`。

5. 处理不确定性
   - 若能唯一确定目标 view：进入下一步。
   - 若无法唯一确定：向用户追问并等待补充（`memAddr` 推荐，或 `id/text/className`）。

6. 转交排查规则
   - 读取并遵循同级 skill：
     - `../codelocator-grab-reader/SKILL.md`
   - 不在本 skill 重复定义排查细则，直接复用该 skill 的排查规则与输出规范。

## Clarification Template

当无法唯一确定目标 view 时，使用：

```text
我已经完成抓取并找到了多个可能匹配的 view，但当前无法唯一确定目标。
请补充以下任一信息：
1) memAddr（推荐）
2) id/idStr
3) text（尽量给完整文案）
可选补充：className 或大致位置（例如顶部标题区域）。
```

## Guardrails

- 不要在没有抓取数据时直接猜测具体 view。
- 不要跳过不确定性处理；无法唯一定位时必须先追问。
- 排查阶段必须复用 `codelocator-grab-reader`，保持规则一致性。
- 不要再使用旧命令 `codelocator-adapter ...` 或旧路径 `adapter/build/install/codelocator-adapter/...`。
