## Phase 5: SubmissionRepository and LoreEntryRepository Methods

The following methods in `DatabaseManager` are direct pass-throughs to their respective repositories and should be refactored to be called directly on the repository.

### 5.1 `getLoreEntry(int id)`

- **Description**: This method forwards the call directly to `loreEntryRepository.getLoreEntryById(id)`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.getLoreEntry(int)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.getLoreEntry(id)` to `databaseManager.getLoreEntryRepository().getLoreEntryById(id)`.
    2. Remove the `getLoreEntry(int id)` method from `DatabaseManager.java`.
    3. Build and test to ensure get functionality works as expected.

### 5.2 `searchLoreEntriesInSubmissions(String keyword)`

- **Description**: This method forwards the call directly to `submissionRepository.searchLoreEntriesInSubmissions(keyword)`.
- **Risk**: Low. Simple search operation.
- **Call Sites**:
    - Any code calling `databaseManager.searchLoreEntriesInSubmissions(keyword)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.searchLoreEntriesInSubmissions(keyword)` to `databaseManager.getSubmissionRepository().searchLoreEntriesInSubmissions(keyword)`.
    2. Remove the `searchLoreEntriesInSubmissions(String keyword)` method from `DatabaseManager.java`.
    3. Build and test to ensure search functionality works as expected.

### 5.3 `getLoreSubmission(int id)`

- **Description**: This method forwards the call directly to `submissionRepository.getLoreSubmission(id)`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.getLoreSubmission(id)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.getLoreSubmission(id)` to `databaseManager.getSubmissionRepository().getLoreSubmission(id)`.
    2. Remove the `getLoreSubmission(int id)` method from `DatabaseManager.java`.
    3. Build and test to ensure get functionality works as expected.

### 5.4 `getCurrentSubmission(int entryId)`

- **Description**: This method forwards the call directly to `submissionRepository.getCurrentSubmission(entryId)`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.getCurrentSubmission(int)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.getCurrentSubmission(entryId)` to `databaseManager.getSubmissionRepository().getCurrentSubmission(entryId)`.
    2. Remove the `getCurrentSubmission(int entryId)` method from `DatabaseManager.java`.
    3. Build and test to ensure get functionality works as expected.

### 5.5 `getSubmissionsForEntry(int entryId)`

- **Description**: This method forwards the call directly to `submissionRepository.getSubmissionsForEntry(entryId)`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.getSubmissionsForEntry(int)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.getSubmissionsForEntry(entryId)` to `databaseManager.getSubmissionRepository().getSubmissionsForEntry(entryId)`.
    2. Remove the `getSubmissionsForEntry(int entryId)` method from `DatabaseManager.java`.
    3. Build and test to ensure get functionality works as expected.

### 5.6 `saveLoreSubmission(LoreSubmissionDTO dto)`

- **Description**: This method forwards the call directly to `submissionRepository.saveLoreSubmission(dto)`.
- **Risk**: Low. Simple save operation.
- **Call Sites**:
    - Any code calling `databaseManager.saveLoreSubmission(...)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.saveLoreSubmission(dto)` to `databaseManager.getSubmissionRepository().saveLoreSubmission(dto)`.
    2. Remove the `saveLoreSubmission(LoreSubmissionDTO dto)` method from `DatabaseManager.java`.
    3. Build and test to ensure save functionality works as expected.

---

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

## Phase 6: LoreEntryRepository Query Methods

The following methods in `DatabaseManager` are direct pass-throughs to `LoreEntryRepository` query methods and should be refactored to be called directly on the repository.

### 6.1 `findNearbyLoreEntries(Location location, double radius)`

- **Description**: This method forwards the call directly to `loreEntryRepository.findNearbyLoreEntries(location, radius)`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.findNearbyLoreEntries(location, radius)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.findNearbyLoreEntries(location, radius)` to `databaseManager.getLoreEntryRepository().findNearbyLoreEntries(location, radius)`.
    2. Remove the `findNearbyLoreEntries(Location, double)` method from `DatabaseManager.java`.
    3. Build and test to ensure functionality works as expected.

### 6.2 `findLoreEntriesInWorld(String worldName)`

- **Description**: This method forwards the call directly to `loreEntryRepository.findLoreEntriesInWorld(worldName)`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.findLoreEntriesInWorld(worldName)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.findLoreEntriesInWorld(worldName)` to `databaseManager.getLoreEntryRepository().findLoreEntriesInWorld(worldName)`.
    2. Remove the `findLoreEntriesInWorld(String)` method from `DatabaseManager.java`.
    3. Build and test to ensure functionality works as expected.

### 6.3 `getLoreEntriesByTypeAndApproved(String type, boolean approved)`

- **Description**: This method forwards the call directly to `loreEntryRepository.getLoreEntriesByTypeAndApproved(type, approved)`.
- **Risk**: Low. Simple read operation.
- **Call Sites**:
    - Any code calling `databaseManager.getLoreEntriesByTypeAndApproved(type, approved)` (search for usages)
- **Refactor Steps**:
    1. Change all call sites from `databaseManager.getLoreEntriesByTypeAndApproved(type, approved)` to `databaseManager.getLoreEntryRepository().getLoreEntriesByTypeAndApproved(type, approved)`.
    2. Remove the `getLoreEntriesByTypeAndApproved(String, boolean)` method from `DatabaseManager.java`.
    3. Build and test to ensure functionality works as expected.

---
*This plan will be expanded as more methods are evaluated.*
