package com.graphhopper.reader.osm;

import java.text.ParseException;
import java.util.Date;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.util.Helper;

public class OSMParse1 implements OSMParseInterface {
	private RelationPreprocessor relationPreprocessor;
    private Date timestamp;
	
	public OSMParse1(RelationPreprocessor relationPreprocessor) {
		this.relationPreprocessor = relationPreprocessor;
	}
	
    @Override
    public void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
        timestamp = Helper.createFormatter().parse(fileHeader.getTag("timestamp"));
    }
	
    @Override
    public void handleRelation(ReaderRelation relation) {
    	relationPreprocessor.preprocessRelation(relation);
    }
        
    public Date getTimeStamp() {
        return timestamp;
    }
}
