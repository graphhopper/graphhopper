HOME=$(dirname $0)
cd $HOME/../..

modules="core web tools"
for module in $modules; do
  echo "====== $module ====="
  cd $module
  mvn test
  EXIT_VAL="$?"
  if [[ "x$EXIT_VAL" != "x0" ]]; then
    exit $EXIT_VAL
  fi
  cd ..
done
