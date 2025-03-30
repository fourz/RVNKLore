# RVNKLore

A comprehensive lore and history plugin for Minecraft servers that allows players and staff to document the stories, landmarks, characters, and events that shape your server's world.

## Features

- **Multiple Lore Types**: Supports various categories of lore including landmarks, cities, player characters, items, events, and more
- **In-game Documentation**: Create, browse, and discover lore entries directly in-game
- **Staff Approval System**: Configurable system requiring staff approval for player-submitted lore
- **Location Awareness**: Automatically connects lore to locations in your Minecraft world
- **Automatic Events**: Optionally create lore entries for significant player events like first joins, notable deaths, etc.
- **Custom Lore Items**: Allow players to customize lore and trigger registration to the lore database.
- **Support for VotingPlugin**: Dynamically generate reward files for Votifier-driven vote rewards.
- **Entity Name Generator**: Add richness to your world with the automatic name generator for special mobs
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

## Automatic Lore Generation

RVNKLore can automatically generate lore entries for:

- **First-time player joins**: Creates a record of when players first join the server
- **Notable player deaths**: Records significant player deaths (with valuable items, killed by other players, etc.)
- **Special enchantments**: Documents when players create powerful enchanted items
- **Named mobs**: Automatically assigns special names to rare or powerful monsters

## Database

RVNKLore stores all lore entries in a database:

- **SQLite**: Default database, stored in `plugins/RVNKLore/data.db`
- **MySQL**: Optional for larger servers or multi-server networks

### Data Structure

Each lore entry includes:
- Unique ID
- Name
- Description
- Type
- Location (for location-based lore)
- Submission information
- Approval status
- Custom metadata

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
```

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
