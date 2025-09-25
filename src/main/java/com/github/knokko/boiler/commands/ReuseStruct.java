package com.github.knokko.boiler.commands;

import org.lwjgl.system.Struct;
import org.lwjgl.system.StructBuffer;

import java.util.function.IntFunction;

import static org.lwjgl.system.MemoryUtil.memSet;

class ReuseStruct<S extends Struct<S>, B extends StructBuffer<S, B>> {

	private final IntFunction<B> stackAlloc, heapAlloc;

	private B cached;
	private boolean isOnHeap;

	ReuseStruct(IntFunction<B> stackAlloc, IntFunction<B> heapAlloc) {
		this.stackAlloc = stackAlloc;
		this.heapAlloc = heapAlloc;
	}

	public B allocate(int capacity) {
		//noinspection ConstantValue
		if (cached == null || cached.capacity() < capacity) {
			if (capacity >= 5) {
				if (isOnHeap) cached.free();
				cached = heapAlloc.apply(capacity);
				isOnHeap = true;
			} else cached = stackAlloc.apply(capacity);
		} else {
			cached.position(0);
			cached.limit(capacity);
			memSet(cached, 0);
		}
		return cached;
	}

	public void cleanUp() {
		if (isOnHeap) cached.free();
	}
}
