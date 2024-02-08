# Changelog

## [Unreleased]
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
