// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.petite.scope;

import jodd.log.Logger;
import jodd.log.LoggerFactory;
import jodd.petite.BeanData;
import jodd.petite.BeanDefinition;
import jodd.petite.PetiteException;
import jodd.servlet.RequestContextListener;
import jodd.servlet.SessionMonitor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Session scope stores unique object instances per single http session.
 * Upon creation, new session listener is registered (dynamically) that will
 * keep track on active sessions. {@link RequestContextListener} is used for accessing
 * the request and the session. {@link jodd.servlet.SessionMonitor} is used for
 * calling destroyable methods and for cleaning the sessions on the end.
 */
public class SessionScope extends ShutdownAwareScope {

	private static Logger log = LoggerFactory.getLogger(SessionScope.class);

	// ---------------------------------------------------------------- session map

	protected static final String ATTR_NAME = SessionScope.class.getName() + ".map";

	/**
	 * Returns instance map from http session.
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, BeanData> getSessionMap(HttpSession session) {
		return (Map<String, BeanData>) session.getAttribute(ATTR_NAME);
	}

	/**
	 * Removes instance map from the session.
	 */
	protected void removeSessionMap(HttpSession session) {
		session.removeAttribute(ATTR_NAME);
	}

	/**
	 * Creates instance map and stores it in the session.
	 */
	protected Map<String, BeanData> createSessionMap(HttpSession session) {
		Map<String, BeanData> map = new HashMap<String, BeanData>();
		session.setAttribute(ATTR_NAME, map);
		return map;
	}


	// ---------------------------------------------------------------- scope

	protected SessionMonitor sessionMonitor;

	protected HttpSessionListener sessionListener;

	/**
	 * Session scope.
	 */
	public SessionScope() {
		sessionMonitor = SessionMonitor.getInstance();
		if (sessionMonitor == null) {
			if (log.isWarnEnabled()) {
				log.warn("SessionMonitor not available");
			}
			sessionListener = null;
		} else {
			sessionListener = new HttpSessionListener() {
				public void sessionCreated(HttpSessionEvent se) {
					// ignore
				}

				public void sessionDestroyed(HttpSessionEvent se) {
					HttpSession httpSession = se.getSession();
					Map<String, BeanData> map = getSessionMap(httpSession);
					if (map == null) {
						return;
					}

					for (BeanData beanData : map.values()) {
						destroyBean(beanData);
					}
				}
			};

			sessionMonitor.registerListener(sessionListener);
		}
	}

	/**
	 * Shutdowns the Session scope. Does 3 things:
	 * <ul>
	 *     <li>removes session listener from <code>SessionMonitor</code></li>
	 *     <li>calls destroyable methods</li>
	 *     <li>cleans all sessions from stored beans</li>
	 * </ul>
	 */
	@Override
	public void shutdown() {
		// remove listener
		if (sessionListener != null) {
			sessionMonitor.removeListener(sessionListener);
			sessionListener = null;
		}

		// call destroyable methods
		super.shutdown();

		// clean all sessions
		if (sessionMonitor != null) {
			Iterator<String> sessions = sessionMonitor.iterator();
			while (sessions.hasNext()) {
				String sessionId = sessions.next();
				HttpSession session = sessionMonitor.getSession(sessionId);
				if (session != null) {
					removeSessionMap(session);
				}
			}
		}
	}

	public Object lookup(String name) {
		HttpSession session = getCurrentHttpSession();
		Map<String, BeanData> map = getSessionMap(session);
		if (map == null) {
			return null;
		}

		BeanData beanData = map.get(name);
		if (beanData == null) {
			return null;
		}
		return beanData.getBean();
	}

	public void register(BeanDefinition beanDefinition, Object bean) {
		HttpSession session = getCurrentHttpSession();
		Map<String, BeanData> map = getSessionMap(session);
		if (map == null) {
			map = createSessionMap(session);
		}

		BeanData beanData = new BeanData(beanDefinition, bean);
		map.put(beanDefinition.getName(), beanData);

		registerDestroyableBeans(beanData);
	}

	public void remove(String name) {
		HttpSession session = getCurrentHttpSession();
		Map<String, BeanData> map = getSessionMap(session);
		if (map != null) {
			map.remove(name);
		}
	}

	public boolean accept(Scope referenceScope) {
		Class<? extends Scope> refScopeType = referenceScope.getClass();

		if (refScopeType == SingletonScope.class) {
			return true;
		}

		if (refScopeType == SessionScope.class) {
			return true;
		}

		return false;
	}

	// ---------------------------------------------------------------- util

	/**
	 * Returns request from current thread.
	 */
	protected HttpSession getCurrentHttpSession() {
		HttpServletRequest request = RequestContextListener.getRequest();
		if (request == null) {
			throw new PetiteException("No HTTP request bound to the current thread. Is RequestContextListener registered?");
		}
		return request.getSession();
	}

}