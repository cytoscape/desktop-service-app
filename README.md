# desktop-service-app

A Cytoscape Desktop app that adds support for **service apps**: tools that run on a web service rather than inside Cytoscape, such as a clustering or enrichment algorithm. Cytoscape Web already runs them; this app lets Cytoscape Desktop install them from the unified App Store, run them on your networks, and apply their results. It follows the [service app specification](https://github.com/cytoscape/cytoscape-web/wiki/Specification-for-Service-App-in-Cytoscape-Web-(draft-v2)) and Cytoscape Web's behaviour.

Requires Cytoscape 3.10 or later, whose bundled CX Support app is used to exchange networks with services as CX2.

## Documentation

- **[User manual](docs/Manual.md)**: installing service apps from the App Store, running them, the Service Apps manager, what results do, errors, settings, and how agents and scripts use the commands.
- **[Changelog](CHANGELOG.md)**: what each release adds.
- **[Service app specification](https://github.com/cytoscape/cytoscape-web/wiki/Specification-for-Service-App-in-Cytoscape-Web-(draft-v2))**: the service-side contract every service app implements.
- **[cytoscape/cytoscape#28](https://github.com/cytoscape/cytoscape/issues/28)**: the feature's tracking issue, with its design. It is delivered in [#43](https://github.com/cytoscape/cytoscape/issues/43), [#44](https://github.com/cytoscape/cytoscape/issues/44) and [#45](https://github.com/cytoscape/cytoscape/issues/45).

## What it adds to Cytoscape Desktop

- **Install from the App Store.** A service app page's **Install in Cytoscape Desktop** button installs or upgrades the app in the running Desktop. Installed apps, with their metadata, are kept across restarts.
- **Menu entries.** An app that names a Desktop menu (File, Edit, View, Select, Layout, Apps, Tools or Help) gets an entry at the place it asks for. An entry is greyed out while the network or selection it needs is missing, and, with the reason in its tooltip, when its service is unreachable or it uses something Desktop doesn't support yet.
- **Generated input dialogs.** Each app's dialog is built from its metadata: text, drop-down, radio, checkbox and column-picker fields, with the app's own validation, and with the values used last time remembered.
- **The Service Apps menu.** **Apps > Service Apps > Manage** lists every installed app with its live status, and runs or uninstalls it. **Apps > Service Apps > Install** opens the App Store's list of service apps in your web browser.
- **Results applied to your networks.** Selection, layouts, table columns, new networks, an in-place network update, or a web page. Results are applied fail-fast, as in Cytoscape Web, and a failure says whether the service or applying its result failed.
- **Commands for agents and scripts.** Everything above is available as `serviceapps` commands, from Desktop's Command Line, over CyREST, and through the Cytoscape MCP Desktop app.

## Commands

All commands are in the `serviceapps` namespace, take named arguments, and return JSON. Over CyREST they are at `http://127.0.0.1:1234/v1/commands/serviceapps/<command>`. Each command's full description and example reply are in its command metadata, which Desktop's `help`, CyREST's `/v1/commands/swagger.json` and the MCP Desktop app show. The [user manual](docs/Manual.md#for-agents-and-scripts) walks through the agent flow.

| Command | Does |
|---|---|
| `serviceapps list` | The installed service apps: `[{name, version, url, reachable}]` |
| `serviceapps install url= [name=] [version=]` | Installs or upgrades the service app at `url`, under the name and version it reports. `name` and `version` are optional checks that the service must match |
| `serviceapps uninstall name=` | Uninstalls a service app and removes its menu entry |
| `serviceapps status name=` | Checks that the app's service answers now |
| `serviceapps describe name=` | The app's metadata and the JSON Schemas of `task call`'s `serviceInput` and `uiInput` for it |
| `serviceapps task call app= serviceInput= uiInput=` | Runs the app in the background exactly as its menu entry does; returns `{task_id}` at once. `serviceInput` and the optional `uiInput` are JSON strings |
| `serviceapps task status task_id=` | `{progress, message, status, result: null}`; `status` is `complete` only after the result is applied in Desktop |

For example, with Cytoscape running and the fixture service app started with `make fixture`:

```
curl "http://127.0.0.1:1234/v1/commands/serviceapps/install?url=http://127.0.0.1:8765/hello"
curl "http://127.0.0.1:1234/v1/commands/serviceapps/list"
```

## Settings

`serviceapps.props` in the Cytoscape configuration directory, also editable in **Edit > Preferences > Properties**:

| Property | Default | Meaning |
|---|---|---|
| `connect.timeout.ms` | 10000 | How long to wait for a service to accept a connection |
| `read.timeout.ms` | 30000 | How long to wait for a service to answer one request |
| `poll.interval.ms` | 500 | How often a run checks its task's progress |
| `run.timeout.ms` | 1800000 | How long a run may take before it is stopped (30 minutes) |

Installed apps are saved in `service-apps.json` under the app's folder in the Cytoscape configuration directory.

## Development

Java 17 and Gradle (wrapper included); `make` is the only entry point, and `make help` lists the targets.

| Target | Does |
|---|---|
| `make build` | Builds the OSGi bundle into `build/libs/` |
| `make test` | Every JVM test, including the style check (details below) |
| `make integration-test` | The `*IT` tests against Cytoscape Desktop in Docker. Needs Docker |
| `make lint` / `make lint-fix` | Checks or fixes code style (google-java-format, AOSP) |
| `make install-app` | Copies the bundle into `~/CytoscapeConfiguration/3/apps/installed`, where a running Cytoscape picks it up |
| `make fixture` | Runs the hello-world service app on `http://127.0.0.1:8765` (`PORT=n` to change) |
| `make release VERSION=x.y.z` | Builds a release jar stamped with that version |
| `make clean` | Removes the build output |

### Tests

The two test targets don't overlap: a test class named `*IT` needs Desktop and runs only in `make integration-test`; every other test runs only in `make test`.

- **`make test`** covers:
  - unit tests;
  - the client, commands and runs against the fixture service app over HTTP;
  - the dialogs (under xvfb in CI);
  - the architecture rules;
  - the bundle manifest checks.
  It also runs the result-action contract through the menu path on real networks and views.
- **`make integration-test`** builds a Docker image from `docker/desktop` (Temurin 17 JDK, Xvfb, and Cytoscape from its Linux installer), installs the app jar, and runs the `*IT` tests over CyREST against the fixture service app on the host. It runs the same result-action contract through `serviceapps task call`, with real CX Support. One container serves the whole run; when a test fails, Cytoscape's logs are printed. To pick the Cytoscape version under test (the checksum is verified when given):

  ```
  make integration-test CYTOSCAPE_INSTALLER_URL=<url of Cytoscape_x_y_z_unix.sh> CYTOSCAPE_INSTALLER_SHA256=<sha256>
  ```

  The default is the Cytoscape 3.10.5 release candidate (rc2) installer in `build.gradle`; CI sets both values in `.github/workflows/ci.yml`.

### The hello-world service app fixture

`src/fixture/java` holds a small service app that implements all six endpoints of the service app specification. It serves a catalogue of named scenarios, each at `/<scenario>`, so one server covers every case the tests need:

- `hello` and `hello-v2`: the same app, "Hello World", at versions 1.0 and 2.0;
- `param-*`: every parameter type and attribute variant;
- `input-*`: input columns and input definitions;
- `menu-*`: menu placement cases;
- `description-*`: an app that asks for its description in the dialog, and one that doesn't;
- `unsupported-*`: the spec's experimental parameter types and the `edgelist` format;
- `result-*`: one per result action, plus multi-action, unknown and undeclared actions, and results that fail while being applied;
- `failure-*`: slow, failed, never finishing, and a refused submission.

`make fixture` prints every scenario's URL. Tests use it through `FixtureServerExtension`, which injects a running `FixtureServer` with a request log.

### Releasing

Publish a GitHub release tagged `vX.Y.Z`. The release workflow runs the full suite, builds the jar with that version, checks its `Bundle-Version`, and attaches it to the release. Record the release in [CHANGELOG.md](CHANGELOG.md).
