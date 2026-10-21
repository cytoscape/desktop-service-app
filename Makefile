# desktop-service-app — the ONLY entry point for building and testing. Do not invoke `gradlew`
# directly; add a target here instead.

GRADLE := ./gradlew --console=plain

.DEFAULT_GOAL := help
.PHONY: help build release test integration-test lint lint-fix clean fixture install-app

## help: list the targets (default)
help:
	@echo "desktop-service-app — make targets"
	@echo
	@grep -E '^## [a-z-]+:' $(MAKEFILE_LIST) | sed 's/^## /  /' | sort

## build: compile and package the OSGi bundle jar
build:
	$(GRADLE) jar
	@ls -1 build/libs/*.jar | sed 's|^|  -> |'

## release: build the jar stamped with a real version, e.g. make release VERSION=0.1.0
release: clean
	@[ -n "$(VERSION)" ] || { echo "usage: make release VERSION=0.1.0" >&2; exit 2; }
	@echo "$(VERSION)" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$$' \
	  || { echo "refusing '$(VERSION)': not plain semver X.Y.Z" >&2; exit 2; }
	$(GRADLE) jar -PreleaseVersion=$(VERSION)
	@ls -1 build/libs/*.jar | sed 's|^|  -> |'

## test: unit tier only. Seconds, for the edit loop.
test:
	$(GRADLE) test

## integration-test: everything -- both tiers, the bundle manifest checks, and lint
integration-test:
	$(GRADLE) check

## lint: report style violations (Spotless is the whole style specification)
lint:
	$(GRADLE) spotlessCheck

## lint-fix: fix them
lint-fix:
	$(GRADLE) spotlessApply

## fixture: run the hello-world service app on http://localhost:8765 (PORT=n to change)
fixture:
	$(GRADLE) runFixture -Pport=$(or $(PORT),8765)

## clean: remove build output
clean:
	$(GRADLE) clean

## install-app: drop the bundle into a running Cytoscape, which hot-installs it
install-app: build
	@mkdir -p "$(HOME)/CytoscapeConfiguration/3/apps/installed"
	@cp build/libs/*.jar "$(HOME)/CytoscapeConfiguration/3/apps/installed/"
	@ls -1 build/libs/*.jar | xargs -n1 basename \
	  | sed 's|^|  -> $(HOME)/CytoscapeConfiguration/3/apps/installed/|'
