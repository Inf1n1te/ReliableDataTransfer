package client;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Helper utilities. Supplied for convenience.
 * 
 * @author Jaco ter Braak, Twente University
 * @version 11-01-2014
 */
public class Utils {
	private Utils() {
	}

	/**
	 * Helper class for setting timeouts. Supplied for convenience.
	 * 
	 * @author Jaco ter Braak, Twente University
	 * @version 11-01-2014
	 */
	public static class Timeout implements Runnable {
		private static Map<Date, HashMap<ITimeoutEventHandler, Object>> eventHandlers = new HashMap<Date, HashMap<ITimeoutEventHandler, Object>>();
		private static Thread eventTriggerThread;
		private static boolean started = false;
		private static ReentrantLock lock = new ReentrantLock();

		/**
		 * Starts the helper thread
		 */
		public static void Start() {
			if (started)
				throw new IllegalStateException("Already started");
			started = true;
			eventTriggerThread = new Thread(new Timeout());
			eventTriggerThread.start();
		}

		/**
		 * Stops the helper thread
		 */
		public static void Stop() {
			if (!started)
				throw new IllegalStateException(
						"Not started or already stopped");
			eventTriggerThread.interrupt();
			try {
				eventTriggerThread.join();
			} catch (InterruptedException e) {
			}
		}

		/**
		 * Set a timeout
		 * 
		 * @param millisecondsTimeout
		 *            the timeout interval, starting now
		 * @param handler
		 *            the event handler that is called once the timeout elapses
		 */
		public static void SetTimeout(long millisecondsTimeout,
				ITimeoutEventHandler handler, Object tag) {
			Date elapsedMoment = new Date();
			elapsedMoment
					.setTime(elapsedMoment.getTime() + millisecondsTimeout);

			lock.lock();
			if (!eventHandlers.containsKey(elapsedMoment)) {
				eventHandlers.put(elapsedMoment,
						new HashMap<ITimeoutEventHandler, Object>());
			}
			eventHandlers.get(elapsedMoment).put(handler, tag);
			lock.unlock();
		}

		/**
		 * Do not call this
		 */
		@Override
		public void run() {
			boolean runThread = true;
			ArrayList<Date> datesToRemove = new ArrayList<Date>();
			HashMap<ITimeoutEventHandler, Object> handlersToInvoke = new HashMap<ITimeoutEventHandler, Object>();
			Date now;
			
			while (runThread) {
				try {
					now = new Date();

					// If any timeouts have elapsed, trigger their handlers
					lock.lock();

					for (Date date : eventHandlers.keySet()) {
						if (date.before(now)) {
							datesToRemove.add(date);
							for (ITimeoutEventHandler handler : eventHandlers
									.get(date).keySet()) {
								handlersToInvoke.put(handler, eventHandlers
										.get(date).get(handler));
							}
						}
					}

					// Remove elapsed events
					for (Date date : datesToRemove) {
						eventHandlers.remove(date);
					}
					datesToRemove.clear();

					lock.unlock();

					// Invoke the event handlers outside of the lock, to prevent
					// deadlocks
					for (ITimeoutEventHandler handler : handlersToInvoke
							.keySet()) {
						handler.TimeoutElapsed(handlersToInvoke.get(handler));
					}
					handlersToInvoke.clear();

					Thread.sleep(1);
				} catch (InterruptedException e) {
					runThread = false;
				}
			}

		}
	}
}
