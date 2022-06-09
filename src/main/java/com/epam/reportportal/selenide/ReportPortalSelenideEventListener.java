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
import com.google.common.io.ByteSource;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;

import static java.util.Optional.ofNullable;

public class ReportPortalSelenideEventListener implements LogEventListener {

	public static final String UNABLE_TO_PROCESS_SELENIDE_EVENT_STATUS = "Unable to process selenide event status, skipping it: ";
	public static final Function<String, String> DEFAULT_LOG_CONVERTER = log -> log;

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

	public ReportPortalSelenideEventListener(@Nonnull LogLevel defaultLogLevel, Function<String, String> stepConverter) {
		logLevel = defaultLogLevel.name();
		converter = stepConverter;
	}

	public ReportPortalSelenideEventListener(@Nonnull LogLevel defaultLogLevel) {
		this(defaultLogLevel, DEFAULT_LOG_CONVERTER);
	}

	public ReportPortalSelenideEventListener() {
		this(LogLevel.INFO);
	}

	public ReportPortalSelenideEventListener logScreenshots(boolean logScreenshots) {
		this.screenshots = logScreenshots;
		return this;
	}

	public ReportPortalSelenideEventListener logPageSources(boolean logPageSources) {
		this.pageSources = logPageSources;
		return this;
	}

	public ReportPortalSelenideEventListener enableSeleniumLogs(@Nonnull String logType, @Nonnull Level logLevel) {
		seleniumLogTypes.put(logType, logLevel);
		return this;
	}

	public ReportPortalSelenideEventListener disableSeleniumLogs(@Nonnull String logType) {
		seleniumLogTypes.remove(logType);
		return this;
	}

	public ReportPortalSelenideEventListener enableSelenideLogs(@Nonnull Class<? extends LogEvent> selenideLogType) {
		selenideLogTypes.add(selenideLogType);
		return this;
	}

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
				Calendar.getInstance().getTime()
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
						Calendar.getInstance().getTime()
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
						Calendar.getInstance().getTime()
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
			ReportPortal.emitLog(UNABLE_TO_PROCESS_SELENIDE_EVENT_STATUS + currentLog.getStatus(),
					LogLevel.WARN.name(),
					Calendar.getInstance().getTime()
			);
			ofNullable(Launch.currentLaunch()).ifPresent(l -> l.getStepReporter().finishPreviousStep(ItemStatus.WARN));
		}
	}
}
