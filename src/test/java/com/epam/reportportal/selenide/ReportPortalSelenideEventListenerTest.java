package com.epam.reportportal.selenide;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.logevents.LogEvent;
import com.codeborne.selenide.logevents.SelenideLog;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.LoggingContext;
import com.epam.reportportal.service.step.StepReporter;
import com.epam.reportportal.utils.files.Utils;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import static java.util.Optional.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;
import static org.mockito.internal.verification.VerificationModeFactory.noInteractions;

public class ReportPortalSelenideEventListenerTest {

	private static final String BROWSER_LOG = "[2022-06-08T15:34:59.131Z] [SEVERE] https://www.example.com/favicon.ico - Failed to load resource: the server responded with a status of 404 ()";

	private static final String SELENIDE_LOG_STRING = "$(\"By.xpath: //*[text()=\"Login with Google\"]\") should have(visible)";
	private static final String IMAGE = "pug/lucky.jpg";
	private static final String PAGE = "html/rp_login_page.html";

	@Mock
	private Launch launch;
	@Mock
	private StepReporter stepReporter;
	@Mock
	private LoggingContext context;

	@BeforeEach
	public void setup() {
		when(launch.getStepReporter()).thenReturn(stepReporter);
	}

	private void runEvent(Consumer<LogEvent> eventConsumer, LogEvent logEvent, Consumer<MockedStatic<LoggingContext>> logMocks) {
		try (MockedStatic<Launch> launchMockedStatic = Mockito.mockStatic(Launch.class)) {
			launchMockedStatic.when(Launch::currentLaunch).thenReturn(launch);
			try (MockedStatic<LoggingContext> utilities = Mockito.mockStatic(LoggingContext.class)) {
				logMocks.accept(utilities);
				eventConsumer.accept(logEvent);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private List<Function<String, SaveLogRQ>> runEventCapture(Consumer<LogEvent> eventConsumer, LogEvent logEvent) {
		ArgumentCaptor<Function<String, SaveLogRQ>> logCapture = ArgumentCaptor.forClass(Function.class);
		runEvent(
				eventConsumer, logEvent, mock -> {
					mock.when(LoggingContext::context).thenReturn(context);
					doNothing().when(context).emit(logCapture.capture());
				}
		);
		return logCapture.getAllValues();
	}

	private void runEvent(Consumer<LogEvent> eventConsumer, LogEvent logEvent) {
		runEvent(eventConsumer, logEvent, mock -> mock.when(LoggingContext::context).thenReturn(context));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void test_step_logging_default() {
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);

		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener();
		runEvent(listener::beforeEvent, logEvent);

		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));
		verify(context, noInteractions()).emit(any(Function.class));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.PASS);
		runEvent(listener::afterEvent, logEvent);

		verify(stepReporter).finishPreviousStep();
		verify(context, noInteractions()).emit(any(Function.class));
	}

	private byte[] getResource(String imagePath) {
		return ofNullable(this.getClass().getClassLoader().getResourceAsStream(imagePath)).map(is -> {
			try {
				return Utils.readInputStreamToBytes(is);
			} catch (IOException e) {
				return null;
			}
		}).orElse(null);
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	public void test_step_logging_failed() {
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);
		byte[] image = getResource(IMAGE);
		String page = new String(getResource(PAGE), StandardCharsets.UTF_8);
		RemoteWebDriver webDriver = mock(RemoteWebDriver.class);
		when(webDriver.getScreenshotAs(eq(OutputType.BYTES))).thenReturn(image);
		when(webDriver.getPageSource()).thenReturn(page);

		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener();
		runEvent(listener::beforeEvent, logEvent);
		verify(context, noInteractions()).emit(any(Function.class));
		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.FAIL);

		try (MockedStatic<WebDriverRunner> driverMockedStatic = Mockito.mockStatic(WebDriverRunner.class)) {
			driverMockedStatic.when(WebDriverRunner::hasWebDriverStarted).thenReturn(true);
			driverMockedStatic.when(WebDriverRunner::getWebDriver).thenReturn(webDriver);

			List<Function<String, SaveLogRQ>> logs = runEventCapture(listener::afterEvent, logEvent);
			verify(stepReporter).finishPreviousStep(eq(ItemStatus.FAILED));
			assertThat(logs, hasSize(2));

			SaveLogRQ screenshotLog = logs.get(0).apply("test");
			assertThat(screenshotLog.getLevel(), equalTo(LogLevel.INFO.name()));
			assertThat(screenshotLog.getFile(), notNullValue());
			SaveLogRQ.File file = screenshotLog.getFile();
			assertThat(file.getContent(), equalTo(image));
			assertThat(file.getContentType(), equalTo("image/png"));

			SaveLogRQ pageLog = logs.get(1).apply("test");
			assertThat(pageLog.getLevel(), equalTo(LogLevel.INFO.name()));
			assertThat(pageLog.getFile(), notNullValue());
			file = pageLog.getFile();
			assertThat(file.getContent(), equalTo(page.getBytes(StandardCharsets.UTF_8)));
			assertThat(file.getContentType(), equalTo("text/html"));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void test_step_logging_failed_no_screenshots_sources() {
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);
		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener().logScreenshots(false).logPageSources(false);
		runEvent(listener::beforeEvent, logEvent);
		verify(context, noInteractions()).emit(any(Function.class));
		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.FAIL);

