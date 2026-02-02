# Documentation Standards Skill

**Purpose**: Ensure consistent documentation placement, Archon-first project tracking, and proper document organization

**Related Agent**: `documentation-specialist` — Comprehensive documentation management
**Related Skill**: `archon-task-workflow` — Archon MCP integration for task tracking

---

## Document Placement Rules

### Root-Level Files (Project Instructions Only)

**Allowed files in project root**:
- `README.md` — Project overview, getting started, directory structure
- `ROADMAP.md` — Project timeline, milestones, status
- `CLAUDE.md` — Primary AI instruction file
- `AGENTS.md` — Agent architecture overview (if multi-agent)
- `LICENSE` — License file

**NOT allowed in root** (move to `docs/`):
- PRP documents → `docs/prp/`
- API specifications → `docs/api/`
- Architecture guides → `docs/architecture/`
- Release notes → `docs/releases/`
- Design documents → `docs/design/`
- Reports and analysis → `docs/reports/`
- Tutorials and guides → `docs/guide/`
- How-to documents → `docs/guide/how-to-*.md`

**Example cleanup**:
```
❌ Before:
README.md
ROADMAP.md
API-REFERENCE.md          → Move to docs/api/reference.md
ARCHITECTURE.md           → Move to docs/architecture/design.md
DEVELOPER-GUIDE.md        → Move to docs/guide/developer-guide.md
INSTALLATION.md           → Move to docs/guide/installation.md

✅ After:
README.md
ROADMAP.md
docs/
├── api/
│   └── reference.md
├── architecture/
│   └── design.md
└── guide/
    ├── developer-guide.md
    └── installation.md
```

### Documentation Directory Structure

**Standard structure** (`docs/` folder):

```
docs/
├── README.md              # Documentation index
├── api/                   # API specifications
│   ├── endpoints.md
│   ├── authentication.md
│   └── error-codes.md
├── architecture/          # Design and architecture docs
│   ├── overview.md
│   ├── data-flow.md
│   └── deployment.md
├── guide/                 # Tutorials and guides
│   ├── getting-started.md
│   ├── installation.md
│   └── how-to-*.md
├── reference/             # Reference documentation
│   ├── configuration.md
│   ├── environment-variables.md
│   └── cli-reference.md
├── spec/                  # Persistent specifications and long-term guidance
│   ├── component-inventory.md      # Component catalog and configuration
│   ├── system-requirements.md      # System specifications
│   ├── feature-spec-name.md        # Feature specifications
│   └── example-integration-summary.md  # Integration patterns & templates
├── prp/                   # Project requirements/plans
│   └── prd-feature-name.md
├── fix/                   # Bug fixes and incidents (TIMESTAMPS)
│   └── YYYY-MM-DD-issue-name.md
└── archive/               # Deprecated or historical docs
```

### Archon-First Project Tracking

**CRITICAL RULE**: Use **Archon task management** as the primary project tracking system, NOT scattered documentation files.

**When to use Archon**:
- ✅ Feature tracking → `manage_task()` in Archon
- ✅ Bug tracking → Create task in Archon
- ✅ Progress status → Update task status (todo → doing → review → done)
- ✅ Work coordination → Assign tasks to agents/users
- ✅ Milestone tracking → Link completed tasks to releases

**When to use documentation**:
- ✅ Implementation guidance → `docs/guide/`
- ✅ Architecture decisions → `docs/architecture/`
- ✅ API specifications → `docs/api/`
- ✅ Reference material → `docs/reference/`
- ✅ Specifications → `docs/spec/`

**Pattern**:
- Task: "Implement authentication feature" → Tracked in Archon
- Implementation guide: "How to implement auth" → `docs/guide/authentication.md` → Reference from task
- API spec: "Auth endpoints" → `docs/api/authentication.md` → Referenced in guide
- Temporal work: Bug fixes → `docs/fix/YYYY-MM-DD-*.md` → Reference in task

**Anti-Pattern** ❌:
- Creating separate project status documents outside Archon
- Tracking progress in markdown files instead of Archon tasks
- Maintaining manual "what's done" checklists (use Archon task board)
- Duplicate tracking across multiple systems

