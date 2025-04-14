# RVNKLore

A comprehensive lore and history plugin for Minecraft servers that allows players and staff to document the stories, landmarks, characters, and events that shape your server's world.

## Features

- **Multiple Lore Types**: Supports various categories of lore including landmarks, cities, player characters, items, events, quests, enchantments, and more
- **In-game Documentation**: Create, browse, and discover lore entries directly in-game
- **Staff Approval System**: Configurable system requiring staff approval for player-submitted lore
- **Location Awareness**: Automatically connects lore to locations in your Minecraft world
- **Automatic Events**: Optionally create lore entries for significant player events like first joins, notable deaths, etc.
- **Custom Lore Items**: Allow players to customize lore and trigger registration to the lore database
- **Support for VotingPlugin**: Dynamically generate reward files for Votifier-driven vote rewards
- **Entity Name Generator**: Add richness to your world with the automatic name generator for special mobs
- **Collection Management**: Create and track thematic collections of items and lore entries
- **Seasonal Content**: Support for time-limited and seasonal lore and items
- **Economy Integration**: Track item values and trading records
- **Community Engagement**: Allow players to vote on and contribute to lore development
- **Roleplay Systems Framework**: Support for structured roleplay including legal, governance, and commerce systems
- **Seamless Integration**: Works with existing plugins and doesn't interfere with core gameplay
- **PlaceHolder API Support**: (planned)

## Installation

1. Download the latest RVNKLore.jar from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Edit the configuration file at `plugins/RVNKLore/config.yml` to customize settings
5. Restart again or use `/lore reload` to apply changes

## Usage

### Basic Commands

- `/lore add <type> <name> <description>` - Add a new lore entry
- `/lore get <name>` - View details about a specific lore entry
- `/lore list [type]` - List available lore entries, optionally filtered by type
- `/lore approve <id>` - Approve a pending lore entry (staff only)
- `/lore export [type]` - Export lore entries to a JSON file (staff only)
- `/lore reload` - Reload the plugin configuration (staff only)

### Lore Types

- `LANDMARK` - Notable locations in the world
- `CITY` - Settlements and cities
- `PLAYER` - Player character histories and achievements
- `FACTION` - Groups, factions, and organizations
- `ITEM` - Legendary or special items
- `HEAD` - Decorative head items with lore
- `EVENT` - Historical events
- `PATH` - Notable roads or pathways between locations
- `QUEST` - Adventures and quests
- `ENCHANTMENT` - Special or legendary enchantments
- `SPECIAL_ENTITY` - Named or significant mobs with lore relevance
- `HAPPENING` - Server events and roleplay activities

### Permissions

- `rvnklore.command.add` - Add lore entries
- `rvnklore.command.get` - View lore entries
- `rvnklore.command.list` - List lore entries
- `rvnklore.command.approve` - Approve pending entries (staff only)
- `rvnklore.command.getitem` - Receive lore items (staff only)
- `rvnklore.command.export` - Export lore entries (staff only)
- `rvnklore.command.reload` - Reload plugin configuration (staff only)
- `rvnklore.command.debug` - Access debug commands (staff only)
- `rvnklore.register.city` - Register new cities
- `rvnklore.notable` - Mark a player as "notable" for automatic lore generation
- `rvnklore.admin` - Access to all administrative functions

## Configuration

The main configuration file is located at `plugins/RVNKLore/config.yml` and contains settings for:

- Database configuration (SQLite by default, MySQL supported)
- Logging levels
- Custom messages
- Automatic lore generation settings

### Example Configuration

```yaml
general:
  logLevel: INFO  # OFF, SEVERE, WARNING, INFO, DEBUG, FINE

storage:
  type: sqlite    # sqlite or mysql
  mysql:
    host: localhost
    port: 3306
    username: root
    password: ''
    useSSL: false
    database: rvnklore
  sqlite:
    database: data.db
```

## Examples

### Adding a Landmark

```
/lore add LANDMARK "Ancient Oak" This massive oak tree has stood since the founding of the server. Legend says it was the first block placed in this world.
```

### Registering a City

```
/lore registercity Ravenport A bustling port city on the eastern shore, known for its skilled fishermen and bustling marketplace.
```

### Creating Character Lore

```
/lore add PLAYER "Wizard Thordak" The most powerful mage to ever walk these lands, Thordak was known for his mastery of fire magic and his distinctive red robes.
```

### Adding Item Lore

