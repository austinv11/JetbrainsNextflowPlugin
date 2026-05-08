# Jetbrains Nextflow LSP Support

![Version](https://img.shields.io/badge/version-latest-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)

Basic plugin to add support for the official Nextflow Language Server Protocol (LSP) implementation in JetBrains IDEs like IntelliJ IDEA, PyCharm, WebStorm, and others.

## Features

This plugin aims to provide a native developer experience for Nextflow within JetBrains IDEs by integrating various features:

* **Official Nextflow LSP Support**: Integrates directly with the official Nextflow language server.
* **Native Run Configurations**: Create and execute Nextflow runs directly from your IDE.
* **Tool Windows**: Dedicated tool windows for interacting with Nextflow processes and logs.
* **Syntax Highlighting**: Official syntax highlighting via TextMate bundle integration.
* **DAG Rendering**: Visualize the Directed Acyclic Graph (DAG) of your Nextflow pipelines natively.

## Development

The project is built using Gradle. Here are the common commands used for development:

### Running in a Sandbox IDE
To launch a lightweight JetBrains IDE instance with the plugin installed for manual testing:
```bash
./gradlew runIde
```

### Building the Plugin
To build the complete plugin distribution (which outputs a ZIP file in `build/distributions`):
```bash
./gradlew buildPlugin
```

### Running Tests
To run the automated test suite:
```bash
./gradlew test
```

## Credits / See Also

This plugin utilizes and integrates with the official Nextflow language server:
* [nextflow-io/language-server](https://github.com/nextflow-io/language-server)

Textmate bundles and feature inspiration are from the official Nextflow VSCode extension:
* [nextflow-io/vscode-language-nextflow](https://github.com/nextflow-io/vscode-language-nextflow)
