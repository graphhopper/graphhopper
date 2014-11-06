for i in 153*;do egrep -v "osgb:changeHistory|osgb:versionDate|osgb:changeDate|osgb:reasonForChange|osgb:theme|osgb:descriptiveGroup|osgb:version" $i > uc$i;done
#for i in uc153*;do grep -v versionDate $i > vd$i;done
#for i in vduc153*;do grep -v versionDate $i > cd$i;done
#for i in vduc153*;do grep -v changeDate $i > cd$i;done
#for i in cdvduc153*;do grep -v reasonForChange $i > rd$i;done
#for i in rdcdvduc153*;do grep -v osgb:theme $i > th$i;done
#for i in thrdcdvduc153*;do grep -v osgb:descriptiveGroup $i > dg$i;done
#for i in dgthrdcdvduc153*;do grep -v osgb:version $i > vn$i;done

