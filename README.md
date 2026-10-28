# ImageMOTD

[![Java](https://img.shields.io/badge/Java-17-green.svg)](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
[![Velocity 4.x](https://img.shields.io/badge/Velocity-4.x-blue.svg)]()

[Preview of the MOTD](image.png)

An image based MOTD (Message of the Day) generator for Minecraft server plugins using [Mineskin](https://mineskin.org/) and [Velocity](https://velocitypowered.com/), with an API available as commands for developers.

- **Version:** 1.1.0
- **Main class:** `me.nos1qnal.imagemotd.q`
- **API Velocity 4.x**
- **Build via Gradle (`build.gradle`) — Java 17**

## Commands

| Command | Description |
| --- | --- |
| `/imagemotd generate` | Generate and cache the MOTD image list from the provided image |
| `/imagemotd status` | Show the current cached state / image URL |
| `/imagemotd reload` | Reload sprites / cache from the txt file |

Usage examples:
```
/imagemotd status
/imagemotd generate --image=image.png
/imagemotd reload
```

## Build

The project is built with standard Maven/Gradle `java` module setup on **Java 17**:
```bash
./gradlew build
# or, if you are not in a git worktree:
cd src && ./gradlew build
```
Run the resulting artifact through your launcher or Velocity API client that uses
the `me.nos1qnal.imagemotd.q` main class.

## Repository

This repository is mirrored at: https://github.com/CyberguySilent/ImageMOTD-Velocity

## License

GPL-3.0 (see LICENSE)
