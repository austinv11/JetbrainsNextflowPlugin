Bring the power of Nextflow to your JetBrains IDE.

This plugin provides enhanced support for Nextflow within JetBrains environments, with a special focus on IntelliJ IDEA and PyCharm. It bridges the gap between official Nextflow tooling and native IDE capabilities.

### Features

* **Official Nextflow Language Server Support**: Real-time code completion, parameter hints, hover documentation, and error checking directly integrated via the official Nextflow Language Server Protocol (LSP).
* **Native Run & Debug Configurations**: Effortlessly create, execute, and remote-debug your Nextflow pipelines directly from the IDE. Includes full support for Windows Subsystem for Linux (WSL).
* **Official Syntax Highlighting**: Highlights Nextflow-specific syntax (like processes, workflows, channels, and operators) via official TextMate bundle integration.
* **Code Actions & Refactoring**: Utilize LSP-powered formatting and specialized actions to convert legacy scripts and pipelines to static types.
* **File Templates**: Quickly bootstrap your projects with built-in templates for `.nf`, `.nf.test`, and `nextflow.config` files.
* **Interactive Nextflow Console**: Launch an interactive REPL console to test Nextflow logic directly within the IDE.
* **Dedicated Tool Windows**: Specialized project and resource panels to better manage and interact with your Nextflow environment.
* **nf-core Support**: Integrated support for managing nf-core-based pipelines, with a dedicated tool window (requires [nf-core/tools](https://github.com/nf-core/tools)).

### Notice for non-IntelliJ IDE Users
* Richer syntax highlighting requires the Groovy plugin, which is only bundled with the IntelliJ family of IDEs. When 
official Groovy support is not available, we fall back to only basic groovy syntax highlighting provided by Nextflow TextMate bundles (i.e. less rich IDE interactions will be available).

* Debugging is only supported through the Java Debugger, which is also only bundled with the IntelliJ family of IDEs. Running pipelines without
debugging enabled will still work on, however.


All screenshots on the plugin page were taken from IntelliJ IDEA. If there are unforseen issues not mentioned, please 
report them to the issue tracker.
