# Report Portal logger for Selenide

[![Maven Central](https://img.shields.io/maven-central/v/com.epam.reportportal/logger-java-selenide.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.epam.reportportal%22%20AND%20a:%22logger-java-selenide%22)
[![CI Build](https://github.com/reportportal/logger-java-selenide/actions/workflows/ci.yml/badge.svg)](https://github.com/reportportal/agent-java-selenide/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/reportportal/logger-java-selenide/branch/develop/graph/badge.svg?token=W3MTDF607A)](https://codecov.io/gh/reportportal/logger-java-selenide)
[![Join Slack chat!](https://reportportal-slack-auto.herokuapp.com/badge.svg)](https://reportportal-slack-auto.herokuapp.com)
[![stackoverflow](https://img.shields.io/badge/reportportal-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/reportportal)
[![Build with Love](https://img.shields.io/badge/build%20with-❤%EF%B8%8F%E2%80%8D-lightgrey.svg)](http://reportportal.io?style=flat)

The latest version: $LATEST_VERSION. Please use `Maven Central` link above to get the agent.

## Overview

Selenide step logger for Report Portal

Description TBD

## Configuration

### Build system configuration

You need to add the logger as one of your dependencies in Maven or Gradle.

#### Maven

`pom.xml`

```xml

<project>
    <!-- project declaration omitted -->

    <dependencies>
        <dependency>
            <groupId>com.epam.reportportal</groupId>
            <artifactId>logger-java-selenide</artifactId>
            <version>$LATEST_VERSION</version>
        </dependency>
    </dependencies>

    <!-- build config omitted -->
</project>
```

#### Gradle

`build.gradle`

```groovy
dependencies {
    testCompile 'com.epam.reportportal:logger-java-selenide:$LATEST_VERSION'
}
```

### Selenide configuration