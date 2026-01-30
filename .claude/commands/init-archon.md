---
description: Initialize Archon assets from templates with conflict resolution
tags: [archon, initialization, templates, setup]
priority: 1
status: active
---

# /init-archon - Template-Based Project Initialization

**Purpose**: Bootstrap ANY project with archon-sandbox's proven patterns (agents, commands, skills, hooks) using template-driven initialization with intelligent conflict resolution.

**Target Audience**: Developers setting up new projects or adding Archon capabilities to existing codebases

## Syntax

```bash
/init-archon <template> [--types=type1,type2,...] [--what-if] [--force]
```

## Arguments

- **template** (required): Template name to install
  - `base`: Minimal Archon setup (2 agents, 3 skills, 1 hook, 3 commands)
  - `rvnk`: Complete RVNKDev setup (all 25 agents + RVNK skills)
  - `worktree`: Parallel development (all 4 hooks + worktree assets)
  - `internal`: Full archon-sandbox replica (ALL assets)
  - `project:<name>`: User-created template from `.claude/templates/project/<name>/`
  - `custom:<url>`: External template from URL or file path

- **--types** (optional): Comma-separated filter for asset types
  - Valid values: `agents`, `commands`, `skills`, `instructions`, `hooks`, `metamake`
  - Default: All types from template
  - Example: `--types=agents,commands,skills`

- **--what-if** (optional): Preview changes without modifying files (safe mode)
  - Shows conflict detection results
  - Displays merge strategy for each file
  - Estimates line counts and file changes
  - No files modified, no git commits

- **--force** (optional): Skip conflict prompts and use default merge strategy
  - Auto-merges agent configs by filename
  - Auto-resolves category conflicts using template values
  - Useful for CI/CD or scripted setups
  - Use with caution in production

## Workflow

### Phase 1: Template Loading

1. **Validate Template**
   - Check template exists in `.claude/templates/{template}/`
   - Load `manifest.json` and validate schema
   - Verify all referenced assets exist
   - Check version compatibility

2. **Load Assets**
   - Parse manifest asset definitions
   - Filter by `--types` if specified
   - Build dependency graph for skill validation
   - Prepare file operation list

### Phase 2: Conflict Detection

**Filename-Based Detection** (automatic):
- Agent configs: Hash comparison of `.claude/agents/{name}.md`
- Commands: File existence check `.claude/commands/{name}.md`
- Skills: File existence check `.claude/skills/{name}.md`
- Hooks: YAML key collision in `.claude/hooks/{name}.yaml`
- Instructions: File existence check (CLAUDE.md, copilot-instructions.md)

**Category Conflicts** (prompt required):
- agents.json category mapping differences
- Detect when same agent exists in different categories
- Prompt user for category resolution only

### Phase 3: Merge Strategy

**Automatic Merges** (no prompt):
1. **Identical Files**: Skip installation (hash match)
2. **New Files**: Copy to destination (no conflict)
3. **Agent Configs**: Merge by filename
   - Preserve frontmatter from existing file
   - Append template sections if missing
   - Update cross-references
   - Git mv for history preservation

**Prompted Merges** (category conflicts only):
- Display existing category: `[active.archon.core]`
- Display template category: `[active.development.planning]`
- Prompt options:
  - `keep`: Keep existing category
  - `replace`: Use template category
  - `manual`: Edit agents.json manually (pause workflow)

### Phase 4: Installation

1. **Backup Creation**
   ```bash
   # Create backup branch
   git checkout -b init-archon-backup-{timestamp}
   git add .
   git commit -m "backup: Pre-init-archon state for {template} template"
   git checkout -
   ```

2. **File Operations**
   - Copy new files to destinations
   - Merge existing files using strategy
   - Update agents.json with merged categories
   - Install hooks to `.claude/hooks/`
   - Copy instruction files if missing

3. **Post-Install Validation**
   - Validate agents.json schema
   - Check skill dependencies resolved
   - Verify hook trigger compatibility
   - Test command execution (dry-run)
   - Generate installation report

### Phase 5: Summary Report

```
=== INIT-ARCHON INSTALLATION REPORT ===

Template: base (v1.0.0)
Timestamp: 2025-12-22T15:30:00Z

INSTALLED ASSETS:
✅ Agents: 2 new, 0 merged, 0 skipped
   • archon-agent.md (new)
   • task-manager.md (new)

✅ Commands: 3 new, 0 merged, 0 skipped
   • archon-sync.md (new)
   • task-status.md (new)
   • agent-audit.md (new)

✅ Skills: 3 new, 0 merged, 0 skipped
   • archon-task-management.md (new)
   • archon-knowledge-query.md (new)
   • archon-project-sync.md (new)

✅ Hooks: 1 new, 0 merged, 0 skipped
   • worktree-task-manager.yaml (new, disabled by default)

✅ Instructions: 0 new, 0 merged, 1 skipped
   • CLAUDE.md (already exists, skipped)

CONFLICTS RESOLVED:
⚠️ Category Conflicts: 0 prompted, 0 resolved

BACKUP CREATED:
📦 Branch: init-archon-backup-20251222-153000
📦 Commit: a1b2c3d "backup: Pre-init-archon state for base template"

NEXT STEPS:
1. Review installed files in .claude/
2. Update agents.json if needed (0 category conflicts detected)
3. Enable hooks in .claude/hooks/ as needed
4. Test commands: /archon-sync --what-if
5. Delete backup branch if satisfied: git branch -D init-archon-backup-20251222-153000

Total Time: 3.2s
```

