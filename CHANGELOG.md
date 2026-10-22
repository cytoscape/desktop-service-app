# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - Unreleased

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
