package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.Collection;

public class OSMRelationFactory
{
    private Collection<OSMRelationFactoryEngine<? extends OSMRelation>> factoryEngines = //
    new ArrayList<OSMRelationFactory.OSMRelationFactoryEngine<? extends OSMRelation>>();

    public OSMRelationFactory()
    {
        factoryEngines.add(new OSMTurnRelation.FactoryEngine());
    }

    /**
     * Specifies an arbitrary OSM relation. If a relation could be specified, it will be returned.
     * 
     * @param relation an arbitrary OSM relation
     * @return the new specified relation, if it could be specified. <code>null</code> else.
     */
    public OSMRelation specify( OSMRelation relation )
    {
        for (OSMRelationFactoryEngine<? extends OSMRelation> engine : factoryEngines)
        {
            OSMRelation specifiedRelation = engine.create(relation);
            if (specifiedRelation != null)
            {
                return specifiedRelation;
            }

        }
        return null;
    }

    public interface OSMRelationFactoryEngine<T extends OSMRelation>
    {
        public T create( OSMRelation relation );
    }
}