## Template Structure

### Manifest Schema (.claude/templates/{name}/manifest.json)

```json
{
  "name": "base",
  "version": "1.0.0",
  "description": "Minimal Archon setup for basic task management",
  "author": "archon-sandbox",
  "created": "2025-12-22T15:00:00Z",
  "compatibility": {
    "min_claude_version": "1.0.0",
    "max_claude_version": "2.0.0",
    "requires_mcp": true
  },
  "assets": {
    "agents": [
      {
        "file": "agents/archon-agent.md",
        "category": "active.archon.core",
        "priority": 1
      },
      {
        "file": "agents/task-manager.md",
        "category": "active.development.planning",
        "priority": 2
      }
    ],
    "commands": [
      "commands/archon-sync.md",
      "commands/task-status.md",
      "commands/agent-audit.md"
    ],
    "skills": [
      "skills/archon-task-management.md",
      "skills/archon-knowledge-query.md",
      "skills/archon-project-sync.md"
    ],
    "hooks": [
      {
        "file": "hooks/worktree-task-manager.yaml",
        "enabled_by_default": false
      }
    ],
    "instructions": [],
    "dependencies": {
      "required_skills": [
        "archon-task-management",
        "archon-knowledge-query"
      ],
      "optional_skills": [
        "git-workflow-basics"
      ]
    }
  },
  "post_install": {
    "validation_checks": [
      "agents.json schema valid",
      "skill dependencies resolved",
      "hook triggers compatible"
    ],
    "recommended_actions": [
      "Review and enable hooks in .claude/hooks/",
      "Test /archon-sync command",
      "Configure .mcp.json if needed"
    ]
  }
}
```

### Directory Layout

```
.claude/templates/
├── base/
│   ├── manifest.json
│   ├── agents/
│   │   ├── archon-agent.md
│   │   └── task-manager.md
│   ├── commands/
│   │   ├── archon-sync.md
│   │   ├── task-status.md
│   │   └── agent-audit.md
│   ├── skills/
│   │   ├── archon-task-management.md
│   │   ├── archon-knowledge-query.md
│   │   └── archon-project-sync.md
│   └── hooks/
│       └── worktree-task-manager.yaml
├── rvnk/
│   ├── manifest.json
│   └── ... (25 agents + RVNK-specific assets)
├── worktree/
│   ├── manifest.json
│   └── ... (4 hooks + worktree commands)
├── internal/
│   ├── manifest.json
│   └── ... (complete archon-sandbox replica)
└── project/
    └── {user-templates}/
```

## Examples

### Example 1: Install Minimal Setup (What-If Mode)

```bash
/init-archon base --what-if
```

**Output:**
```
=== INIT-ARCHON PREVIEW ===

Template: base (v1.0.0)
Mode: WHAT-IF (no files will be modified)

PLANNED OPERATIONS:

📁 Agents (2 new):
   ✅ archon-agent.md → .claude/agents/archon-agent.md
      Category: active.archon.core
      Priority: 1
   
   ✅ task-manager.md → .claude/agents/task-manager.md
      Category: active.development.planning
      Priority: 2

📁 Commands (3 new):
   ✅ archon-sync.md → .claude/commands/archon-sync.md
   ✅ task-status.md → .claude/commands/task-status.md
   ✅ agent-audit.md → .claude/commands/agent-audit.md

📁 Skills (3 new):
   ✅ archon-task-management.md → .claude/skills/archon-task-management.md
   ✅ archon-knowledge-query.md → .claude/skills/archon-knowledge-query.md
   ✅ archon-project-sync.md → .claude/skills/archon-project-sync.md

📁 Hooks (1 new, disabled by default):
   ✅ worktree-task-manager.yaml → .claude/hooks/worktree-task-manager.yaml
      Triggers: pre-tool
      Status: disabled (manual enable required)

CONFLICT ANALYSIS:
✅ No conflicts detected (clean installation)

ESTIMATED CHANGES:
• Files created: 9
• Files modified: 1 (agents.json)
• Backup branch: init-archon-backup-{timestamp}
• Total lines added: ~1,200

Run without --what-if to proceed with installation.
```

### Example 2: Install RVNKDev Setup (Agents & Commands Only)

```bash
/init-archon rvnk --types=agents,commands
```

