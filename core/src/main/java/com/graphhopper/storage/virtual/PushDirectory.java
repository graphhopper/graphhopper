package com.graphhopper.storage.virtual;

import java.util.HashMap;
import java.util.Map;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;

/**
 * Directory proxy over an actual {@link Directory}.You can see this class as a
 * two layer stack where write are done in the top of the stack and read are
 * done sequentially from top to bottom until a result is found. This class is
 * meant to be used to decorate an exiting graph and add it few mode nodes in
 * order to compute accurate route
 * 
 * @author NG
 * 
 */
public class PushDirectory implements Directory {

	private Directory base;
	/** map of virtual DataAccess */
	protected Map<String, DataAccess> map = new HashMap<String, DataAccess>();
	
	/**
	 * Creates a new instance of PushDirectory over an actual directory
	 * @param base base Directory to wrap
	 */
	public PushDirectory(Directory base) {
		this.base = base;
	}
	
	@Override
	public String location() {
		return base.location();
	}

	@Override
	public DataAccess find(String name) {
		// try to find in current layer
		DataAccess da = map.get(name);
		if(da == null) {
			// fall back on underlying layer
			da = base.find(name);
		}
		return da;
	}
	
	@Override
	public DataAccess findCreate(String name) {
		// try to find in current layer
		DataAccess da = map.get(name);		
		if(da != null) {
			return da;
		}
		// does not exists, create it as a wrapper over the existing DataAccess
		da = base.find(name);
		if(da == null) {
			throw new IllegalArgumentException("No DataAces found for " + name + ". This class is not meant to create DataAcces but only mirror existing ones.");
		}
		da = new PushDataAccess(da);
		map.put(name, da);
		
		return da;
	}

	@Override
	public DataAccess rename(DataAccess da, String newName) {
		if(find(da.name()) == null) {
			throw new IllegalArgumentException(da.name() + " is not contained in the PushDirectory");
		}
		da = find(da.name());
        da.rename(newName);
        map.put(newName, da);
        return da;
	}

	@Override
	public void remove(DataAccess da) {
		map.remove(da.name());
	}

	@Override
	public boolean isLoadRequired() {
		return false;
	}

}
