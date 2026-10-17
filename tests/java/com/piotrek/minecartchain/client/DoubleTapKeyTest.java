package com.piotrek.minecartchain.client;

/** Standalone regression test; no Minecraft runtime required. */
public final class DoubleTapKeyTest {
	public static void main(String[] args) {
		DoubleTapKey key = new DoubleTapKey(300);
		check(!key.update(true, 0), "first press");
		check(!key.update(true, 100), "held key");
		check(!key.update(false, 150), "release");
		check(key.update(true, 300), "double tap at boundary");
		check(!key.update(true, 350), "hold after activation");
		key.update(false, 360);
		check(!key.update(true, 400), "third press starts new pair");
		key.update(false, 410);
		check(!key.update(true, 701), "slow second tap");
		key.reset();
		check(!key.update(true, 750), "reset clears previous tap");
		key.update(false, 760);
		check(key.update(true, 800), "new pair after reset");
		System.out.println("DoubleTapKeyTest passed");
	}

	private static void check(boolean condition, String scenario) {
		if (!condition) throw new AssertionError(scenario);
	}
}
