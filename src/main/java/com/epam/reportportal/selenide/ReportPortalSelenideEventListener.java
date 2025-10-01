/*
 * Copyright 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.selenide;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.logevents.LogEvent;
import com.codeborne.selenide.logevents.LogEventListener;
import com.codeborne.selenide.logevents.SelenideLog;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.message.ReportPortalMessage;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.utils.files.ByteSource;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import jakarta.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;

import static java.util.Optional.ofNullable;

/**
 * Selenide step logging listener for Report Portal.
 * <p>
 * The listener listen for Selenide log events and send them to Report Portal as steps. It has ability to log screenshots and page sources
 * on failure, this is enabled by default. Also, it is possible to attach different types of WebDriver logs on failure.
 * <p>
 * Basic usage:
 * <pre>
 *     SelenideLogger.addListener("Report Portal logger", new ReportPortalSelenideEventListener());
 * </pre>
 */
public class ReportPortalSelenideEventListener implements LogEventListener {

	public static final Function<String, String> DEFAULT_STEP_NAME_CONVERTER = log -> log;

	private static final String SCREENSHOT_MESSAGE = "Screenshot";
	private static final String PAGE_SOURCE_MESSAGE = "Page source";
	private static final String SELENIUM_LOG_MESSAGE_PATTERN = "WebDriver logs of '%s' type";
	private static final String SELENIUM_SCREENSHOT_TYPE = "image/png";
	private static final String SELENIUM_PAGE_SOURCE_TYPE = "text/html";
	private static final String SELENIUM_LOG_TYPE = "text/plain";

	private final String logLevel;

	private final Function<String, String> converter;

	private final Map<String, Level> seleniumLogTypes = new HashMap<>();
	private final Set<Class<? extends LogEvent>> selenideLogTypes = new HashSet<>(Collections.singleton(SelenideLog.class));
	private boolean screenshots = true;
	private boolean pageSources = true;

	/**
	 * Create listener instance with specified log level and step name converter.
	 *
	 * @param defaultLogLevel logging level of attachments
	 * @param stepConverter   step name converter, suitable to sanitize step string from secret data
	 */
	public ReportPortalSelenideEventListener(@Nonnull LogLevel defaultLogLevel, Function<String, String> stepConverter) {
		logLevel = defaultLogLevel.name();
		converter = stepConverter;
	}

	/**
	 * Create listener instance with specified log level.
	 *
	 * @param defaultLogLevel logging level of attachments
	 */
	public ReportPortalSelenideEventListener(@Nonnull LogLevel defaultLogLevel) {
		this(defaultLogLevel, DEFAULT_STEP_NAME_CONVERTER);
	}

	/**
	 * Create listener instance with default attachment {@link LogLevel}: "INFO".
	 */
	public ReportPortalSelenideEventListener() {
		this(LogLevel.INFO);
	}

	/**
	 * Set screenshot on failure logging enable/disable. Enabled by default.
	 *
	 * @param logScreenshots use <code>false</code> to disable screenshot logging
	 * @return self instance for convenience
	 */
	public ReportPortalSelenideEventListener logScreenshots(boolean logScreenshots) {
		this.screenshots = logScreenshots;
		return this;
	}

	/**
	 * Set page sources on failure logging enable/disable. Enabled by default.
	 *
	 * @param logPageSources use <code>false</code> to disable page sources logging
	 * @return self instance for convenience
	 */
	public ReportPortalSelenideEventListener logPageSources(boolean logPageSources) {
		this.pageSources = logPageSources;
		return this;
	}

	/**
	 * Enable certain selenium log attach on failure.
	 *
	 * @param logType  a string from {@link org.openqa.selenium.logging.LogType} describing desired log type to be logged
	 * @param logLevel desired log level to see in attachment
	 * @return self instance for convenience
	 */
	public ReportPortalSelenideEventListener enableSeleniumLogs(@Nonnull String logType, @Nonnull Level logLevel) {
		seleniumLogTypes.put(logType, logLevel);
		return this;
	}

	/**
	 * Disable certain selenium log attach on failure.
	 *
	 * @param logType a string from {@link org.openqa.selenium.logging.LogType} describing desired log type to be muted
	 * @return self instance for convenience
	 */
	public ReportPortalSelenideEventListener disableSeleniumLogs(@Nonnull String logType) {
		seleniumLogTypes.remove(logType);
		return this;
	}