### File Naming Conventions

**Temporal files** (short-lived, timestamped):
```
docs/fix/YYYY-MM-DD-issue-description.md
docs/fix/2025-01-15-auth-timeout-fix.md
docs/fix/2025-01-20-provider-timeout-handling.md
```
- Include date prefix (YYYY-MM-DD)
- Archive after resolution
- Reference in task description only

**Persistent files** (long-lived, no timestamps):
```
docs/api/authentication.md
docs/guide/getting-started.md
docs/spec/system-requirements.md
docs/architecture/data-flow.md
```
- NO date prefix
- Updated as content evolves
- Sync to Archon RAG/KB if core knowledge

**Implementation files** (in-code documentation):
- Docstrings in code files (Google style)
- Comment blocks for complex logic
- Type hints for function signatures

---

## Docstring Format (Google Style)

**Pattern**:

```python
def authenticate(user_id: str, password: str) -> Token:
    """Authenticate user and return access token.
    
    Validates user credentials against database and generates JWT token.
    Token includes user ID, roles, and expiration timestamp.
    
    Args:
        user_id: Unique user identifier (email or username)
        password: User password (plaintext, will be hashed for comparison)
    
    Returns:
        Token: JWT access token with 24-hour expiration
        
    Raises:
        AuthenticationError: If credentials invalid or user not found
        ConfigurationError: If JWT secret not configured
        
    Examples:
        >>> token = authenticate("user@example.com", "password123")
        >>> token.expires_in
        86400  # 24 hours in seconds
        
        >>> authenticate("invalid@example.com", "wrong")
        AuthenticationError: Invalid credentials
    """
    pass
```

**Components**:

1. **Summary line** (first line): One-sentence description
2. **Extended description** (optional): Detailed explanation, context, behavior
3. **Args section**: Parameter names, types, descriptions
4. **Returns section**: Return type and description
5. **Raises section**: Exception types that may be raised
6. **Examples section**: Usage examples (executable if possible)
7. **Notes section** (optional): Important caveats or implementation details

---

## Documentation Placement Decision Tree

**Question**: Where should this documentation go?

```
Is it instruction for AI/developers?
├─ YES → Root level file (README, CLAUDE.md, AGENTS.md)
└─ NO ↓

Is it API specification?
├─ YES → docs/api/
└─ NO ↓

Is it architecture/design?
├─ YES → docs/architecture/
└─ NO ↓

Is it a tutorial/how-to guide?
├─ YES → docs/guide/
└─ NO ↓

Is it reference material (config, CLI, etc.)?
├─ YES → docs/reference/
└─ NO ↓

Is it a feature/system specification?
├─ YES → docs/spec/
└─ NO ↓

Is it a bug fix or temporal issue?
├─ YES → docs/fix/YYYY-MM-DD-*.md
└─ NO ↓

Is it a project requirement/plan?
├─ YES → docs/prp/
└─ NO ↓

→ **Unknown type**: Create in `docs/` and document rationale
```

---

## Release Notes & Version Documentation

### Version Update Locations

When releasing a new version, update these files in order:

1. **pyproject.toml** — Python package version
   ```toml
   [project]
   version = "2.1.5"
   ```

2. **config.yaml** (or config files) — Runtime version reference
   ```yaml
   server:
     version: "2.1.5"
   ```

3. **ROADMAP.md** — Current status heading
   ```markdown
   # Project Roadmap
   ## Current Status (v2.1.5)
   ```

4. **Release notes** (optional: `docs/releases/v2.1.5.md`)
   ```markdown
   # Release v2.1.5
   
   ## New Features
   - Feature 1
   - Feature 2
   
   ## Bug Fixes
   - Fix 1
   - Fix 2
   
   ## Breaking Changes
   - None
   
   ## Migration Guide
   - See docs/guide/migration-v2.1.md
   ```

5. **Git tag** — Version control marker
   ```bash
   git tag -a v2.1.5 -m "Release v2.1.5: Description"
   ```

