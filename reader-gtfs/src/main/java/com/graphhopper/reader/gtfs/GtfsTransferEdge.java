package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.model.Transfer;

public class GtfsTransferEdge extends AbstractPtEdge {

	Transfer transfer;

	public GtfsTransferEdge(Transfer transfer) {
		this.transfer = transfer;
	}

	public Transfer getTransfer() {
		return transfer;
	}

}
