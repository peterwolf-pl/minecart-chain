package com.piotrek.minecartchain.client;

/** Detects two separate presses; holding a key is never a double tap. */
final class DoubleTapKey {
	private final long windowMillis;
	private boolean wasDown;
	private long firstPress = -1L;

	DoubleTapKey(final long windowMillis) {
		this.windowMillis = windowMillis;
	}

	boolean update(final boolean down, final long nowMillis) {
		boolean pressed = down && !this.wasDown;
		this.wasDown = down;
		if (!pressed) {
			return false;
		}
		if (this.firstPress >= 0L && nowMillis >= this.firstPress
			&& nowMillis - this.firstPress <= this.windowMillis) {
			this.firstPress = -1L;
			return true;
		}
		this.firstPress = nowMillis;
		return false;
	}

	void reset() {
		this.wasDown = false;
		this.firstPress = -1L;
	}
}
