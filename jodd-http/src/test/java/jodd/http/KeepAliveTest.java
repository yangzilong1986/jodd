// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.http;

import jodd.io.StringInputStream;
import jodd.io.StringOutputStream;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class KeepAliveTest {

	private static final String[] RESPONSES = new String[] {
			"HTTP/1.1 200 OK\r\n" +
			"Content-Type: text/html; charset=utf-8\r\n" +
			"Content-Length: 13\r\n" +
			"Connection: Keep-Alive\r\n" +
			"Keep-Alive: timeout=100, max=2\r\n" +
			"\r\n" +
			"<html></html>",

			"HTTP/1.1 200 OK\r\n" +
			"Content-Type: text/html; charset=utf-8\r\n" +
			"Content-Length: 13\r\n" +
			"Connection: Keep-Alive\r\n" +
			"Keep-Alive: timeout=100, max=1\r\n" +
			"\r\n" +
			"<html></html>",

			"HTTP/1.1 200 OK\r\n" +
			"Content-Type: text/html; charset=utf-8\r\n" +
			"Content-Length: 13\r\n" +
			"Connection: Close\r\n" +
			"\r\n" +
			"<html></html>"
	};

	private static int currentResponse;

	HttpConnectionProvider httpConnectionProvider = new HttpConnectionProvider() {
		public void useProxy(ProxyInfo proxyInfo) {
		}

		public HttpConnection createHttpConnection(HttpRequest httpRequest) throws IOException {
			return new HttpConnection() {
				public OutputStream getOutputStream() throws IOException {
					return new StringOutputStream();
				}

				public InputStream getInputStream() throws IOException {
					return new StringInputStream(RESPONSES[currentResponse], StringInputStream.Mode.ASCII);
				}

				public void close() {
				}
			};
		}
	};

	@Test
	public void testKeepAlive() {
		currentResponse = 0;

		// ->
		HttpRequest request = HttpRequest.get("http://jodd.org");
		request.connectionKeepAlive(true);

		// <-
		HttpResponse response = request.open(httpConnectionProvider).send();
		HttpConnection connection = request.httpConnection();

		assertTrue(request.connectionKeepAlive());
		assertEquals(2, response.keepAliveMax());
		assertTrue(response.connectionKeepAlive());
		assertNotNull(request.httpConnection());

		currentResponse = 1;

		// ->
		request = HttpRequest.get("http://jodd.org");
		response = request.keepAliveContinue(response).send();

		// <-
		assertSame(connection, request.httpConnection());
		assertTrue(request.connectionKeepAlive());
		assertEquals(1, response.keepAliveMax());
		assertTrue(response.connectionKeepAlive());
		assertNotNull(request.httpConnection());

		currentResponse = 2;

		// -> LAST request
		request = HttpRequest.get("http://jodd.org");
		response = request.keepAliveContinue(response).send();

		// <-
		assertNull(request.httpConnection()); // connection is closed
		assertFalse(request.connectionKeepAlive());
		assertEquals("Close", request.header("Connection"));
		assertEquals(-1, response.keepAliveMax());
		assertFalse(response.connectionKeepAlive());

		currentResponse = 0;

		// -> AFTER THE LAST, STARTS EVERYTHING AGAIN

		request = HttpRequest.get("http://jodd.org");
		response = request.keepAliveContinue(response).send();

		// <-
		assertTrue(request.connectionKeepAlive());
		assertEquals(2, response.keepAliveMax());
		assertTrue(response.connectionKeepAlive());
		assertNotNull(request.httpConnection());

		// CLOSE

		response.close();
		assertNull(request.httpConnection());	// connection closed
	}

	@Test
	public void testKeepAliveBrowser() {
		HttpBrowser browser = new HttpBrowser();
		browser.setKeepAlive(true);
		browser.setHttpConnectionProvider(httpConnectionProvider);

		currentResponse = 0;

		// ->
		HttpRequest request = HttpRequest.get("http://jodd.org");
		browser.sendRequest(request);

		// <-
		HttpResponse response = browser.getHttpResponse();
		HttpConnection connection = request.httpConnection();

		assertTrue(request.connectionKeepAlive());
		assertEquals(2, response.keepAliveMax());
		assertTrue(response.connectionKeepAlive());
		assertNotNull(request.httpConnection());

		currentResponse = 1;

		// ->
		request = HttpRequest.get("http://jodd.org");
		response = browser.sendRequest(request);

		// <-
		assertSame(connection, request.httpConnection());
		assertTrue(request.connectionKeepAlive());
		assertEquals(1, response.keepAliveMax());
		assertTrue(response.connectionKeepAlive());
		assertNotNull(request.httpConnection());

		currentResponse = 2;

		// -> LAST request
		request = HttpRequest.get("http://jodd.org");
		response = browser.sendRequest(request);

		// <-
		assertNull(request.httpConnection()); // connection is closed
		assertFalse(request.connectionKeepAlive());
		assertEquals("Close", request.header("Connection"));
		assertEquals(-1, response.keepAliveMax());
		assertFalse(response.connectionKeepAlive());

		currentResponse = 0;

		// -> AFTER THE LAST, STARTS EVERYTHING AGAIN

		request = HttpRequest.get("http://jodd.org");
		response = browser.sendRequest(request);

		// <-
		assertTrue(request.connectionKeepAlive());
		assertEquals(2, response.keepAliveMax());
		assertTrue(response.connectionKeepAlive());
		assertNotNull(request.httpConnection());

		// CLOSE

		browser.close();
		assertNull(request.httpConnection());	// connection closed
	}
}