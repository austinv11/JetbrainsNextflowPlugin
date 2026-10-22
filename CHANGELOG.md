# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Download the Nextflow language server from official nextflow-io releases instead of the patched fork.
- Match automatic language-server downloads to Nextflow Version and cache each server version separately, with a warning when falling back to a different cached version.
- Store language-server downloads in the IDE system directory to avoid triggering development plugin reloads; reuse older plugin-directory caches without modifying them.
- Reuse matching installed servers at startup and retry unexpectedly empty GitHub release lists.

### Added
- Enable language-server features in additional `.config` files under pipeline folders containing `nextflow.config`, excluding `nf-test.config` and unmarked project folders.
- Automatically restart the language server after a short pause when closed pipeline files, configuration files, or parameter schemas change, or pipeline files are created, deleted, moved, or renamed. Ignore excluded directories and preserve unsaved open documents.
- Daily language-server update checks with Install update and Dismiss actions, plus a manual check in the Tools menu. Updates stay within the selected Nextflow release series and leave custom JARs unchanged.

### Fixed
- Recognize included pipeline configs as Nextflow files and show Nextflow icons with either the native parser or TextMate fallback.
- Preserve IntelliJ's semantic-token capabilities and request semantic highlighting for Nextflow scripts and configuration files alongside existing syntax highlighting.
- Normalize Windows drive separators in language-server file URIs so hover and other requests match the server's indexed files.
- Register the Nextflow file type without fixed extension associations so hover signatures and code snippets can be highlighted without a null PSI file.
- Keep failed Nextflow executable probes at debug level and report a concise runtime-detection warning.
- Remove unsupported editor metadata from the nf-core icon to avoid SVG renderer warnings.

## [1.1.3]

### Changed
- Made groovy integrations more lazy to improve non-IntelliJ IDEs.

## [1.1.0]

### Added
- Add a plugin thumbnail icon
- Better support for non-IntelliJ IDEs (like PyCharm, WebStorm, etc.)

### Changed
- Made groovy inspections optional when outside of IntelliJ
- Made debug configurations dependent on IntelliJ (It requires the Java Debugger)


## [1.1.0]

### Added
- Add a plugin thumbnail icon
- Better support for non-IntelliJ IDEs (like PyCharm, WebStorm, etc.)

### Changed
- Made groovy inspections optional when outside of IntelliJ
- Made debug configurations dependent on IntelliJ (It requires the Java Debugger)

## [1.0.3]

### Added

### Changed
- Bump minimum supported version to 2026.1

### Removed



## [1.0.2]

### Added

### Changed
- Fixed plugin verifier compatibility issue with Terminal API for IDEA versions 2025.3+
- Fixed override-only API usage violation for `NextflowLspCustomization`
- Fixed deprecated `doNotActivateOnStart` toolWindow property usage

### Removed


## [1.0.0]

Initial full release with official Nextflow LSP Support, Complete IDEA integration, experimental nf-core support, and more.

## [1.0-SNAPSHOT]

### Added
- Initial release with basic Nextflow support.