**Output:**
```
=== INIT-ARCHON INSTALLATION ===

Template: rvnk (v1.0.0)
Types: agents, commands (skills, hooks, instructions excluded)

CONFLICT DETECTION:

⚠️ Filename conflicts detected (3):
   • archon-agent.md exists → Auto-merge by filename
   • task-manager.md exists → Auto-merge by filename
   • doc-sync-agent.md exists → Auto-merge by filename

⚠️ Category conflicts detected (1):
   
   Agent: doc-sync-agent
   Existing category: [active.documentation.sync]
   Template category: [active.archon.documentation]
   
   Choose resolution:
   [k]eep existing, [r]eplace with template, [m]anual edit
   > r

   ✅ Using template category: [active.archon.documentation]

CREATING BACKUP:
✅ Branch created: init-archon-backup-20251222-153500
✅ Committed: "backup: Pre-init-archon state for rvnk template"

INSTALLING ASSETS:

📁 Agents (25 total):
   ✅ 3 merged (archon-agent, task-manager, doc-sync-agent)
   ✅ 22 new (automation-discovery, claude-hooks, ...)
   ⏭️ 0 skipped

📁 Commands (12 total):
   ✅ 2 merged (archon-sync, agent-audit)
   ✅ 10 new (roadmap-update, hook-audit, ...)
   ⏭️ 0 skipped

UPDATING CONFIGURATION:
✅ agents.json updated (1 category conflict resolved)

POST-INSTALL VALIDATION:
✅ agents.json schema valid
✅ Skill dependencies: N/A (skills not installed)
✅ Hook triggers: N/A (hooks not installed)

INSTALLATION COMPLETE!
See full report above for details.
```

### Example 3: Install Worktree Template (Force Mode)

```bash
/init-archon worktree --force
```

**Output:**
```
=== INIT-ARCHON INSTALLATION (FORCE MODE) ===

Template: worktree (v1.0.0)
Mode: FORCE (automatic conflict resolution)

CONFLICT RESOLUTION (AUTO):
✅ All filename conflicts auto-merged
✅ 2 category conflicts auto-resolved (using template values)

INSTALLING ASSETS:

📁 Hooks (4 total):
   ✅ 4 new (doc-sync, doc-sync-validation, worktree-context-sync, worktree-task-manager)
   
📁 Commands (6 total):
   ✅ 3 merged (worktree-init, worktree-sync, worktree-status)
   ✅ 3 new (worktree-cleanup, worktree-switch, worktree-archive)

📁 Skills (4 total):
   ✅ 4 new (git-worktree-advanced, parallel-development, context-sharing, task-isolation)

INSTALLATION COMPLETE! (2.8s)
```

## Error Handling

### Template Not Found

```
❌ ERROR: Template 'invalid' not found

Available templates:
  • base (v1.0.0) - Minimal Archon setup
  • rvnk (v1.0.0) - Complete RVNKDev setup
  • worktree (v1.0.0) - Parallel development
  • internal (v1.0.0) - Full archon-sandbox replica

Run: /init-archon <template> --what-if
```

### Invalid Manifest

```
❌ ERROR: Invalid manifest for template 'base'

Schema validation failed:
  • Missing required field: assets.agents
  • Invalid version format: '1.0' (expected semver: 1.0.0)

Fix manifest.json and retry.
```

### Dependency Conflict

```
❌ ERROR: Skill dependency not resolved

Template 'base' requires:
  • archon-task-management.md (OK)
  • archon-knowledge-query.md (OK)
  • git-workflow-basics.md (MISSING)

Options:
  1. Install missing skill manually
  2. Use --types to exclude dependent assets
  3. Choose template with all dependencies

Run: /init-archon base --types=agents,commands (exclude skills)
```

## Maintenance

### Adding New Templates

1. Create directory: `.claude/templates/{name}/`
2. Create `manifest.json` with schema above
3. Add asset files in subdirectories (agents/, commands/, skills/, hooks/)
4. Validate manifest: Run `/init-archon {name} --what-if`
5. Test installation in clean project
6. Document template in this command file

### Updating Existing Templates

1. Increment version in manifest.json (semver)
2. Update compatibility.min_claude_version if needed
3. Add/remove assets as needed
4. Update dependencies if asset relationships changed
5. Test with `--what-if` before distribution
6. Add changelog entry in manifest description

### Template Versioning

Follow semantic versioning:
- **Major (1.0.0 → 2.0.0)**: Breaking changes (removed assets, incompatible structure)
- **Minor (1.0.0 → 1.1.0)**: New assets added (backward compatible)
- **Patch (1.0.0 → 1.0.1)**: Bug fixes, documentation updates (no asset changes)

## Related Commands

- `/agent-audit` - Validate agents.json after installation
- `/hook-audit` - Check hook performance after installation
- `/archon-sync` - Sync tasks to Archon after installation
- `/doc-cleanup` - Clean up documentation after installation

## See Also

- `.claude/templates/` - Template storage directory
- `agents.json` - Agent registry schema
- `.claude/hooks/` - Hook configuration
- `docs/spec/archon-board-inventory.md` - Asset inventory

## Notes

- **Backup Safety**: All installations create git backup branches
- **Category Conflicts**: Only prompt for category differences (filename merges are automatic)
- **Skill Dependencies**: Validated post-install, but not auto-installed
- **Hook Defaults**: Hooks installed disabled by default (manual enable required)
- **Force Mode Caution**: Use `--force` only when merge strategy is well-understood
- **Custom Templates**: Place in `.claude/templates/project/` for reuse across projects
