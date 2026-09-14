# Workspace Rules

## Protected Files
- **Never edit `todo.txt`** (e.g. `docs/todo.txt` or any `todo.txt` files).
- The user edits this file manually while tasks are running; do not modify,
- delete, or overwrite it under any circumstances.

## Codebase Conventions

If the user references a module by its short name, for example "envSeg module",
then look in ModuleRegistry.kt to find the short name and map it to the Kotlin class name,
for example EnvSegModule. Here is an excerpt from ModuleRegistry.kt

    control.modules.add(ModuleEntry("envSeg") { EnvSegModule() })

When the user refers to a "ksyn.view bundle" then that refers to the "view" bundle
inside the "ksyn" bundle. The bundles are like packages for modules so the user may refer to them with dot
notation like "ksyn.view" even though that string will not be found in the code.
Bundles are defined in ModuleRegistry.kt, for example:

    val ksynView = getOrCreateBundle(ksyn, "view")

The design for exporting JSyn or KSyn code from Syntona is in "notes/export_source.md".

## Instructions

When adding new modules, add a corresponding one-line help entry to the table in
ModuleRegistry.moduleDescriptions.