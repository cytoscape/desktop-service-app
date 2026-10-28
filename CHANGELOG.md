# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - Unreleased

Parity with Cytoscape Web's newer service app metadata.

### Changed

- **Uninstalling from the manager.** Each row ends with a trashcan, which asks "Are you sure you
want to uninstall <name>?" with Yes and No. The Uninstall button and the light red row highlight
are gone, and a selected row is light grey
([#4](https://github.com/cytoscape/desktop-service-app/issues/4)).

### Fixed

- **`columnTypeFilter` given as a list.** A service app whose column picker names several column
types, as Cytoscape Web allows, failed to install. It now installs, and the picker lists columns of
any of those types.
- **`serviceInputDefinition.type: "none"`.** An app that asks for no data no longer gets an empty
node table and no longer needs an open network: it is sent `data: {}`, as in Cytoscape Web.

### Added

- **Parameter type `ndexUUID`.** A service app that asks for the network's NDEx address now runs.
Desktop fills the value itself, as Cytoscape Web does: the network's NDEx URL when it came from or
was saved to NDEx and is unchanged since, and an empty value otherwise. The NDEx server is the new
`ndex.base.url` setting
([#4](https://github.com/cytoscape/desktop-service-app/issues/4)).
- **Parameter `groups`.** Grouped parameters are shown in nested, titled sections of the dialog.
Parameters that share a display name are keyed by their groups and name, such as `Nodes/Gap`, as
Cytoscape Web keys them, so one no longer overwrites the other.

## [0.1.0] - 2026-10-23

### Added

- **The first release of desktop-service-app**, the Cytoscape Desktop app that brings service apps
to Desktop as part of the service apps feature,
[cytoscape/cytoscape#28](https://github.com/cytoscape/cytoscape/issues/28). It installs service apps
from the unified App Store and keeps them across restarts
([#43](https://github.com/cytoscape/cytoscape/issues/43)); gives them menu entries, input dialogs, a
Service Apps manager and result rendering
([#44](https://github.com/cytoscape/cytoscape/issues/44)); and lets agents and other apps describe
and run them with `serviceapps describe`, `task call` and `task status`
([#45](https://github.com/cytoscape/cytoscape/issues/45)). Requires Cytoscape 3.10 or later.