### CHANGELOG Format

If maintaining a CHANGELOG.md:

```markdown
# Changelog

All notable changes to this project will be documented in this file.

## [2.1.5] - 2025-01-15

### Added
- New authentication strategy (OAuth 2.0)

### Fixed
- Session timeout handling

### Changed
- Provider API simplified

## [2.1.4] - 2025-01-08

### Added
- Diagnostic tools for provider status

### Fixed
- Rate limiting issue

---

The format is based on [Keep a Changelog](https://keepachangelog.com/)
```

---

## docs/spec/ Folder — Long-Term Specifications

**Purpose**: Home for persistent, maintained specifications and long-term architectural guidance

**NOT for**: Temporal work summaries, audit reports, work-in-progress documentation

### What Belongs in docs/spec/

✅ **Specifications**:
- System requirements and design specs
- Feature specifications
- Component inventories
- Architecture decision records (ADRs)
- Configuration specifications

✅ **Guidance Documents**:
- Documentation strategy and standards
- Integration patterns and templates
- Best practices documentation
- Reference materials

✅ **Examples & Templates**:
- Example implementations (e.g., `example-integration-summary.md`)
- Pattern templates for future use
- Integration workflow examples

### What Does NOT Belong in docs/spec/

❌ **Temporal Work**:
- Project summaries (→ Move to `docs/fix/YYYY-MM-DD-*.md`)
- Audit reports (→ Move to `docs/fix/YYYY-MM-DD-*.md`)
- Progress summaries (→ Track in Archon tasks)
- Work-in-progress documents (→ Create in Archon tasks)
- Issue documentation (→ Reference in tasks, store in `docs/fix/`)

❌ **Short-Lived Content**:
- Bug reports (→ `docs/fix/YYYY-MM-DD-*.md`)
- Incident summaries (→ `docs/fix/YYYY-MM-DD-*.md`)
- Release-specific notes (→ `docs/releases/` or task description)
- Decision logs without permanence (→ ADRs in `docs/architecture/`)

### Maintenance Rules

1. **No timestamps in filenames** — These are updated in place
2. **Use kebab-case naming** — `example-integration-summary.md`
3. **Keep updated** — Specifications become stale quickly; plan for maintenance
4. **Archive when superseded** — Move old versions to `docs/archive/spec/`
5. **Sync to Archon** — Persistent specs should sync to RAG/KB for cross-project knowledge

### Decision Tree for docs/spec/

```
Is this a long-term specification or guidance?
├─ YES → Place in docs/spec/
└─ NO → Is it a completed work summary?
    ├─ YES → Move to docs/fix/YYYY-MM-DD-*.md
    └─ NO → Is it work-in-progress?
        ├─ YES → Create Archon task with description
        └─ NO → Where does it belong?
```

---

## Documentation Sync to Archon RAG/KB

**When to sync**:
- Create `docs/spec/feature-name.md` (persistent, core knowledge)
- Mark for RAG sync in task description
- Archon documentation-specialist will synchronize

**Files that sync**:
- `docs/spec/` — Specifications (sync to RAG)
- `docs/guide/` — Guides and tutorials (sync to RAG)
- `docs/architecture/` — Architecture docs (sync to RAG)
- `docs/reference/` — Reference material (sync to RAG)
- `docs/api/` — API documentation (sync to RAG)

**Files that DON'T sync**:
- `docs/fix/` — Bug fix reports (temporal, task-referenced only)
- `docs/prp/` — Project plans (versioned in Archon PRPs)
- Root-level CLAUDE.md (use for instruction only)
- Orphaned markdown files (move to proper location first)

**Sync example**:

Task description:
```
Implement authentication feature

Related documentation (sync to RAG):
- docs/spec/authentication.md
- docs/guide/how-to-implement-auth.md
- docs/api/auth-endpoints.md
```

---

## Common Documentation Mistakes

### ❌ Mistake 1: Scattered Status Files

```
❌ BAD:
PROJECT-STATUS.md          # Status file NOT in Archon
PROGRESS.md                # Manual progress tracking
TODO.md                    # Checklist outside Archon
COMPLETED-FEATURES.md      # Manual completed list
```

