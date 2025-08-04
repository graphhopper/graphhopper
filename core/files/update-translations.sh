HOME=$(dirname $0)
cd $HOME/..

destination=src/main/resources/com/graphhopper/util/

translations="en_US SKIP SKIP ar ast az bg bn_BN ca cs_CZ da_DK de_DE el eo es fa fil fi fr_FR fr_CH gl he hr_HR hsb hu_HU in_ID it ja ko kz lt_LT mn nb_NO ne nl pl_PL pt_BR pt_PT ro ru sk sl_SI sr_RS sv_SE tr uk uz vi_VN zh_CN zh_HK zh_TW"
file=$1

# See translations.md for how to run this

INDEX=1
for tr in $translations; do
  INDEX=$(($INDEX + 1))
  if [[ "x$tr" = "xSKIP" ]]; then
    continue
  fi
  echo -e '# do not edit manually, instead use spreadsheet from translations.md and script ./core/files/update-translations.sh\n' > $destination/$tr.txt
  tail -n+5 "$file" | cut -s -f1,$INDEX --output-delimiter='=' >> $destination/$tr.txt
done
