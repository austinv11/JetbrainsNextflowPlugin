Bring the power of Nextflow to your JetBrains IDE.

This plugin provides enhanced support for Nextflow within JetBrains environments, with a special focus on IntelliJ IDEA and PyCharm. It bridges the gap between official Nextflow tooling and native IDE capabilities.

### Features

* **Official Nextflow Language Server Support**: Real-time code completion, parameter hints, hover documentation, and error checking directly integrated via the official Nextflow Language Server Protocol (LSP).
* **Native Run & Debug Configurations**: Effortlessly create, execute, and remote-debug your Nextflow pipelines directly from the IDE. Includes full support for Windows Subsystem for Linux (WSL).
* **Official Syntax Highlighting**: Highlights Nextflow-specific syntax (like processes, workflows, channels, and operators) via official TextMate bundle integration.
* **Code Actions & Refactoring**: Use LSP-powered formatting and code actions. Static-type conversion actions are available only with language-server versions that advertise these commands.
* **Process Configuration Previews**: Inspect process directives, source locations, and overridden values from **Preview config**. Choose profiles in a searchable checkbox popup that stays open for multiple selections and updates the preview automatically.
* **File Templates**: Quickly bootstrap your projects with built-in templates for `.nf`, `.nf.test`, and `nextflow.config` files.
* **Interactive Nextflow Console**: Launch an interactive REPL console to test Nextflow logic directly within the IDE.
* **Dedicated Tool Windows**: Specialized project and resource panels to better manage and interact with your Nextflow environment.
* **nf-core Support**: Integrated support for managing nf-core-based pipelines, with a dedicated tool window (requires [nf-core/tools](https://github.com/nf-core/tools)).

### Notice for non-IntelliJ IDE Users
* When the Groovy plugin is unavailable, TextMate bundles provide Nextflow and embedded script highlighting. Language-server features remain available; Groovy-specific IDE integrations require the Groovy plugin.

* Debugging is only supported through the Java Debugger, which is also only bundled with the IntelliJ family of IDEs. Running pipelines without
debugging enabled remains available in other supported IDEs.


All screenshots on the plugin page were taken from IntelliJ IDEA. If there are unforeseen issues not mentioned, please report them to the issue tracker.
