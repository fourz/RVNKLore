# Copilot Instructions for RVNKLore Development

## Development Workflow

### Building and Testing

To build and test the plugin, follow these steps:

1. **Build the Plugin**:
   Use the `Build Plugin` task to compile and package the plugin. This will generate the JAR file in the `target` directory.

2. **Copy to Server**:
   The `Copy to Server` task will automatically copy the built JAR file to the server's plugins folder.

3. **Reload or Restart the Server**:
   - Use the `Reload Server` task to reload the plugin without restarting the server. This is useful for quick testing of changes.
   - Use the `Restart Server` task to fully restart the server. This ensures a clean state and is recommended for testing major changes.

These tasks can be executed from the VS Code task runner or directly from the terminal using the provided PowerShell scripts.

### Additional Notes

- Always ensure the server is in a stable state before reloading or restarting.
- Use the `Clean&Reload Server` or `Clean&Restart Server` tasks if you need to clean up the server environment before testing.
