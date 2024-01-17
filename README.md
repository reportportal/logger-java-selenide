# Report Portal logger for Selenide

[![Maven Central](https://img.shields.io/maven-central/v/com.epam.reportportal/logger-java-selenide.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.epam.reportportal/logger-java-selenide)
[![CI Build](https://github.com/reportportal/logger-java-selenide/actions/workflows/ci.yml/badge.svg)](https://github.com/reportportal/agent-java-selenide/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/reportportal/logger-java-selenide/branch/develop/graph/badge.svg?token=auT9sla0dF)](https://codecov.io/gh/reportportal/logger-java-selenide)
[![Join Slack chat!](https://slack.epmrpp.reportportal.io/badge.svg)](https://slack.epmrpp.reportportal.io/)
[![stackoverflow](https://img.shields.io/badge/reportportal-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/reportportal)
[![Build with Love](https://img.shields.io/badge/build%20with-❤%EF%B8%8F%E2%80%8D-lightgrey.svg)](http://reportportal.io?style=flat)

The latest version: 5.2.0. Please use `Maven Central` link above to get the agent.

## Overview

Selenide step logging listener for Report Portal. The listener listen for Selenide log events and send them to Report Portal as steps.
It has ability to log screenshots and page sources on failure, this is enabled by default. Also, it is possible to attach different types of
WebDriver logs on failure.

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
            <version>5.2.0</version>
        </dependency>
    </dependencies>

    <!-- build config omitted -->
</project>
```

#### Gradle

`build.gradle`

```groovy
dependencies {
    testCompile 'com.epam.reportportal:logger-java-selenide:5.2.0'
}
```

### Selenide configuration

To start getting Selenide step logging in Report Portal you need to add the logger to `SelenideLogger` listeners. The best
place for it is one which will be initialized at the earliest moment once during the test execution. E.G. a static initialization block in a
base class for all tests:

```java
public class BaseTest {
	static {
		SelenideLogger.addListener("Report Portal logger", new ReportPortalSelenideEventListener());
	}
}
```

### Logger configuration

#### Screenshots and page sources

The logger has screenshots and page sources logging enabled by default. It logs them inside the step on every failure. To disable / enable
this behavior we have separate setters methods in the logger.
E.G.:
```java
public class BaseTest {
	static {
		SelenideLogger.addListener("Report Portal logger", 
                new ReportPortalSelenideEventListener().logScreenshots(false).logPageSources(false));
	}
}
```
This disables both: screenshot and page sources logging.

#### Selenium logs

The logger can also attach Selenium logs on step failure. To enable it you need to call specific setter method inside the listener and
bypass desired log type and level:
```java
public class BaseTest {
	static {
		SelenideLogger.addListener("Report Portal logger",
				new ReportPortalSelenideEventListener().enableSeleniumLogs(LogType.BROWSER, Level.FINER));
	}
}
```

#### Step name sanitizing

If you need to hide some secret data from you step logs you can do this by specifying step name converter in logger constructor.
```java
public class BaseTest {
	static {
		SelenideLogger.addListener("Report Portal logger", 
                new ReportPortalSelenideEventListener(LogLevel.INFO, l -> l.replaceAll("secret_token=[^&]*", "secret_token=<removed>")));
	}
}
```

