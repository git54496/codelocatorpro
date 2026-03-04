---
name: codelocator-grab-reader
description: 读取和分析 CodeLocator grab 产物（screenshot.png、snapshot.json、index.json）。当用户提供 CodeLocator Grab Context、本地绝对路径、grab_id，或要求基于 grab 做 UI 结构分析、视图定位、属性解释、问题定位时使用。
---

# Codelocator Grab Reader

## Overview

- 使用本地 grab 结果还原页面结构，并给出可追溯的分析结论。
- 统一消费 `CodeLocator Grab Context`，避免手工猜路径。

## Expected Input

优先识别如下结构化文本：

```text
[CodeLocator Grab Context]
grab_id: ...
screenshot_path: ...
snapshot_path: ...
index_path: ...
grab_dir: ...
```

用户也可能只提供 `grab_id` 或 `grab_dir`。

## Specific View Input Gate

当任务目标是“排查某个特定 view”时，先检查是否有可定位输入。

- 推荐：`memAddr`（优先级最高）
- 可选：`id`（`idStr`）、`text`
- 可补充：`className`、大致位置描述（例如顶部/底部）

如果以上定位信息都缺失，不进入排查流程，先返回并提示用户补充。

提示模板：

```text
要排查某个特定 view，请先提供至少一个定位条件：
1) memAddr（推荐，最准确）
2) id/idStr
3) text
可选再补充 className 或位置描述，便于缩小范围。
```

## Workflow

1. 解析输入并标准化路径。
2. 校验文件是否存在。
3. 读取 `snapshot.json`（必需），`index.json`（可选），`screenshot.png`（可选）。
4. 输出结构化结论并附证据（路径、memAddr、idStr、className）。

## Path Resolution

按以下优先级解析路径：

1. 用户显式给出的 `snapshot_path` / `index_path` / `screenshot_path`
2. 用户给出的 `grab_dir` 下的默认文件
3. 仅有 `grab_id` 时，使用：
   - `~/.codeLocator_mcp/grabs/<grab_id>/snapshot.json`
   - `~/.codeLocator_mcp/grabs/<grab_id>/index.json`
   - `~/.codeLocator_mcp/grabs/<grab_id>/screenshot.png`

## Minimal Parsing Rules

- `snapshot.json` 关键字段：
  - `meta`: 抓取来源、包名、Activity、时间
  - `uiTree`: 视图树
- `uiTree` 节点常用字段：
  - `memAddr`, `className`, `idStr`, `text`
  - `left`, `top`, `width`, `height`
  - `visible`, `alpha`, `children`, `raw`
- `index.json`：通常是 `memAddr -> node summary` 的索引映射。

## Raw Key Mapping Rule

当用户要求解释 `raw` 短键时，按 WView 映射解释。常见键：

- `ac -> mIdStr`
- `af -> mMemAddr`
- `ag -> mClassName`
- `aq -> mText`
- `ab -> mVisibility`
- `ae -> mAlpha`
- `a -> mChildren`

如果遇到未知短键，明确标注为 `unknown_key`，不要臆测。

## Quick Analysis Output Format

默认按以下格式输出：

1. Context
   - `grab_id` 与实际读取文件绝对路径
2. Snapshot Summary
   - package/activity/source/grabTime
   - root 节点数、总节点数
3. Target Findings
   - 按用户目标（id/class/text/memAddr）列出命中节点
   - 每条包含 `memAddr + idStr + className + frame`
4. Evidence
   - 引用具体路径与关键字段
5. Next Step (optional)
   - 如需要，提示用户追加条件（例如指定 id 或 memAddr）

## Quick Node Count Snippet

当需要快速统计节点数量时，执行：

```bash
python3 - <<'PY'
import json
from pathlib import Path

snapshot = Path("<snapshot_path>")
data = json.loads(snapshot.read_text())

def walk(nodes):
    total = 0
    for n in nodes or []:
        total += 1
        total += walk(n.get("children") or [])
    return total

roots = data.get("uiTree") or []
print("root_count=", len(roots))
print("node_count=", walk(roots))
print("package=", (data.get("meta") or {}).get("packageName"))
print("activity=", (data.get("meta") or {}).get("activity"))
PY
```

## Guardrails

- 不要编造文件内容、路径或图片内容。
- 如果路径不可访问，明确说明并要求用户提供可读路径或文件。
- 输出结论时始终带证据字段，不给“无证据结论”。
