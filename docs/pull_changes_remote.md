# Pull Changes from GraphHopper remote Workflow

## First step. Add the remote to your local repository

```commandline
git remote add graphhopper git@github.com:graphhopper/graphhopper.git
```

In order to check the remote is added, you can type the flowing command:
```commandline
git remote
```
The remote `graphhopper` should be listed down.

## Second step. Create a branch from develop where we pull the changes from the GH remote

```commandline
git checkout develop
git pull
git checkout -b my_branch_to_pull_latest_changes_from_remote
git pull graphhopper master
```

## Third step. Pull changes from GH remote repository (master branch).
```commandline
git pull graphhopper master
```

## Fourth step. Resolve conflicts and push branch to our repository to create the PR
```commandline
#Resolve conflicts in the IDE
git add .
git commit -m "Message"
git push origin -u my_branch_to_pull_latest_changes_from_remote
```