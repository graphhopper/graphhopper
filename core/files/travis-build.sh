HOME=$(dirname $0)
cd $HOME/../..

modules="core web tools"
for module in $modules; do
  echo "====== $module ====="
  cd $module
  # INSTALL    
  mvn install -DskipTests=true
  if [[ "x$EXIT_VAL" != "x0" ]]; then
    exit $EXIT_VAL
  fi 
  
  # TEST
  mvn test
  EXIT_VAL="$?"
  if [[ "x$EXIT_VAL" != "x0" ]]; then
    exit $EXIT_VAL
  fi
  cd ..
done
