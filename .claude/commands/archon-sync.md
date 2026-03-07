---
description: Synchronize documentation to Archon knowledge base for RAG queries
argument-hint: [mode: full|validate|dryrun|whatif] (default: full)
---

# Archon Knowledge Base Sync

Execute the recurring Archon sync workflow to synchronize docs/ to Archon's RAG knowledge base.

**Workflow Type**: Recurring Template (Weekly/as-needed)
**Example Task Name**: `recurr-D` (or your project-specific identifier)

**Mode: $ARGUMENTS** (full|validate|dryrun|whatif)

## Actions & Modes

**Primary Modes**:
- `full` (default) - Full sync: convert and upload documents to Archon
- `validate` - Validation only: check existing RAG indexing
- `dryrun` - Preview: show what would be synced without uploading
- `whatif` - Safety preview: show sync plan with detailed analysis

**Optional Flags**:
- `--force` - Override sync protection (requires confirmation)

### Mode Comparison

| Mode | Discovers Docs | Converts | Uploads | RAG Validates | Safe |
|------|---------------|----------|---------|---------------|------|
| `whatif` | ✅ | ❌ | ❌ | ❌ | ✅ Safe |
| `dryrun` | ✅ | ✅ | ❌ | ❌ | ✅ Safe |
| `validate` | ❌ | ❌ | ❌ | ✅ | ✅ Safe |
| `full` | ✅ | ✅ | ✅ | ✅ | ⚠️ Modifies |

---

## WHAT-IF MODE (Safe Preview)

### Purpose

Preview what documents WOULD be synced to Archon without making any changes:
- ✅ Discover documents eligible for sync
- ✅ Show file sizes, categories, RAG sync status
- ✅ Identify new vs existing documents
- ✅ Estimate sync time and impact
- ❌ NO conversion, NO upload, NO modifications

### When to Use What-If

**Recommended before**:
- First-time sync of new project
- After major documentation reorganization
- Before batch sync of many documents
- When verifying documentation strategy

**Example Output**:
```
=== ARCHON SYNC WHAT-IF PREVIEW ===

Documents to Sync (Long-term only):
  ✅ docs/spec/documentation-lifecycle-workflow.md (19 KB)
  ✅ docs/spec/archon-board-inventory.md (17 KB)
  ✅ docs/spec/component-inventory.md (12 KB)
  ✅ docs/guide/claude-hooks-documentation.md (14 KB)
  ✅ docs/reference/documentation-workflows-quick-reference.md (7 KB)

Excluded (Short-term - not synced):
  ℹ️ docs/fix/2025-12-22-restructure-summary.md (temporal)
  ℹ️ docs/summary/*.md (optional sync)

Summary:
  - 9 documents eligible for sync
  - Total size: 97 KB
  - Estimated sync time: 30-60 seconds
  - New documents: 2
  - Updates: 7

No changes made. Run with 'full' mode to execute sync.
```

### Running What-If Mode

```bash
# Preview sync without changes
claude /archon-sync whatif

# Preview specific directory
claude /archon-sync whatif --dir docs/spec
```

**What-If checks**:
1. ✅ Scans docs/ for long-term documentation
2. ✅ Identifies spec/, guide/, api/, reference/ categories
3. ✅ Excludes fix/ (temporal) and archive/ (historical)
4. ✅ Shows file sizes and modification dates
5. ✅ Estimates conversion and upload time
6. ✅ Identifies new vs existing documents
7. ✅ Reports summary statistics
8. ❌ NO files converted or uploaded

---

## TASK MANAGEMENT WORKFLOW

### 1. Find and Mark Task as "doing"

Find the recurring Archon sync task:
```javascript
find_tasks(query="Archon sync")
// or filter by project:
find_tasks(filter_by="project", filter_value="Ravenkraft Dev")
// Look for task labeled "archon-sync" or "recurr-D"
```

Mark task as in progress:
```javascript
manage_task("update", task_id="<task-id>", status="doing")
```

---

## SYNC EXECUTION WORKFLOW

### 2. Pre-Sync Check

**ALWAYS run what-if preview first** (recommended):
```bash
# Preview sync plan
claude /archon-sync whatif
```

Review output and confirm:
- ✅ Correct documents identified
- ✅ Categories are accurate (spec/, guide/, etc.)
- ✅ Temporal docs excluded (fix/, summary/ if appropriate)
- ✅ File sizes reasonable

Verify Archon connectivity:
```javascript
mcp__archon__health_check()
// Expected: {"status": "healthy"}
```

Check documents to sync:
```powershell
# Long-term docs (RAG-eligible)
Get-ChildItem docs/spec, docs/guide, docs/api, docs/reference -File -Filter "*.md" -Recurse -ErrorAction SilentlyContinue

# Show file sizes
Get-ChildItem docs/spec, docs/guide, docs/api, docs/reference -File -Filter "*.md" -Recurse -ErrorAction SilentlyContinue | 
  Select-Object @{N="Category";E={$_.Directory.Name}}, Name, @{N="Size";E={"{0:N0} KB" -f ($_.Length / 1KB)}}
```

