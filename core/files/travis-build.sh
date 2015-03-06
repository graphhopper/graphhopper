HOME=$(dirname $0)
cd $HOME/../..

mvn clean test verify

#modules="core web tools"
#for module in $modules; do
#  echo "====== INSTALL $module ====="
#  mvn -pl $module clean install -DskipTests=true
#  EXIT_VAL="$?"    
#  if [[ "x$EXIT_VAL" != "x0" ]]; then
#    exit $EXIT_VAL
#  fi 
#  
#  echo "====== TEST $module ====="
#  # verify necessary for failsafe, otherwise it won't fail the build!?
#  mvn -pl $module test failsafe:integration-test verify  
#  EXIT_VAL="$?"
#  if [[ "x$EXIT_VAL" != "x0" ]]; then
#    exit $EXIT_VAL
#  fi
#done
