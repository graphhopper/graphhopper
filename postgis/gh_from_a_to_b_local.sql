
CREATE OR REPLACE FUNCTION gh_from_a_to_b_local(lata double precision, lona double precision, latb double precision, lonb double precision, vehicle varchar)
  RETURNS text[] AS
$BODY$
import requests, json,os
def ms_to_hms(milliseconds):
    hours, milliseconds = divmod(milliseconds, 3600000)
    minutes, milliseconds = divmod(milliseconds, 60000)
    seconds = float(milliseconds) / 1000
    s = "%i:%02i" % (hours, minutes)
    return s
def ghroutetime(p1,p2,v):
    gcurl="http://localhost:8989/route?"
    params=[]
    result={}
    #result=''
    for x in range(6):
        params.append([])
    params[0].append('point')
    params[0].append(p1)
    params[1].append('point')
    params[1].append(p2)
    params[2].append('instructions')
    params[2].append('false')
    params[3].append('calc_points')
    params[3].append('true')
    params[4].append('vehicle')
    params[4].append(v)
    params[5].append('points_encoded')
    params[5].append('false')
    data = requests.get(url=gcurl, params=params)
    binary = data.content
    result=json.loads(binary)
    result['paths'][0]['time']=ms_to_hms(result['paths'][0]['time'])
    distance=(result['paths'][0]['distance'])
    time=result['paths'][0]['time']
    geom=json.dumps(result['paths'][0]['points'])
    return distance,time,geom
def format_point(lat, lon):
	p=str(lat)+', '+str(lon)
	return p
try:
	dist,time,geom=ghroutetime(format_point(lata,lona),format_point(latb,lonb),vehicle)
except:
	dist,time,geom=0,0,''
return dist,time,geom	
$BODY$
  LANGUAGE plpythonu VOLATILE
  COST 100;
  
comment on function gh_from_a_to_b_local(lata double precision, lona double precision, latb double precision, lonb double precision, vehicle varchar) is 
'
pl/python function to enable graphhopper routing from postgis.
Make sure to have created the extension plpythonu beforehand.

Result is a text array with 

Distance[m] 
drivetime[hh:mm] 
Geometry as GeoJSON in CRS 4326 (WGS84)
To transform the GeoJSON geometry to wkb, use the postgis function "st_geomfromgeojson(geojson)"
';
