# Changelog

## [Unreleased]

## [5.4.0]
### Changed
- Client version updated on [5.4.3](https://github.com/reportportal/client-java/releases/tag/5.4.3), by @HardNorth
- Replace "jsr305" annotations with "jakarta.annotation-api", by @HardNorth
- Switch on use of `Instant` class instead of `Date` to get more timestamp precision, by @HardNorth
### Removed
- Java 8, 9, 10 support, by @HardNorth

## [5.3.0]
### Changed
- Client version updated on [5.3.14](https://github.com/reportportal/client-java/releases/tag/5.3.14), by @HardNorth

## [5.2.3]
### Changed
- Client version updated on [5.2.13](https://github.com/reportportal/client-java/releases/tag/5.2.13), by @HardNorth

## [5.2.2]
### Changed
- Client version updated on [5.2.11](https://github.com/reportportal/client-java/releases/tag/5.2.11), by @HardNorth
- `client-java` dependency marked as `compileOnly`, by @HardNorth
- `selenide` dependency marked as `compileOnly`, by @HardNorth

## [5.2.1]
### Changed
- Client version updated on [5.2.4](https://github.com/reportportal/client-java/releases/tag/5.2.4), by @HardNorth
- All dependencies are marked as `implementation`, by @HardNorth
### Removed
- `commons-model` dependency to rely on `clinet-java` exclusions in security fixes, by @HardNorth

## [5.2.0]
### Changed
- Client version updated on [5.2.0](https://github.com/reportportal/client-java/releases/tag/5.2.0), by @HardNorth
- Selenide version updated on 6.18.0 to address vulnerabilities, by @HardNorth
- Selenide dependency marked as `implementation` to force users specify their own selenide versions, by @HardNorth

## [5.1.4]
### Changed
- Client version updated on [5.1.22](https://github.com/reportportal/client-java/releases/tag/5.1.22), by @HardNorth

## [5.1.3]
### Changed
- Client version updated on [5.1.16](https://github.com/reportportal/client-java/releases/tag/5.1.16), by @HardNorth
- Selenide version updated on 6.12.3 to address vulnerabilities, by @HardNorth

## [5.1.2]
### Changed
- Broader catch in screenshot and page source getting methods to avoid throwing any exceptions in logger, by @HardNorth
- Constant names refactoring, by @HardNorth

## [5.1.1]
### Changed
- Selenium log types are now `text/plain`, by @HardNorth
### Removed
- Stack Trace logging in nested steps on failure, since it should be logged on higher level, by @HardNorth

## [5.1.0]
### Added
- Initial release of Selenide logger, by @HardNorth

## [5.1.0-ALPHA-1]
