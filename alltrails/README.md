# graphhopper-service

AllTrails custom GraphHopper routing service using israelhikingmap/graphhopper Docker as a base. This service was created to support the BYOT project.

## Repository files

- `helm/graphhopper-service` : Helm chart for this service
- `scripts` : scripts for building local and Linux Docker images
- `config` : GraphHopper config files to copy into image at build-time
- `graphhopper` : GraphHopper service.  Fork of Java GraphHopper repo, with AT-specific code changes
- `data` : Mount point for local dev: download OSM PBF files to this, and to build GraphHopper's routing network files to

## Local development

### Building and Running GraphHopper Java App (outside of Docker)

It is useful when making changes to the Java code to be able to build and run it outside a container.
The `mvn` build step is slow - I am sure there is a much more efficient waty to run it incrementally during development.
```Bash
# Build Java app
cd .. # If needed, to root of `graphhopper` repo
mvn clean install -DskipTest
# Run it in "import" mode to import an OSM PBF file and an AT CSV custom attribute file
# Data files should be in `alltrails/data` 
rm -r alltrails/data/default-gh
java -Ddw.graphhopper.datareader.file=alltrails/data/california-latest.osm.pbf \
  -Ddw.graphhopper.graph.location=alltrails/data/default-gh \
  -jar web/target/graphhopper-web-*.jar import alltrails/config/config-alltrails.yml
# Run it as local server, http://localhost:8989/maps/
java -Ddw.graphhopper.graph.location=alltrails/data/default-gh \
  -jar web/target/graphhopper-web-*.jar server alltrails/config/config-alltrails.yml

```

### Building and Running Docker Image

1. Build Docker image:

```Bash
cd .. # If needed, to root of `graphhopper` repo
alltrails/scripts/build_graphhopper.sh
```

2. Run Docker container locally to import data

**Note** Without further Java tuning within the container this import will only work with small OSM PBF files.
Use the raw Java app above to build larger ones.
This method also does not currently support the `datareader.at_csv` import

```Bash
rm -r alltrails/data/default-gh
docker run --rm -p 8989:8989 -v ./alltrails/data:/graphhopper/data graphhopper-service --import -i /graphhopper/data/california-latest.osm.pbf
```

3. Run Docker container locally as a server, http://localhost:8989/maps/

```Bash
docker run --rm -p 8989:8989 -v ./alltrails/data:/graphhopper/data graphhopper-service --host 0.0.0.0
```

4. Build and push Linux image to ECR:

```Bash
# Login if needed (this example is for Mostpaths)
aws eks --region us-west-2 update-kubeconfig --name eks-alpha
kubectl config set-context --current --namespace=alpha # no need to then add -n alpha 
docker login -u AWS -p $(aws ecr get-login-password --region us-west-2) 873326996015.dkr.ecr.us-west-2.amazonaws.com

# Build/push
alltrails/scripts/build_linux_graphhopper.sh
# See below for restarting service with new image
```

5. Updating the Alpha service's data

Upload all 10 files from local data/default-gh folder to default-gh folder in S3 bucket https://us-west-2.console.aws.amazon.com/s3/buckets/alltrails-alpha-us-west-2-graphhopper-service?region=us-west-2&bucketType=general&prefix=default-gh/&showversions=false

Then restart containers with new data (or use this after image change in ECR)

```Bash
kubectl rollout restart deploy graphhopper-service-tester
kubectl get pods | grep graphhopper
```

## Interactive map service URLs
http://localhost:8989/maps/?profile=hike&layer=OpenStreetMap

https://alpha.mostpaths.com/api/alltrails/graphhopper-service/maps/?profile=hike&layer=OpenStreetMap

## Example routing call (can be GET or POST)
http://localhost:8989/route?point=40.3517,-106.3860&point=40.2831,-106.3466&profile=hike

## Routing service info
https://alpha.mostpaths.com/api/alltrails/graphhopper-service/info