```
/lore add ITEM "Frost Edge" This ancient blade radiates cold energy, leaving a trail of frost wherever it goes. Created during the Great Winter of the Third Era.
```

### Adding Head Lore

```
/lore add HEAD "Crown of the Ancient King" This crown belonged to King Jarthan III, the last ruler of the Old Kingdom before it fell to the darkness.
```

## Lore Data Ingestion

RVNKLore supports multiple methods for ingesting lore data into the database:

### 1. Command Input
The most direct method using in-game commands:
```
/lore add <type> <name> <description>
/lore registercity <name> <description>
```

### 2. Player Actions
Lore can be automatically generated from player activities:
- **Location Claims**: City or landmark lore generated when players claim territory
- **Item Enchanting**: Special enchantments can trigger lore generation for items
- **Mob Naming**: Using name tags on mobs can register them in the lore database

### 3. UI Integration
RVNKLore integrates with existing Minecraft interfaces:
- **Anvil UI**: Hijacked to allow players to input custom lore for items
- **Container UI**: Custom menus for lore browsing and management
- **Book Editing**: Create lore entries through book & quill interfaces

### 4. Web Interface
A separate web interface for comprehensive lore management:
- Supports all lore types and operations
- Admin dashboard for approval and management
- Public-facing view for community browsing

### 5. GitHub Automated Workflow
For team-maintained special items and lore:
- Automated workflow for consistent additions
- Version-controlled lore entries
- Supports batch processing for collections (like MickeyHats)

### 6. Custom Data Import
For migration and bulk additions:
- Custom scripting tools for importing from other systems
- Server data analysis for retroactive lore creation
- Batch processing capabilities

## Automatic Lore Generation

RVNKLore can automatically generate lore entries for:

- **First-time player joins**: Creates a record of when players first join the server
- **Notable player deaths**: Records significant player deaths (with valuable items, killed by other players, etc.)
- **Special enchantments**: Documents when players create powerful enchanted items
- **Named mobs**: Automatically assigns special names to rare or powerful monsters

## Database

RVNKLore stores all lore entries in a comprehensive relational database:

- **SQLite**: Default database, stored in `plugins/RVNKLore/data.db`
- **MySQL**: Optional for larger servers or multi-server networks

### Core Data Structure

The database uses a sophisticated schema design that supports all lore functionality:

#### Core Entities
- **Lore Items**: Stores information about all custom items including properties, enchantments, and collection memberships
- **Lore Locations**: Records significant places in the world with coordinates and detailed information
- **Lore Characters**: Tracks notable NPCs, historical figures, and player characters
- **Lore Quests**: Defines quests, objectives, rewards, and progression paths
- **Lore Events**: Documents significant historical events that shaped the server world
- **Special Entities**: Records special mobs, companions, and named creatures
- **Server Happenings**: Tracks server events, roleplay scenarios, and community activities

#### Collection System
- **Collections**: Organizes items into thematic groups with metadata
- **Seasonal Items**: Controls availability based on season, event, or time window
- **Item Value**: Tracks economic value and trading information for items

#### Engagement Features
- **Player Achievements**: Records player interactions with lore elements
- **Community Voting**: Allows players to rate and provide feedback on lore content
- **Trading Records**: Logs economic transactions involving lore items

#### Roleplay Framework
- **Roleplay Systems**: Defines structured roleplay systems for legal proceedings, governance, etc.
- **Roleplay Instances**: Records specific roleplay events and their outcomes

### Data Taxonomy

The plugin uses standardized classification systems for various entity types:

- **Item Types**: LEGENDARY, ARTIFACT, SEASONAL, EVENT, COMMON, UNIQUE, CRAFTED, SET_PIECE
- **Location Types**: CITY, TOWN, VILLAGE, LANDMARK, POI, DUNGEON, RUIN, WILDERNESS, etc.
- **Character Types**: NPC, HISTORICAL, MYTHICAL, PLAYER, DEITY, CREATURE, GROUP, FAMILY
- **Event Types**: HISTORICAL, SEASONAL, PLAYER, SERVER, TRIAL, LEGAL, ROLEPLAY, etc.
- **Happening Types**: TRIAL, CELEBRATION, BUILD_EVENT, COMPETITION, DISCOVERY, etc.
- **Faction Types**: KINGDOM, GUILD, CULT, TRIBE, ALLIANCE, FAMILY, ORDER, OUTLAW
- **Rarity Levels**: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, UNIQUE, ARTIFACT
- **Collection Types**: SEASONAL, MICKY_HATS, LEGENDARY, QUEST_REWARDS, THEMED_SET

