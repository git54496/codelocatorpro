---
name: android-ui-grab-reader
description: 读取和分析 Android UI Grab 的 grab 产物（包括 screenshot、snapshot、view index、compose 相关 index，以及 activity 栈和 fragment 信息）。当用户提供当前实现产出的 `CodeLocator Grab Context`、本地绝对路径、grab_id，或要求基于 grab 做 UI 结构分析、视图或 Compose 节点定位、属性解释、activity/fragment 上下文分析、问题定位时使用。
---

# Android UI Grab Reader

## Overview

- 使用本地 grab 结果还原页面结构，并给出可追溯的分析结论。
- 统一消费当前实现产出的 `CodeLocator Grab Context`，避免手工猜路径。
- 同时支持传统 View 树与 Compose 相关索引，不要只按 View-only 心智分析。
- 同时支持 activity 栈和 fragment 摘要，但要明确区分“当前页面正在展示”的信息与“被盖住”的信息。

## Expected Input

优先识别如下结构化文本：

```text
[CodeLocator Grab Context]
grab_id: ...
screenshot_path: ...
snapshot_path: ...
index_path: ...
compose_index_path: ...
component_index_path: ...
render_index_path: ...
semantics_index_path: ...
link_index_path: ...
grab_dir: ...
```

用户也可能只提供 `grab_id` 或 `grab_dir`。

## Targeting Gate

当任务目标是“精确排查某个特定 view / Compose 节点”时，先判断当前信息是否足够直接唯一定位。

- 推荐：`memAddr`（优先级最高）
- View 可选：`id`（`idStr`）、`text`
- Compose 可选：`nodeId` / `composeKey` / `semanticsId` / `testTag` / `contentDescription`
- 可补充：`className`、大致位置描述（例如顶部/底部）

若只有 `className`、位置描述、模糊文案等粗粒度线索，不要立刻拒绝；应先基于现有 grab 产物给出候选节点。
只有在需要输出“唯一目标”且现有信息不足以缩到单个候选时，才追问用户补充。

提示模板：

```text
我已经基于当前 grab 找到多个可能目标，但还不能唯一确定。
请补充至少一个更精确的定位条件：
1) memAddr（推荐，最准确）
2) id/idStr（View）或 nodeId/composeKey（Compose）
3) text / testTag / contentDescription
可选再补充 className 或位置描述，便于缩小范围。
```

## Workflow

1. 解析输入并标准化路径。
2. 校验文件是否存在。
3. 读取 `snapshot.json`（必需）。
4. 按需读取相关索引：
   - View 分析优先读取 `index.json`
   - Compose 分析优先读取 `compose_index.json` / `component_index.json` / `render_index.json` / `semantics_index.json` / `link_index.json`
   - `screenshot.png` 仅作为辅助证据，可选
5. 先给出 summary，再按用户目标输出 View 或 Compose 的命中结果与证据。
6. 若无法唯一定位但可以缩小范围，先给候选；只有需要唯一目标时才追问。

## Path Resolution

按以下优先级解析路径：

1. 用户显式给出的 `snapshot_path` / `index_path` / `screenshot_path` / `compose_index_path` / `component_index_path` / `render_index_path` / `semantics_index_path` / `link_index_path`
2. 用户给出的 `grab_dir` 下的默认文件
3. 仅有 `grab_id` 时，使用：
   - `~/.android-ui-grab/grabs/<grab_id>/snapshot.json`
   - `~/.android-ui-grab/grabs/<grab_id>/index.json`
   - `~/.android-ui-grab/grabs/<grab_id>/compose_index.json`
   - `~/.android-ui-grab/grabs/<grab_id>/component_index.json`
   - `~/.android-ui-grab/grabs/<grab_id>/render_index.json`
   - `~/.android-ui-grab/grabs/<grab_id>/semantics_index.json`
   - `~/.android-ui-grab/grabs/<grab_id>/link_index.json`
   - `~/.android-ui-grab/grabs/<grab_id>/screenshot.png`

