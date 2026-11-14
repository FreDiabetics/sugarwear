# Suite 0.7.0 integration plan

Goal: integrate all accumulated Sugarlicious changes into main with one suite version and verified local/remote gates.

Architecture: reuse the root suite version properties for every application package, including WFF. Preserve clinical contracts and existing uncommitted work. README contains only the current icon and the user's exact teaser sentence.

- [x] Centralize version 0.7.0 / code 70 in all apps; retain increasing version codes.
- [x] Replace README, ignore caches, inspect staged files for generated/private material.
- [x] Format and run architecture, unit, build, Lint, Detekt, formatting, WFF, code-free and Python/tool gates; repair findings without weakening gates.
- [x] Independent review of integration and important runtime boundaries; resolve findings.
- [ ] Commit on temporary integration branch, push, create PR to main, wait for passing CI.
- [ ] Merge passing PR, fast-forward local main, remove merged temporary branch; verify clean tree and identical local/remote main. One main worktree found; never force-remove uncommitted work.

Hardware evidence remains separate from software gates while the watch is unavailable.