	/**
	 * Enable custom selenide step logging.
	 *
	 * @param selenideLogType type of selenide event to enable logging
	 * @return self instance for convenience
	 */
	public ReportPortalSelenideEventListener enableSelenideLogs(@Nonnull Class<? extends LogEvent> selenideLogType) {
		selenideLogTypes.add(selenideLogType);
		return this;
	}

	/**
	 * Disable custom selenide step logging.
	 *
	 * @param selenideLogType type of selenide event to mute
	 * @return self instance for convenience
	 */
	public ReportPortalSelenideEventListener disableSelenideLogs(@Nonnull Class<? extends LogEvent> selenideLogType) {
		selenideLogTypes.remove(selenideLogType);
		return this;
	}

	private boolean skip(LogEvent currentLog) {
		return !selenideLogTypes.contains(currentLog.getClass());
	}

	@Override
	public void beforeEvent(@Nonnull LogEvent currentLog) {
		if (skip(currentLog)) {
			return;
		}
		ofNullable(Launch.currentLaunch()).ifPresent(l -> l.getStepReporter()
				.sendStep(ItemStatus.INFO, converter.apply(currentLog.toString())));
	}

	private void attachBinary(@Nonnull String message, @Nonnull byte[] attachment, @Nonnull String type) {
		ReportPortal.emitLog(new ReportPortalMessage(ByteSource.wrap(attachment), type, message),
				logLevel,
				Instant.now()
		);
	}

	private void logScreenshot() {
		if (WebDriverRunner.hasWebDriverStarted()) {
			byte[] screenshot;
			try {
				if ((screenshot = ((TakesScreenshot) WebDriverRunner.getWebDriver()).getScreenshotAs(OutputType.BYTES)) != null) {
					attachBinary(SCREENSHOT_MESSAGE, screenshot, SELENIUM_SCREENSHOT_TYPE);
				}
			} catch (Exception e) {
				ReportPortal.emitLog("Unable to get WebDriver screenshot: " + e.getMessage(),
						LogLevel.ERROR.name(),
						Instant.now()
				);
			}
		}
	}

	private void logPageSource() {
		if (WebDriverRunner.hasWebDriverStarted()) {
			String pageSource;
			try {
				if ((pageSource = WebDriverRunner.getWebDriver().getPageSource()) != null) {
					attachBinary(PAGE_SOURCE_MESSAGE, pageSource.getBytes(StandardCharsets.UTF_8), SELENIUM_PAGE_SOURCE_TYPE);
				}
			} catch (Exception e) {
				ReportPortal.emitLog("Unable to get WebDriver page source: " + e.getMessage(),
						LogLevel.ERROR.name(),
						Instant.now()
				);
			}
		}
	}

	private static String getBrowserLogs(@Nonnull String logType, @Nonnull Level level) {
		return String.join("\n\n", Selenide.getWebDriverLogs(logType, level));
	}

	@Override
	public void afterEvent(@Nonnull LogEvent currentLog) {
		if (skip(currentLog)) {
			return;
		}
		if (LogEvent.EventStatus.FAIL.equals(currentLog.getStatus())) {
			if (screenshots) {
				logScreenshot();
			}
			if (pageSources) {
				logPageSource();
			}
			seleniumLogTypes.forEach((k, v) -> {
				String logs = getBrowserLogs(k, v);
				attachBinary(String.format(SELENIUM_LOG_MESSAGE_PATTERN, k), logs.getBytes(StandardCharsets.UTF_8), SELENIUM_LOG_TYPE);
			});
			ofNullable(Launch.currentLaunch()).ifPresent(l -> l.getStepReporter().finishPreviousStep(ItemStatus.FAILED));
		} else if (LogEvent.EventStatus.PASS.equals(currentLog.getStatus())) {
			ofNullable(Launch.currentLaunch()).ifPresent(l -> l.getStepReporter().finishPreviousStep());
		} else {
			ReportPortal.emitLog("Unable to process selenide event status, skipping it: " + currentLog.getStatus(),
					LogLevel.WARN.name(),
					Instant.now()
			);
			ofNullable(Launch.currentLaunch()).ifPresent(l -> l.getStepReporter().finishPreviousStep(ItemStatus.WARN));
		}
	}
}
