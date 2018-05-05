# Translation

You can help improve GraphHopper by adding your language! We have a dedicated [forum for translations](https://discuss.graphhopper.com/c/developers/translations) in case you are unsure or want to discuss before changing things.

See [this spreadsheet](https://docs.google.com/spreadsheets/d/10HKSFmxGVEIO92loVQetVmjXT0qpf3EA2jxuQSSYTdU/edit?pli=1#gid=0)
and add a column for your language. Revisit it regularly to update or add new items. And see your language live at GraphHopper Maps e.g. explicitly specify the locale via:

[https://graphhopper.com/maps/?point=40.979898%2C-3.164062&point=39.909736%2C-2.8125**&locale=de**](https://graphhopper.com/maps/?point=40.979898%2C-3.164062&point=39.909736%2C-2.8125&locale=de) 

de → German, en → English, zh → Simplified Chinese, …

There are already many existing :jp: :cn: :us: :fr: :es: :it: :ru: :de:

## Questions

 1. **What does the string after the language name mean ala 'Spanish: es'?**
    This is the language its ISO code (639-1) with two characters. E.g. look up your language on wikipedia to get this code. 
    See [Spanish](http://en.wikipedia.org/wiki/Spanish_language) as an example again.
 2. **What does the strange characters ala '%1$s' in the items means?**
    This is a placeholder which is filled by GraphHopper. It is important to have as in some languages the position
    is different than in other languages or the translation is completely different. 
    Example: "Enter roundabout and use exit %1$s". In German language you have to add the word 'nehmen' after the
    exit-number parameter: "In den Kreisverkehr einfahren und Ausfahrt %1$s nehmen". 
    It is very important that you do not forget about these parameter placeholders, please ask if you are unsure.

## Integrate into GraphHopper

We'll regularly update GraphHopper with new translations or fixes so no need to do this work for you. But if you still
want to try your changes or want to speed up the integration you can do the following:

 * Make GraphHopper working on your computer, where you need to git clone the repository - see [here](./quickstart-from-source.md) for more information.
 * If you created a new language then add it in lexicographical order to TranslationMap.LOCALES (core/src/main/java/com/graphhopper/util) and to the script: core/files/update-translations.sh
 * Do `cd graphhopper/core; curl 'https://docs.google.com/spreadsheets/d/10HKSFmxGVEIO92loVQetVmjXT0qpf3EA2jxuQSSYTdU/export?format=tsv&id=10HKSFmxGVEIO92loVQetVmjXT0qpf3EA2jxuQSSYTdU&gid=0' > tmp.tsv`
 * Then `./files/update-translations.sh tmp.tsv && rm tmp.tsv`
 * Now you can see your changes via `git diff`. Make sure that is the only one with `git status`
 * Now execute `mvn clean test` to see if you did not miss arguments in your translation (see point 2 in the questions above) and start
 the [GraphHopper service](./quickstart-from-source.md) and go to localhost:8989 append e.g. &locale=de if your translation does not show up automatically
 * Read the [contributing guide](https://github.com/graphhopper/graphhopper/blob/master/CONTRIBUTING.md) to submit your changes

## License Agreement

Please sign the <a href="https://github.com/graphhopper/graphhopper/blob/master/.github/CONTRIBUTING.md">GraphHopper License Agreement</a>.
