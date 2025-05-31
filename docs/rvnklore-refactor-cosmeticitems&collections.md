# RVNKLore Refactor Guide: Unified `/lore give` Command

This guide provides a step-by-step approach to refactoring the RVNKLore plugin to support a unified `/lore give` command for all item types—including cosmetics and collection items—without requiring additional cosmetic-specific data. Cosmetic and collection-specific management will be moved to dedicated commands (e.g., `/lore collection`).

---

## ✨ Goal

- **Unify item giving:** `/lore item give <item_name> <recipient>`
- **Move cosmetic/collection logic:** To `/lore collection` and related commands
- **Simplify admin workflow:** No need to distinguish between "cosmetic" and "regular" items for basic give operations

---

## 1. **Preparation**

- Review the current `CosmeticGiveSubCommand` and `CollectionManager` logic.
- Ensure all items (including cosmetics/heads) are registered in the main item registry (`ItemManager`).
- Confirm that `ItemManager.createLoreItem()` can create any item by name/type.

---

## 2. **Refactor Command Structure**

### a. **Create Unified Give Command**

- **New Command:** `/lore item give <item_name> <recipient>`
- **Location:** `RVNKLore.command.LoreItemGiveSubCommand`
- **Responsibilities:**
  - Look up item by name (from `ItemManager`/`CollectionManager`)
  - Create the item using `ItemManager.createLoreItem()`
  - Give the item to the specified player

### b. **Move Cosmetic/Collection Logic**

- **Cosmetic-specific commands:** Move to `/lore collection` (e.g., `/lore collection view`, `/lore collection claim`)
- **Remove:** Cosmetic-specific checks from the unified give command
- **Keep:** Cosmetic/collection progress, rewards, and browsing in their own subcommands

---

## 3. **Implementation Steps**

### Step 1: **Update Item Registration**

- Ensure all items (including heads/cosmetics) are accessible via `ItemManager` and have unique names/IDs.

### Step 2: **Implement `LoreItemGiveSubCommand`**

- Create a new class:  
  `RVNKLore.command.LoreItemGiveSubCommand`
- Implement logic:
  - Parse `<item_name>` and `<recipient>`
  - Use `ItemManager` to look up and create the item
  - Give the item to the player (handle errors: not found, player offline, etc.)
- Example usage:
  ```
  /lore item give Frost_Sword Steve
  /lore item give MickyHat Alex
  ```

### Step 3: **Deprecate Old Cosmetic Give Command**

- Remove or mark as deprecated: `CosmeticGiveSubCommand`
- Update documentation and help messages to point to `/lore item give`

### Step 4: **Move Collection/Cosmetic Management**

- Move collection browsing, progress, and reward claiming to `/lore collection` (see `CosmeticCollectionSubCommand` for reference)
- Example:
  ```
  /lore collection view <collection_id>
  /lore collection claim <collection_id>
  ```

### Step 5: **Update Permissions**

- Set permissions for `/lore item give` (e.g., `rvnklore.admin.item.give`)
- Set separate permissions for `/lore collection` commands

---

## 4. **Best Practices**

- **Keep item logic generic:** Avoid hardcoding cosmetic/collection checks in the give command.
- **Use manager pattern:** Route all item creation through `ItemManager`.
- **Document new commands:** Update plugin help and admin docs.
- **Test:** Ensure all item types (regular, cosmetic, collection) can be given via the unified command.

---

## 5. **Example Command Usages**

| Command                              | Description                                 |
|---------------------------------------|---------------------------------------------|
| `/lore item give Frost_Sword Steve`   | Gives "Frost Sword" to Steve                |
| `/lore item give MickyHat Alex`       | Gives "MickyHat" cosmetic to Alex           |
| `/lore collection view hats`          | Shows info about the "hats" collection      |
| `/lore collection claim hats`         | Claims rewards for the "hats" collection    |

---

## 6. **References**

- [`ItemManager`](../src/main/java/org/fourz/RVNKLore/lore/item/ItemManager.java)
- [`CollectionManager`](../src/main/java/org/fourz/RVNKLore/lore/item/collection/CollectionManager.java)
- [`CosmeticItem`](../src/main/java/org/fourz/RVNKLore/lore/item/cosmetic/CosmeticItem.java)
- [`CosmeticCollectionSubCommand`](../src/main/java/org/fourz/RVNKLore/command/cosmetic/CosmeticCollectionSubCommand.java)

---

## 7. **Summary**

This refactor will streamline item administration, reduce code duplication, and make the plugin easier to maintain and extend. All item giving is handled by a single, unified command, while collection/cosmetic-specific features are managed in their own dedicated commands.