## Development

### Building from Source

To build RVNKLore from source:

1. Clone the repository
2. Run `mvn clean package` to build the JAR
3. The compiled JAR will be in the `target` directory

### API for Developers

RVNKLore provides an API for other plugins to interact with the lore system:

```java
// Get the RVNKLore plugin instance
RVNKLore rvnkLore = (RVNKLore) Bukkit.getPluginManager().getPlugin("RVNKLore");

// Create a new lore entry programmatically
LoreEntry entry = new LoreEntry();
entry.setType(LoreType.EVENT);
entry.setName("Battle of the North");
entry.setDescription("A legendary battle that took place between rival factions.");
entry.setLocation(location);
entry.setSubmittedBy("PluginName");
entry.setApproved(true);

// Add the entry
rvnkLore.getLoreManager().addLoreEntry(entry);

// Working with items
LoreItem item = new LoreItem();
item.setName("Frost Blade");
item.setMaterial(Material.DIAMOND_SWORD);
item.setItemType(ItemType.LEGENDARY);
item.setRarity(RarityLevel.EPIC);
item.setDescription("A blade forged in the eternal ice of the North.");

// Add custom model data
item.setCustomModelData(101); // Using weapon range (101-200)

// Add the item to a collection
rvnkLore.getCollectionManager().addItemToCollection("Winter Weapons", item);

// Set seasonal availability
SeasonalSettings seasonal = new SeasonalSettings();
seasonal.setSeason(Season.WINTER);
seasonal.setEventName(Holiday.CHRISTMAS);
seasonal.setAvailabilityWindow(new TimeWindow(WindowType.ANNUAL_HOLIDAY, "12-01", "12-25"));
item.setSeasonalSettings(seasonal);

// Create a lore character
LoreCharacter character = new LoreCharacter();
character.setName("Frostbeard the Smith");
character.setType(CharacterType.NPC);
character.setDescription("The legendary smith who forged the Frost Blade.");
character.setHomeLocation(northernForgeLocation);

// Add the character
rvnkLore.getCharacterManager().addCharacter(character);
```

### Entity Relationship Model

The plugin's data model supports complex relationships:

- Items can belong to multiple collections
- Characters can be associated with locations, quests, and events
- Events can reference locations, characters, and items
- Quests can have multiple objectives, prerequisites, and rewards
- Trading records track economic exchanges of lore items
- Roleplay systems can have multiple instances and participants

### Adding Custom Handlers

Developers can create custom handlers for specific lore types:

```java
public class CustomLoreHandler implements LoreHandler {
    // Implement the LoreHandler interface methods
    // ...
}

// Register the custom handler
rvnkLore.getHandlerFactory().registerHandler(LoreType.CUSTOM, new CustomLoreHandler(rvnkLore));
```

## Compatibility

- **Minecraft Versions**: 1.17+
- **Server Software**: Spigot, Paper, and derivatives
- **Recommended Plugins**: Works well with map, economy, roleplay, and quest plugins

## Troubleshooting

- **Database Issues**: Check database connection settings in config.yml
- **Permission Problems**: Verify permission nodes in your permissions plugin
- **Command Errors**: Use `/lore debug` to get more detailed error information

## Future Roadmap

### Version 1.1
- **Web Interface**: View and manage lore through a web portal
- **Advanced Filtering**: Search lore entries by multiple criteria
- **Lore Books**: Physical in-game books that can be placed in the world
- **NPC Integration**: Connect lore entries to Citizens or other NPC plugins

### Version 1.2
- **Lore Quests**: Generate simple quests based on lore entries
- **Timeline View**: Interactive timeline of server history
- **Lore Maps**: In-game maps showing locations of lore entries
- **Image Support**: Link external images to lore entries via web interface

### Version 1.3
- **Multi-server Support**: Share lore across a network of servers
- **Conditional Display**: Show different lore based on player permissions/progress
- **API Expansion**: More hooks for developer integration
- **Custom Categories**: Allow server owners to define their own lore types

### Long-term Goals
- **LoreScript**: Simple scripting language for creating interactive lore
- **Procedural Generation**: AI-assisted lore generation based on world features
- **Resource Pack Integration**: Custom models and textures for lore items

## Credits

Developed by Derek and Fourz.

## License

RVNKLore is licensed under the [MIT License](LICENSE).
