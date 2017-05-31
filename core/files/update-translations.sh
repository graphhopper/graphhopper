HOME=$(dirname $0)
cd $HOME/..

destination=src/main/resources/com/graphhopper/util/

translations="en_US SKIP SKIP ar ast bg ca cs_CZ da_DK de_DE el eo es fa fil fi fr_FR fr_CH gl he hr_HR hsb hu_HU it ja ko lt_LT ne nl pl_PL pt_BR pt_PT ro ru sk sl_SI sr_RS sv_SE tr uk vi_VI zh_CN zh_HK"
file=$1

# You can execute the following
# curl 'https://docs.google.com/spreadsheets/d/10HKSFmxGVEIO92loVQetVmjXT0qpf3EA2jxuQSSYTdU/export?format=tsv&id=10HKSFmxGVEIO92loVQetVmjXT0qpf3EA2jxuQSSYTdU&gid=0' > tmp.tsv
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