		runEvent(listener::afterEvent, logEvent);
		verify(stepReporter).finishPreviousStep(eq(ItemStatus.FAILED));
		verify(context, noInteractions()).emit(any(Function.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void test_step_logging_invalid_status() {
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);

		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener();
		runEvent(listener::beforeEvent, logEvent);

		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));
		verify(context, noInteractions()).emit(any(Function.class));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.IN_PROGRESS);
		List<Function<String, SaveLogRQ>> logs = runEventCapture(listener::afterEvent, logEvent);
		verify(stepReporter).finishPreviousStep(eq(ItemStatus.WARN));
		assertThat(logs, hasSize(1));

		SaveLogRQ warningLog = logs.get(0).apply("test");
		assertThat(warningLog.getLevel(), equalTo(LogLevel.WARN.name()));
		assertThat(warningLog.getFile(), nullValue());
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	public void test_step_logging_failed_browser_logs() {
		String logType = LogType.BROWSER;
		Level logLevel = Level.FINER;
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);
		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener().logScreenshots(false)
				.logPageSources(false)
				.enableSeleniumLogs(logType, logLevel);
		runEvent(listener::beforeEvent, logEvent);
		verify(context, noInteractions()).emit(any(Function.class));
		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.FAIL);

		RemoteWebDriver webDriver = mock(RemoteWebDriver.class);
		try (MockedStatic<WebDriverRunner> driverMockedStatic = Mockito.mockStatic(WebDriverRunner.class)) {
			driverMockedStatic.when(WebDriverRunner::hasWebDriverStarted).thenReturn(true);
			driverMockedStatic.when(WebDriverRunner::getWebDriver).thenReturn(webDriver);

			try (MockedStatic<Selenide> selenideMockedStatic = Mockito.mockStatic(Selenide.class)) {
				selenideMockedStatic.when(() -> Selenide.getWebDriverLogs(same(logType), same(logLevel)))
						.thenReturn(Collections.singletonList(BROWSER_LOG));
				List<Function<String, SaveLogRQ>> logRequests = runEventCapture(listener::afterEvent, logEvent);
				verify(stepReporter).finishPreviousStep(eq(ItemStatus.FAILED));
				assertThat(logRequests, hasSize(1));

				SaveLogRQ browserLog = logRequests.get(0).apply("test");
				assertThat(browserLog.getMessage(), equalTo("WebDriver logs of 'browser' type"));
				assertThat(browserLog.getLevel(), equalTo(LogLevel.INFO.name()));
				assertThat(browserLog.getFile().getContentType(), equalTo("text/plain"));
				assertThat(browserLog.getFile().getContent(), equalTo(BROWSER_LOG.getBytes(StandardCharsets.UTF_8)));
			}
		}
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	public void test_step_logging_failed_screenshot_exception() {
		String exceptionMessage = "my exception message";
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);
		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener().logPageSources(false);
		runEvent(listener::beforeEvent, logEvent);
		verify(context, noInteractions()).emit(any(Function.class));
		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.FAIL);

		RemoteWebDriver webDriver = mock(RemoteWebDriver.class);
		when(webDriver.getScreenshotAs(any(OutputType.class))).thenThrow(new RuntimeException(exceptionMessage));
		try (MockedStatic<WebDriverRunner> driverMockedStatic = Mockito.mockStatic(WebDriverRunner.class)) {
			driverMockedStatic.when(WebDriverRunner::hasWebDriverStarted).thenReturn(true);
			driverMockedStatic.when(WebDriverRunner::getWebDriver).thenReturn(webDriver);

			List<Function<String, SaveLogRQ>> logs = runEventCapture(listener::afterEvent, logEvent);
			verify(stepReporter).finishPreviousStep(eq(ItemStatus.FAILED));
			assertThat(logs, hasSize(1));

			SaveLogRQ screenshotLog = logs.get(0).apply("test");
			assertThat(screenshotLog.getLevel(), equalTo(LogLevel.ERROR.name()));
			assertThat(screenshotLog.getFile(), nullValue());
			assertThat(screenshotLog.getMessage(), equalTo("Unable to get WebDriver screenshot: " + exceptionMessage));
		}
	}

	@Test
	@SuppressWarnings({ "unchecked" })
	public void test_step_logging_failed_page_source_exception() {
		String exceptionMessage = "my exception message";
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);
		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener().logScreenshots(false);
		runEvent(listener::beforeEvent, logEvent);
		verify(context, noInteractions()).emit(any(Function.class));
		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.FAIL);

		RemoteWebDriver webDriver = mock(RemoteWebDriver.class);
		when(webDriver.getPageSource()).thenThrow(new RuntimeException(exceptionMessage));
		try (MockedStatic<WebDriverRunner> driverMockedStatic = Mockito.mockStatic(WebDriverRunner.class)) {
			driverMockedStatic.when(WebDriverRunner::hasWebDriverStarted).thenReturn(true);
			driverMockedStatic.when(WebDriverRunner::getWebDriver).thenReturn(webDriver);

			List<Function<String, SaveLogRQ>> logs = runEventCapture(listener::afterEvent, logEvent);
			verify(stepReporter).finishPreviousStep(eq(ItemStatus.FAILED));
			assertThat(logs, hasSize(1));

			SaveLogRQ pageSourceLog = logs.get(0).apply("test");
			assertThat(pageSourceLog.getLevel(), equalTo(LogLevel.ERROR.name()));
			assertThat(pageSourceLog.getFile(), nullValue());
			assertThat(pageSourceLog.getMessage(), equalTo("Unable to get WebDriver page source: " + exceptionMessage));
		}
	}
}
