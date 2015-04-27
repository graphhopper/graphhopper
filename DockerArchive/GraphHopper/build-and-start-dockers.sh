#rm ./graphhopper-web-*.war
#cp ../../web/target/graphhopper-web-*.war .

rm -rf graphhopper-web
mkdir graphhopper-web
#jar xvf ../../web/target/graphhopper-web-*.war -C graphhopper-web/
unzip -p ../../web/target/graphhopper-web-*.war -d graphhopper-web/

cp ../../config.properties .

rm -rf graphhopper-gh/
#cp -r /home/phopkins/Documents/graphhopper/core/58096-SX9192-modified-gh/ graphhopper-gh/
cp -r /data/Development/graphhopper/core/target/output/os-itn-hn-test-network-gh/ graphhopper-gh/

sudo docker build -t="wspip/graphhopper:v1" .
sudo docker stop graphhopper-web
sudo docker rm graphhopper-web


#port forward port 8000 (tomcat debug port) to port 8000 for master, 8001 for slave1, 8002 for slave2
sudo docker run -p 80:8080 -p 8000:8000 --name="graphhopper-web" -d wspip/graphhopper:v1

gnome-terminal --geometry 240x60+0+0 --title "GraphHopper: 80" -e "sudo docker start -a graphhopper-web"

