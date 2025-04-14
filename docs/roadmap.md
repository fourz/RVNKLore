# RVNKLore Project Roadmap
*Last Updated: April 12, 2025*

## Current Status

RVNKLore has established a solid foundation with core functionality in place:

- ✅ Database structure and connection management
- ✅ Basic lore entry creation and management
- ✅ Handler system for different lore types
- ✅ Command framework for player interaction
- ✅ Debug and logging systems

## Q2 2025 Priorities

### Item Generation System

The next major focus for RVNKLore is building a comprehensive item generation system that will form the backbone of server lore items.

#### Feature Set:

- [ ] **Enchanted Item Generation** *(High Priority)*
  - Create API for generating custom enchanted items with unique properties
  - Support for compound enchantments (combinations with special effects)
  - Enchantment level variation based on rarity tiers
  - Integration with vanilla enchantment limits and compatibility rules

- [ ] **Item Lore Interface** *(High Priority)*
  - In-game interface for players to add lore to items
  - Customizable lore formatting and display options
  - Support for lore templates (predefined text structures)

  - [ ] **Head & Cosmetic System** *(High Priority)*
  - Player head texture application and management
  - Head collections with thematic grouping
  - Mob head variants with custom properties
  - Support for both built-in and custom textures

- [ ] **Custom Model Data Integration** *(Medium Priority)*
  - Resource pack synchronization system
  - Organized model ID allocation and tracking
  - Support for seasonal texture variants
  - Item appearance change based on world events

- [ ] **Dynamic Lore Generation** *(Medium Priority)*
  - Template-based lore text generation
  - Variables integration in lore (player names, dates, locations)
  - Progressive lore revelation based on in-game actions
  - Different lore styles based on item type and rarity

- [ ] **Historical Metadata System** *(Medium Priority)*
  - Creation date, location and context storage
  - Item "lineage" tracking (crafting history)
  - Previous owners and notable uses
  - Integration with server events/happenings

## Q3 2025 Priorities

### Item History & Storage

Building on the item generation system, we'll implement comprehensive storage and historical tracking for all items.

#### Feature Set:
- [ ] **YAML-Based Item Definition** *(High Priority)*
  - Structured YAML format for defining item properties
  - Validation system for YAML item definitions
  - Command-line tools for bulk item import/export
  - Migration path from legacy item systems

- [ ] **Database Integration** *(High Priority)*
  - Entity relationship models for items and collections
  - Efficient query patterns for item lookups
  - Transaction support for batch operations
  - Backup and recovery systems

- [ ] **Collection Management** *(Medium Priority)*
  - Collection definition and organization tools
  - UI for browsing and managing collections
  - Completion tracking and rewards
  - Collection statistics and leaderboards

- [ ] **Item Lifecycle Management** *(Medium Priority)*
  - Item deprecation and replacement mechanisms
  - Version control for items across server updates
  - Audit logs for item changes
  - Restoration/rollback capabilities

## Q4 2025 Priorities

### VotingPlugin Integration

Integrate the item and collection systems with VotingPlugin to provide dynamic, seasonal rewards.

#### Feature Set:
- [ ] **VotingPlugin Reward File Generation** *(High Priority)*
  - Dynamic reward file generation based on active collections
  - Configurable reward distribution and weighting
  - Support for VotingPlugin's custom reward types
  - Command-based manual reward generation

- [ ] **Seasonal Rotation System** *(High Priority)*
  - Time-based collection rotation (daily, weekly, monthly)
  - Special event activations (holidays, server events)
  - Scheduled rotations with preview and announcements
  - Emergency rotation override controls

- [ ] **Collection Data Maintenance** *(Medium Priority)*
  - Data integrity checks and validation
  - Performance optimization for reward generation
  - Caching strategies for frequently accessed collections
  - Monitoring and alerting for collection issues

- [ ] **Admin Controls & Management UI** *(Medium Priority)*
  - Web-based or in-game controls for rotation management
  - Preview system for upcoming rotations
  - Analytics dashboard for reward distribution
  - A/B testing capabilities for reward effectiveness

## 2026 Long-Term Goals

- **Player Collection Interface**
  - In-game UI for players to browse and track collections
  - Collection completion rewards and achievements
  - Trading system for collection items

- **Lore-Based Quest System**
  - Quests based on item collections and lore
  - Progressive storylines linked to server history
  - Dynamic quest generation based on player actions

