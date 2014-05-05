# Graphhopper Repo

This is a branch of the graphhopper repo. We are tracking the original repo by adding a remote repo called upstream.  This was done with the following command (this command  does not need to be run locally, this is just here for informational purposes)...

    git remote add upstream git@github.com:graphhopper/graphhopper.git

## Updating from original

If you want to get the lateset changes from the original repo into this repo, run the following steps.

    git fetch upstream
    git merge upstream/master
    git push

    