✅ **Solution**: Use Archon tasks for all status tracking
- Create task in Archon
- Update task status (todo → doing → review → done)
- Reference completed tasks in release notes

### ❌ Mistake 2: Outdated Root-Level Documentation

```
❌ BAD:
README.md                  # Outdated, not maintained
INSTALLATION.md            # Out of sync with docs/guide/
QUICKSTART.md              # Duplicate of docs/guide/getting-started.md
API-SPEC.md                # Duplicate of docs/api/
```

✅ **Solution**: Keep root files as navigation only
- README.md → Points to documentation structure
- README.md → Links to `docs/guide/getting-started.md`
- No duplicate content

### ❌ Mistake 3: Missing File Extensions in Links

```
❌ BAD:
See [Getting Started](docs/guide/getting-started)
Visit [API Reference](docs/api/reference)
```

✅ **Solution**: Always include `.md` extension
```markdown
See [Getting Started](docs/guide/getting-started.md)
Visit [API Reference](docs/api/reference.md)
```

### ❌ Mistake 4: Inconsistent Documentation Language

```
❌ BAD:
docs/guide/installation.md     (present tense imperative)
docs/guide/setup.md            (infinitive)
docs/guide/how_to_config.md    (underscore naming)
docs/guide/get-auth.md         (abbreviated)
```

✅ **Solution**: Standardize naming and language
```
docs/guide/getting-started.md       (all lowercase, hyphens)
docs/guide/installation.md          (imperative: "Install...", "Configure...")
docs/guide/how-to-authenticate.md   (How-to format)
docs/guide/configuration.md         (Reference format)
```

---

## Integration with Code

**When documenting code**:

1. **Public API** → Full Google-style docstrings
2. **Complex logic** → Inline comments explaining why (not what)
3. **Configuration** → Document in `docs/reference/configuration.md`
4. **Examples** → Include in docstring examples section
5. **Type hints** → Use for self-documenting code

**Example: Complete documentation**:

```python
# config.py
"""Configuration management module.

Loads and validates configuration from environment variables and config files.
Supports environment-specific overrides.

Example:
    >>> config = Config.from_env()
    >>> config.api_key
    'sk-...'
"""

def load_config(env: str = "production") -> Config:
    """Load application configuration.
    
    Prioritizes: env vars > config file > defaults
    
    Args:
        env: Environment name (development, staging, production)
    
    Returns:
        Config object with validated settings
    
    Raises:
        ConfigurationError: If required variables missing
    """
    pass
```

**Corresponding documentation** (`docs/reference/configuration.md`):
```markdown
# Configuration Reference

## Environment Variables

- `API_KEY` — Required. OpenAI API key for auth
- `API_URL` — Optional. Override API endpoint
- `LOG_LEVEL` — Optional. Logging level (debug, info, warning, error)

See [Configuration Guide](../guide/configuration.md) for setup.
```

---

## Documentation Checklist

Before committing documentation changes:

- [ ] File placed in correct directory (per decision tree)
- [ ] File naming follows conventions (no timestamps for persistent docs)
- [ ] Links include `.md` extension
- [ ] Headings properly formatted (# → ##)
- [ ] Code blocks have language specified
- [ ] Google-style docstrings in code
- [ ] No duplicate content across files
- [ ] Related Archon task referenced (if applicable)
- [ ] Outdated docs removed or archived
- [ ] Root-level files point to proper docs/ locations

---

## Integration Points

**Related Skills**:
- `archon-task-workflow` — Task-driven development with Archon
- `code-quality-standards` — Code documentation standards

**Related Agents**:
- `documentation-specialist` — Comprehensive documentation management
- `project-architect` — Documentation structure planning

**Related Archon Features**:
- Task management → Use for project tracking (NOT scattered docs)
- RAG/KB → Persistent docs sync to knowledge base
- Project management → Link documentation to projects

---

**Remember**: Keep documentation where it belongs — Archon tasks for tracking, docs/ for reference, and code for implementation details.
