# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]


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
