HOME=$(dirname $0)
cd $HOME/..

destination=src/main/resources/com/graphhopper/util/

translations="en_US SKIP bg ca de_DE el es fa fil fi fr gl he it ja ne nl pt_BR pt_PT ro ru si sk sv_SE tr uk vi_VI zh_CN"
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
