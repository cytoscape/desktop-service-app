# Desktop Service App user manual

Service apps are tools that run on a web service rather than inside Cytoscape, such as a clustering or enrichment algorithm. Cytoscape Web already runs them. The Desktop Service App lets Cytoscape Desktop install them from the App Store, run them on your networks, and apply their results, as it does with any other app.

Requirements: Cytoscape 3.10 or later. Service apps that work on whole networks also need Cytoscape's CX Support app, which is bundled with Cytoscape 3.10 and later.

- [Install the Desktop Service App](#install-the-desktop-service-app)
- [Install a service app from the App Store](#install-a-service-app-from-the-app-store)
- [Run a service app](#run-a-service-app)
- [The Service Apps manager](#the-service-apps-manager)
- [What a run does to your networks](#what-a-run-does-to-your-networks)
- [Errors and what they mean](#errors-and-what-they-mean)
- [Restarts, upgrades and uninstalling](#restarts-upgrades-and-uninstalling)
- [Settings](#settings)
- [For agents and scripts](#for-agents-and-scripts)

## Install the Desktop Service App

Install it from the Cytoscape App Store, or with **Apps > App Manager**, like any other app. Once it is installed, the **Apps > Service Apps** menu appears, with **Manage** and **Install**, even before you install any service apps.

## Install a service app from the App Store

With Cytoscape running, choose **Apps > Service Apps > Install**, which opens the App Store's list of service apps in your web browser (the App Store site set in Desktop's `appStoreDownloadSiteUrl1` property, or apps.cytoscape.org), and open a service app's page. The **Install in Cytoscape Desktop** button shows what will happen:

| Button | Meaning |
|---|---|
| **Install** | The service app isn't installed. Click to install it into the running Cytoscape. |
| **Installed** | This version is already installed. |
| **Upgrade** | An older version is installed. Click to replace it with this one. |

Two situations disable the button:

- **Cytoscape isn't running, or can't be reached.** The page shows the app's service URL to copy and a link to help with starting Cytoscape. Start Cytoscape and reload the page.
- **The Desktop Service App isn't installed.** The button is greyed out with an info icon. Its tooltip says "Desktop needs the Service App installed first to enable service app installation" and links to the Desktop Service App's App Store page. Install it, then reload the page.

Installing checks that the service answers, and installs the app under the name and version the service reports. A service app is never downloaded: Cytoscape keeps its name, version and web address, and calls the service when you run it.

## Run a service app

A service app can be run in two ways:

- **Its menu entry.** A service app that names a Desktop menu gets an entry there, at the place it asks for, such as **Apps > Clustering > Community Detection**. The menus it can use are File, Edit, View, Select, Layout, Apps, Tools and Help. A service app that names no menu, or a menu Desktop doesn't have, gets no entry; run it from the manager instead.
- **A double click on its row** in the [Service Apps manager](#the-service-apps-manager).

Both open the same dialog:

- **Title and details.** The app's name and version are in the title. Its citation, and its description if the app asks for it, are shown at the top.
- **One field per input**, built from the app's own definition: text boxes, drop-down lists, radio buttons, checkboxes, and column pickers that list only the columns of the right kind and type in your network.
- **Validation as you type.** A value the app doesn't accept is marked with the app's own help text as you type. Clicking **OK** while a value is invalid shows the problem and keeps the dialog open.
- **Remembered values.** The dialog starts with the values you used last time with that app. After an upgrade to a different version, whether installed or found when Cytoscape starts, it starts from the app's defaults again.
- **A reminder.** Results are applied to your network and can't be undone.

The dialog appears even for an app with no inputs, so you always see what is about to run.

Click **OK** and the network you were looking at is sent to the service: the whole network, or only the selected nodes and edges when the app works on a selection. The progress bar follows the service. When the service is done, its result is applied to that same network, even if you've switched to another one in the meantime.

### When a menu entry is greyed out

Like Desktop's own menu entries, an entry is greyed out while what it works on is missing:

- **The app needs a selection.** An app that works on selected nodes needs at least one selected node, an app that works on selected edges needs a selected edge, and an app that works on a selected part of a network needs at least one selected node or edge.
- **No network is open** and the app works on one.

It is also greyed out when the app can't run at all. Then its tooltip ends with "Unavailable:" and the reason:

- **The service can't be reached.** Check your connection, then open the manager, which checks every service again.
- **Cytoscape Desktop can't run this app.** It uses an input type Desktop doesn't support yet (a file, an access token or an NDEx network ID), asks for its network in a format Desktop doesn't send, or needs the CX Support app.

## The Service Apps manager

**Apps > Service Apps > Manage** lists every installed service app, including those without a menu entry:

| Column | Shows |
|---|---|
| **Name** | The app's name. Hover to see its version and web address. |
| **Status** | **Checking…** when the manager opens, then **Online** (bold green) or **Offline** (bold red) as each service answers or doesn't. |

- **Double-click** an app's row to run it, opening the same dialog as its menu entry. The row turns light blue until the dialog closes. A double click does nothing while the app is **Checking…** or **Offline**, or when Desktop can't run it; hover over the name to see why.
- **Click** a row to highlight it in light red (the highlight waits a moment, so a double click never flashes red), then click **Uninstall** beside **Close** to remove that app. You are asked "Uninstall <name>? Are you sure?"; **Cancel** leaves everything as it was. **Uninstall** stays greyed out until a row is highlighted, and clicking anywhere else in the manager clears the highlight.

The manager stays open while you work and updates on its own when apps are installed or removed elsewhere, such as from the App Store page. Close it with **Close**, the window's close button, or Escape. With no service apps installed, it says **No Service Apps installed**, with a link that opens the App Store's service apps page in your web browser so you can choose one to install: [apps.cytoscape.org/apps/platform/service/](https://apps.cytoscape.org/apps/platform/service/), or the same page on the App Store site set in Desktop's `appStoreDownloadSiteUrl1` property (**Edit > Preferences > Properties**, `cytoscape3`), when that is set. If you already know a service app's URL, it also shows the command to install it from the Command Line (**View > Show Command Panel**): `serviceapps install url=<service URL>`.

## What a run does to your networks

A service app's result is a list of actions, applied in order:

| Action | What happens in Desktop |
|---|---|
| Update selection | Exactly the listed nodes and edges become selected; everything else is deselected. |
| Update layouts | The listed nodes move. Without a particular view, they move in every view of the network. |
| Update tables | Node or edge columns are added or filled in. Rows for IDs that aren't in the network are skipped and counted. |
| Update network | The network is updated in place, keeping its identity, views and style: attributes are updated, new nodes and edges are added, and nodes and edges that were sent but left out of the result are removed. A run on a selection never removes anything outside it. |
| Add networks | Each returned network opens as a new network collection with a view. The first becomes the current network. |
| Open URL | The address opens in your web browser. |
| Add tables | Not supported yet. It is skipped, with a note. |

Results can't be undone. Save your session first if you may want to go back.

## Errors and what they mean

A run either completes or fails. When it completes, the task history shows what was applied, plus any notes, such as rows that were skipped. When it fails, Cytoscape shows an error, and the message starts with one of these:

- **Service task failed:** the service refused the task, reported a failure, stopped answering, or took longer than the time limit (30 minutes by default). Nothing was applied.
- **Rendering failed:** the result couldn't be applied. As in Cytoscape Web, the first action that can't be applied stops the run. Actions before it stay applied and later ones aren't, and the message lists both. For example:
  `Rendering failed at updateTables (2 of 3): updateTables column 'score' is integer in Desktop but string in the result. Applied: updateLayouts. Not applied: updateSelection.`

Common reasons a result can't be applied:

- a column already exists in your network with a different type than the result uses;
- the result is malformed or uses a type Desktop doesn't know;
- it asks to change a network when none was given;
- it asks to open a web page somewhere other than your browser;
- no web browser could be opened.

Updating an HCX (hierarchy) network in place isn't supported yet; that action is skipped with a note, as in Cytoscape Web.

## Restarts, upgrades and uninstalling

- **Restarts.** Installed service apps are kept across restarts, with their menu entries. Each time Cytoscape starts, the app asks every service for its latest details in the background, so menus appear even when you're offline.
- **Upgrades.** An app whose service now reports a different version is upgraded in place.
- **Uninstalling.** Uninstall from the manager, or with `serviceapps uninstall`, removes the app and its menu entry and stops its runs in progress.

## Settings

**Edit > Preferences > Properties**, in the `serviceapps` group:

| Property | Default | Meaning |
|---|---|---|
| `connect.timeout.ms` | 10000 | How long to wait for a service to accept a connection |
| `read.timeout.ms` | 30000 | How long to wait for a service to answer one request |
| `poll.interval.ms` | 500 | How often a run checks its task's progress |
| `run.timeout.ms` | 1800000 | How long a run may take before it is stopped |

## For agents and scripts

Every feature above is also available as commands in the `serviceapps` namespace. You can use them from Desktop's Command Line, from scripts over CyREST at `http://127.0.0.1:1234/v1/commands/serviceapps/<command>`, and from AI agents through the Cytoscape MCP Desktop app. Commands take named arguments (`name=value`); Desktop has no positional arguments.

| Command | Does |
|---|---|
| `serviceapps list` | The installed service apps: name, version, web address, and whether the service last answered |
| `serviceapps install url= [name=] [version=]` | Installs or upgrades the service app at `url`, under the name and version it reports. `name` and `version` are optional checks: when given, the install is refused if the service reports something else |
| `serviceapps uninstall name=` | Uninstalls a service app |
| `serviceapps status name=` | Checks that a service answers now |
| `serviceapps describe name=` | An app's metadata and the JSON Schemas of what to pass to `task call` |
| `serviceapps task call app= serviceInput= uiInput=` | Runs an app in the background, as its menu entry does, and returns a `task_id` |
| `serviceapps task status task_id=` | The run's state: `{progress, message, status, result: null}` |

Each command's own description, arguments and example reply are in its command metadata. Desktop's `help serviceapps <command>`, CyREST's command documentation (`/v1/commands/swagger.json`) and the MCP Desktop app's command search all show it.

### Running a service app as an agent

1. **Find the app.** `serviceapps list` lists the installed apps.
2. **Learn its inputs.** `serviceapps describe name=<app>` returns two JSON Schemas, one for each JSON argument of `task call`:
   - **`serviceInputSchema`**, for `serviceInput`. Parameters are keyed by their display names, and input columns by their names. It gives each value's type, allowed values, pattern, bounds, default and description. Column pickers say which table and column type they need; the columns come from the network the run targets.
   - **`uiInputSchema`**, for `uiInput`.
3. **Start the run.** Call `serviceapps task call app=<app> serviceInput=<JSON> uiInput=<JSON>`.
   - **`serviceInput` (optional when every value has a default).** For example, `{"parameters":{"Resolution":0.5},"inputColumns":{"Gene names":"name"}}`. Omitted values take their defaults, and unknown names are rejected. Values are checked with the dialog's own rules, and every problem names its field.
   - **`uiInput` (optional).** For example, `{"networkId":52}`.
     - `networkId` is a network SUID, by default the active network. The input data is read from it, and the results are applied to it.
     - `networkViewId` is a view SUID, by default every view of that network. Given alone, it also selects its network.
     - An ID that doesn't exist is rejected before anything is sent.
   - **Pass both as JSON strings, not objects.** An object sent in their place is rejected with a message saying so.
   - **The reply** is `{"task_id": "…"}`, returned at once.
4. **Wait for it to finish.** Poll `serviceapps task status task_id=<task_id>` until `status` is `complete` or `failed`.
   - `complete` is reported only after the result has been applied in Desktop.
   - `message` says what was applied, or why the run failed, with the same messages as [above](#errors-and-what-they-mean).
   - Desktop keeps the last 100 runs.

For example, with curl:

```
curl -X POST -H 'Content-Type: application/json' \
  -d '{"name":"Community Detection"}' \
  http://127.0.0.1:1234/v1/commands/serviceapps/describe

curl -X POST -H 'Content-Type: application/json' \
  -d '{"app":"Community Detection","serviceInput":"{\"parameters\":{\"Resolution\":0.5}}","uiInput":"{\"networkId\":52}"}' \
  "http://127.0.0.1:1234/v1/commands/serviceapps/task%20call"

curl -X POST -H 'Content-Type: application/json' \
  -d '{"task_id":"6f1c2b2e-6a8e-4f0b-9a55-2b0c1f9d7e41"}' \
  "http://127.0.0.1:1234/v1/commands/serviceapps/task%20status"
```

An app that can't run is rejected with the same reasons a greyed-out menu entry gives. One exception: `task call` doesn't refuse an app just because its service was unreachable the last time Cytoscape checked. If the service still doesn't answer, the run fails with `Service task failed:`.
