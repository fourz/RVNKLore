# RVNKLore Database Architecture Refactor - Repository Access

**Last Updated**: July 5, 2025

This document outlines the step-by-step plan for refactoring the `DatabaseManager` to delegate repository-specific operations back to their respective repositories. The goal is to enforce the Single Responsibility Principle (SRP), reduce the `DatabaseManager` to its core responsibilities (connection, transaction, and schema management), and make the overall architecture more modular and maintainable.

## Core Problem

The `DatabaseManager` currently acts as a facade for every method in every repository. This makes it a "god object" that knows about every single data operation, violating SRP and making it a bottleneck for changes.

**Current Pattern:**
```java
// Code calls a method on DatabaseManager
databaseManager.getPlayersByName("username");

// DatabaseManager just passes it through
public CompletableFuture<List<PlayerDTO>> getPlayersByName(String name) {
    return playerRepository.getPlayersByName(name);
}
```

**Target Pattern:**
```java
// Code gets the specific repository and calls the method directly
PlayerRepository playerRepository = databaseManager.getPlayerRepository();
playerRepository.getPlayersByName("username");

// The pass-through method in DatabaseManager is removed.
```

## Refactoring Workflow

This refactor will proceed on a method-by-method basis to ensure incremental, manageable changes. For each method identified for migration:

1.  **Identify Usages**: Locate all call sites for the `DatabaseManager` method.
2.  **Refactor Call Site**: Modify the calling code to get the appropriate repository instance from `DatabaseManager` and call the method on the repository directly.
3.  **Remove Method**: Delete the pass-through method from `DatabaseManager`.
4.  **Validate**: Build and test to ensure the change was successful.

---

## Phase 1: PlayerRepository Methods

The following methods in `DatabaseManager` are direct pass-throughs to `PlayerRepository`.

### 1.1 `getPlayersByName(String name)`

-   **Description**: This method forwards the call directly to `playerRepository.getPlayersByName(name)`.
-   **Risk**: Low. This is a simple read operation.
-   **Call Sites**:
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\command\LoreDebugSubCommand.java`
-   **Refactor Steps**:
    1.  In `LoreDebugSubCommand`, change the call from `plugin.getDatabaseManager().getPlayersByName(...)` to `plugin.getDatabaseManager().getPlayerRepository().getPlayersByName(...)`.
    2.  Remove the `getPlayersByName(String name)` method from `DatabaseManager.java`.
    3.  Run the `Reload Server` task and test the `/lore debug player ...` command to verify functionality.

### 1.2 `getPlayerByUuid(UUID uuid)`

-   **Description**: This method forwards the call directly to `playerRepository.getPlayerByUuid(uuid)`.
-   **Risk**: Low.
-   **Call Sites**:
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\command\LoreDebugSubCommand.java`
-   **Refactor Steps**:
    1.  In `LoreDebugSubCommand`, change the call from `plugin.getDatabaseManager().getPlayerByUuid(...)` to `plugin.getDatabaseManager().getPlayerRepository().getPlayerByUuid(...)`.
    2.  Remove the `getPlayerByUuid(UUID uuid)` method from `DatabaseManager.java`.
    3.  Run the `Reload Server` task and test the relevant debug command.

### 1.3 `savePlayer(PlayerDTO dto)`

-   **Description**: This method forwards the call directly to `playerRepository.savePlayer(dto)`.
-   **Risk**: Low.
-   **Call Sites**:
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\data\service\PlayerJoinListener.java`
-   **Refactor Steps**:
    1.  In `PlayerJoinListener`, change the call to `plugin.getDatabaseManager().getPlayerRepository().savePlayer(...)`.
    2.  Remove the `savePlayer(PlayerDTO dto)` method from `DatabaseManager.java`.
    3.  Run the `Restart Server` task and verify player join logic works correctly.

---

## Phase 2: ItemRepository Methods

The following methods in `DatabaseManager` are direct pass-throughs to `ItemRepository`.

### 2.1 `getItemsByType(String type)`

-   **Description**: This method forwards the call directly to `itemRepository.getItemsByType(type)`.
-   **Risk**: Low.
-   **Call Sites**:
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\lore\item\ItemManager.java`
-   **Refactor Steps**:
    1.  In `ItemManager`, change the call to `plugin.getDatabaseManager().getItemRepository().getItemsByType(...)`.
    2.  Remove the `getItemsByType(String type)` method from `DatabaseManager.java`.
    3.  Run the `Reload Server` task and test functionality that lists items by type.

