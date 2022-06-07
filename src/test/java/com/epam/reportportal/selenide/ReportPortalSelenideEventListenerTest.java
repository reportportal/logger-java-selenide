package com.epam.reportportal.selenide;

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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.noInteractions;

public class ReportPortalSelenideEventListenerTest {

	private static final String SELENIDE_LOG_STRING = "$(\"By.xpath: //*[text()=\"Login with Google\"]\") should have(visible)";
	private static final String IMAGE = "pug/lucky.jpg";
	private static final String PAGE = "html/rp_login_page.html";
	private static final Throwable TEST_ERROR = new RuntimeException("error");
	private static final String TEST_STACK_TRACE = ExceptionUtils.getStackTrace(TEST_ERROR);

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
		runEvent(eventConsumer, logEvent, mock -> {
			mock.when(LoggingContext::context).thenReturn(context);
			doNothing().when(context).emit(logCapture.capture());
		});
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
	@SuppressWarnings({ "ResultOfMethodCallIgnored", "unchecked" })
	public void test_step_logging_failed() {
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);
		RemoteWebDriver webDriver = mock(RemoteWebDriver.class);
		byte[] image = getResource(IMAGE);
		String page = new String(getResource(PAGE), StandardCharsets.UTF_8);
		when(webDriver.getScreenshotAs(eq(OutputType.BYTES))).thenReturn(image);
		when(webDriver.getPageSource()).thenReturn(page);

		try (MockedStatic<WebDriverRunner> driverMockedStatic = Mockito.mockStatic(WebDriverRunner.class)) {
			driverMockedStatic.when(WebDriverRunner::hasWebDriverStarted).thenReturn(true);
			driverMockedStatic.when(WebDriverRunner::getWebDriver).thenReturn(webDriver);

			ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener();
			runEvent(listener::beforeEvent, logEvent);
			verify(context, noInteractions()).emit(any(Function.class));

			verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));

			when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.FAIL);
			when(logEvent.getError()).thenReturn(TEST_ERROR);

			List<Function<String, SaveLogRQ>> logs = runEventCapture(listener::afterEvent, logEvent);
			verify(stepReporter).finishPreviousStep(eq(ItemStatus.FAILED));
			assertThat(logs, hasSize(3));

			SaveLogRQ stackTraceLog = logs.get(0).apply("test");
			assertThat(stackTraceLog.getMessage(), equalTo(TEST_STACK_TRACE));
			assertThat(stackTraceLog.getLevel(), equalTo(LogLevel.ERROR.name()));

			SaveLogRQ screenshotLog = logs.get(1).apply("test");
			assertThat(screenshotLog.getLevel(), equalTo(LogLevel.INFO.name()));
			assertThat(screenshotLog.getFile(), notNullValue());
			SaveLogRQ.File file = screenshotLog.getFile();
			assertThat(file.getContent(), equalTo(image));
			assertThat(file.getContentType(), equalTo("image/png"));

			SaveLogRQ pageLog = logs.get(2).apply("test");
			assertThat(pageLog.getLevel(), equalTo(LogLevel.INFO.name()));
			assertThat(pageLog.getFile(), notNullValue());
			file = pageLog.getFile();
			assertThat(file.getContent(), equalTo(page.getBytes(StandardCharsets.UTF_8)));
			assertThat(file.getContentType(), equalTo("text/html"));
		}
	}

	@Test
	@SuppressWarnings("unchecked" )
	public void test_step_logging_failed_no_screenshots_sources() {
		LogEvent logEvent = mock(SelenideLog.class);
		when(logEvent.toString()).thenReturn(SELENIDE_LOG_STRING);
		ReportPortalSelenideEventListener listener = new ReportPortalSelenideEventListener().logScreenshots(false).logPageSources(false);
		runEvent(listener::beforeEvent, logEvent);
		verify(context, noInteractions()).emit(any(Function.class));
		verify(stepReporter).sendStep(eq(ItemStatus.INFO), eq(SELENIDE_LOG_STRING));

		when(logEvent.getStatus()).thenReturn(LogEvent.EventStatus.FAIL);
		when(logEvent.getError()).thenReturn(TEST_ERROR);

		List<Function<String, SaveLogRQ>> logs = runEventCapture(listener::afterEvent, logEvent);
		verify(stepReporter).finishPreviousStep(eq(ItemStatus.FAILED));
		assertThat(logs, hasSize(1));

		SaveLogRQ stackTraceLog = logs.get(0).apply("test");
		assertThat(stackTraceLog.getMessage(), equalTo(TEST_STACK_TRACE));
		assertThat(stackTraceLog.getLevel(), equalTo(LogLevel.ERROR.name()));
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
}
