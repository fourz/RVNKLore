# Testing Guide: Enhanced Debug Diagnostics Command

**Feature**: `/lore debug diagnostics [--verbose]`
**Implementation**: impl-12
**Status**: Ready for deployment testing

---

## Quick Test Commands

### 1. Basic Diagnostics (Standard Mode)

```bash
# In-game (player)
/lore debug diagnostics

# Console
lore debug diagnostics
```

**Expected Output** (Console):
```
[RVNKLore] === SYSTEM DIAGNOSTICS ===
[RVNKLore] Database: MYSQL CONNECTED (MySQL 8.0.33)
[RVNKLore] Connection Pool: X active, Y idle, Z total
[RVNKLore] RVNKCore Integration: ACTIVE
[RVNKLore] ServiceRegistry: 5/5 services registered
[RVNKLore] Fallback Mode: INACTIVE
[RVNKLore] Total Entries: N
[RVNKLore] === END DIAGNOSTICS ===
```

### 2. Verbose Diagnostics

```bash
# In-game (player)
/lore debug diagnostics --verbose

# Console
lore debug diagnostics --verbose
```

**Expected Output** (Console):
```
[RVNKLore] === SYSTEM DIAGNOSTICS ===
[RVNKLore] Database: MYSQL CONNECTED (MySQL 8.0.33)
[RVNKLore] Connection Pool: X active, Y idle, Z total
[RVNKLore] RVNKCore Integration: ACTIVE
[RVNKLore] ServiceRegistry: 5/5 services registered
[RVNKLore]   - ILoreService: OK
[RVNKLore]   - IItemService: OK
[RVNKLore]   - ICollectionService: OK
[RVNKLore]   - ISubmissionService: OK
[RVNKLore]   - IPlayerService: OK
[RVNKLore] Fallback Mode: INACTIVE
[RVNKLore] Total Entries: N
[RVNKLore]   Approved: M (XX%)
[RVNKLore] Entries by Type:
[RVNKLore]   LANDMARK: X
[RVNKLore]   CITY: X
[RVNKLore]   PLAYER: X
[RVNKLore]   ITEM: X
[RVNKLore]   EVENT: X
[RVNKLore] Memory: XXXMB / XXXXMB (XX%)
[RVNKLore]   Free: XXXMB
[RVNKLore] Handlers: 11 registered
[RVNKLore]   All lore types have handlers
[RVNKLore] Dependencies:
[RVNKLore]   - RVNKCore: OK (v1.3.0-alpha)
[RVNKLore] Plugin Info:
[RVNKLore]   Version: 1.0-SNAPSHOT
[RVNKLore]   API Version: 1.20
[RVNKLore]   Authors: fourz
[RVNKLore] === END DIAGNOSTICS ===
```

---

## Test Scenarios

### Scenario 1: RVNKCore Available

**Setup**: RVNKCore plugin loaded and enabled

**Expected**:
- `RVNKCore Integration: ACTIVE`
- `ServiceRegistry: 5/5 services registered`
- All 5 services show `OK` in verbose mode

**Test Command**:
```
lore debug diagnostics --verbose
```

**Verify**:
- Check console logs for service registration on plugin load
- Confirm all 5 services listed in output
- No errors in server log

---

### Scenario 2: RVNKCore Not Available

**Setup**: RVNKCore plugin not loaded or disabled

**Expected**:
- `RVNKCore Integration: INACTIVE (standalone mode)`
- No ServiceRegistry section shown

**Test Command**:
```
lore debug diagnostics
```

**Verify**:
- Plugin still functions normally
- No errors or warnings
- Database and lore stats still work

---

### Scenario 3: HikariCP Connection Pool

**Setup**: DatabaseManager using HikariCP (future implementation)

**Expected**:
- `Connection Pool: X active, Y idle, Z total`

**Test Command**:
```
lore debug diagnostics
```

**Verify**:
- Pool stats show realistic numbers
- Active + Idle ≤ Total
- No negative values

---

### Scenario 4: No HikariCP (Basic JDBC)

**Setup**: DatabaseManager using basic JDBC connections

**Expected**:
- In verbose mode: `Connection Pool: Not using HikariCP`
- In standard mode: No pool stats shown

**Test Command**:
```
lore debug diagnostics --verbose
```

**Verify**:
- No errors or exceptions
- Graceful fallback behavior

---

### Scenario 5: Database Connection Failed

**Setup**: Stop MySQL or corrupt config

**Expected**:
- `Database: MYSQL DISCONNECTED`
- `Last Error: [error message]`

**Test Command**:
```
lore debug diagnostics
```

**Verify**:
- Clear error message shown
- Plugin doesn't crash
- Fallback mode may activate

---

### Scenario 6: Fallback Mode Active

