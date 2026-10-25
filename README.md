# DSCommon

DSCommon is a library and Play Framework plugin for Play 1.x applications (Play 1.2 and later). It provides reusable document and workflow functionality, including data modeling, persistence, access control, HTTP APIs, and AngularJS-based screens.

## Features

- **Declarative document workflows:** describe document types, fields, actions, roles, and messages in YAML; DSCommon loads and validates these definitions at application startup.
- **Persistence and data binding:** JPA/Hibernate document models, version/history support, and JSON binding for common and structured field types.
- **Document API:** routes for listing, exporting, reading, and acting on documents, plus file upload and download.
- **Configurable access control:** roles, states, and action rights are part of the document/workflow model.
- **AngularJS client:** reusable document forms, tables, editors, action controls, and field widgets, with CoffeeScript source and prebuilt JavaScript assets.
- **Developer tools:** in development mode, the plugin exposes document/code-generation and rights-inspection pages at `/@docflow`, as well as an SQL script page at `/@sql`.

## Repository layout

| Path | Contents |
| --- | --- |
| `app/code/` | Java plugin, document-flow engine, API, compiler, and shared utilities |
| `app/controllers/` | HTTP endpoints and development-time generators |
| `app/models/` | Built-in JPA models |
| `app/docflow/` | YAML workflow, document, and field-type definitions |
| `app/ngApp/` | AngularJS client source written in CoffeeScript |
| `conf/` | Play configuration, routes, dependencies, and messages |
| `static/` | Browser assets, including prebuilt DSCommon JavaScript |
| `build.coffee`, `gulpfile.*` | Legacy Gulp tasks for rebuilding the CoffeeScript client |

## Requirements

- Play Framework **1.2 or later**, with its `play` command available on `PATH`.
- A JDK version supported by the Play 1.x version used by your application.
- A supported database and its JDBC driver for normal development or production use. `conf/application.conf` contains an H2 in-memory configuration for the `test` mode.
- Node.js and npm only if you need to rebuild the bundled client assets.

DSCommon is designed to be used with a Play 1.x application. The checked-in configuration contains a commented-out database example rather than a default development database, so configure `db` and any related connection settings in `conf/application.conf` before starting the app outside test mode.

## Run locally

From the repository root, resolve the Play dependencies and start the application:

```sh
play deps
play run
```

The checked-in configuration sets the HTTP port to `9010`, so the app is available at <http://localhost:9010/> after startup. In development mode, open <http://localhost:9010/@docflow> for the document-flow generator and related tools. These tools are available only in development mode.

## Build the client assets

Prebuilt JavaScript is included in `static/ngApp/js/`. To regenerate it from the CoffeeScript source:

```sh
npm install
npx gulp dsc-uglify
```

The task compiles `app/ngApp/**/*.coffee`, concatenates the generated files, and writes `static/ngApp/js/dscommon.js` and `static/ngApp/js/dscommon.min.js`. The build uses an older Gulp 3 toolchain; a compatible Node.js version may be required.

## Test

Run the Play 1.x test runner from the repository root:

```sh
play test
```

Test mode is configured to use an in-memory H2 database and mock mail. The repository currently contains no project-owned `test/` directory or automated test suite, so this command starts the Play test runner but there are no DSCommon tests to execute yet.
