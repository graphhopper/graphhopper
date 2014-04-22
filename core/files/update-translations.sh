HOME=$(dirname $0)
cd $HOME/..

destination=src/main/resources/com/graphhopper/util/

translations="en_US SKIP de_DE ro pt_PT pt_BR bg es ru ja fr si tr nl it fil SKIP"
file=$1
#file=/tmp/gh.csv
#rm $file
#wget -O $file "https://docs.google.com/spreadsheet/ccc?key=0AmukcXek0JP6dGM4R1VTV2d3TkRSUFVQakhVeVBQRHc&pli=1&output=csv"

INDEX=1
for tr in $translations; do
  INDEX=$(($INDEX + 1))
  if [[ "x$tr" = "xSKIP" ]]; then
    continue
  fi
  echo -e '# do not edit manually, instead use spreadsheet https://t.co/f086oJXAEI and script ./core/files/update-translations.sh\n' > $destination/$tr.txt
  tail -n+6 "$file" | cut -d',' -s -f1,$INDEX --output-delimiter='=' >> $destination/$tr.txt
done
