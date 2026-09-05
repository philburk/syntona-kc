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

The design for exporting JSyn or KSyn code from Syntona is in "notes/export_source.md".