### 3. Run Sync Script

Execute the document conversion and upload:

```powershell
cd metamake/projects/archon-doc-sync/scripts

# Full sync (default - converts and uploads)
.\Sync-AllDocs-ToArchon.ps1 -SourceDirectory "..\..\..\..\docs" -Mode "full"

# Dry run (preview without upload)
.\Sync-AllDocs-ToArchon.ps1 -DryRun

# Validation only (check existing)
.\Sync-AllDocs-ToArchon.ps1 -ValidationMode
```

**Output:** JSON files in `metamake/projects/archon-doc-sync/output/`

---

## RAG VALIDATION WORKFLOW

### 4. Validate Sync Results

Test RAG queries to confirm documents indexed:

```javascript
// Architecture patterns
rag_search_knowledge_base(query="CommandManager pattern", match_count=3)
rag_search_knowledge_base(query="ServiceRegistry dependency injection", match_count=3)
rag_search_knowledge_base(query="Repository pattern async", match_count=3)

// Database patterns
rag_search_knowledge_base(query="HikariCP connection pooling", match_count=3)
rag_search_knowledge_base(query="Database patterns transactions", match_count=3)

// Integration
rag_search_knowledge_base(query="RVNKCore migration", match_count=3)
rag_search_knowledge_base(query="async CompletableFuture", match_count=3)

// Code examples
rag_search_code_examples(query="Repository QueryBuilder", match_count=3)
rag_search_code_examples(query="ServiceRegistry injection", match_count=3)
```

**Expected:** All queries return relevant matches from docs/

---

## TASK COMPLETION WORKFLOW

### 5. Update Task to Review

When sync succeeds and validation passes:

```javascript
manage_task("update",
  task_id="<task-id>",
  status="review",
  description="Archon sync completed: 7 docs converted, RAG validation passed"
)
```

**Task remains in "review" status until manually marked done or next cycle begins.**

---

## Document Registry

| Document | Archon ID |
|----------|-----------|
| coding-standards.md | 417fc4de-5a65-4e60-beec-3670970c82e4 |
| database-patterns.md | dac7d61d-febc-41a9-a7e2-e4c2a9c34be4 |
| rest-api-standards.md | 29176389-17de-4889-8685-a1ec52d9a990 |
| rvnkcore-integration.md | 917f31a5-0272-46a6-9a8c-6cf2d0e1da4b |
| dependency-graph.md | 64cd8a05-d895-4897-bd29-caf967b31f26 |
| shared-patterns.md | ba6339fb-cbfe-4a75-b056-3f48bdf8b6fa |
| to-rvnkcore.md | 4e897e75-e130-4977-8ed4-dc8d81dbe6f0 |

---

## Task Status Flow

```
todo (waiting) → doing (syncing) → review (completed, awaiting validation) → done (approved)
```

**This command keeps tasks in "review" status after completion, visible for audit trail.**

---

## What-If Workflow (Recommended)

### Step-by-Step Safe Sync

**1. Preview with What-If**
```bash
claude /archon-sync whatif
```
Review output:
- Documents to sync
- Excluded documents
- File sizes and categories
- Estimated sync time

**2. Verify Archon Connectivity**
```javascript
mcp__archon__health_check()
```

**3. Execute Full Sync** (after confirming preview)
```bash
claude /archon-sync full
```

**4. Validate RAG Indexing**
```bash
claude /archon-sync validate
```
Or run RAG queries:
```javascript
rag_search_knowledge_base(query="documentation lifecycle", match_count=3)
```

**5. Update Task Status**
```javascript
manage_task("update", task_id="...", status="review")
```

### Best Practices

✅ **DO**:
- Always run `whatif` before `full` sync
- Review what-if output carefully
- Verify document categories are correct
- Check file sizes are reasonable
- Confirm temporal docs excluded
- Run validation after sync

❌ **DON'T**:
- Skip what-if preview on first sync
- Sync without checking Archon connectivity
- Forget to validate RAG indexing after sync
- Ignore what-if warnings about large files

---

## Troubleshooting

- **Script not found**: Verify PowerShell 7+ installed
- **Upload fails**: Check Archon health_check and connectivity
- **Stale RAG results**: Allow 5-10 minutes for indexing after upload
- **Task not found**: Use `find_tasks(query="archon")` to search

---

## Related Commands

- `/doc-cleanup` - Run before sync if docs reorganized
- `/agent-audit` - Audit instruction files
- `/automation-discovery` - Find automation opportunities

---

*This command executes recurr-D: Archon Knowledge Base Sync workflow*
*Task automatically transitioned: todo → doing → review*
