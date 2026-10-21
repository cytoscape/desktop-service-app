# desktop-service-app

A Cytoscape Desktop app that brings [service apps](https://github.com/cytoscape/cytoscape-web/wiki/Specification-for-Service-App-in-Cytoscape-Web-(draft-v2)) to Cytoscape Desktop. It installs them from the unified App Store, keeps track of the installed ones, and exposes `serviceapps` automation commands. Menus, dialogs and running service apps come in later releases.

Requires Cytoscape 3.10 or later and Java 17.

Work is tracked in [cytoscape/cytoscape#28](https://github.com/cytoscape/cytoscape/issues/28). This release covers [cytoscape/cytoscape#43](https://github.com/cytoscape/cytoscape/issues/43).

## Commands

Every command is in the `serviceapps` namespace. It is available from Desktop's Command Line, from automation scripts, and over CyREST at `http://127.0.0.1:1234/v1/commands/serviceapps/<command>`. Each returns JSON.

| Command | Does |
|---|---|
| `serviceapps list` | Lists the installed service apps as `[{name, version, url, reachable}]` |
| `serviceapps install name= version= url=` | Fetches the app's metadata from `url`, checks that it reports that `name` and `version`, and installs it. Installing a name that is already installed replaces it (an upgrade). The text reply contains `installed` |
| `serviceapps uninstall name=` | Removes an installed app. The text reply contains `uninstalled` |
| `serviceapps status name=` | Checks the app's `GET /status` and returns `{name, reachable, httpStatus, serviceStatus}` |

For example, with Cytoscape running:

```
curl "http://127.0.0.1:1234/v1/commands/serviceapps/install?name=Hello%20World&version=1.0&url=http://127.0.0.1:8765/hello"
curl "http://127.0.0.1:1234/v1/commands/serviceapps/list"
```

Installed apps are saved in `service-apps.json` under the app's folder in the Cytoscape configuration directory, so they survive restarts.

## Settings

`serviceapps.props` in the Cytoscape configuration directory, also editable in Edit > Preferences > Properties:

| Property | Default | Meaning |
|---|---|---|
| `connect.timeout.ms` | 10000 | How long to wait for a service app to accept a connection |
| `read.timeout.ms` | 30000 | How long to wait for a service app to answer one request |

## Build and test

`make` is the only entry point; `make help` lists the targets.

| Target | Does |
|---|---|
| `make build` | Builds the OSGi bundle into `build/libs/` |
| `make test` | Every test that runs in the JVM: unit tests, the client and commands against the fixture service app over HTTP, the bundle manifest checks, and the style check |
| `make integration-test` | The `*IT` tests: Cytoscape Desktop running in Docker, with the app installed, reached over CyREST. Needs Docker |
| `make lint` / `make lint-fix` | Checks or fixes code style (google-java-format, AOSP) |
| `make install-app` | Copies the bundle into `~/CytoscapeConfiguration/3/apps/installed`, where a running Cytoscape picks it up |
| `make fixture` | Runs the hello-world service app on `http://127.0.0.1:8765` (`PORT=n` to change) |
| `make release VERSION=x.y.z` | Builds a release jar stamped with that version |

The two test targets don't overlap: a test class named `*IT` needs Desktop and runs only in `make integration-test`; every other test runs only in `make test`.

### Integration tests against Cytoscape Desktop

`make integration-test` builds a Docker image from `docker/desktop` (Temurin 17 JDK, Xvfb, and Cytoscape from its Linux installer), starts it, copies the app jar into Cytoscape's installed-apps folder, and runs the `*IT` tests over CyREST. Cytoscape in the container reaches the fixture service app on the test host as `host.docker.internal`. One container serves the whole run and is removed at the end; when a test fails, Cytoscape's logs are printed.

Pick the Cytoscape version under test with the installer URL. The checksum is verified when given:

```
make integration-test CYTOSCAPE_INSTALLER_URL=<url of Cytoscape_x_y_z_unix.sh> CYTOSCAPE_INSTALLER_SHA256=<sha256>
```

The default is the Cytoscape 3.10.5 installer in `build.gradle`. CI sets both values explicitly in `.github/workflows/ci.yml`.

## The hello-world service app fixture

`src/fixture/java` holds a small service app that implements all six endpoints of the [service app spec](https://github.com/cytoscape/cytoscape-web/wiki/Specification-for-Service-App-in-Cytoscape-Web-(draft-v2)). It serves a catalogue of named scenarios, each at `/<scenario>`, so one server covers every case the tests need:

- `hello` and `hello-v2`: the same app, "Hello World", at versions 1.0 and 2.0;
- `param-*`: every parameter type and attribute variant;
- `input-*`: input columns and input definitions;
- `menu-*`: menu placement cases;
- `unsupported-*`: the spec's experimental parameter types and the `edgelist` format;
- `result-*`: one per result action, plus multi-action, unknown and undeclared actions;
- `failure-*`: slow, failed, never finishing, and a refused submission.

`make fixture` prints every scenario's URL. Tests use it through `FixtureServerExtension`, which injects a running `FixtureServer` with a request log.

## Releasing

Publish a GitHub release tagged `vX.Y.Z`. The release workflow runs the full suite, builds the jar with that version, checks its `Bundle-Version`, and attaches it to the release.
