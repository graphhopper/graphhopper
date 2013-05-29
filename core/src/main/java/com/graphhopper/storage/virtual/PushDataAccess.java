package com.graphhopper.storage.virtual;

import java.util.HashMap;
import java.util.Map;

import com.graphhopper.storage.DataAccess;

/**
 * DataAcces proxy over an actual {@link DataAccess}. You can see this class as a two
 * layer stack where write are done in the top of the stack and read are done
 * sequentially from top to bottom until a result is found.
 * This class is meant to be used to decorate an exiting graph and add it few
 * mode nodes in order to compute accurate route
 * 
 * @author NG
 * 
 */
public class PushDataAccess implements DataAccess {

	private DataAccess base;
	/** map containing the virtual graph's data */
	private Map<Long, Integer> map = new HashMap<Long, Integer>();
	
	/**
	 * Creates the PushDataAccess
	 * @param base base DataAccess to wrap
	 */
	public PushDataAccess(DataAccess base) {
		this.base = base;
	}
	
	@Override
	public boolean loadExisting() {
		return true;
	}

	@Override
	public void flush() {
		throw new RuntimeException("PushDataAcces are not meant to be flushed");
	}

	@Override
	public void close() {
		throw new RuntimeException("PushDataAcces are not meant to be closed");
	}

	@Override
	public long capacity() {
		return base.capacity() + 2*segmentSize();
	}

	@Override
	public String name() {
		return base.name();
	}

	@Override
	public void rename(String newName) {
		throw new RuntimeException("PushDataAcces are not meant to be renamed");
	}

	@Override
	public void setInt(long index, int value) {
		this.map.put(index, value);
	}

	@Override
	public int getInt(long index) {
		// look into it's own data first
		Integer val = this.map.get(index);
		if(val == null) {
			// fall back on underlying data access
			val = base.getInt(index);
		}
		return val;
	}

	@Override
	public void setHeader(int index, int value) {
		throw new RuntimeException("Headers not implemented for PushDataAcces");
	}

	@Override
	public int getHeader(int index) {
		return base.getHeader(index);
	}

	@Override
	public DataAccess create(long bytes) {
		return this;
	}

	@Override
	public void ensureCapacity(long bytes) {
		
	}

	@Override
	public void trimTo(long bytes) {
		throw new RuntimeException("PushDataAcces are not meant to be trimed");
	}

	@Override
	public DataAccess copyTo(DataAccess da) {
		throw new RuntimeException("PushDataAcces are not meant to be copied");
	}

	@Override
	public DataAccess segmentSize(int bytes) {
		return this;
	}

	@Override
	public int segmentSize() {
		return base.segmentSize();
	}

	@Override
	public int segments() {
		return base.segments() + this.map.size();
	}

}
