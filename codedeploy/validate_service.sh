#!/usr/bin/env bash
PORT=80

if [ "$DEPLOYMENT_GROUP_NAME" == "osrm" ]
then
  PORT=5200
else
  PORT=5200
fi
echo ${PORT}

while true
do
  result=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:5200/route?point=12.980269%2C77.694232&point=12.979178%2C77.69725&type=json&locale=en-GB&vehicle=car&weighting=fastest&elevation=false")
  if [ "$result" == "200" ]; then
    break
  fi
  sleep 5
done