### 2.2 `saveItem(ItemPropertiesDTO dto)`

-   **Description**: This method forwards the call directly to `itemRepository.saveItem(dto)`.
-   **Risk**: Low.
-   **Call Sites**:
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\lore\item\ItemManager.java`
-   **Refactor Steps**:
    1.  In `ItemManager`, change the call to `plugin.getDatabaseManager().getItemRepository().saveItem(...)`.
    2.  Remove the `saveItem(ItemPropertiesDTO dto)` method from `DatabaseManager.java`.
    3.  Run the `Reload Server` task and test item saving functionality.

---


## Phase 3: LoreEntryRepository Methods

The following methods in `DatabaseManager` are direct pass-throughs to `LoreEntryRepository` and should be refactored to be called directly on the repository.

### 3.1 `deleteLoreEntry(int id)`

- **Description**: This method forwards the call directly to `loreEntryRepository.deleteLoreEntry(id)`.
- **Risk**: Low. Simple delete operation.
- **Call Sites**:
    - Any code calling `databaseManager.deleteLoreEntry(...)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.deleteLoreEntry(id)` to `databaseManager.getLoreEntryRepository().deleteLoreEntry(id)`.
    2. Remove the `deleteLoreEntry(int id)` method from `DatabaseManager.java`.
    3. Build and test to ensure delete functionality works as expected.

### 3.2 `saveLoreEntry(LoreEntryDTO dto)`

- **Description**: This method forwards the call directly to `loreEntryRepository.saveLoreEntry(dto)`.
- **Risk**: Low. Simple save operation.
- **Call Sites**:
    - Any code calling `databaseManager.saveLoreEntry(...)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.saveLoreEntry(dto)` to `databaseManager.getLoreEntryRepository().saveLoreEntry(dto)`.
    2. Remove the `saveLoreEntry(LoreEntryDTO dto)` method from `DatabaseManager.java`.
    3. Build and test to ensure save functionality works as expected.

### 3.3 `getAllLoreEntries()`

- **Description**: This method forwards the call directly to `loreEntryRepository.getAllLoreEntries()`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.getAllLoreEntries()` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.getAllLoreEntries()` to `databaseManager.getLoreEntryRepository().getAllLoreEntries()`.
    2. Remove the `getAllLoreEntries()` method from `DatabaseManager.java`.
    3. Build and test to ensure list functionality works as expected.

---

## Phase 4: LoreEntryRepository Domain Conversion Methods

The following methods in `DatabaseManager` convert DTOs to domain objects and are direct pass-throughs to `LoreEntryRepository`.

### 4.1 `getLoreEntryDomain(UUID uuid)`

-   **Description**: This method retrieves a LoreEntryDTO by UUID and converts it to a LoreEntry domain object.
-   **Risk**: Low. Pure conversion logic.
-   **Call Sites**:
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\lore\LoreManager.java`
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\command\LoreGetSubCommand.java`
-   **Refactor Steps**:
    1.  Move the domain conversion method to `LoreEntryRepository` as `getLoreEntryDomain(UUID uuid)`.
    2.  Update all call sites to use `plugin.getDatabaseManager().getLoreEntryRepository().getLoreEntryDomain(uuid)`.
    3.  Remove the `getLoreEntryDomain(UUID uuid)` method from `DatabaseManager.java`.
    4.  Build and test `/lore get <uuid>` and any LoreManager usages.

### 4.2 `getLoreEntryDomain(int id)`

-   **Description**: This method retrieves a LoreEntryDTO by int ID and converts it to a LoreEntry domain object.
-   **Risk**: Low.
-   **Call Sites**:
    -   `c:\tools\RVNKLore\src\main\java\org\fourz\RVNKLore\lore\LoreManager.java`
-   **Refactor Steps**:
    1.  Move the domain conversion method to `LoreEntryRepository` as `getLoreEntryDomain(int id)`.
    2.  Update all call sites to use `plugin.getDatabaseManager().getLoreEntryRepository().getLoreEntryDomain(id)`.
    3.  Remove the `getLoreEntryDomain(int id)` method from `DatabaseManager.java`.
    4.  Build and test LoreManager usages.

---
*This plan will be expanded as more methods are evaluated.*
