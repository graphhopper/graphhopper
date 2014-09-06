HOME=$(dirname $0)
cd $HOME/..

destination=src/main/resources/com/graphhopper/util/

translations="en_US SKIP de_DE ro pt_PT pt_BR bg es ru ja fr si tr nl it fil gl el uk ca"
file=$1

# You can execute the following
# curl "https://docs.google.com/spreadsheet/pub?key=0AmukcXek0JP6dGM4R1VTV2d3TkRSUFVQakhVeVBQRHc&single=true&gid=0&output=txt" > tmp.tsv
# ./files/update-translations.sh tmp.tsv && rm tmp.tsv

INDEX=1
for tr in $translations; do
  INDEX=$(($INDEX + 1))
  if [[ "x$tr" = "xSKIP" ]]; then
    continue
  fi
  echo -e '# do not edit manually, instead use spreadsheet https://t.co/f086oJXAEI and script ./core/files/update-translations.sh\n' > $destination/$tr.txt
  tail -n+5 "$file" | cut -s -f1,$INDEX --output-delimiter='=' >> $destination/$tr.txt
done