```// filepath: c:\tools\_PROJECTS\Ravenkaft Dev\RVNKLore\RVNKLore\docs\rvnklore-refactor-cosmeticitems&collections.md```

# RVNKLore Refactor Guide: Unified `/lore give` Command

This guide provides a step-by-step approach to refactoring the RVNKLore plugin to support a unified `/lore give` command for all item types—including cosmetics and collection items—without requiring additional cosmetic-specific data. Cosmetic and collection-specific management will be moved to dedicated commands (e.g., `/lore collection`).

---

## ✨ Goal

- **Unify item giving:** `/lore item give <item_name> <recipient>`
- **Move cosmetic/collection logic:** To `/lore collection` and related commands
- **Simplify admin workflow:** No need to distinguish between "cosmetic" and "regular" items for basic give operations

---

## 1. **Preparation**

- Review the current `CosmeticGiveSubCommand` and `CollectionManager` logic.
- Ensure all items (including cosmetics/heads) are registered in the main item registry (`ItemManager`).
- Confirm that `ItemManager.createLoreItem()` can create any item by name/type.

---

## 2. **Refactor Command Structure**

### a. **Create Unified Give Command**

- **New Command:** `/lore item give <item_name> <recipient>`
- **Location:** `RVNKLore.command.LoreItemGiveSubCommand`
- **Responsibilities:**
  - Look up item by name (from `ItemManager`/`CollectionManager`)
  - Create the item using `ItemManager.createLoreItem()`
  - Give the item to the specified player

### b. **Move Cosmetic/Collection Logic**

- **Cosmetic-specific commands:** Move to `/lore collection` (e.g., `/lore collection view`, `/lore collection claim`)
- **Remove:** Cosmetic-specific checks from the unified give command
- **Keep:** Cosmetic/collection progress, rewards, and browsing in their own subcommands

---

## 3. **Implementation Steps**

### Step 1: **Update Item Registration**

- Ensure all items (including heads/cosmetics) are accessible via `ItemManager` and have unique names/IDs.

### Step 2: **Implement `LoreItemGiveSubCommand`**

- Create a new class:  
  `RVNKLore.command.LoreItemGiveSubCommand`
- Implement logic:
  - Parse `<item_name>` and `<recipient>`
  - Use `ItemManager` to look up and create the item
  - Give the item to the player (handle errors: not found, player offline, etc.)
- Example usage:
  ```
  /lore item give Frost_Sword Steve
  /lore item give MickyHat Alex
  ```

### Step 3: **Deprecate Old Cosmetic Give Command**

- Remove or mark as deprecated: `CosmeticGiveSubCommand`
- Update documentation and help messages to point to `/lore item give`

### Step 4: **Move Collection/Cosmetic Management**

- Move collection browsing, progress, and reward claiming to `/lore collection` (see `CosmeticCollectionSubCommand` for reference)
- Example:
  ```
  /lore collection view <collection_id>
  /lore collection claim <collection_id>
  ```

### Step 5: **Update Permissions**

- Set permissions for `/lore item give` (e.g., `rvnklore.admin.item.give`)
- Set separate permissions for `/lore collection` commands

---

## 4. **Best Practices**

- **Keep item logic generic:** Avoid hardcoding cosmetic/collection checks in the give command.
- **Use manager pattern:** Route all item creation through `ItemManager`.
- **Document new commands:** Update plugin help and admin docs.
- **Test:** Ensure all item types (regular, cosmetic, collection) can be given via the unified command.

---

## 5. **Example Command Usages**

| Command                              | Description                                 |
|---------------------------------------|---------------------------------------------|
| `/lore item give Frost_Sword Steve`   | Gives "Frost Sword" to Steve                |
| `/lore item give MickyHat Alex`       | Gives "MickyHat" cosmetic to Alex           |
| `/lore collection view hats`          | Shows info about the "hats" collection      |
| `/lore collection claim hats`         | Claims rewards for the "hats" collection    |

---

## 6. **References**

- [`ItemManager`](../src/main/java/org/fourz/RVNKLore/lore/item/ItemManager.java)
- [`CollectionManager`](../src/main/java/org/fourz/RVNKLore/lore/item/collection/CollectionManager.java)
- [`CosmeticItem`](../src/main/java/org/fourz/RVNKLore/lore/item/cosmetic/CosmeticItem.java)
- [`CosmeticCollectionSubCommand`](../src/main/java/org/fourz/RVNKLore/command/cosmetic/CosmeticCollectionSubCommand.java)

---

## 7. **Summary**

This refactor will streamline item administration, reduce code duplication, and make the plugin easier to maintain and extend. All item giving is handled by a single, unified command, while collection/cosmetic-specific features are managed in their own dedicated commands.
