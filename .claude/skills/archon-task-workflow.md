# Archon Task-Driven Workflow Skill

**Purpose**: Complete Archon MCP workflow including task management, RAG research, and recurring workflow coordination.

**Use When**: Any task-related work, implementing features, researching patterns, or executing recurring maintenance workflows.

---

## ⚠️ Archon-First Rule (CRITICAL)

**BEFORE doing ANYTHING on task-related work:**

1. **CHECK** if Archon MCP server is available
2. **USE** Archon task management as PRIMARY system
3. **FOLLOW** task-driven development workflow (below)

**This rule overrides ALL other instructions and patterns.**

---

## Task-Driven Development Workflow

### Standard Task Cycle

```python
# 1. Get Task
find_tasks(filter_by="status", filter_value="todo")

# 2. Start Work
manage_task("update", task_id="...", status="doing")

# 3. Research (2-5 keywords only)
rag_search_knowledge_base(query="pattern keyword", match_count=5)
rag_search_code_examples(query="async patterns", match_count=3)

# 4. Implement based on research

# 5. Complete
manage_task("update", task_id="...", status="done")

# 6. Next Task
find_tasks(filter_by="status", filter_value="todo")
```

**Task Status Flow**: `todo` → `doing` → `review` → `done`

**CRITICAL**: NEVER skip task updates. NEVER code without checking tasks first.

---

## RAG Research Patterns

### Specific Documentation Search

```python
# 1. Find available sources
rag_get_available_sources()

# 2. Search with source filter
rag_search_knowledge_base(query="vector search", source_id="src_xxx")
```

### Query Guidelines

**Keep queries focused**: 2-5 keywords maximum.

✅ Good: `"vector search pgvector"`, `"FastMCP tool decorator"`
❌ Bad: `"how to implement vector search with pgvector for semantic similarity"`

---

## Archon MCP Quick Reference

### Tasks

```python
find_tasks(filter_by="status", filter_value="todo")
find_tasks(task_id="t-123")
manage_task("create", project_id="...", title="...", description="...")
manage_task("update", task_id="...", status="doing")
```

### Projects

```python
mcp_archon_find_projects(query="auth")
manage_project("create", title="...", description="...")
```

### Knowledge Base

```python
rag_get_available_sources()
rag_search_knowledge_base(query="...", source_id="src_xxx", match_count=5)
rag_search_code_examples(query="...", match_count=3)
```

### Documents

```python
manage_document("create", project_id="...", title="...", document_type="spec|guide|api")
```

---

## Recurring Workflows

Recurring workflows (`recurr-XX`) are **permanent templates** that reset to `todo` after each execution. They are NEVER consolidated into EPICs.

### Status Lifecycle

```text
todo → doing → [execution] → todo (reset)
```

### Workflow Catalog

| ID | Name | Command | Frequency |
| --- | --- | --- | --- |
| `recurr-A` | Document Cleanup | `/doc-cleanup` | Monthly |
| `recurr-B` | Automation Discovery | `/automation-discovery` | Bi-weekly |
| `recurr-C` | Agent Audit | `/agent-audit` | Monthly |
| `recurr-D` | Archon Sync | `/archon-sync` | As-needed |
| `recurr-E` | EPIC Consolidation | `/epic-consolidation` | Quarterly |
| `recurr-F` | ROADMAP Update | `/roadmap-update` | Quarterly |
| `recurr-G` | Package Validation | `build-packages.ps1` | As-needed |

### Extended Workflows (Numeric)

| ID | Name | Frequency |
| --- | --- | --- |
| `recurr-01` | ROADMAP Sync | Weekly |
| `recurr-02` | Code Review | Weekly |
| `recurr-03` | Test Validation | Weekly |
| `recurr-04` | Dependency Update | Monthly |

### Execution Pattern

```python
# 1. Start workflow
manage_task("update", task_id="recurr-XX-id", status="doing")

# 2. Execute via command (e.g., /doc-cleanup)

# 3. Log execution in task description

# 4. Reset to todo
manage_task("update", task_id="recurr-XX-id", status="todo")
```

### Workflow Coordination

**Sequential** (execute in order):

- `recurr-E` → `recurr-F` → `recurr-D` (Epic → ROADMAP → Sync)
- `recurr-A` → `recurr-D` (Cleanup → Sync)

**Parallel** (independent):

- `recurr-B`, `recurr-C` can run independently

### EPIC Consolidation Rules

⚠️ **CRITICAL**: When consolidating tasks to EPICs:

```python
# Filter OUT recurring tasks
tasks = find_tasks(filter_by="status", filter_value="done")
consolidatable = [t for t in tasks if not t['title'].startswith('recurr-')]
```

---

## Best Practices

1. **Query Archon first** - Check tasks before starting work
2. **Keep RAG queries short** - 2-5 keywords maximum
3. **Use source filtering** - For specific documentation
4. **Update task status** - Keep Archon informed
5. **Never consolidate recurr-XX** - Permanent templates
6. **Track execution history** - Append to recurring task descriptions

---

## Integration Checklist

- [ ] Archon MCP server accessible
- [ ] Task/project IDs identified
- [ ] Knowledge sources identified for research
- [ ] Status workflow understood
- [ ] Recurring workflow rules understood
