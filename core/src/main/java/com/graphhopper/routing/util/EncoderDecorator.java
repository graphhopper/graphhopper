package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

public interface EncoderDecorator {
	int defineWayBits(int shift);
	long handleWayTags(Way way, long encoded);
	public InstructionAnnotation getAnnotation(long flags, Translation tr);
	long getBitMask(String[] attributes);
	double getDouble(long flags);
	long getLong(long flags);
	boolean supports(int key);
}