- **Extended Plugin Integrations**
  - PlaceholderAPI expanded support
  - MythicMobs custom drops integration
  - PAPI integration for conditional display
  - WorldGuard region-based collection availability

- **Community Contribution System**
  - Player submission interface for lore suggestions
  - Voting/rating system for community lore
  - Recognition for top lore contributors

## Lore Input Methods Implementation

### Q2-Q3 2025: Core Input Methods
- [ ] **Command-Based Input** *(High Priority)*
  - Comprehensive command framework for all lore types
  - Tab completion with contextual suggestions
  - Command aliases for common operations
  - Permission-based command access control

- [ ] **Player Action Triggers** *(High Priority)*
  - Location-based lore generation via territorial claims
  - Enchantment-triggered item lore creation
  - Mob naming integration with lore database
  - Player milestone event capture (achievements, etc.)

- [ ] **Game UI Integration** *(Medium Priority)*
  - Anvil UI hijacking for lore input
  - Container-based menu system for lore browsing
  - In-game book editing for lengthy lore entries
  - Sign-based quick input for simple entries

### Q4 2025: Advanced Input Methods
- [ ] **Web Interface Development** *(High Priority)*
  - Admin dashboard for lore management
  - Public-facing lore browser with search capabilities
  - User authentication and permission system
  - Real-time synchronization with in-game database

- [ ] **GitHub Workflow Integration** *(Medium Priority)*
  - Automated GitHub Actions for lore validation
  - YAML-based lore definition format
  - Pull request system for team contributions
  - Specialized workflows for collections (MickyHats, etc.)

- [ ] **Custom Data Import Tools** *(Medium Priority)*
  - CSV/JSON import functionality
  - Legacy database migration tools
  - Server log analysis for retroactive lore creation
  - Bulk processing utilities for mass imports

## Plugin Integrations Roadmap

### Q3 2025: Primary Integrations
- [ ] **VotingPlugin Integration** *(High Priority)*
  - Dynamic reward file generation
  - Collection-based voting rewards
  - Vote streak bonuses with special lore items
  - Admin controls for reward management

- [ ] **PlaceholderAPI Integration** *(High Priority)*
  - Lore-based placeholders for player profiles
  - Collection completion status placeholders
  - Dynamic text insertion in other plugins
  - Server-wide lore statistics

### Q4 2025: Extended Integrations
- [ ] **WorldGuard Integration** *(Medium Priority)*
  - Region-based lore display
  - Location-sensitive collection availability
  - Custom flags for lore-related features
  - Region-specific item properties

- [ ] **MythicMobs Integration** *(Medium Priority)*
  - Custom drop tables based on lore collections
  - Mob-specific lore generation
  - Boss encounters with lore significance
  - Mythic item drop customization

### Q1-Q2 2026: Advanced Integrations
- [ ] **Quests Plugin Integration** *(Medium Priority)*
  - Lore-based quest objectives
  - Collection completion quests
  - Progressive storyline integration
  - NPC dialogue from lore database

- [ ] **Economy Plugin Integration** *(Low Priority)*
  - Lore item marketplaces
  - Collection value calculation
  - Lore-based economy modifiers
  - Trading system for rare lore items

## Implementation Notes

### Development Approach
1. Focus on core functionality first (item generation)
2. Ensure solid database design before implementing storage
3. Build comprehensive tests for core item manipulation
4. Create admin tools to simplify management
5. Implement player-facing features last

### Technology Considerations
- Use the Transaction Manager for all database operations
- Leverage asynchronous processing for resource-intensive operations
- Implement caching for frequently accessed data
- Establish clear migration paths between versions

### Resource Allocation
- Primary developer focus on item generation system (Q2)
- Database specialist consultation for storage optimization (Q3)
- UI/UX design resources for admin controls (Q4)
- Community testing phase before each major release

## Success Metrics

- **Item Generation**: Complete generation system with support for all proposed item types
- **Storage**: Zero data loss during item operations, sub-50ms query times
- **VotingPlugin**: Seamless integration with dynamic rewards, positive player feedback
- **Performance**: No measurable TPS impact during normal operation

## Risk Assessment

- **Technical Complexity**: Item generation system requires careful design to avoid performance issues
- **Data Migration**: Existing items need consistent migration path
- **Plugin Dependencies**: Changes to VotingPlugin API may require adaptation
- **Resource Pack Coordination**: Custom model data needs coordination with server resource pack

## Revision History

| Date | Version | Notes |
|------|---------|-------|
| April 12, 2025 | 1.0 | Initial roadmap draft |