如新路径不存在，再回退兼容旧路径 `~/.codeLocator_mcp/grabs/<grab_id>/...`。

## Minimal Parsing Rules

- `snapshot.json` 关键字段：
  - `meta`: 抓取来源、包名、Activity、时间
  - `uiTree`: 视图树
  - `activityStack`: activity 栈摘要；每个 activity 可带 fragment 树
  - `composeIndexes` / `componentIndexes` / `renderIndexes` / `semanticsIndexes` / `linkIndexes`：Compose 相关索引也可能直接存在于 snapshot 中
- `uiTree` 节点常用字段：
  - `memAddr`, `className`, `idStr`, `text`
  - `left`, `top`, `width`, `height`
  - `visible`, `alpha`, `children`, `raw`
  - `composeNodes`, `composeCapture`
- `index.json`：通常是 `memAddr -> node summary` 的索引映射。
- `compose_index.json`：通常是 `compose_key -> compose node summary`。
- `component_index.json`：通常是 `component_key -> compose component summary`。
- `render_index.json`：通常是 `render_key -> compose render summary`。
- `semantics_index.json`：通常是 `semantics_key -> semantics node summary`。
- `link_index.json`：通常是 `link_key -> compose link summary`。
- `activityStack[*]` 常用字段：
  - `className`, `memAddr`, `startInfo`
  - `current`, `covered`, `paused`, `stopped`
  - `fragments`
- `fragment` 常用字段：
  - `className`, `memAddr`, `tag`, `fragmentId`, `viewMemAddr`
  - `visible`, `added`, `userVisibleHint`, `boundViewVisible`
  - `coveredByTopActivity`, `effectiveVisible`, `children`

## Visibility Rule

- `screenshot.png`、主 overlay、高亮框、`uiTree` 主树，只代表当前顶层 activity 正在展示的页面。
- `activityStack` 中 `covered=true` 的 activity，以及其下 `coveredByTopActivity=true` 的 fragment，代表“已经抓到但当前被盖住”的信息。
- 如果 fragment `effectiveVisible=true`，说明它当前既没有被顶层 activity 盖住，也满足 fragment/view 可见性条件；否则只能作为上下文线索，不应当作当前截图里的直接证据。

## Raw Key Mapping Rule

当用户要求解释 `raw` 短键时，只对 WView raw 使用短键映射解释。常见键：

- `ac -> mIdStr`
- `af -> mMemAddr`
- `ag -> mClassName`
- `aq -> mText`
- `ab -> mVisibility`
- `ae -> mAlpha`
- `a -> mChildren`

如果遇到未知短键，明确标注为 `unknown_key`，不要臆测。
不要把 WView 短键映射生搬到 Compose index 或 Compose capture 字段上。

## Quick Analysis Output Format

默认按以下格式输出：

1. Context
   - `grab_id` 与实际读取文件绝对路径
2. Snapshot Summary
   - package/activity/source/grabTime
   - root 节点数、总节点数
   - activity 总数、covered activity 数
   - fragment 总数、effectiveVisible fragment 数、covered fragment 数
   - 如存在 Compose 数据，补充 compose/component/render/semantics/link 索引数量
3. Target Findings
   - 按用户目标列出命中节点
   - View 命中至少包含 `memAddr + idStr + className + frame`
   - Compose 命中至少包含 `hostMemAddr + key + nodeId/componentId/renderId/semanticsId + frame_or_source`
4. Evidence
   - 引用具体路径与关键字段
5. Next Step (optional)
   - 如需要，提示用户追加条件（例如指定 memAddr、composeKey 或更精确文本）

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
- 当存在 Compose 相关索引或 `composeCapture` 时，不要忽略这些证据后只按传统 View 树下结论。
- 不要在仅有粗粒度线索时直接拒绝分析；先给候选，再决定是否追问更精确定位条件。
- 不要把 `activityStack` 里 `covered` 的 activity / fragment 当成当前主截图里正在展示的内容。