**Setup**: Trigger database failures to activate fallback mode

**Expected**:
- `Fallback Mode: ACTIVE (database issues detected)`
- In verbose: `Consecutive Failures: N`

**Test Command**:
```
lore debug diagnostics --verbose
```

**Verify**:
- Fallback mode correctly detected
- Failure count shown in verbose
- System still operational

---

### Scenario 7: Console Execution

**Setup**: Run from server console (not in-game)

**Expected**:
- All output prefixed with `[RVNKLore]`
- No color codes in output
- Plain text formatting

**Test Command**:
```
lore debug diagnostics --verbose
```

**Verify**:
- Easy to read in console
- No color code artifacts (§c, etc.)
- Can be parsed by scripts

---

### Scenario 8: Player Execution

**Setup**: Run from in-game chat

**Expected**:
- Color-coded output for readability
- Compact format (standard mode)
- Full details (verbose mode)

**Test Command**:
```
/lore debug diagnostics --verbose
```

**Verify**:
- Colors enhance readability
- No chat spam in standard mode
- Verbose mode shows all details

---

## Tab Completion Test

**Type**: `/lore debug diagnostics `

**Expected**: Tab completion suggests `--verbose`

**Verify**:
- Tab key shows `--verbose` option
- Completing the flag works correctly

---

## Permission Test

### With Permission

**Setup**: Player has `rvnklore.admin` permission

**Expected**: Command executes successfully

### Without Permission

**Setup**: Player lacks `rvnklore.admin` permission

**Expected**: Permission denied message

---

## Integration Test with Other Debug Commands

```bash
# List all debug commands
/lore debug

# Run diagnostics
/lore debug diagnostics

# Check specific lore entry
/lore debug check <id>

# List handlers
/lore debug handlers

# Attempt fixes
/lore debug fix

# Player diagnostics
/lore debug player <name>
```

**Verify**:
- All commands still work
- No conflicts or errors
- Consistent output formatting

---

## CI/CD Integration Test

### Automated Health Check Script

```powershell
# Example PowerShell script for automated testing
$serverCommand = "lore debug diagnostics"
$expectedPattern = "\[RVNKLore\] === SYSTEM DIAGNOSTICS ==="

# Send command and capture output
# Parse for expected patterns
# Exit with appropriate code
```

**Test**:
1. Run diagnostics via console
2. Parse output for key indicators
3. Verify all services are healthy
4. Check for error patterns

---

## Performance Test

**Objective**: Ensure diagnostics don't impact server performance

**Test**:
1. Run diagnostics 10 times in quick succession
2. Monitor server TPS
3. Check memory usage

**Expected**:
- No TPS drop
- Minimal memory impact
- Quick response time (< 1 second)

---

## Deployment Checklist

- [ ] Build plugin: `mvn clean package -DskipTests`
- [ ] Deploy JAR to dev server
- [ ] Reload/restart server
- [ ] Run basic diagnostics: `lore debug diagnostics`
- [ ] Run verbose diagnostics: `lore debug diagnostics --verbose`
- [ ] Verify console output format
- [ ] Test with RVNKCore enabled
- [ ] Test with RVNKCore disabled
- [ ] Check ServiceRegistry status
- [ ] Verify lore entry counts
- [ ] Test tab completion
- [ ] Test permission checks
- [ ] Review server logs for errors

---

## Troubleshooting

### Issue: ServiceRegistry shows 0/5 services

**Cause**: RVNKCore not loaded or services failed to register

**Fix**: Check plugin load order, verify RVNKCore dependency

### Issue: Connection Pool stats not showing

**Cause**: HikariCP not yet implemented

**Expected**: This is normal - pool stats are optional

### Issue: Color codes appearing in console

**Cause**: Player output format used for console

**Fix**: Verify `!(sender instanceof Player)` check in code

### Issue: Verbose flag not recognized

**Cause**: Tab completion or argument parsing error

**Fix**: Check args parsing in `runDiagnostics(sender, args)`

---

## Dev Server Details

**Server ID**: `1eb313b1-40f7-4209-aa9d-352128214206` (MCSS Dev)
**Plugin Path**: `F:\Minecraft\MCSS\servers\RVNK Dev\plugins\RVNKLore-1.0-SNAPSHOT.jar`
**Console Access**: MCSS web interface

---

## Success Criteria

✅ Command executes without errors
✅ Console output uses `[RVNKLore]` prefix
✅ Verbose mode shows additional details
✅ ServiceRegistry status accurately reflects registration
✅ Database connection status is correct
✅ Lore entry counts match database
✅ Memory stats are reasonable
✅ No performance impact on server
✅ Tab completion works
✅ Permission checks function correctly

---

**Implementation Date**: 2026-02-01
**Ready for Testing**: ✅
