---
description: Document cleanup and organization - enforce standards, verify sync, archive old docs
argument-hint: [action: audit|organize|archive|validate] (default: audit)
---

# Document Cleanup & Organization

Maintain documentation standards, verify knowledge base synchronization, and archive deprecated files.

**Workflow Type**: Recurring (Monthly recommended)

## Action: $ARGUMENTS

Default action: `audit` (check without making changes)

**Actions**:
- `audit` - Check current state, report findings
- `organize` - Rename and move files to correct locations
- `archive` - Move old docs to archive/
- `validate` - Verify documentation standards compliance

---

## Workflow

### 1. Discovery & Audit

**Scan documentation**:
```bash
find docs -type f -name "*.md" | sort
```

**Verify each document**:
1. ✅ Correct location per documentation strategy
2. ✅ Naming convention (kebab-case, timestamps only for temporal docs)
3. ✅ Content still relevant
4. ✅ Synced to knowledge base (if long-term)
5. ✅ Cross-references valid

---

### 2. Documentation Standards

**Common categories**:

| Category | Location | Sync? | Purpose |
|----------|----------|-------|---------|
| **Spec** | docs/spec/ | ✅ YES | System specifications, architecture |
| **Guide** | docs/guide/ | ✅ YES | Tutorials, setup, how-tos |
| **API** | docs/api/ | ✅ YES | API documentation |
| **Reference** | docs/reference/ | ✅ YES | Configuration, CLI reference |
| **Fix** | docs/fix/ | ❌ NO | Temporal: bug fixes, incidents |
| **Archive** | docs/archive/ | ❌ NO | Historical (deprecated docs) |
| **Summary** | docs/summary/ | ✅ Optional | Session summaries, reports |

**Note**: Adapt to match your project's documentation strategy.

**File naming**:
- **Long-term**: `documentation-lifecycle.md` (no timestamp)
- **Temporal**: `2025-12-22-bug-fix.md` (with timestamp)
- **Archive after**: Temporal docs resolved, specs deprecated

---

### 3. Component Verification (Claude Code Projects)

**For Claude Code component verification**, see:
- [component-management.md](../skills/component-management.md) skill
- Includes scripts for agents.json validation
- Covers skillsets configuration checks
- Orphaned file detection patterns

**Quick check**:
```bash
# Verify Claude components exist and are configured
# See component-management.md for full validation scripts
ls -la .claude/agents/*.md | wc -l    # Agent count
ls -la .claude/skills/*.md | wc -l    # Skill count
ls -la .claude/commands/*.md | wc -l  # Command count
```

---

### 4. File Organization

**Move files preserving history**:
```bash
# Use git mv to preserve history
git mv docs/oldname.md docs/spec/newname.md

# Find cross-references
grep -r "oldname" docs/
```

**Archive deprecated docs**:
```bash
# Move to archive
git mv docs/spec/deprecated.md docs/archive/

# Document reason
echo "- **deprecated.md**: Replaced by new-spec.md on $(date +%Y-%m-%d)" >> docs/archive/ARCHIVE_MANIFEST.md
```

---

### 5. Knowledge Base Sync Verification

**Test RAG queries for long-term docs**:
```javascript
// Verify sync status
rag_search_knowledge_base(query="key concept", match_count=3)
```

**List unsynced docs**:
```bash
# Find long-term docs
find docs/spec docs/guide docs/api docs/reference -name "*.md" 2>/dev/null

# Verify via RAG queries
```

---

### 6. Generate Audit Report

**Create report** at `docs/summary/cleanup-YYYYMMDD.md`:

```markdown
# Document Cleanup Audit Report

**Date**: {YYYY-MM-DD}
**Project**: {project-name}
**Action**: {audit|organize|archive|validate}

## Summary

| Check | Status |
|-------|--------|
| Long-term docs | {count} files |
| Temporal docs | {count} files |
| Archived docs | {count} files |
| Component config | {✅ Valid | ⚠️ Issues | N/A} |
| Naming standards | {✅ Compliant | ⚠️ Issues} |
| Cross-references | {✅ Valid | ⚠️ Broken} |

## Issues Found

{List issues}

## Recommendations

{Project-specific recommendations}

## Actions Taken

{Files moved, renamed, archived}
```

---

## Best Practices

✅ **DO**:
- Use `git mv` (preserves history)
- Update cross-references before committing
- Document archival reasons
- Test knowledge base sync for long-term docs
- Run validation after changes

❌ **DON'T**:
- Copy + delete (loses git history)
- Leave orphaned/untracked docs
- Skip cross-reference updates
- Delete without archiving

---

## Usage Examples

### Audit Current State
```bash
/doc-cleanup audit
```

Shows:
- Documentation structure
- Naming compliance
- Component status (if Claude Code)
- Cross-reference validity
- Issues found

### Organize Files
```bash
/doc-cleanup organize
```

Performs:
- Move misplaced docs
- Rename to standards
- Update cross-references

### Archive Old Docs
```bash
/doc-cleanup archive
```

Identifies:
- Temporal docs >90 days old
- Deprecated specs
- Prompts before moving

### Validate Everything
```bash
/doc-cleanup validate
```

Checks:
- All docs properly categorized
- Cross-references valid
- Knowledge base sync status
- Naming standards
- Git history preserved

---

## Related

**Commands**:
- `archon-sync` - Sync docs to knowledge base
- `doc-batch-sync` - Batch sync multiple docs
- `agent-audit` - Audit agent definitions

**Skills**:
- [component-management.md](../skills/component-management.md) - Component verification patterns
- [documentation-lifecycle.md](../skills/documentation-lifecycle.md) - Documentation workflows

**Documentation**:
- `docs/spec/documentation-lifecycle-workflow.md` - Implementation decisions (if available)
