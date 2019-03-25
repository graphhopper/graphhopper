#true is given so that even if the container doesn't exists, the command should not fail, because if the command fails, it will fail the CodeDeploy process also.
# Sleeping for 60 seconds before stopping the container. This will ensure that if there are any current requests being served they should be completed.
sleep 60
sudo docker stop graphhopper || true
sudo docker rm graphhopper || true
sudo rm -rf /home/ubuntu/graphhopper